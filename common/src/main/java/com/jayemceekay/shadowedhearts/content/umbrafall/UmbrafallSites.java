package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.aura.*;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundSavedData;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderSearchProgressS2CPacket;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.world.umbrafall.*;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Server-thread queue. Preflight is read-only; committed placement resumes from its saved cursor. */
public final class UmbrafallSites {
    private static final Map<UUID, UmbrafallSiteSearch> SEARCHES = new LinkedHashMap<>();
    private static final Map<ServerLevel, LinkedHashSet<UUID>> PLACING = new WeakHashMap<>();
    private static final Map<UUID, UmbrafallProgressDelivery> PROGRESS_DELIVERY = new HashMap<>();
    private static long progressSequence;
    private record Reservation(ServerLevel level, UmbrafallSiteData.Site site) { }
    private UmbrafallSites() { }

    public static void init() {
        TickEvent.SERVER_POST.register(UmbrafallSites::tick);
        PlayerEvent.PLAYER_JOIN.register(UmbrafallSites::restoreProgress);
        PlayerEvent.PLAYER_QUIT.register(player -> {
            SEARCHES.remove(player.getUUID());
            PROGRESS_DELIVERY.remove(player.getUUID());
        });
        PlayerEvent.CHANGE_DIMENSION.register((player, from, to) -> {
            var search = SEARCHES.remove(player.getUUID());
            PROGRESS_DELIVERY.remove(player.getUUID());
            if (search != null) publishProgress(player, AuraReaderSearchProgress.of(search.progress().tier(),
                    AuraReaderSearchProgress.Stage.CANCELLED, search.progress().candidate()));
            else restoreProgress(player);
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            SEARCHES.clear(); PLACING.clear(); PROGRESS_DELIVERY.clear(); progressSequence = 0;
        });
        AuraReaderService.registerProvider(new ReadingProvider());
    }

    public static void request(ServerPlayer player, InteractionHand hand, UmbrafallTier tier) {
        ServerLevel world = player.serverLevel();
        var config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();
        if (!world.dimension().equals(Level.OVERWORLD) || !player.mayBuild()
                || !config.umbrafallSignalsEnabled()) {
            message(player, Component.translatable("message.shadowedhearts.umbrafall.dimension_unsupported")); return;
        }
        if (!UmbrafallSignalPolicy.allowsTier(config, tier)) {
            message(player, Component.translatable("message.shadowedhearts.umbrafall.signal_tier_disabled")); return;
        }
        if (SEARCHES.containsKey(player.getUUID())) {
            message(player, Component.translatable("message.shadowedhearts.umbrafall.search_already_running")); return;
        }
        if (SEARCHES.size() >= 4) {
            message(player, Component.translatable("message.shadowedhearts.umbrafall.search_busy")); return;
        }
        var data = UmbrafallSiteData.get(world);
        if (reservations(player.server).stream().anyMatch(entry -> player.getUUID().equals(entry.site.owner))) {
            message(player, Component.translatable("message.shadowedhearts.umbrafall.reservation_pending")); return;
        }
        var existing = data.sites.values().stream().filter(site -> site.tier == tier && site.canLocate())
                .filter(site -> UmbrafallSignalPolicy.inLocateRange(config, site.center.distSqr(player.blockPosition())))
                .filter(site -> safeFootprint(world, site.center, site.footprintRadius(), false))
                .min(Comparator.comparingDouble(site -> site.center.distSqr(player.blockPosition())));
        if (existing.isPresent()) {
            PROGRESS_DELIVERY.remove(player.getUUID());
            consume(player, hand); assign(world, existing.get(), player);
            publishProgress(player, AuraReaderSearchProgress.of(tier, AuraReaderSearchProgress.Stage.READY, 0));
            message(player, Component.translatable("message.shadowedhearts.aura_reader.signal.located")); return;
        }
        if (!UmbrafallSignalPolicy.canGenerate(config, tier)) {
            PROGRESS_DELIVERY.remove(player.getUUID());
            publishProgress(player, AuraReaderSearchProgress.of(tier, AuraReaderSearchProgress.Stage.NO_LOCATION, 0));
            message(player, Component.translatable("message.shadowedhearts.umbrafall.no_existing_site")); return;
        }
        SEARCHES.put(player.getUUID(), new UmbrafallSiteSearch(player, hand, tier));
        PROGRESS_DELIVERY.remove(player.getUUID());
        publishProgress(player, AuraReaderSearchProgress.of(tier, AuraReaderSearchProgress.Stage.SEARCHING, 0));
        message(player, Component.translatable("message.shadowedhearts.umbrafall.search_started"));
    }

