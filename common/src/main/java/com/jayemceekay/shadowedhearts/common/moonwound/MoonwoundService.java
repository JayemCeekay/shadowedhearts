package com.jayemceekay.shadowedhearts.common.moonwound;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.config.ServerConfig;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder;
import com.jayemceekay.shadowedhearts.content.umbrafall.*;
import com.jayemceekay.shadowedhearts.common.moonwound.raid.MoonwoundRaidIntegration;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.moonwound.*;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.jayemceekay.shadowedhearts.world.umbrafall.*;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.function.BooleanSupplier;

import static com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.*;

/** Server-owned opt-in excavation. Client snapshots never authorize mining, combat or rewards. */
public final class MoonwoundService {
    private static final int RANGE = 64;
    private static final double ENCOUNTER_ROAM_RADIUS = 32;
    private static final double ENCOUNTER_VERTICAL_RANGE = 48;
    private static final Map<UUID, Long> ACTION_TICKS = new HashMap<>();
    private record Mining(ServerLevel level, BlockPos pos) { }
    private static final ThreadLocal<Mining> MINING = new ThreadLocal<>();
    private record Located(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) { }
    private MoonwoundService() { }

    public static void init() {
        MoonwoundRaidIntegration.initialize();
        TickEvent.SERVER_POST.register(MoonwoundService::tick);
        PlayerEvent.PLAYER_QUIT.register(player -> ACTION_TICKS.remove(player.getUUID()));
        LifecycleEvent.SERVER_STOPPED.register(server -> { ACTION_TICKS.clear(); MINING.remove(); });
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> dispatcher.register(
                Commands.literal("moonwound").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var found = nearest(player);
                    if (found == null) message(player, Component.translatable("message.shadowedhearts.moonwound.nearby_party_not_found"));
                    else if (MoonwoundClaimProtection.canInteract(player, found.level, found.site)) send(player, found.site, found.event, true);
                    else permissionDenied(player);
                    return 1;
                }).then(Commands.literal("leave").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Located found = activeMembership(player);
                    if (found == null) message(player, Component.translatable("message.shadowedhearts.moonwound.active_party_required"));
                    else leave(player, found);
                    return 1;
                })).then(Commands.literal("release").executes(context -> releaseClaimedSite(context.getSource().getPlayerOrException())))
                .then(Commands.literal("list").requires(source -> source.hasPermission(2))
                        .executes(context -> listActiveEvents(context.getSource())))
                .then(Commands.literal("cancel").requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            Located found = activeEvents(player.getServer()).stream()
                                    .filter(entry -> near(player, entry.level, entry.site))
                                    .min(Comparator.comparingDouble(entry -> entry.site.center.distSqr(player.blockPosition())))
                                    .orElse(null);
                            return cancelEvent(context.getSource(), found);
                        }).then(Commands.argument("event", UuidArgument.uuid())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        activeEvents(context.getSource().getServer()).stream()
                                                .map(entry -> entry.event.eventId().toString()), builder))
                                .executes(context -> {
                                    UUID id = UuidArgument.getUuid(context, "event");
                                    Located found = activeEvents(context.getSource().getServer()).stream()
                                            .filter(entry -> entry.event.eventId().equals(id)).findFirst().orElse(null);
                                    return cancelEvent(context.getSource(), found);
                                })))));
        MoonwoundEncounters.init(new MoonwoundEncounters.Listener() {
            @Override public boolean isActive(MinecraftServer server, UUID eventId, UUID pokemonId) {
                Located found = find(server, eventId);
                return found != null && pending(found.event, pokemonId);
            }
            @Override public boolean canParticipate(MinecraftServer server, UUID eventId, UUID playerId) {
                Located found = find(server, eventId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                return found != null && near(player, found.level, found.site)
                        && MoonwoundClaimProtection.canParticipate(player, eventId);
            }
            @Override public boolean canSpawn(ServerLevel level, UUID eventId, AABB bounds) {
                return MoonwoundClaimProtection.canSpawn(level, eventId, bounds);
            }
            @Override public boolean canInteractEncounter(ServerPlayer player, UUID eventId, AABB bounds) {
                return MoonwoundClaimProtection.canInteractEncounter(player, eventId, bounds);
            }
            @Override public void onDefeated(MinecraftServer server, UUID eventId, UUID pokemonId, Set<UUID> winners) {
                Located found = find(server, eventId);
                if (found == null || !MoonwoundClaimProtection.canRun(found.level, found.site, found.event)
                        || winners.stream().map(server.getPlayerList()::getPlayer).filter(Objects::nonNull)
                        .noneMatch(player -> MoonwoundClaimProtection.canParticipate(player, eventId))) return;
                boolean cleared = found.event.phase() == Phase.OVERLOAD ? found.event.completeOverload(pokemonId, true)
                        : found.event.recordDefeat(pokemonId);
                if (cleared) MoonwoundEncounters.discard(found.level, eventId, pokemonId);
                if (found.event.phase() == Phase.COMPLETE) finish(found.level, found.site, found.event);
                else applyPaceBonus(found.level, found.site, found.event);
                publish(found.level, found.site, found.event);
            }
            @Override public void onCaptured(MinecraftServer server, UUID eventId, UUID pokemonId, UUID playerId) {
                Located found = find(server, eventId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (found == null || player == null || !MoonwoundClaimProtection.canParticipate(player, eventId)) return;
                if (found.event.completeOverload(pokemonId, true)) {
                    finish(found.level, found.site, found.event);
                    publish(found.level, found.site, found.event);
                }
            }
        });
    }

    /** Empty-hand or stabilizer interaction explicitly opts a site into a lobby, not into an active event. */
    public static boolean interact(ServerPlayer player, BlockPos pos, ItemStack held) {
        if ((!held.isEmpty() && !held.is(ModItems.PURE_STABILIZER.get())) || player.isSpectator()
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64) return false;
        ServerLevel level = player.serverLevel();
        var site = siteAt(level, pos);
        if (site == null || !eligible(site) || !originalAt(level, site, pos)) return false;
        if (!MoonwoundClaimProtection.canInteract(player, level, site)
                || !FTBChunksClaimBridgeHolder.INSTANCE.canInteractBlock(player, pos, InteractionHand.MAIN_HAND)) {
            permissionDenied(player);
            return true;
        }
        var data = MoonwoundSavedData.get(level);
        MoonwoundEvent event = data.find(site.id);
        if (event != null && !reconcilePresence(level, site, event, 0)) event = null;
        if (event == null) {
            if (data.isReserved(site.id)) {
                message(player, Component.translatable("message.shadowedhearts.moonwound.record_needs_attention"));
                return true;
            }
            if (!site.state.active() || site.rewardClaimed) return false;
            if (!ServerConfig.INSTANCE.newEventsEnabled()) {
                message(player, Component.translatable("message.shadowedhearts.moonwound.events_disabled"));
                return true;
            }
            if (activeMembership(player) != null) {
                message(player, Component.translatable("message.shadowedhearts.moonwound.party_already_joined"));
                return true;
            }
            // Capture all difficulty before reserving the lobby; config reloads cannot retune it.
            event = new MoonwoundEvent(site.id, player.getUUID(), ServerConfig.INSTANCE.excavationTargets(),
                    ServerConfig.INSTANCE.waveOpponentsPerMember(), ServerConfig.INSTANCE.moonwoundDifficulty());
            if (!data.add(event)) return true;
        }
        send(player, site, event, true);
        return true;
    }

    public static void handleAction(ServerPlayer player, MoonwoundActionPacket packet) {
        ServerLevel level = player.serverLevel();
        // Leaving is idempotent and must not be swallowed by a console refresh in the same tick.
        // It also remains available after travelling away or changing dimension.
        if (packet.getAction() == MoonwoundActionPacket.Action.LEAVE) {
            Located found = find(player.getServer(), packet.getEventId());
            if (found != null && found.event.siteId().equals(packet.getSiteId())) leave(player, found);
            return;
        }
        long now = level.getGameTime();
        if (ACTION_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE) == now) return;
        ACTION_TICKS.put(player.getUUID(), now);
        var site = UmbrafallSiteData.get(level).sites.get(packet.getSiteId());
        var event = MoonwoundSavedData.get(level).find(packet.getSiteId());
        if (site == null || event == null || !event.eventId().equals(packet.getEventId())
                || !eligible(site)) {
            ShadowedHeartsNetwork.sendToPlayer(player, new MoonwoundClosedPacket(packet.getEventId()));
            return;
        }
        if (!near(player, level, site) || player.isSpectator()) return;
        if (!MoonwoundClaimProtection.canInteract(player, level, site)) {
            ShadowedHeartsNetwork.sendToPlayer(player, new MoonwoundClosedPacket(event.eventId()));
            permissionDenied(player);
            return;
        }
        if (!reconcilePresence(level, site, event, 0)) return;
        UUID actor = player.getUUID();
        switch (packet.getAction()) {
            case JOIN -> {
                Located membership = activeMembership(player);
                if (membership == null || membership.event == event) event.requestJoin(actor);
                else message(player, Component.translatable("message.shadowedhearts.moonwound.party_already_joined"));
            }
            case LEAVE -> { } // Handled before proximity checks above.
            case READY -> event.setReady(actor, !event.ready().contains(actor));
            case ACCEPT_JOIN -> {
                ServerPlayer applicant = packet.getTarget() == null ? null : level.getServer().getPlayerList().getPlayer(packet.getTarget());
                if (near(applicant, level, site) && activeMembership(applicant) == null
                        && MoonwoundClaimProtection.canInteract(applicant, level, site)) event.acceptJoin(actor, applicant.getUUID());
            }
            case DECLINE_JOIN -> { if (packet.getTarget() != null) event.declineJoin(actor, packet.getTarget()); }
            case START -> start(player, site, event);
            case VOTE_HARVEST -> vote(player, site, event, Outcome.HARVEST);
            case VOTE_SEAL -> vote(player, site, event, Outcome.SEAL);
            case VOTE_OVERLOAD -> vote(player, site, event, Outcome.OVERLOAD);
            case REFRESH -> { }
        }
        publish(level, site, event);
        send(player, site, event, false);
    }

    private static void leave(ServerPlayer player, Located found) {
        MoonwoundEvent event = found.event;
        UUID actor = player.getUUID();
        if (event.phase() == Phase.LOBBY) {
            if (event.host().equals(actor)) {
                if (event.members().size() == 1) {
                    closeLobby(found.level, event, Component.translatable("message.shadowedhearts.moonwound.lobby_closed"));
                    return;
                }
                event.transferHost(actor, event.members().stream().filter(id -> !id.equals(actor)).findFirst().orElseThrow());
            }
            if (!event.leaveLobby(actor)) return;
            message(player, Component.translatable("message.shadowedhearts.moonwound.lobby_left"));
        } else {
            if (!event.abandon(actor)) return;
            // Revoke eligibility first: synchronous battle/capture cleanup may dispatch callbacks.
            MoonwoundEncounters.leaveParticipant(found.level, event.eventId(), actor);
            MoonwoundRaidIntegration.leaveParticipant(event.eventId(), actor);
            if (event.phase() == Phase.ABANDONED) abandonSite(found.level, found.site, event);
            else announce(found.level, event, event.phase() == Phase.VOTE
                    ? Component.translatable("message.shadowedhearts.moonwound.member_abandoned_vote", player.getGameProfile().getName())
                    : Component.translatable("message.shadowedhearts.moonwound.member_abandoned", player.getGameProfile().getName()));
            message(player, Component.translatable("message.shadowedhearts.moonwound.event_abandoned"));
        }
        if (found.site != null) {
            publish(found.level, found.site, event);
            if (player.serverLevel() == found.level) send(player, found.site, event, false);
        }
    }

    private static void abandonSite(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        abandonSite(level, site, event, false);
    }

    private static void abandonSite(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event, boolean forceCleanup) {
        if (event.phase() != Phase.ABANDONED) return;
        boolean unresolved = site == null || !site.rewardClaimed || site.state.active();
        boolean corePresent = site != null && level.hasChunkAt(site.core)
                && level.getBlockEntity(site.core) instanceof UmbrafallCoreBlockEntity core && core.belongsToSite(site.id);
        // Commit a spent core before releasing the gate, so normal core breaking cannot award it.
        if (site != null && unresolved) markAbandonedCoreSpent(level, site);
        if (forceCleanup || unresolved || corePresent) {
            MoonwoundEncounters.cleanupEvent(level, event.eventId());
            MoonwoundRaidIntegration.cleanupEvent(event.eventId());
        }
        if (corePresent && MoonwoundClaimProtection.canRetireCore(level, site, event)) {
            level.setBlockAndUpdate(site.core, ModBlocks.UMBRAFALL_SHELL.get().defaultBlockState());
        }
    }

    private static void markAbandonedCoreSpent(ServerLevel level, UmbrafallSiteData.Site site) {
        if (site.rewardClaimed && !site.state.active()) return;
        site.rewardClaimed = true;
        site.state = UmbrafallCoreState.RUPTURED;
        UmbrafallSiteData.get(level).setDirty();
    }

    private static void start(ServerPlayer host, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        ServerLevel level = host.serverLevel();
        if (event.phase() != Phase.LOBBY || !event.host().equals(host.getUUID())) return;
        if (!loaded(level, site)) { message(host, Component.translatable("message.shadowedhearts.moonwound.start_terrain_unloaded")); return; }
        if (!MoonwoundClaimProtection.canEditSite(host, level, site)) { permissionDenied(host); return; }
        if (!site.state.active() || site.rewardClaimed || !level.getBlockState(site.core).is(ModBlocks.UMBRAFALL_CORE.get())) {
            message(host, Component.translatable("message.shadowedhearts.moonwound.core_unavailable")); return;
        }
        for (UUID member : event.members()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (!near(player, level, site) || !MoonwoundClaimProtection.canInteract(player, level, site)
                    || BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
                message(host, Component.translatable("message.shadowedhearts.moonwound.start_party_unavailable")); return;
            }
        }
        Map<Layer, Set<Long>> positions = new EnumMap<>(Layer.class);
        for (Layer layer : Layer.values()) positions.put(layer, new LinkedHashSet<>());
        var shape = site.shape();
        var cursor = shape.cursor();
        cursor.scan(cursor.total(), (x, y, z, material) -> {
            Layer layer = layer(shape.structuralLayerAt(x, y, z));
            BlockPos pos = site.center.offset(x, y, z);
            if (layer != null && originalAt(level, site, pos)) positions.get(layer).add(pos.asLong());
        });
        if (positions.values().stream().anyMatch(Set::isEmpty)) {
            message(host, Component.translatable("message.shadowedhearts.moonwound.layers_incomplete")); return;
        }
        event.setEligiblePositions(positions);
        configurePace(event);
        if (!event.start(host.getUUID(), level.getGameTime())) { message(host, Component.translatable("message.shadowedhearts.moonwound.start_members_not_ready")); return; }
        announce(level, event, Component.translatable("message.shadowedhearts.moonwound.event_started"));
    }

    private static void vote(ServerPlayer player, UmbrafallSiteData.Site site, MoonwoundEvent event, Outcome outcome) {
        ServerLevel level = player.serverLevel();
        if (event.phase() != Phase.VOTE || !event.isActiveMember(player.getUUID())) return;
        if (!canResolve(player, level, site, event)) { permissionDenied(player); return; }
        if (!coreExposed(level, site)) { message(player, Component.translatable("message.shadowedhearts.moonwound.vote_core_not_exposed")); return; }
        if (outcome == Outcome.SEAL && stabilizerHolder(level, site, event) == null) {
            message(player, Component.translatable("message.shadowedhearts.moonwound.seal_stabilizer_required")); return;
        }
        VoteResult result = event.vote(player.getUUID(), outcome);
        if (result == VoteResult.REVOTE) announce(level, event, Component.translatable("message.shadowedhearts.moonwound.vote_no_majority"));
        Outcome winner = event.majorityOutcome();
        if (winner == null) return;
        // Recheck at commitment, before consuming the stabilizer or changing saved outcome.
        if (!canResolve(player, level, site, event)) { permissionDenied(player); return; }
        if (winner == Outcome.SEAL) {
            ServerPlayer holder = stabilizerHolder(level, site, event);
            if (holder == null) { announce(level, event, Component.translatable("message.shadowedhearts.moonwound.seal_waiting_for_stabilizer")); return; }
            // Validate and consume on the same server thread; no client inventory claim is trusted.
            if (!consumeStabilizer(holder)) return;
        }
        if (winner == Outcome.OVERLOAD) {
            event.selectFinaleMode(site.tier == UmbrafallTier.RESONANT && MoonwoundRaidIntegration.isAvailable()
                    ? FinaleMode.RAID : FinaleMode.ALPHA);
        }
        if (event.resolveVote(winner)) {
            if (event.phase() == Phase.COMPLETE) finish(level, site, event);
            else {
                applyPaceBonus(level, site, event);
                announce(level, event, event.finaleMode() == FinaleMode.RAID
                        ? Component.translatable("message.shadowedhearts.moonwound.overload_raid")
                        : Component.translatable("message.shadowedhearts.moonwound.overload_alpha"));
            }
        }
    }

    private static boolean canResolve(ServerPlayer player, ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        boolean revoked = false;
        for (UUID voter : List.copyOf(event.votes().keySet())) {
            ServerPlayer previous = level.getServer().getPlayerList().getPlayer(voter);
            if (!MoonwoundClaimProtection.canInteract(previous, level, site)
                    || !FTBChunksClaimBridgeHolder.INSTANCE.canEditBlock(previous, site.core, InteractionHand.MAIN_HAND)) {
                event.revokeConsent(voter);
                revoked = true;
            }
        }
        if (revoked) return false;
        return MoonwoundClaimProtection.canInteract(player, level, site)
                && FTBChunksClaimBridgeHolder.INSTANCE.canEditBlock(player, site.core, InteractionHand.MAIN_HAND)
                && MoonwoundClaimProtection.canRun(level, site, event);
    }

    private static ServerPlayer stabilizerHolder(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        for (UUID member : event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (near(player, level, site) && MoonwoundClaimProtection.canInteract(player, level, site)
                    && player.getInventory().contains(new ItemStack(ModItems.PURE_STABILIZER.get()))) return player;
        }
        return null;
    }

    static boolean consumeStabilizer(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.PURE_STABILIZER.get())) {
                stack.shrink(1); player.getInventory().setChanged(); return true;
            }
        }
        return false;
    }

    /** Wrap the real vanilla break: count only successful removal and always release its narrow permit. */
    public static boolean mine(ServerPlayer player, BlockPos pos, BooleanSupplier vanilla) {
        ServerLevel level = player.serverLevel();
        Located found = protectedAt(level, pos);
        if (found == null) return vanilla.getAsBoolean();
        if (found.event != null && (!MoonwoundClaimProtection.canRun(level, found.site, found.event)
                || !MoonwoundClaimProtection.canInteract(player, level, found.site)
                || !FTBChunksClaimBridgeHolder.INSTANCE.canEditBlock(player, pos, InteractionHand.MAIN_HAND))) {
            message(player, Component.translatable("message.shadowedhearts.moonwound.claim_paused"));
            return false;
        }
        if (found.event == null || !canMine(found.event, player, found.site, pos)) {
            message(player, Component.translatable("message.shadowedhearts.moonwound.layer_protected"));
            return false;
        }
        Mining previous = MINING.get();
        MINING.set(new Mining(level, pos.immutable()));
        boolean removed;
        try { removed = vanilla.getAsBoolean(); }
        finally { if (previous == null) MINING.remove(); else MINING.set(previous); }
        if (removed && level.getBlockState(pos).isAir()) {
            Layer layer = layerAt(found.site, pos);
            Phase before = found.event.phase();
            found.event.recordMined(player.getUUID(), layer, pos.asLong());
            found.event.markRemoved(layer, pos.asLong());
            if (before != found.event.phase()) {
                applyPaceBonus(level, found.site, found.event);
                announce(level, found.event, Component.translatable("message.shadowedhearts.moonwound.wave_started"));
            }
            publish(level, found.site, found.event);
        }
        return removed;
    }

    private static boolean canMine(MoonwoundEvent event, ServerPlayer player, UmbrafallSiteData.Site site, BlockPos pos) {
        if (!event.isActiveMember(player.getUUID()) || player.isSpectator() || pos.equals(site.core)
                || BattleRegistry.getBattleByParticipatingPlayer(player) != null) return false;
        Layer layer = layerAt(site, pos);
        if (layer == null) return false;
        if (event.phase() == Phase.VOTE) return true; // Finish the tunnel to the final core objective.
        return event.phase().isExcavation() && layer.ordinal() <= event.phase().layer().ordinal();
    }

    public static boolean protectedBlock(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && server.getServer().isSameThread()
                && !permitted(server, pos) && protectedAt(server, pos) != null;
    }

    public static boolean preventChange(Level level, BlockPos pos, BlockState replacement) {
        return !level.getBlockState(pos).equals(replacement) && protectedBlock(level, pos);
    }

    private static boolean permitted(ServerLevel level, BlockPos pos) {
        Mining permit = MINING.get();
        return permit != null && permit.level == level && permit.pos.equals(pos);
    }

    /** No per-block entity or full-geometry rebuild: saved coordinate membership owns the barrier. */
    private static Located protectedAt(ServerLevel level, BlockPos pos) {
        if (!meteorMaterial(level.getBlockState(pos))) return null;
        var data = MoonwoundSavedData.get(level);
        for (var site : UmbrafallSiteData.get(level).sites.values()) {
            if (!eligible(site) || !inside(site, pos)) continue;
            MoonwoundEvent event = data.find(site.id);
            if (event == null) {
                if (data.isReserved(site.id) && originalAt(level, site, pos)) return new Located(level, site, null);
                continue;
            }
            if (event.phase() == Phase.LOBBY || event.phase().isTerminal()) continue;
            if (site.core.equals(pos)) return new Located(level, site, event);
            Layer layer = layerAt(site, pos);
            if (layer != null && event.eligiblePositions(layer).contains(pos.asLong())
                    && !event.removedPositions(layer).contains(pos.asLong()) && originalAt(level, site, pos)) return new Located(level, site, event);
        }
        return null;
    }

    public static boolean controlsCore(ServerLevel level, UUID siteId) {
        var data = MoonwoundSavedData.get(level);
        var event = data.find(siteId);
        if (event != null && (event.phase() == Phase.ABANDONED || event.phase() == Phase.COMPLETE)) {
            // Event and site stores may have been flushed separately. Reconcile the receipt before
            // core.rupture can see an old active site, even before the next event-service tick.
            var site = UmbrafallSiteData.get(level).sites.get(siteId);
            if (site == null) return true;
            if (event.phase() == Phase.COMPLETE) reconcileCompletedSite(level, site, event);
            else markAbandonedCoreSpent(level, site);
            // The receipt may be committed while a new claim forbids the physical cleanup.
            // Keep the core's own tick/rupture callbacks from bypassing that deferred write.
            return !MoonwoundClaimProtection.canRetireCore(level, site, event);
        }
        return event == null ? data.isReserved(siteId) : event.phase() != Phase.LOBBY && !event.phase().isTerminal();
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (MoonwoundEvent event : List.copyOf(MoonwoundSavedData.get(level).events())) {
                var site = UmbrafallSiteData.get(level).sites.get(event.siteId());
                if (site == null || !eligible(site)) continue;
                try {
                    if (!reconcilePresence(level, site, event, 20)) continue;
                    if (event.phase() == Phase.COMPLETE) finish(level, site, event);
                    else if (event.phase() == Phase.ABANDONED) abandonSite(level, site, event);
                    else if (!presentMembers(level, site, event).isEmpty() && loaded(level, site)
                            && MoonwoundClaimProtection.canRun(level, site, event)) {
                        event.advancePace(20);
                        applyPaceBonus(level, site, event);
                        if (event.phase().isWave() || event.phase() == Phase.OVERLOAD) encounters(level, site, event);
                    }
                    if (level.players().stream().anyMatch(player -> player.distanceToSqr(Vec3.atCenterOf(site.center)) <= 96 * 96)) publish(level, site, event);
                } catch (RuntimeException error) {
                    Shadowedhearts.LOGGER.error("Moonwound {} paused this tick; its saved state was retained", event.eventId(), error);
                }
            }
        }
    }

    /** Reconcile before terrain/spawn gates and before actions, including while the site is unloaded. */
    private static boolean reconcilePresence(ServerLevel level, UmbrafallSiteData.Site site,
                                             MoonwoundEvent event, int elapsedTicks) {
        if (event.phase().isTerminal()) return true;
        if (reconcileSpentSite(level, site, event)) return true;
        if (event.phase() != Phase.LOBBY) configurePace(event);
        for (UUID member : event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null && player.serverLevel() == level
                    && !MoonwoundClaimProtection.canInteract(player, level, site)) event.revokeConsent(member);
        }
        int graceTicks = ServerConfig.INSTANCE.missingMemberGraceSeconds() * 20;
        Set<UUID> present = presentMembers(level, site, event);
        int previousRound = event.voteRound();
        event.updatePresence(present, elapsedTicks, graceTicks);
        if (event.phase() != Phase.LOBBY) {
            if (event.voteRound() != previousRound) announce(level, event,
                    Component.translatable("message.shadowedhearts.moonwound.availability_changed"));
            return true;
        }
        if (event.lobbyIdleTicks() >= ServerConfig.INSTANCE.lobbyIdleTimeoutSeconds() * 20) {
            closeLobby(level, event, Component.translatable("message.shadowedhearts.moonwound.lobby_expired"));
            return false;
        }
        Set<UUID> expired = event.expiredLobbyMembers(graceTicks);
        if (expired.isEmpty()) return true;
        if (expired.contains(event.host())) {
            UUID nextHost = event.members().stream().filter(id -> !expired.contains(id) && present.contains(id))
                    .findFirst().orElseGet(() -> event.members().stream().filter(id -> !expired.contains(id)).findFirst().orElse(null));
            if (nextHost == null) {
                closeLobby(level, event, Component.translatable("message.shadowedhearts.moonwound.lobby_closed_away"));
                return false;
            }
            event.transferHost(event.host(), nextHost);
            announce(level, event, Component.translatable("message.shadowedhearts.moonwound.host_changed", playerName(level, nextHost)));
        }
        for (UUID member : expired) {
            if (!event.leaveLobby(member)) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null) message(player, Component.translatable("message.shadowedhearts.moonwound.lobby_left_away"));
        }
        return true;
    }

    private static Set<UUID> presentMembers(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        Set<UUID> present = new LinkedHashSet<>();
        for (UUID member : event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (near(player, level, site) && MoonwoundClaimProtection.canInteract(player, level, site)) present.add(member);
        }
        return present;
    }

    private static void closeLobby(ServerLevel level, MoonwoundEvent event, Component reason) {
        if (!MoonwoundSavedData.get(level).removeLobby(event.siteId())) return;
        notifyClosed(level, event, reason);
    }

    private static void notifyClosed(ServerLevel level, MoonwoundEvent event, Component reason) {
        // Any former viewer may still have the console open, even after teleporting away.
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            ShadowedHeartsNetwork.sendToPlayer(player, new MoonwoundClosedPacket(event.eventId()));
            if (event.members().contains(player.getUUID()) || event.joinRequests().contains(player.getUUID())) message(player, reason);
        }
    }

    /** Operator lookup includes orphaned records and unloaded sites without loading their chunks. */
    private static List<Located> activeEvents(MinecraftServer server) {
        List<Located> active = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (MoonwoundEvent event : MoonwoundSavedData.get(level).events()) {
                if (!event.phase().isTerminal()) {
                    active.add(new Located(level, UmbrafallSiteData.get(level).sites.get(event.siteId()), event));
                }
            }
        }
        return active;
    }

    private static int listActiveEvents(CommandSourceStack source) {
        List<Located> active = activeEvents(source.getServer());
        if (active.isEmpty()) source.sendSuccess(() -> Component.translatable("message.shadowedhearts.moonwound.no_active_moonwound_events"), false);
        for (Located found : active) {
            Component location = found.site == null
                    ? Component.translatable("message.shadowedhearts.moonwound.location_missing", found.level.dimension().location().toString())
                    : Component.translatable("message.shadowedhearts.moonwound.location", found.level.dimension().location().toString(), found.site.core.toShortString());
            long online = found.event.activeMembers().stream()
                    .filter(id -> source.getServer().getPlayerList().getPlayer(id) != null).count();
            Component details = Component.translatable("message.shadowedhearts.moonwound.event_listing", found.event.eventId().toString(), Component.translatable("moonwound.phase." + found.event.phase().name().toLowerCase(java.util.Locale.ROOT)), location, online, found.event.activeMembers().size(), found.event.siteId().toString());
            source.sendSuccess(() -> details, false);
        }
        return active.size();
    }

    private static int cancelEvent(CommandSourceStack source, Located found) {
        if (found == null) {
            source.sendFailure(Component.translatable("message.shadowedhearts.moonwound.event_not_found"));
            return 0;
        }
        MoonwoundEvent event = found.event;
        boolean lobby = event.phase() == Phase.LOBBY;
        if (lobby) {
            if (!MoonwoundSavedData.get(found.level).removeLobby(event.siteId())) {
                source.sendFailure(Component.translatable("message.shadowedhearts.moonwound.release_failed"));
                return 0;
            }
        } else {
            // Revoke participation and completion before battle/capture cleanup can call back.
            // Retain the terminal receipt so a stale core save cannot pay out or restart the event.
            abandonParty(found.level, found.site, event);
        }
        Component reason = lobby
                ? Component.translatable("message.shadowedhearts.moonwound.operator_lobby_cancelled")
                : Component.translatable("message.shadowedhearts.moonwound.operator_event_cancelled");
        notifyClosed(found.level, event, reason);
        source.sendSuccess(() -> (lobby ? Component.translatable("message.shadowedhearts.moonwound.cancelled_lobby", event.eventId().toString()) : Component.translatable("message.shadowedhearts.moonwound.cancelled_event", event.eventId().toString())), true);
        return 1;
    }

    /** A current land editor can release a claim-blocked event without operator access. */
    private static int releaseClaimedSite(ServerPlayer player) {
        Located found = activeEvents(player.getServer()).stream()
                .filter(entry -> near(player, entry.level, entry.site)
                        && MoonwoundClaimProtection.canRecover(player, entry.level, entry.site, entry.event))
                .min(Comparator.comparingDouble(entry -> entry.site.center.distSqr(player.blockPosition())))
                .orElse(null);
        if (found == null) {
            message(player, Component.translatable("message.shadowedhearts.moonwound.claim_release_denied"));
            return 0;
        }
        if (found.event.phase() == Phase.LOBBY) closeLobby(found.level, found.event,
                Component.translatable("message.shadowedhearts.moonwound.claim_released"));
        else {
            abandonParty(found.level, found.site, found.event);
            notifyClosed(found.level, found.event, Component.translatable("message.shadowedhearts.moonwound.claim_released"));
        }
        message(player, Component.translatable("message.shadowedhearts.moonwound.claim_released"));
        return 1;
    }

    private static void abandonParty(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        for (UUID member : List.copyOf(event.activeMembers())) event.abandon(member);
        abandonSite(level, site, event, true);
    }

    /** Also repair the reverse partial save: a spent site paired with an older active event. */
    private static boolean reconcileSpentSite(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (site == null || event.phase() == Phase.LOBBY || event.phase().isTerminal()
                || (!site.rewardClaimed && site.state.active())) return false;
        abandonParty(level, site, event);
        notifyClosed(level, event, Component.translatable("message.shadowedhearts.moonwound.event_closed_core_spent"));
        return true;
    }

    private static void configurePace(MoonwoundEvent event) {
        event.configurePace(ServerConfig.INSTANCE.excavationPaceSeconds() * 20,
                ServerConfig.INSTANCE.wavePaceSecondsPerOpponent() * 20);
    }

    /** Claim first, then apply once outside native battles. Unclaimed bonuses last only for their stage. */
    private static void applyPaceBonus(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (event.paceBonus() == PaceBonus.NONE || !MoonwoundClaimProtection.canRun(level, site, event)) return;
        for (UUID member : presentMembers(level, site, event)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (BattleRegistry.getBattleByParticipatingPlayer(player) != null || !event.claimPaceBonus(member)) continue;
            if (event.paceBonus() == PaceBonus.COMBAT_HEAL) {
                for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                    if (pokemon.getCurrentHealth() <= 0) continue;
                    int healing = Math.max(1, (int) Math.ceil(pokemon.getMaxHealth() * .25));
                    pokemon.setCurrentHealth(Math.min(pokemon.getMaxHealth(), pokemon.getCurrentHealth() + healing));
                }
                message(player, Component.translatable("message.shadowedhearts.moonwound.pace_recovery_awarded"));
            } else if (event.paceBonus() == PaceBonus.MINING_HASTE) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60 * 20, 0));
                message(player, Component.translatable("message.shadowedhearts.moonwound.pace_haste_awarded"));
            }
        }
    }

    private static void encounters(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (!MoonwoundClaimProtection.canRun(level, site, event)) return;
        boolean resonant = site.tier == UmbrafallTier.RESONANT;
        int pokemonLevel = resonant ? event.difficulty().resonantWaveLevel() : event.difficulty().strongWaveLevel();
        if (event.phase() == Phase.OVERLOAD) {
            if (event.finaleMode() == FinaleMode.RAID) {
                MoonwoundRaidIntegration.tick(level, site, event);
                if (event.phase() == Phase.COMPLETE) finish(level, site, event);
                return;
            }
            if (event.alphaId() == null) event.issueAlpha(UUID.randomUUID());
            UUID id = event.alphaId();
            PokemonEntity alpha = MoonwoundEncounters.find(level, event.eventId(), id);
            if (alpha == null && !MoonwoundEncounters.hasActiveBattle(event.eventId(), id)
                    && !MoonwoundEncounters.hasPendingCapture(event.eventId(), id)) {
                Vec3 position = spawnPosition(level, site, 0);
                CompoundTag snapshot = event.alphaSnapshot();
                Pokemon pokemon;
                if (snapshot == null) {
                    int alphaLevel = resonant ? event.difficulty().resonantAlphaLevel() : event.difficulty().strongAlphaLevel();
                    pokemon = MoonwoundEncounters.createAlpha(level, position, alphaLevel, null, event.eventId(), id);
                    event.rememberAlphaSnapshot(pokemon.saveToNBT(level.registryAccess(), new CompoundTag()));
                } else {
                    // The native instance copyFrom path omits Alpha; decode a fresh Pokemon instead.
                    pokemon = Pokemon.Companion.loadFromNBT(level.registryAccess(), snapshot);
                    if (!id.equals(pokemon.getUuid()) || !pokemon.isAlpha()) {
                        throw new IllegalStateException("Moonwound Alpha snapshot does not match its issuance");
                    }
                }
                MoonwoundEncounters.spawnPrepared(level, position, pokemon);
            } else if (alpha != null && !MoonwoundEncounters.hasPendingCapture(event.eventId(), id)) tether(alpha, spawnPosition(level, site, 0), event.eventId());
            return;
        }
        Layer layer = event.phase().layer();
        // Reserve identifiers before spawning. Missing entities are reissued, never credited as defeats.
        while (event.issuedEnemies(layer).size() < event.waveTarget(layer)) event.issueEnemy(UUID.randomUUID());
        int slot = 0;
        int spawned = 0;
        int active = 0;
        for (UUID id : event.issuedEnemies(layer)) {
            if (event.defeatedEnemies(layer).contains(id)) continue;
            if (MoonwoundEncounters.find(level, event.eventId(), id) != null) active++;
        }
        for (UUID id : event.issuedEnemies(layer)) {
            Vec3 position = spawnPosition(level, site, slot++);
            if (event.defeatedEnemies(layer).contains(id)) continue;
            PokemonEntity existing = MoonwoundEncounters.find(level, event.eventId(), id);
            if (existing != null) { tether(existing, position, event.eventId()); continue; }
            if (MoonwoundEncounters.hasActiveBattle(event.eventId(), id)) continue;
            if (active >= event.activeMembers().size() * 2 || spawned >= 2) break;
            if (MoonwoundEncounters.spawnVoid(level, position, pokemonLevel, event.eventId(), id) != null) { active++; spawned++; }
        }
    }

    private static Vec3 spawnPosition(ServerLevel level, UmbrafallSiteData.Site site, int slot) {
        double angle = slot * Math.PI * 2 / 8;
        double radius = site.geometry.bodyRadius(site.tier) + 2 + (slot / 8) * 2;
        int x = (int) Math.floor(site.center.getX() + .5 + Math.cos(angle) * radius);
        int z = (int) Math.floor(site.center.getZ() + .5 + Math.sin(angle) * radius);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(x + .5, Math.min(level.getMaxBuildHeight() - 4, y + 1), z + .5);
    }

    private static void tether(PokemonEntity entity, Vec3 anchor, UUID eventId) {
        MoonwoundEncounters.protect(entity);
        if (MoonwoundEncounters.movementLocked(entity)) return;
        double dx = entity.getX() - anchor.x;
        double dz = entity.getZ() - anchor.z;
        // Let native AI walk/fly and gravity settle the spawn. Only rescue encounters which leave
        // the local arena or fall far into terrain; never pin them to a two-block spawn slot.
        if (dx * dx + dz * dz > ENCOUNTER_ROAM_RADIUS * ENCOUNTER_ROAM_RADIUS
                || Math.abs(entity.getY() - anchor.y) > ENCOUNTER_VERTICAL_RANGE) {
            AABB destination = entity.getBoundingBox().move(anchor.subtract(entity.position())).expandTowards(0, -1, 0);
            if (!(entity.level() instanceof ServerLevel level)
                    || !MoonwoundClaimProtection.canSpawn(level, eventId, destination)) return;
            entity.getNavigation().stop();
            entity.teleportTo(anchor.x, anchor.y, anchor.z);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Reconcile the receipt and encounters without replacing the core or granting party rewards. */
    private static void reconcileCompletedSite(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (!site.rewardClaimed || site.state.active()) {
            for (Layer layer : Layer.values()) for (UUID id : event.issuedEnemies(layer)) MoonwoundEncounters.discard(level, event.eventId(), id);
            if (event.alphaId() != null) MoonwoundEncounters.discard(level, event.eventId(), event.alphaId());
            site.rewardClaimed = true;
            site.state = event.outcome() == Outcome.SEAL ? UmbrafallCoreState.STABILIZED : UmbrafallCoreState.RUPTURED;
            UmbrafallSiteData.get(level).setDirty();
        }
    }

    private static void finish(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (event.phase() != Phase.COMPLETE) return;
        reconcileCompletedSite(level, site, event);
        if (!MoonwoundClaimProtection.canRun(level, site, event)) return;
        if (level.hasChunkAt(site.core) && level.getBlockEntity(site.core) instanceof UmbrafallCoreBlockEntity core
                && core.belongsToSite(site.id) && MoonwoundClaimProtection.canRetireCore(level, site, event)) {
            level.setBlockAndUpdate(site.core, ModBlocks.UMBRAFALL_SHELL.get().defaultBlockState());
        }
        for (UUID member : event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (!near(player, level, site) || !MoonwoundClaimProtection.canInteract(player, level, site)
                    || event.claimedRewards().contains(member)) continue;
            List<ItemStack> rewards = MoonwoundRewards.roll(level, site.core, site.tier, event, player);
            if (!event.claimReward(member)) continue;
            for (ItemStack reward : rewards) grant(player, reward);
            if (event.outcome() != Outcome.OVERLOAD || event.overloadSucceeded()) {
                ModCriteriaTriggers.triggerProgression(player, switch (event.outcome()) {
                    case HARVEST -> "moonwound_harvest";
                    case SEAL -> "moonwound_seal";
                    case OVERLOAD -> "moonwound_overload";
                });
            }
            message(player, Component.translatable("message.shadowedhearts.moonwound.rewards_delivered", Component.translatable("moonwound.outcome." + event.outcome().name().toLowerCase(java.util.Locale.ROOT))));
        }
    }

    private static void grant(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static boolean pending(MoonwoundEvent event, UUID id) {
        if (event.phase() == Phase.OVERLOAD) return id.equals(event.alphaId());
        return event.phase().isWave() && event.issuedEnemies(event.phase().layer()).contains(id)
                && !event.defeatedEnemies(event.phase().layer()).contains(id);
    }

    private static Located find(MinecraftServer server, UUID eventId) {
        for (ServerLevel level : server.getAllLevels()) for (var event : MoonwoundSavedData.get(level).events()) {
            if (!event.eventId().equals(eventId)) continue;
            var site = UmbrafallSiteData.get(level).sites.get(event.siteId());
            if (site != null) {
                // Entity-load and battle callbacks may run before the next periodic recovery tick.
                reconcileSpentSite(level, site, event);
                return new Located(level, site, event);
            }
        }
        return null;
    }

    private static Located activeMembership(ServerPlayer player) {
        if (player == null) return null;
        for (ServerLevel level : player.getServer().getAllLevels()) for (var event : MoonwoundSavedData.get(level).events()) {
            if (!event.phase().isTerminal() && event.isActiveMember(player.getUUID())) {
                return new Located(level, UmbrafallSiteData.get(level).sites.get(event.siteId()), event);
            }
        }
        return null;
    }

    private static Located nearest(ServerPlayer player) {
        return MoonwoundSavedData.get(player.serverLevel()).events().stream()
                .map(event -> new Located(player.serverLevel(), UmbrafallSiteData.get(player.serverLevel()).sites.get(event.siteId()), event))
                .filter(found -> found.site != null && near(player, found.level, found.site))
                .min(Comparator.comparingDouble(found -> found.site.center.distSqr(player.blockPosition()))).orElse(null);
    }

    private static boolean eligible(UmbrafallSiteData.Site site) {
        return site.hasValidTerrainProfile() && site.placement == UmbrafallSiteData.Placement.READY
                && site.geometry.usesStratifiedLayers() && (site.tier == UmbrafallTier.STRONG || site.tier == UmbrafallTier.RESONANT);
    }

    private static boolean near(ServerPlayer player, ServerLevel level, UmbrafallSiteData.Site site) {
        return player != null && player.isAlive() && player.serverLevel() == level && site != null
                && player.distanceToSqr(Vec3.atCenterOf(site.center)) <= RANGE * RANGE;
    }

    private static boolean loaded(ServerLevel level, UmbrafallSiteData.Site site) {
        int radius = site.footprintRadius() + 16;
        for (int x = (site.center.getX() - radius) >> 4; x <= (site.center.getX() + radius) >> 4; x++)
            for (int z = (site.center.getZ() - radius) >> 4; z <= (site.center.getZ() + radius) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    private static boolean coreExposed(ServerLevel level, UmbrafallSiteData.Site site) {
        if (!level.hasChunkAt(site.core) || !level.getBlockState(site.core).is(ModBlocks.UMBRAFALL_CORE.get())) return false;
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = site.core.relative(direction);
            if (level.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4) != null
                    && level.getBlockState(adjacent).isAir()) return true;
        }
        return false;
    }

    private static UmbrafallSiteData.Site siteAt(ServerLevel level, BlockPos pos) {
        return UmbrafallSiteData.get(level).sites.values().stream().filter(site -> eligible(site) && inside(site, pos))
                .min(Comparator.comparingDouble(site -> site.core.distSqr(pos))).orElse(null);
    }

    private static boolean inside(UmbrafallSiteData.Site site, BlockPos pos) {
        return Math.abs(pos.getX() - site.center.getX()) <= site.footprintRadius()
                && Math.abs(pos.getZ() - site.center.getZ()) <= site.footprintRadius()
                && Math.abs(pos.getY() - site.core.getY()) <= 24;
    }

    private static Layer layerAt(UmbrafallSiteData.Site site, BlockPos pos) {
        return layer(site.shape().structuralLayerAt(pos.getX() - site.center.getX(), pos.getY() - site.center.getY(), pos.getZ() - site.center.getZ()));
    }

    private static Layer layer(UmbrafallShapeBuilder.StructuralLayer layer) {
        return switch (layer) { case OUTER_SHELL -> Layer.SHELL; case LATTICE_MANTLE -> Layer.MANTLE; case CORE_CASING -> Layer.CASING; default -> null; };
    }

    private static boolean originalAt(ServerLevel level, UmbrafallSiteData.Site site, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        var material = site.shape().materialAt(pos.getX() - site.center.getX(), pos.getY() - site.center.getY(), pos.getZ() - site.center.getZ());
        return switch (material) {
            case SHELL, CRACKED_SHELL, UMBRAL_LATTICE, CORE_CASING, VEIN, CRYSTAL, CORE ->
                    level.getBlockState(pos).is(UmbrafallSiteGenerator.stateFor(material, level, pos).getBlock());
            default -> false;
        };
    }

    private static boolean meteorMaterial(BlockState state) {
        return state.is(ModBlocks.UMBRAFALL_SHELL.get()) || state.is(ModBlocks.CRACKED_UMBRAFALL_SHELL.get())
                || state.is(ModBlocks.UMBRAL_LATTICE.get()) || state.is(ModBlocks.UMBRAFALL_CORE_CASING.get())
                || state.is(ModBlocks.UMBRAFALL_VEIN.get()) || state.is(ModBlocks.UMBRAFALL_CRYSTAL.get())
                || state.is(ModBlocks.UMBRAFALL_CORE.get());
    }

    private static String playerName(ServerLevel level, UUID id) {
        var player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) return player.getGameProfile().getName();
        var cache = level.getServer().getProfileCache();
        return cache == null ? id.toString().substring(0, 8) : cache.get(id).map(profile -> profile.getName()).orElse(id.toString().substring(0, 8));
    }

    private static void send(ServerPlayer player, UmbrafallSiteData.Site site, MoonwoundEvent event, boolean open) {
        ServerLevel level = player.serverLevel();
        Layer layer = event.phase().layer();
        Set<UUID> present = presentMembers(level, site, event);
        int graceTicks = ServerConfig.INSTANCE.missingMemberGraceSeconds() * 20;
        var members = event.activeMembers().stream().map(id -> {
            var availability = level.getServer().getPlayerList().getPlayer(id) == null ? MoonwoundSnapshot.Availability.OFFLINE
                    : present.contains(id) ? MoonwoundSnapshot.Availability.PRESENT : MoonwoundSnapshot.Availability.AWAY;
            return new MoonwoundSnapshot.Member(id, playerName(level, id), event.ready().contains(id), event.votes().get(id),
                    availability, present.contains(id) || event.phase().ordinal() > Phase.VOTE.ordinal() ? 0 : Math.max(0, graceTicks - event.missingTicks(id)),
                    event.votingSuspended(id));
        }).toList();
        var requests = event.joinRequests().stream().map(id -> new MoonwoundSnapshot.Applicant(id, playerName(level, id))).toList();
        boolean raidFinale = event.phase() == Phase.LOBBY || event.phase() == Phase.VOTE
                ? site.tier == UmbrafallTier.RESONANT && MoonwoundRaidIntegration.isAvailable()
                : event.finaleMode() == FinaleMode.RAID;
        Component status = switch (event.phase()) {
            case LOBBY -> raidFinale ? Component.translatable("message.shadowedhearts.moonwound.status.lobby_raid") : Component.translatable("message.shadowedhearts.moonwound.status.lobby_alpha");
            case EXCAVATE_SHELL, EXCAVATE_MANTLE, EXCAVATE_CASING -> Component.translatable("message.shadowedhearts.moonwound.status.excavate");
            case WAVE_SHELL, WAVE_MANTLE, WAVE_CASING -> Component.translatable("message.shadowedhearts.moonwound.status.wave");
            case VOTE -> coreExposed(level, site) ? Component.translatable("message.shadowedhearts.moonwound.status.vote") : Component.translatable("message.shadowedhearts.moonwound.status.expose_core");
            case OVERLOAD -> raidFinale ? MoonwoundRaidIntegration.isAvailable() ? Component.translatable("message.shadowedhearts.moonwound.status.raid") : Component.translatable("message.shadowedhearts.moonwound.status.raid_unavailable") : Component.translatable("message.shadowedhearts.moonwound.status.alpha");
            case COMPLETE -> Component.translatable("message.shadowedhearts.moonwound.status.complete", Component.translatable("moonwound.outcome." + event.outcome().name().toLowerCase(java.util.Locale.ROOT)));
            case ABANDONED -> Component.translatable("message.shadowedhearts.moonwound.status.abandoned");
        };
        boolean claimPaused = !event.phase().isTerminal() && !MoonwoundClaimProtection.canRun(level, site, event);
        if (claimPaused) status = Component.translatable("message.shadowedhearts.moonwound.claim_paused");
        float portalSize = 7f;
        // Share the aperture dimensions and ground contact plane with the renderer.
        // The saved shape keeps the portal steady as players excavate it, including after a reload.
        int portalY = site.center.getY() + site.shape().bodyTopY() + 1
                + MoonwoundPortalGeometry.centerOffsetAboveBodyTop(portalSize);
        var snapshot = new MoonwoundSnapshot(site.id, event.eventId(), level.dimension().location(), site.center,
                portalY, portalSize, event.phase(), event.host(), members, requests,
                layer == null ? 0 : event.mined(layer).size(), layer == null ? 0 : event.target(layer),
                layer == null ? 0 : event.defeatedEnemies(layer).size(), layer == null ? 0 : event.waveTarget(layer),
                event.voteRound(), !claimPaused && stabilizerHolder(level, site, event) != null, status, raidFinale,
                event.portalAgeTicks(level.getGameTime()), new MoonwoundSnapshot.Recovery(
                        event.phase() != Phase.LOBBY && !event.phase().isTerminal() && (present.isEmpty() || claimPaused),
                        event.phase() == Phase.LOBBY ? Math.max(0, ServerConfig.INSTANCE.lobbyIdleTimeoutSeconds() * 20 - event.lobbyIdleTicks()) : 0),
                new MoonwoundSnapshot.Pace(event.paceTargetTicks(), event.paceElapsedTicks(), event.paceBonus()));
        ShadowedHeartsNetwork.sendToPlayer(player, new MoonwoundStatePacket(snapshot, open));
    }

    private static void publish(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(Vec3.atCenterOf(site.center)) <= 96 * 96) {
            if (MoonwoundClaimProtection.canInteract(player, level, site)) send(player, site, event, false);
            else ShadowedHeartsNetwork.sendToPlayer(player, new MoonwoundClosedPacket(event.eventId()));
        }
    }

    private static void announce(ServerLevel level, MoonwoundEvent event, Component text) {
        for (UUID id : event.activeMembers()) {
            var player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) message(player, text);
        }
    }

    private static void permissionDenied(ServerPlayer player) {
        message(player, Component.translatable("message.shadowedhearts.moonwound.claim_denied"));
    }

    private static void message(ServerPlayer player, Component text) { player.displayClientMessage(text, false); }
}
