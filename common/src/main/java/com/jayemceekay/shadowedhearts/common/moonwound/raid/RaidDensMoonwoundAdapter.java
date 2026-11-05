package com.jayemceekay.shadowedhearts.common.moonwound.raid;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.drop.DropTable;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundClaimProtection;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundSavedData;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import com.jayemceekay.shadowedhearts.network.AuraBroadcastQueue;
import com.jayemceekay.shadowedhearts.mixin.raiddens.MoonwoundRaidHealthAccessor;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.events.RaidEvents;
import com.necro.raid.dens.common.network.RaidDenNetworkMessages;
import com.necro.raid.dens.common.raids.RaidInstance;
import com.necro.raid.dens.common.raids.RaidState;
import com.necro.raid.dens.common.raids.helpers.RaidHelper;
import com.necro.raid.dens.common.raids.helpers.RaidJoinHelper;
import com.necro.raid.dens.common.registry.RaidRegistry;
import com.necro.raid.dens.common.showdown.events.AbstractEvent;
import com.necro.raid.dens.common.showdown.events.ShowdownEvents;
import com.necro.raid.dens.common.util.IRaidAccessor;
import com.necro.raid.dens.common.util.IRaidBattle;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import kotlin.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Typed adapter for the published Raid Dens 0.13.5 API, loaded only through the version-gated façade. */
public final class RaidDensMoonwoundAdapter {
    public static final ResourceLocation BOSS = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "moonwound_resonant");
    public static final String KEY = MoonwoundRaidIntegration.ENCOUNTER_KEY;
    private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();
    private static final Map<UUID, Long> RETRY_AFTER = new HashMap<>();
    private static final Map<UUID, UUID> LOADING = new HashMap<>();
    private record NativeChallenge(ServerPlayer player, PokemonEntity entity) { }
    private static final ThreadLocal<NativeChallenge> NATIVE_CHALLENGE = new ThreadLocal<>();
    private static boolean initialized;

    private RaidDensMoonwoundAdapter() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            for (UUID id : new ArrayList<>(ATTEMPTS.keySet())) cleanupEvent(id);
            ATTEMPTS.clear(); RETRY_AFTER.clear(); LOADING.clear(); NATIVE_CHALLENGE.remove();
        });
        TickEvent.SERVER_POST.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (Attempt attempt : new ArrayList<>(ATTEMPTS.values())) {
                if (!active(attempt.event) || attempt.entity.isRemoved()
                        || !MoonwoundClaimProtection.canRun(attempt.level, attempt.site, attempt.event)) cleanupEvent(attempt.event.eventId());
                else if (attempt.raid != null) {
                    attempt.raid.refreshPlayers();
                    keepNearby(attempt);
                }
            }
        });
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.LOWEST, event -> {
            for (var actor : event.getBattle().getActors()) {
                if (actor.getType() != ActorType.WILD) continue;
                for (var member : actor.getPokemonList()) {
                    Pokemon pokemon = member.getEffectedPokemon();
                    if (!pokemon.getPersistentData().contains(KEY)) continue;
                    PokemonEntity entity = member.getEntity();
                    NativeChallenge challenge = NATIVE_CHALLENGE.get();
                    if (entity == null || challenge == null || challenge.entity() != entity
                            || event.getBattle().getPlayers().size() != 1
                            || !event.getBattle().getPlayers().getFirst().getUUID().equals(challenge.player().getUUID())
                            || event.getBattle().getPlayers().stream().anyMatch(player -> !eligible(player, entity))) {
                        event.setReason(Component.translatable("message.shadowedhearts.moonwound.raid_active_member_required"));
                        event.cancel();
                        return Unit.INSTANCE;
                    }
                }
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.LOWEST, event -> {
            if (event.getPokemon().getPokemon().getPersistentData().contains(KEY)) event.cancel();
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe(Priority.LOWEST, event -> {
            PokemonEntity entity = event.getPokemonEntity();
            if (!entity.getPokemon().getPersistentData().contains(KEY)) return Unit.INSTANCE;
            if (entity.getPokemon().isPlayerOwned()) {
                entity.getPokemon().getPersistentData().remove(KEY);
                return Unit.INSTANCE;
            }
            MoonwoundEvent model = eventFor(entity);
            Attempt attempt = model == null ? null : ATTEMPTS.get(model.eventId());
            if (model == null || !active(model) || !entity.getPokemon().getUuid().equals(model.raidId())
                    || !MoonwoundClaimProtection.canSpawn((ServerLevel) entity.level(), model.eventId(),
                    entity.getBoundingBox().expandTowards(0, -1, 0))
                    || (attempt != null && attempt.entity != entity && !attempt.entity.isRemoved())) event.cancel();
            else protect(entity);
            return Unit.INSTANCE;
        });
        // Upstream may clone a shared reward after the feature hook: repair the actual capture candidate too.
        RaidEvents.RAID_END.subscribe(Priority.HIGHEST, event -> {
            if (event.isWin() && BOSS.equals(event.raidBoss().getId()) && event.pokemon() != null) {
                Pokemon reward = event.pokemon();
                reward.getPersistentData().remove(KEY);
                reward.setAlpha(false);
                ShadowService.fullyCorrupt(reward, null, false);
                ShadowMoveUtil.assignShadowMoves(reward);
                UncatchableProperty.INSTANCE.catchable().apply(reward);
            }
            return Unit.INSTANCE;
        });
    }

    public static void tick(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (!active(event) || !MoonwoundClaimProtection.canRun(level, site, event)) { cleanupEvent(event.eventId()); return; }
        Attempt existing = ATTEMPTS.get(event.eventId());
        if (existing != null) {
            if (existing.entity.isRemoved() || existing.raid == null || existing.raid.isFinished()) cleanupEvent(event.eventId());
            return;
        }
        if (LOADING.containsKey(event.eventId()) || level.getGameTime() < RETRY_AFTER.getOrDefault(event.eventId(), 0L)) return;
        if (event.raidId() == null && !event.issueRaid(UUID.randomUUID())) return;
        CompoundTag saved = event.raidDefinition();
        if (saved != null) {
            try {
                RaidBoss definition = RaidBoss.decode(ResourceLocation.parse(saved.getString("Id")), saved.getByteArray("Definition"));
                if (!BOSS.equals(definition.getId())) throw new IllegalStateException("Unexpected saved Moonwound raid definition");
                spawn(level, site, event, definition);
            } catch (RuntimeException error) { pause(level, event, error); }
            return;
        }
        UUID request = UUID.randomUUID();
        LOADING.put(event.eventId(), request);
        RaidRegistry.requestRaidBoss(BOSS, template -> {
            if (!LOADING.remove(event.eventId(), request)) return;
            if (!active(event) || level.getServer().isStopped()
                    || !MoonwoundClaimProtection.canRun(level, site, event)) return;
            try {
                RaidBoss definition = template.copy();
                definition.setId(BOSS);
                definition.setFeature("shadow");
                definition.setMaxPlayers(MoonwoundEvent.MAX_MEMBERS);
                definition.setMaxCatches(1);
                applyBossLevel(definition, event.difficulty().raidBossLevel());
                selectBossSpecies(level, definition);
                spawn(level, site, event, definition);
            } catch (RuntimeException error) { pause(level, event, error); }
        }, (id, error) -> {
            if (!LOADING.remove(event.eventId(), request)) return;
            pause(level, event, error == null ? new IllegalStateException("Missing raid definition " + id) : error);
        }, level.getServer());
    }

    /** A zero override preserves authored levels; capture rewards and issued snapshots remain unchanged. */
    private static void applyBossLevel(RaidBoss definition, int requestedLevel) {
        if (requestedLevel == 0) return;
        definition.getBoss().setLevel(Math.clamp(requestedLevel, 1, Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel()));
        definition.clearCaches();
    }

    /** Called only before first issuance; retries and reloads use the saved definition and Pokemon. */
    private static void selectBossSpecies(ServerLevel level, RaidBoss definition) {
        var species = MoonwoundRaidBossPool.select(level.getRandom());
        String id = species.getResourceIdentifier().toString();
        PokemonProperties reward = definition.getReward();
        PokemonProperties boss = definition.getBoss();
        for (PokemonProperties properties : new PokemonProperties[]{reward, boss}) {
            properties.setSpecies(id);
            properties.setForm(null);
            properties.setAbility(null);
            properties.setMoves(null);
            properties.setAspects(Set.of());
            properties.setCustomProperties(new ArrayList<>());
        }
        definition.applyAspects();
        definition.clearCaches();
        definition.setDisplaySpecies(species.getResourceIdentifier());
        definition.setDisplayAspects(boss.getAspects());
        definition.setBossBarText(Component.translatable("message.shadowedhearts.moonwound.raid_boss_name", species.getTranslatedName()));
    }

    private static void spawn(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event, RaidBoss definition) {
        if (!MoonwoundClaimProtection.canRun(level, site, event)) return;
        definition.setBossBarText(MoonwoundRaidLabel.localizeLegacy(
                definition.getBossBarText(), definition.getDisplaySpecies().getTranslatedName()));
        if (RaidHelper.ACTIVE_RAIDS.containsKey(event.raidId())) {
            throw new IllegalStateException("Moonwound raid identity is already owned by another live instance");
        }
        PokemonEntity entity;
        CompoundTag snapshot = event.raidSnapshot();
        if (snapshot == null) {
            entity = definition.getBossEntity(level, null);
            Pokemon pokemon = entity.getPokemon();
            if (pokemon.isLegendary() || pokemon.isMythical()) {
                throw new IllegalStateException("Selected Moonwound raid boss resolved to a legendary or mythical form");
            }
            pokemon.setUuid(event.raidId());
            pokemon.setAlpha(false);
            identify(pokemon, event);
            ShadowService.fullyCorrupt(pokemon, entity, false);
            ShadowMoveUtil.assignShadowMoves(pokemon);
            pokemon.heal();
            CompoundTag savedDefinition = new CompoundTag();
            savedDefinition.putString("Id", definition.getId().toString());
            savedDefinition.putByteArray("Definition", definition.encode());
            if (!event.rememberRaidSnapshot(pokemon.saveToNBT(level.registryAccess(), new CompoundTag()), savedDefinition)) {
                throw new IllegalStateException("Could not persist Moonwound raid issuance");
            }
        } else {
            Pokemon pokemon = Pokemon.Companion.loadFromNBT(level.registryAccess(), snapshot);
            if (!event.raidId().equals(pokemon.getUuid()) || pokemon.isAlpha()) throw new IllegalStateException("Invalid saved raid Pokemon");
            pokemon.heal();
            identify(pokemon, event);
            Entity loaded = level.getEntity(event.raidId());
            if (loaded != null) {
                if (!(loaded instanceof PokemonEntity previous) || eventFor(previous) != event) {
                    throw new IllegalStateException("Moonwound raid entity identity collision");
                }
                previous.discard();
            }
            entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
        }
        entity.setUUID(event.raidId());
        entity.setDrops(new DropTable());
        entity.addTag("alphas.non_wild");
        Vec3 origin = MoonwoundRaidSpawn.findGround(level, site, entity,
                MoonwoundClaimProtection.spawnCheck(level, event.eventId()));
        if (origin == null) {
            var dimensions = entity.getDimensions(entity.getPose());
            throw new IllegalStateException("No clear supported ground on or beside the Moonwound meteorite: species="
                    + entity.getPokemon().getSpecies().getResourceIdentifier() + ", dimensions="
                    + dimensions.width() + "x" + dimensions.height() + ", site=" + site.center);
        }
        if (!MoonwoundClaimProtection.canSpawn(level, event.eventId(),
                entity.getDimensions(entity.getPose()).makeBoundingBox(origin).expandTowards(0, -1, 0))) return;
        entity.moveTo(origin);
        protect(entity);
        IRaidAccessor accessor = (IRaidAccessor) entity;
        accessor.crd_setRaidId(event.raidId());
        accessor.crd_setRaidBoss(definition.getId());
        accessor.crd_setRaidState(RaidState.NOT_STARTED);
        Attempt attempt = new Attempt(level, site, event, entity, definition, origin);
        ATTEMPTS.put(event.eventId(), attempt);
        try {
            OwnedRaid raid = new OwnedRaid(entity, attempt);
            attempt.raid = raid;
            if (raid.failedToStart()) throw new IllegalStateException("Raid Dens could not initialize the Moonwound raid");
            if (!MoonwoundClaimProtection.canSpawn(level, event.eventId(), entity.getBoundingBox().expandTowards(0, -1, 0))) {
                cleanupEvent(event.eventId());
                return;
            }
            RaidInstance previous = RaidHelper.ACTIVE_RAIDS.putIfAbsent(event.raidId(), raid);
            if (previous != null) throw new IllegalStateException("Moonwound raid instance identity collision");
            if (!level.addFreshEntity(entity)) throw new IllegalStateException("Could not spawn Moonwound raid boss");
            if (snapshot != null) AuraBroadcastQueue.queueBroadcast(entity, 2.5F, Integer.MAX_VALUE, 1);
            notifyParty(attempt, Component.translatable("message.shadowedhearts.moonwound.raid_emerged"));
        } catch (RuntimeException error) {
            cleanupEvent(event.eventId());
            throw error;
        }
    }

    private static void identify(Pokemon pokemon, MoonwoundEvent event) {
        CompoundTag identity = new CompoundTag();
        identity.putUUID("Event", event.eventId());
        identity.putUUID("Site", event.siteId());
        pokemon.getPersistentData().put(KEY, identity);
    }

    private static void protect(PokemonEntity entity) {
        entity.setPersistenceRequired();
        entity.setInvulnerable(true);
        // NoAI also disables normal mob physics. Let Cobblemon handle gravity, poses and wandering.
        entity.setNoAi(false);
        entity.setNoGravity(false);
        entity.setDeltaMovement(Vec3.ZERO);
        UncatchableProperty.INSTANCE.uncatchable().apply(entity.getPokemon());
    }

    private static void keepNearby(Attempt attempt) {
        PokemonEntity entity = attempt.entity;
        if (!MoonwoundClaimProtection.canRun(attempt.level, attempt.site, attempt.event)) return;
        // Raid Dens shares one world boss between battles. Its single battleId can clear while
        // another participant still fights, so inspect the native raid's entire battle list.
        if (entity.isBusy() || entity.getBeamMode() != 0
                || attempt.raid.getBattles().stream().anyMatch(battle -> !battle.getEnded())) return;
        Vec3 offset = entity.position().subtract(attempt.origin);
        if (offset.horizontalDistanceSqr() <= 32 * 32 && Math.abs(offset.y) <= 48
                && MoonwoundClaimProtection.canSpawn(attempt.level, attempt.event.eventId(),
                entity.getBoundingBox().expandTowards(0, -1, 0))) return;
        Vec3 ground = MoonwoundRaidSpawn.findGround(attempt.level, attempt.site, entity,
                MoonwoundClaimProtection.spawnCheck(attempt.level, attempt.event.eventId()));
        if (ground == null || !MoonwoundClaimProtection.canSpawn(attempt.level, attempt.event.eventId(),
                entity.getDimensions(entity.getPose()).makeBoundingBox(ground).expandTowards(0, -1, 0))) return;
        entity.getNavigation().stop();
        entity.teleportTo(ground.x, ground.y, ground.z);
        entity.setDeltaMovement(Vec3.ZERO);
        attempt.origin = ground;
    }

    public static @Nullable RaidBoss definitionFor(PokemonEntity entity) {
        MoonwoundEvent event = eventFor(entity);
        Attempt attempt = event == null ? null : ATTEMPTS.get(event.eventId());
        return attempt != null && attempt.entity == entity ? attempt.definition : null;
    }

    public static boolean allowChallenge(ServerPlayer player, @Nullable Entity target) {
        if (!(target instanceof PokemonEntity entity) || !entity.getPokemon().getPersistentData().contains(KEY)) {
            var participant = RaidJoinHelper.getParticipant(player);
            if (participant != null && ATTEMPTS.values().stream().anyMatch(attempt -> attempt.event.raidId().equals(participant.raid()))) {
                player.displayClientMessage(Component.translatable("message.shadowedhearts.moonwound.raid_leave_first"), true);
                return false;
            }
            return true;
        }
        if (!eligible(player, entity)) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.moonwound.raid_party_required"), true);
            return false;
        }
        MoonwoundEvent event = eventFor(entity);
        if ((RaidJoinHelper.isParticipatingOrInQueue(player, false) && !RaidJoinHelper.isParticipating(player, event.raidId()))
                || RaidHelper.REWARD_QUEUE.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.moonwound.raid_other_pending"), true);
            return false;
        }
        return true;
    }

    /** Only the native raid packet can authorize our PRE event; ordinary wild battles never own shared raid HP. */
    public static void nativeChallenge(ServerPlayer player, @Nullable Entity target, Runnable original) {
        if (!(target instanceof PokemonEntity entity) || !entity.getPokemon().getPersistentData().contains(KEY)) {
            original.run();
            return;
        }
        if (!eligible(player, entity)) return;
        NativeChallenge previous = NATIVE_CHALLENGE.get();
        NATIVE_CHALLENGE.set(new NativeChallenge(player, entity));
        try { original.run(); }
        finally {
            if (previous == null) NATIVE_CHALLENGE.remove();
            else NATIVE_CHALLENGE.set(previous);
        }
    }

    private static boolean eligible(ServerPlayer player, PokemonEntity entity) {
        MoonwoundEvent event = eventFor(entity);
        Attempt attempt = event == null ? null : ATTEMPTS.get(event.eventId());
        return event != null && active(event) && event.isActiveMember(player.getUUID())
                && attempt != null && attempt.entity == entity && attempt.raid != null && !attempt.raid.closed
                && player.level() == entity.level() && player.distanceToSqr(entity) <= 96 * 96
                && MoonwoundClaimProtection.canInteractEncounter(player, event.eventId(), entity.getBoundingBox());
    }

    private static @Nullable MoonwoundEvent eventFor(PokemonEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        CompoundTag identity = entity.getPokemon().getPersistentData().getCompound(KEY);
        if (!identity.hasUUID("Site") || !identity.hasUUID("Event")) return null;
        MoonwoundEvent event = MoonwoundSavedData.get(level).find(identity.getUUID("Site"));
        return event != null && event.eventId().equals(identity.getUUID("Event")) ? event : null;
    }

    private static boolean active(MoonwoundEvent event) {
        return event.phase() == MoonwoundEvent.Phase.OVERLOAD && event.finaleMode() == MoonwoundEvent.FinaleMode.RAID
                && !event.activeMembers().isEmpty();
    }

    public static void leaveParticipant(UUID eventId, UUID playerId) {
        Attempt attempt = ATTEMPTS.get(eventId);
        if (attempt == null || attempt.raid == null) return;
        attempt.raid.depart(playerId);
    }

    /** Native leave/disconnect ends only this player's battle; the Moonwound roster is still controller-owned. */
    public static void leaveNativeRaid(net.minecraft.world.entity.player.Player player) {
        var participant = RaidJoinHelper.getParticipant(player);
        if (participant == null) return;
        for (Attempt attempt : new ArrayList<>(ATTEMPTS.values())) {
            if (participant.raid().equals(attempt.event.raidId()) && attempt.raid != null) {
                attempt.raid.withdraw(player.getUUID(), false);
                return;
            }
        }
    }

    public static void cleanupEvent(UUID eventId) {
        Attempt attempt = ATTEMPTS.remove(eventId);
        LOADING.remove(eventId);
        if (attempt == null) return;
        RETRY_AFTER.put(eventId, attempt.level.getGameTime() + 100);
        if (attempt.raid != null) attempt.raid.closeRaid(attempt.level.getServer(), true);
        else attempt.entity.discard();
    }

    public static boolean isMoonwoundRaidBattle(ServerPlayer player) {
        PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
        return battle != null && ((IRaidBattle) battle).crd_getRaidBattle() instanceof OwnedRaid raid
                && raid.attempt != null && raid.attempt.event.isActiveMember(player.getUUID()) && !raid.closed;
    }

    private static void pause(ServerLevel level, MoonwoundEvent event, Throwable error) {
        RETRY_AFTER.put(event.eventId(), level.getGameTime() + 1200);
        Shadowedhearts.LOGGER.error("Moonwound {} raid paused; saved boss and event retained", event.eventId(), error);
        for (UUID id : event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) player.displayClientMessage(Component.translatable("message.shadowedhearts.moonwound.raid_start_failed"), false);
        }
    }

    private static void notifyParty(Attempt attempt, Component message) {
        for (UUID id : attempt.event.activeMembers()) {
            ServerPlayer player = attempt.level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == attempt.level) player.displayClientMessage(message, false);
        }
    }

    private static final class Attempt {
        final ServerLevel level;
        final UmbrafallSiteData.Site site;
        final MoonwoundEvent event;
        final PokemonEntity entity;
        final RaidBoss definition;
        Vec3 origin;
        OwnedRaid raid;
        Attempt(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event, PokemonEntity entity,
                RaidBoss definition, Vec3 origin) {
            this.level = level; this.site = site; this.event = event; this.entity = entity; this.definition = definition;
            this.origin = origin;
        }
    }

    /** The callback is bound to this exact native instance, not a species-wide/per-player end event. */
    private static final class OwnedRaid extends RaidInstance {
        private Attempt attempt;
        private final Set<UUID> joined = new HashSet<>();
        private final Map<UUID, ServerPlayer> participantOwners = new HashMap<>();
        private boolean stopping;
        private boolean closed;

        OwnedRaid(PokemonEntity entity, Attempt attempt) {
            super(entity, null, false);
            this.attempt = attempt;
        }

        @Override public void addPlayer(ServerPlayer player) {
            if (attempt == null || !eligible(player, attempt.entity)) return;
            if (!RaidJoinHelper.isParticipating(player, attempt.event.raidId())
                    && !RaidJoinHelper.addParticipant(player, attempt.event.raidId(), false, false)) return;
            participantOwners.put(player.getUUID(), player);
            if (joined.add(player.getUUID())) {
                float ratio = getCurrentHealth() / getMaxHealth();
                super.addPlayer(player);
                // Native RaidInstance counts its retained player list. We remove leavers from that list,
                // so retain the same native formula using unique historical joins instead.
                if (attempt.definition.getMultiplayerHealthMulti() > 1F) {
                    MoonwoundRaidHealthAccessor health = (MoonwoundRaidHealthAccessor) (Object) this;
                    float initial = health.shadowedhearts$initialHealth();
                    float maximum = initial + initial * (attempt.definition.getMultiplayerHealthMulti() - 1F) * (joined.size() - 1);
                    health.shadowedhearts$maxHealth(maximum);
                    health.shadowedhearts$currentHealth(maximum * ratio);
                }
            } else replacePlayer(player);
        }

        @Override public boolean isPlayerIn(ServerPlayer player) {
            if (attempt == null || !eligible(player, attempt.entity) || !joined.contains(player.getUUID())) return false;
            replacePlayer(player);
            return true;
        }

        private void replacePlayer(ServerPlayer player) {
            if (!RaidJoinHelper.isParticipating(player, attempt.event.raidId())) {
                if (!RaidJoinHelper.addParticipant(player, attempt.event.raidId(), false, false)) return;
            }
            participantOwners.put(player.getUUID(), player);
            for (ServerPlayer previous : new ArrayList<>(getPlayers())) {
                if (previous.getUUID().equals(player.getUUID()) && previous != player) {
                    removeFromBossEvent(previous);
                    getPlayers().remove(previous);
                }
            }
            if (!getPlayers().contains(player)) { getPlayers().add(player); addToBossEvent(player); }
        }

        @Override public void addBattle(PokemonBattle battle) {
            if (attempt == null || closed || battle.getPlayers().isEmpty()
                    || battle.getPlayers().stream().anyMatch(player -> !eligible(player, attempt.entity)
                    || !RaidJoinHelper.isParticipating(player, attempt.event.raidId()))) {
                battle.stop();
                return;
            }
            super.addBattle(battle);
        }

        @Override public void sendEvent(AbstractEvent event, @Nullable PokemonBattle source) {
            if (event instanceof ShowdownEvents.PlayerJoinShowdownEvent join) {
                // Raid Dens 0.13.5 still emits >eval for this cosmetic notification.
                // Cobblemon 1.8.4 rejects it and closes the first participant's simulator output.
                // Health scaling already happens in addPlayer; keep the notification out of Showdown.
                if (closed || isFinished()) return;
                for (PokemonBattle battle : getBattles()) {
                    if (battle.getEnded()) continue;
                    battle.broadcastChatMessage(Component.translatable("battle.cobblemonraiddens.player_join", join.player()));
                    battle.broadcastChatMessage(Component.translatable("battle.cobblemonraiddens.raid_energy",
                            getBossEntity().getPokemon().getDisplayName(false)));
                }
                return;
            }
            super.sendEvent(event, source);
        }

        @Override public void syncHealth(ServerPlayer player, PokemonBattle battle, float damage) {
            if (attempt != null && !closed && getBattles().contains(battle)
                    && eligible(player, attempt.entity) && Float.isFinite(damage)) super.syncHealth(player, battle, damage);
        }

        @Override public void removePlayer(ServerPlayer player, @Nullable PokemonBattle battle, boolean ignoreLives) {
            if (ignoreLives && attempt != null && attempt.event.isActiveMember(player.getUUID())) {
                // Flee/disconnect is a pause for this Moonwound member, not implicit abandonment.
                if (battle != null) {
                    getBattles().remove(battle);
                    ((IRaidBattle) battle).crd_setRaidBattle(null);
                }
                removeFromBossEvent(player);
                getPlayers().remove(player);
                if (RaidJoinHelper.isParticipating(player, attempt.event.raidId())) RaidJoinHelper.removeParticipant(player);
                return;
            }
            super.removePlayer(player, battle, ignoreLives);
        }

        @Override public void stopRaid(boolean success) {
            if (closed || stopping || attempt == null) return;
            if (!MoonwoundClaimProtection.canSpawn(attempt.level, attempt.event.eventId(),
                    attempt.entity.getBoundingBox().expandTowards(0, -1, 0))) {
                // Do not call the native outcome/reward distributor for a revoked event.
                closeRaid(attempt.level.getServer(), true);
                return;
            }
            stopping = true;
            // Exclude departed/offline/revoked players before native outcome and reward handling.
            refreshPlayers();
            if (getPlayers().isEmpty()) {
                closeRaid(attempt.level.getServer(), true);
                return;
            }
            boolean won = success && getCurrentHealth() <= 0F && active(attempt.event)
                    && MoonwoundClaimProtection.canRun(attempt.level, attempt.site, attempt.event);
            try {
                super.stopRaid(won);
            } finally {
                if (won && getRaidState() == RaidState.SUCCESS) attempt.event.completeRaid(attempt.event.raidId(), true);
                else if (active(attempt.event)) notifyParty(attempt, Component.translatable("message.shadowedhearts.moonwound.raid_retry_available"));
                closeRaid(attempt.level.getServer(), true);
            }
        }

        @Override public void closeRaid(net.minecraft.server.MinecraftServer server, boolean cancelled) {
            if (closed) return;
            closed = true;
            var entityIds = new ArrayList<Integer>();
            for (PokemonBattle battle : getBattles()) for (var actor : battle.getActors()) for (var pokemon : actor.getPokemonList()) {
                if (pokemon.getEntity() != null) entityIds.add(pokemon.getEntity().getId());
            }
            getPlayers().forEach(player -> RaidDenNetworkMessages.RAID_HEALTH_BAR.accept(player, entityIds, false));
            for (PokemonBattle battle : new ArrayList<>(getBattles())) {
                ((IRaidBattle) battle).crd_setRaidBattle(null);
                if (!battle.getEnded()) battle.stop();
            }
            getBattles().clear();
            // Remove only our own participant bindings, including disconnected players.
            // Clearing the list before upstream cleanup protects unrelated den participation.
            if (attempt != null) for (ServerPlayer player : participantOwners.values()) {
                if (RaidJoinHelper.isParticipating(player, attempt.event.raidId())) RaidJoinHelper.removeParticipant(player);
            }
            var players = new ArrayList<>(getPlayers());
            players.forEach(this::removeFromBossEvent);
            getPlayers().clear();
            RaidInstance registered = RaidHelper.ACTIVE_RAIDS.get(((IRaidAccessor) getBossEntity()).crd_getRaidId());
            if (registered == null || registered == this) super.closeRaid(server, cancelled);
            else getBossEntity().discard();
            if (attempt != null) {
                ATTEMPTS.remove(attempt.event.eventId(), attempt);
                RETRY_AFTER.put(attempt.event.eventId(), attempt.level.getGameTime() + 100);
            }
        }

        void depart(UUID playerId) {
            withdraw(playerId, true);
        }

        void withdraw(UUID playerId, boolean permanentlyLeaving) {
            for (PokemonBattle battle : new ArrayList<>(getBattles())) {
                if (battle.getPlayers().stream().anyMatch(player -> player.getUUID().equals(playerId))) {
                    ServerPlayer player = battle.getPlayers().stream().filter(p -> p.getUUID().equals(playerId)).findFirst().orElse(null);
                    if (player != null && permanentlyLeaving) super.removePlayer(player, battle, true);
                    else {
                        getBattles().remove(battle);
                        ((IRaidBattle) battle).crd_setRaidBattle(null);
                    }
                    if (!battle.getEnded()) battle.stop();
                }
            }
            for (ServerPlayer player : new ArrayList<>(getPlayers())) {
                if (player.getUUID().equals(playerId)) { removeFromBossEvent(player); getPlayers().remove(player); }
            }
            ServerPlayer owner = participantOwners.remove(playerId);
            if (owner != null && RaidJoinHelper.isParticipating(owner, attempt.event.raidId())) RaidJoinHelper.removeParticipant(owner);
        }

        void refreshPlayers() {
            if (attempt == null) return;
            for (ServerPlayer player : new ArrayList<>(getPlayers())) {
                ServerPlayer online = attempt.level.getServer().getPlayerList().getPlayer(player.getUUID());
                if (!attempt.event.isActiveMember(player.getUUID()) || online == null || online.level() != attempt.level
                        || !MoonwoundClaimProtection.canInteractEncounter(online, attempt.event.eventId(), attempt.entity.getBoundingBox())) {
                    withdraw(player.getUUID(), false);
                } else if (online != player) replacePlayer(online);
            }
        }
    }
}