    private static void tick(MinecraftServer server) {
        int budget = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().umbrafallWorkPerTick();
        if (!SEARCHES.isEmpty() && server.getTickCount() % 2 == 0) {
            var iterator = SEARCHES.entrySet().iterator();
            var entry = iterator.next(); iterator.remove();
            try {
                if (!entry.getValue().step(server, budget)) {
                    SEARCHES.put(entry.getKey(), entry.getValue());
                    ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player != null) publishProgress(player, entry.getValue().progress());
                }
            } catch (RuntimeException error) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                var reserved = player == null || entry.getValue().reservedSiteId() == null ? null
                        : UmbrafallSiteData.get(player.serverLevel()).sites.get(entry.getValue().reservedSiteId());
                if (reserved != null) {
                    reserved.placement = UmbrafallSiteData.Placement.PAUSED;
                    UmbrafallSiteData.get(player.serverLevel()).setDirty();
                    Shadowedhearts.LOGGER.error("Umbrafall preparation paused after reservation", error);
                    publishSiteProgress(player.serverLevel(), reserved, AuraReaderSearchProgress.Stage.PAUSED,
                            AuraReaderSearchProgress.WaitReason.NONE);
                    message(player, Component.translatable("message.shadowedhearts.umbrafall.preparation_paused"));
                } else if (player != null) {
                    Shadowedhearts.LOGGER.error("Umbrafall preflight failed without consuming its signal", error);
                    publishProgress(player, AuraReaderSearchProgress.of(entry.getValue().progress().tier(),
                            AuraReaderSearchProgress.Stage.FAILED, entry.getValue().progress().candidate()));
                    message(player, Component.translatable("message.shadowedhearts.umbrafall.search_failed"));
                } else Shadowedhearts.LOGGER.error("Umbrafall preflight failed after its player disconnected", error);
            }
            return;
        }
        for (ServerLevel world : server.getAllLevels()) {
            var queue = pending(world);
            if (queue.isEmpty()) continue;
            UUID id = queue.iterator().next(); queue.remove(id);
            var site = UmbrafallSiteData.get(world).sites.get(id);
            if (site != null && site.placement == UmbrafallSiteData.Placement.PLACING) {
                try { place(world, site, budget); }
                catch (RuntimeException error) {
                    site.placement = UmbrafallSiteData.Placement.PAUSED;
                    UmbrafallSiteData.get(world).setDirty();
                    Shadowedhearts.LOGGER.error("Umbrafall placement paused at {}", site.center, error);
                    publishSiteProgress(world, site, AuraReaderSearchProgress.Stage.PAUSED, AuraReaderSearchProgress.WaitReason.NONE);
                }
                if (site.placement == UmbrafallSiteData.Placement.PLACING) queue.add(id);
            }
            return;
        }
    }

    private static LinkedHashSet<UUID> pending(ServerLevel world) {
        return PLACING.computeIfAbsent(world, level -> {
            LinkedHashSet<UUID> pending = new LinkedHashSet<>();
            UmbrafallSiteData.get(level).sites.values().stream()
                    .filter(site -> site.placement == UmbrafallSiteData.Placement.PLACING).forEach(site -> pending.add(site.id));
            return pending;
        });
    }

    static void reserve(ServerLevel world, UmbrafallSiteData.Site site, ServerPlayer player, InteractionHand hand) {
        site.owner = player.getUUID(); site.placement = UmbrafallSiteData.Placement.PLACING;
        UmbrafallSiteData.get(world).sites.put(site.id, site);
        UmbrafallSiteData.get(world).setDirty();
        consume(player, hand); pending(world).add(site.id);
        publishSiteProgress(world, site, AuraReaderSearchProgress.Stage.ESTABLISHING, AuraReaderSearchProgress.WaitReason.NONE);
        message(player, Component.translatable("message.shadowedhearts.umbrafall.site_reserved"));
    }

    private static void place(ServerLevel world, UmbrafallSiteData.Site site, int budget) {
        var failure = footprintFailure(world, site.center, site.footprintRadius(), true);
        if (failure != null) {
            publishSiteProgress(world, site, AuraReaderSearchProgress.Stage.WAITING, waitReason(failure));
            return;
        }
        UmbrafallShapeBuilder shape = site.shape();
        var cursor = shape.cursor(site.cursor);
        cursor.scan(budget, (x,y,z,material) -> {
            if (material == UmbrafallShapeBuilder.Material.CORE) return;
            BlockPos pos = site.center.offset(x,y,z);
            BlockState target = UmbrafallSiteGenerator.stateFor(material, world, pos);
            BlockState old = world.getBlockState(pos);
            if (old.equals(target)) return;
            if (!natural(old)) throw new IllegalStateException("Terrain changed during placement at " + pos);
            if (shape.isSurfaceDebris(x, y, z, material)
                    && !world.getBlockState(pos.below()).isSolidRender(world, pos.below())) return;
            if (!world.setBlock(pos, target, Block.UPDATE_CLIENTS) && !world.getBlockState(pos).equals(target)) {
                throw new IllegalStateException("Rejected Umbrafall placement at " + pos);
            }
        });
        site.cursor = cursor.position(); UmbrafallSiteData.get(world).setDirty();
        if (!cursor.done()) {
            publishSiteProgress(world, site, AuraReaderSearchProgress.Stage.ESTABLISHING, AuraReaderSearchProgress.WaitReason.NONE);
            return;
        }
        if (site.tier.hasActiveCore()) {
            BlockState old = world.getBlockState(site.core);
            if (!natural(old) && !old.is(ModBlocks.UMBRAFALL_CORE.get())) throw new IllegalStateException("Core position changed during placement");
            if (!old.is(ModBlocks.UMBRAFALL_CORE.get())) world.setBlockAndUpdate(site.core, ModBlocks.UMBRAFALL_CORE.get().defaultBlockState());
            if (!(world.getBlockEntity(site.core) instanceof UmbrafallCoreBlockEntity core)) throw new IllegalStateException("Missing Umbrafall core entity");
            core.configure(site.tier, site.id, site.owner, site.center, site.seed, site.geometry);
        }
        site.placement = UmbrafallSiteData.Placement.READY;
        site.signalExpires = world.getGameTime() + signalDurationTicks();
        site.pendingSignalAcknowledgement = site.owner != null;
        UmbrafallSiteData.get(world).setDirty();
        ServerPlayer player = site.owner == null ? null : world.getServer().getPlayerList().getPlayer(site.owner);
        if (player != null && player.serverLevel() == world) {
            acknowledgeCompletion(world, site, player, true);
        } else if (player != null) {
            publishProgress(player, AuraReaderSearchProgress.of(site.tier, AuraReaderSearchProgress.Stage.READY_ELSEWHERE, 0));
            message(player, Component.translatable("message.shadowedhearts.aura_reader.signal.ready_elsewhere"));
        }
    }

    static boolean natural(BlockState state) {
        return UmbrafallTerrainMaterials.replaceable(state);
    }

    /** Untouched aquifers need not veto a site, but a fluid beside an excavation still does. */
    static boolean safeTerrain(BlockState state, UmbrafallShapeBuilder shape, int x, int y, int z) {
        if (natural(state)) return true;
        return (state.is(Blocks.WATER) || state.is(Blocks.LAVA))
                && !state.hasBlockEntity() && !UmbrafallPlacementPolicy.fluidTouchesWrites(shape,x,y,z);
    }

    enum FootprintFailure {
        WORLD_BORDER("message.shadowedhearts.umbrafall.obstacle.world_border"),
        SPAWN("message.shadowedhearts.umbrafall.obstacle.spawn"),
        UNLOADED("message.shadowedhearts.umbrafall.obstacle.unloaded"),
        UNLOADED_INFRASTRUCTURE("message.shadowedhearts.umbrafall.obstacle.unloaded_infrastructure"),
        INFRASTRUCTURE("message.shadowedhearts.umbrafall.obstacle.infrastructure"),
        CLAIMED("message.shadowedhearts.umbrafall.obstacle.claimed"),
        CLAIM_CHECK("message.shadowedhearts.umbrafall.obstacle.claim_check"),
        PLAYERS("message.shadowedhearts.umbrafall.obstacle.players");
        final Component description;
        FootprintFailure(String key) { this.description = Component.translatable(key); }
    }

    static boolean safeFootprint(ServerLevel world, BlockPos center, UmbrafallTier tier, boolean players) {
        return footprintFailure(world,center,tier,players) == null;
    }

    private static boolean safeFootprint(ServerLevel world, BlockPos center, int footprintRadius, boolean players) {
        return footprintFailure(world,center,footprintRadius,players) == null;
    }

    static FootprintFailure footprintFailure(ServerLevel world, BlockPos center, UmbrafallTier tier, boolean players) {
        return footprintFailure(world,center,tier.radius(),players);
    }

    static FootprintFailure footprintFailure(ServerLevel world, BlockPos center, int footprintRadius, boolean players) {
        int radius = footprintRadius + 8;
        BlockPos min = center.offset(-radius,0,-radius), max = center.offset(radius,0,radius);
        if (!world.getWorldBorder().isWithinBounds(min) || !world.getWorldBorder().isWithinBounds(max)) return FootprintFailure.WORLD_BORDER;
        int spawnBuffer = Math.max(16,world.getServer().getSpawnProtectionRadius());
        if (Math.abs(center.getX() - world.getSharedSpawnPos().getX()) <= radius + spawnBuffer
                && Math.abs(center.getZ() - world.getSharedSpawnPos().getZ()) <= radius + spawnBuffer) return FootprintFailure.SPAWN;
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (world.getChunkSource().getChunkNow(x,z) == null) return FootprintFailure.UNLOADED;
                try {
                    var claim = FTBChunksClaimBridgeHolder.INSTANCE.lookupClaim(world, new ChunkPos(x,z));
                    if (claim.isUnavailable()) return FootprintFailure.CLAIM_CHECK;
                    if (claim.isClaimed()) return FootprintFailure.CLAIMED;
                } catch (RuntimeException | LinkageError error) { return FootprintFailure.CLAIM_CHECK; }
            }
        }
        // Cobblemon does not index all machines/pastures as POIs. Inspect only already loaded
        // chunks, but require the whole safety halo so an unloaded pasture cannot be missed.
        int clearanceRadius = footprintRadius + UmbrafallInfrastructure.maxClearance(world);
        for (int x = (center.getX()-clearanceRadius) >> 4; x <= (center.getX()+clearanceRadius) >> 4; x++) {
            for (int z = (center.getZ()-clearanceRadius) >> 4; z <= (center.getZ()+clearanceRadius) >> 4; z++) {
                if (world.getChunkSource().getChunkNow(x,z) == null) return FootprintFailure.UNLOADED_INFRASTRUCTURE;
            }
        }
        if (UmbrafallInfrastructure.obstruction(world,center,footprintRadius) != null) return FootprintFailure.INFRASTRUCTURE;
        return players && world.players().stream().anyMatch(player ->
                Math.abs(player.getX() - center.getX()) < radius + 16 && Math.abs(player.getZ() - center.getZ()) < radius + 16)
                ? FootprintFailure.PLAYERS : null;
    }

    private static void consume(ServerPlayer player, InteractionHand hand) { if (!player.isCreative()) player.getItemInHand(hand).shrink(1); }

    private static void assign(ServerLevel world, UmbrafallSiteData.Site site, ServerPlayer player) {
        site.owner = player.getUUID(); site.signalExpires = world.getGameTime() + signalDurationTicks();
        site.pendingSignalAcknowledgement = false;
        UmbrafallSiteData.get(world).setDirty();
        selectSite(player, site);
        ModCriteriaTriggers.triggerProgression(player, "signal_received");
    }

    private static long signalDurationTicks() {
        return 20L * ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().umbrafallSignalLifetimeSeconds();
    }

    private static void selectSite(ServerPlayer player, UmbrafallSiteData.Site site) {
        AuraReaderPlayerState.setSelectedSignal(player, site.id.toString());
        AuraReaderPlayerState.setTracking(player, true);
    }

    private static boolean hasValidCurrentSelection(ServerPlayer player) {
        String selected = AuraReaderPlayerState.getSelectedSignal(player);
        if (selected.isBlank()) return false;
        UUID id;
        try { id = UUID.fromString(selected); }
        catch (IllegalArgumentException ignored) { return false; }
        ServerLevel world = player.serverLevel();
        var selectedSite = UmbrafallSiteData.get(world).sites.get(id);
        if (selectedSite != null && selectedSite.hasGuidanceFor(player.getUUID(), world.getGameTime())) return true;
        var contacts = AuraReaderService.session(player).contacts();
        contacts.observeDimension(world.dimension().location().toString());
        var entity = world.getEntity(id);
        int range = Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange());
        return entity instanceof PokemonEntity pokemon && contacts.isAuthorized(id, world.getGameTime())
                && AuraReaderContacts.usable(pokemon.isAlive() && !pokemon.isRemoved(),
                        ShadowAspectUtil.hasShadowAspect(pokemon.getPokemon()),
                        AuraReaderTargeting.distanceToHitboxSqr(player, pokemon), range);
    }

    /** Delayed acknowledgments preserve a destination the player has already chosen and never renew expiry. */
    private static void acknowledgeCompletions(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        var pending = UmbrafallSiteData.get(world).sites.values().stream()
                .filter(site -> player.getUUID().equals(site.owner) && site.pendingSignalAcknowledgement
                        && site.placement == UmbrafallSiteData.Placement.READY)
                .sorted(Comparator.comparingLong((UmbrafallSiteData.Site site) -> site.signalExpires).reversed()
                        .thenComparing(site -> site.id)).toList();
        boolean selectNext = !hasValidCurrentSelection(player);
        for (var site : pending) {
            boolean select = selectNext && site.hasGuidanceFor(player.getUUID(), world.getGameTime());
            acknowledgeCompletion(world, site, player, select);
            if (select) selectNext = false;
        }
    }

    private static void acknowledgeCompletion(ServerLevel world, UmbrafallSiteData.Site site, ServerPlayer player, boolean select) {
        site.pendingSignalAcknowledgement = false;
        UmbrafallSiteData.get(world).setDirty();
        if (!site.hasGuidanceFor(player.getUUID(), world.getGameTime())) {
            message(player, Component.translatable("message.shadowedhearts.aura_reader.signal.completed_expired"));
            return;
        }
        ModCriteriaTriggers.triggerProgression(player, "signal_received");
        if (select) selectSite(player, site);
        if (site.id.toString().equals(AuraReaderPlayerState.getSelectedSignal(player))) {
            publishProgress(player, AuraReaderSearchProgress.of(site.tier, AuraReaderSearchProgress.Stage.READY, 0));
            message(player, Component.translatable("message.shadowedhearts.aura_reader.signal.ready"));
        } else message(player, Component.translatable("message.shadowedhearts.aura_reader.signal.ready_available"));
        AuraReaderService.sendState(player);
    }

    public static void onMaterialMined(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel world)) return;
        UmbrafallSiteData.get(world).sites.values().stream()
                .filter(site -> site.hasValidTerrainProfile() && site.state.active() && site.placement == UmbrafallSiteData.Placement.READY
                        && pos.distSqr(site.core) <= Math.pow(site.geometry.bodyRadius(site.tier)+4, 2))
                .min(Comparator.comparingDouble(site -> pos.distSqr(site.core))).ifPresent(site -> {
                    if (world.hasChunkAt(site.core) && world.getBlockEntity(site.core) instanceof UmbrafallCoreBlockEntity core) core.disturbed(player);
                });
    }

    static void message(ServerPlayer player, Component text) { player.displayClientMessage(text,false); }

    static void publishProgress(ServerPlayer player, AuraReaderSearchProgress progress) {
        var delivery = PROGRESS_DELIVERY.computeIfAbsent(player.getUUID(), id -> new UmbrafallProgressDelivery());
        if (delivery.shouldSend(progress, player.server.getTickCount())) {
            try {
                ShadowedHeartsNetwork.sendToPlayer(player, new AuraReaderSearchProgressS2CPacket(++progressSequence, progress));
            } catch (RuntimeException error) {
                // A disconnected client or feedback failure must not change committed terrain work.
                Shadowedhearts.LOGGER.debug("Unable to send Umbrafall search feedback to {}", player.getUUID(), error);
            }
        }
    }

    private static AuraReaderSearchProgress.WaitReason waitReason(FootprintFailure failure) {
        return switch (failure) {
            case UNLOADED, UNLOADED_INFRASTRUCTURE -> AuraReaderSearchProgress.WaitReason.TERRAIN;
            case PLAYERS -> AuraReaderSearchProgress.WaitReason.AREA;
            default -> AuraReaderSearchProgress.WaitReason.SAFETY;
        };
    }

    private static void publishSiteProgress(ServerLevel world, UmbrafallSiteData.Site site,
                                            AuraReaderSearchProgress.Stage stage, AuraReaderSearchProgress.WaitReason wait) {
        ServerPlayer player = site.owner == null ? null : world.getServer().getPlayerList().getPlayer(site.owner);
        if (player == null) return;
        int total = site.hasValidTerrainProfile() ? site.shape().bounds().volume() : 0;
        publishProgress(player, new AuraReaderSearchProgress(site.tier, stage, 0, site.cursor, total, wait));
    }

    /** Only committed world-owned jobs survive reconnects/restarts; preflight searches remain transient. */
    private static void restoreProgress(ServerPlayer player) {
        PROGRESS_DELIVERY.remove(player.getUUID());
        // Offline owners may retain an old selection, but must not receive an old release toast
        // over a newer job when reconnecting or returning to this dimension.
        for (ServerLevel world : player.server.getAllLevels()) {
            for (var site : UmbrafallSiteData.get(world).sites.values()) {
                if (player.getUUID().equals(site.owner) && site.placement == UmbrafallSiteData.Placement.CANCELLED) {
                    clearReleasedSelection(player, site.id);
                }
            }
        }
        acknowledgeCompletions(player);
        var search = SEARCHES.get(player.getUUID());
        if (search != null) { publishProgress(player, search.progress()); return; }
        for (ServerLevel world : player.server.getAllLevels()) {
            for (var site : UmbrafallSiteData.get(world).sites.values()) {
                if (!player.getUUID().equals(site.owner) || !site.hasPendingPlacement()) continue;
                if (site.placement == UmbrafallSiteData.Placement.PAUSED) {
                    publishSiteProgress(world, site, AuraReaderSearchProgress.Stage.PAUSED, AuraReaderSearchProgress.WaitReason.NONE);
                } else {
                    var failure = footprintFailure(world, site.center, site.footprintRadius(), true);
                    publishSiteProgress(world, site, failure == null ? AuraReaderSearchProgress.Stage.ESTABLISHING
                                    : AuraReaderSearchProgress.Stage.WAITING,
                            failure == null ? AuraReaderSearchProgress.WaitReason.NONE : waitReason(failure));
                }
                return;
            }
        }
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command() {
        return net.minecraft.commands.Commands.literal("umbrafall").requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("status")
                        .executes(context -> reservationStatus(context.getSource())))
                .then(net.minecraft.commands.Commands.literal("cancel")
                        .executes(context -> cancelSearch(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(net.minecraft.commands.Commands.literal("search")
                                .then(net.minecraft.commands.Commands.argument("player", EntityArgument.player())
                                        .executes(context -> cancelSearch(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(net.minecraft.commands.Commands.argument("site", UuidArgument.uuid())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        reservations(context.getSource().getServer()).stream()
                                                .map(entry -> entry.site.id.toString()), builder))
                                .executes(context -> cancelReservation(context.getSource(), UuidArgument.getUuid(context, "site")))))
                .then(net.minecraft.commands.Commands.literal("resume")
                        .then(net.minecraft.commands.Commands.argument("site",net.minecraft.commands.arguments.UuidArgument.uuid())
                                .executes(context -> {
                                    ServerLevel world = context.getSource().getLevel();
                                    UUID id = net.minecraft.commands.arguments.UuidArgument.getUuid(context,"site");
                                    var site = UmbrafallSiteData.get(world).sites.get(id);
                                    if (site == null || site.placement != UmbrafallSiteData.Placement.PAUSED || !site.state.active()) {
                                        context.getSource().sendFailure(Component.translatable("message.shadowedhearts.umbrafall.paused_site_not_found")); return 0;
                                    }
                                    if (!site.hasValidTerrainProfile()) {
                                        context.getSource().sendFailure(Component.translatable("message.shadowedhearts.umbrafall.resume_unsupported_geometry")); return 0;
                                    }
                                    var failure = footprintFailure(world,site.center,site.footprintRadius(),true);
                                    if (failure != null) {
                                        context.getSource().sendFailure(Component.translatable("message.shadowedhearts.umbrafall.cannot_resume", failure.description)); return 0;
                                    }
                                    site.placement = UmbrafallSiteData.Placement.PLACING;
                                    UmbrafallSiteData.get(world).setDirty(); pending(world).add(site.id);
                                    publishSiteProgress(world, site, AuraReaderSearchProgress.Stage.ESTABLISHING, AuraReaderSearchProgress.WaitReason.NONE);
                                    context.getSource().sendSuccess(() -> Component.translatable("message.shadowedhearts.umbrafall.placement_resuming"),false);
                                    return 1;
                                })));
    }

    private static List<Reservation> reservations(MinecraftServer server) {
        List<Reservation> result = new ArrayList<>();
        for (ServerLevel world : server.getAllLevels()) {
            for (var site : UmbrafallSiteData.get(world).sites.values()) {
                if (site.hasPendingPlacement()) result.add(new Reservation(world, site));
            }
        }
        return result;
    }

    private static int reservationStatus(CommandSourceStack source) {
        List<Reservation> pending = reservations(source.getServer());
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.umbrafall.reservation_summary", pending.size(), SEARCHES.size()), false);
        SEARCHES.forEach((owner, search) -> {
            ServerPlayer player = source.getServer().getPlayerList().getPlayer(owner);
            String name = player == null ? owner.toString() : player.getGameProfile().getName() + " (" + owner + ")";
            source.sendSuccess(() -> Component.translatable("message.shadowedhearts.umbrafall.search_listing", name, Component.translatable("umbrafall.tier." + search.progress().tier().name().toLowerCase(java.util.Locale.ROOT)), search.dimension().toString(), search.origin().toShortString(), Component.translatable("hud.shadowedhearts.aura_reader.search.stage." + search.progress().stage().name().toLowerCase(java.util.Locale.ROOT)), search.progress().candidate()), false);
        });
        for (Reservation entry : pending) {
            var site = entry.site;
            source.sendSuccess(() -> Component.translatable("message.shadowedhearts.umbrafall.site_listing", site.id.toString(), Component.translatable("umbrafall.placement." + site.placement.name().toLowerCase(java.util.Locale.ROOT)), entry.level.dimension().location().toString(), site.center.toShortString(), site.owner == null ? Component.translatable("message.shadowedhearts.umbrafall.owner_none") : Component.literal(site.owner.toString()), site.cursor, site.hasValidTerrainProfile() ? Component.empty() : Component.translatable("message.shadowedhearts.umbrafall.unsupported_profile_suffix")), false);
        }
        return pending.size() + SEARCHES.size();
    }

    private static int cancelSearch(CommandSourceStack source, ServerPlayer player) {
        var search = SEARCHES.remove(player.getUUID());
        if (search == null) {
            source.sendFailure(Component.translatable("message.shadowedhearts.umbrafall.no_preflight", player.getGameProfile().getName()));
            return 0;
        }
        publishProgress(player, AuraReaderSearchProgress.of(search.progress().tier(),
                AuraReaderSearchProgress.Stage.CANCELLED, search.progress().candidate()));
        message(player, Component.translatable("message.shadowedhearts.umbrafall.search_cancelled"));
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.umbrafall.cancelled_preflight", player.getGameProfile().getName()), true);
        return 1;
    }

    private static int cancelReservation(CommandSourceStack source, UUID id) {
        Reservation found = reservations(source.getServer()).stream()
                .filter(entry -> entry.site.id.equals(id)).findFirst().orElse(null);
        if (found == null) {
            source.sendFailure(Component.translatable("message.shadowedhearts.umbrafall.reservation_not_found"));
            return 0;
        }
        ServerLevel world = found.level;
        var site = found.site;
        if (MoonwoundSavedData.get(world).isReserved(id)) {
            source.sendFailure(Component.translatable("message.shadowedhearts.umbrafall.moonwound_recovery_required"));
            return 0;
        }
        if (!site.cancelPlacement()) return 0;
        // Retain the original profile/cursor and spent receipt before any core cleanup or feedback.
        // Partial writes cannot be rolled back safely, and an already-consumed signal is not refunded.
        UmbrafallSiteData.get(world).setDirty();
        var queue = PLACING.get(world);
        if (queue != null) queue.remove(id);
        if (world.hasChunkAt(site.core) && world.getBlockEntity(site.core) instanceof UmbrafallCoreBlockEntity core) {
            core.retireCancelled(world);
        }
        ServerPlayer owner = site.owner == null ? null : source.getServer().getPlayerList().getPlayer(site.owner);
        if (owner != null) {
            clearReleasedSelection(owner, id);
            // Progress packets identify the current job, not a site: never replace a newer job's card.
            if (!SEARCHES.containsKey(owner.getUUID()) && reservations(source.getServer()).stream()
                    .noneMatch(entry -> owner.getUUID().equals(entry.site.owner))) {
                publishProgress(owner, AuraReaderSearchProgress.of(site.tier, AuraReaderSearchProgress.Stage.RELEASED, 0));
            }
            message(owner, Component.translatable("message.shadowedhearts.umbrafall.operator_released"));
        }
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.umbrafall.released_reservation", id.toString()), true);
        return 1;
    }

    private static void clearReleasedSelection(ServerPlayer player, UUID id) {
        boolean changed = false;
        if (id.toString().equals(AuraReaderPlayerState.getSelectedSignal(player))) {
            AuraReaderPlayerState.clearSelectedSignal(player);
            changed = true;
        }
        if (AuraReaderPlayerState.getLockType(player) == AuraReaderLockType.UMBRAFALL_SITE
                && id.toString().equals(AuraReaderPlayerState.getLockedTarget(player))) {
            AuraReaderPlayerState.clearLock(player);
            changed = true;
        }
        if (changed) {
            var session = AuraReaderService.session(player);
            AuraReaderPlayerState.setTracking(player, session.isExplicitTracking() || session.contacts().pinnedId() != null
                    || !AuraReaderPlayerState.getSelectedSignal(player).isBlank()
                    || AuraReaderPlayerState.getLockType(player) != AuraReaderLockType.NONE);
            AuraReaderService.sendState(player);
        }
    }

    private static final class ReadingProvider implements AuraReadingProvider {
        @Override public int priority() { return 200; }
        @Override public List<AuraReading> scan(AuraScanContext context) {
            List<AuraReading> readings = new ArrayList<>();
            ServerLevel world = context.level();
            for (var site : UmbrafallSiteData.get(world).sites.values()) {
                if (!site.hasGuidanceFor(context.player().getUUID(), world.getGameTime())) continue;
                Vec3 delta = Vec3.atCenterOf(site.core).subtract(context.player().position());
                double distance = delta.length(); Vec3 direction = delta.normalize();
                readings.add(new AuraReading(site.id.toString(),AuraReadingType.UMBRAFALL_SIGNAL,AuraReadingSource.TRACKED,
                        Component.translatable("hud.shadowedhearts.aura_reader.reading.umbrafall",
                                Component.translatable("umbrafall.tier." + site.tier.name().toLowerCase(java.util.Locale.ROOT))),
                        Component.translatable("umbrafall.core_state." + site.state.name().toLowerCase(java.util.Locale.ROOT)),world.dimension().location(),direction.x,direction.y,direction.z,10,
                        distance<16 ? "Located" : distance<64 ? "Near" : distance<192 ? "Distant" : "Far",
                        delta.y>6 ? "Above" : delta.y < -6 ? "Below" : "Level",
                        (float)Math.max(0.1,1-distance/640),1,0,200,site.signalExpires,
                        site.id.toString().equals(AuraReaderPlayerState.getSelectedSignal(context.player())),false,false,false));
            }
            return readings.stream().sorted(Comparator.comparing(AuraReading::selected).reversed()).toList();
        }
    }
}
