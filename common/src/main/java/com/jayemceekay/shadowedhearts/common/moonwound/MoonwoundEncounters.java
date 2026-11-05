package com.jayemceekay.shadowedhearts.common.moonwound;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.BattleEndResult;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.jayemceekay.shadowedhearts.common.moonwound.raid.MoonwoundRaidIntegration;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowSpawnTemplates;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import kotlin.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Native encounters for the server-owned event ledger; entity disappearance is never a victory. */
public final class MoonwoundEncounters {
    public static final String KEY = "shadowedhearts:moonwound_encounter";
    private static final String MOVEMENT_VERSION_KEY = "movement_version";
    private static final int MOVEMENT_VERSION = 1;
    private static final ResourceLocation FALLBACK_ALPHA = ResourceLocation.fromNamespaceAndPath("cobblemon", "absol");
    private static final Map<EmptyPokeBallEntity, CapturePermit> CAPTURE_PERMITS = new WeakHashMap<>();
    private static final Map<UUID, BattlePermit> BATTLE_PERMITS = new HashMap<>();
    private static Listener listener;
    private static boolean initialized;

    private MoonwoundEncounters() { }

    /** The controller must persist issuance before spawning and make outcome handling idempotent. */
    public interface Listener {
        boolean isActive(MinecraftServer server, UUID eventId, UUID pokemonId);
        boolean canParticipate(MinecraftServer server, UUID eventId, UUID playerId);
        void onDefeated(MinecraftServer server, UUID eventId, UUID pokemonId, Set<UUID> winners);
        void onCaptured(MinecraftServer server, UUID eventId, UUID pokemonId, UUID playerId);
        default void onLoaded(MinecraftServer server, UUID eventId, UUID pokemonId, PokemonEntity entity) { }
        /** Controllers with a persisted roster must supply player-authorized event placement. */
        default boolean canSpawn(ServerLevel world, UUID eventId, AABB bounds) {
            return unclaimedFootprint(world, bounds);
        }
        /** Claim-only recheck; unlike admission, an already-thrown ball may settle beyond party range. */
        default boolean canInteractEncounter(ServerPlayer player, UUID eventId, AABB bounds) {
            return canParticipate(player.server, eventId, player.getUUID())
                    && interactionFootprint(player, bounds);
        }
    }

    /** Register once during common initialization, after the event ledger is available. */
    public static void init(Listener controller) {
        listener = Objects.requireNonNull(controller);
        if (initialized) return;
        initialized = true;
        LifecycleEvent.SERVER_STOPPED.register(server -> { CAPTURE_PERMITS.clear(); BATTLE_PERMITS.clear(); });
        TickEvent.SERVER_PRE.register(server -> {
            for (EmptyPokeBallEntity ball : new ArrayList<>(CAPTURE_PERMITS.keySet())) {
                if (ball != null && ball.getServer() == server) recheckCapture(ball);
            }
            if (server.getTickCount() % 20 == 0) recheckBattles(server);
        });
        TickEvent.SERVER_POST.register(server -> {
            if (server.getTickCount() % 200 == 0) {
                pruneCaptures(server);
                BATTLE_PERMITS.keySet().removeIf(id -> BattleRegistry.getBattle(id) == null);
            }
        });
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.LOWEST, event -> {
            guardBattle(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.LOWEST, event -> {
            guardCapture(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, event -> {
            rememberBattle(event.getBattle());
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_ENDED.subscribe(Priority.NORMAL, event -> {
            // A native victory event follows end(); retain its roster until that event arrives.
            if (event.getResult() != BattleEndResult.VICTORY) BATTLE_PERMITS.remove(event.getBattle().getBattleId());
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            recordVictory(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            recordCapture(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe(Priority.LOWEST, event -> {
            PokemonEntity entity = event.getPokemonEntity();
            Identity identity = identity(entity.getPokemon());
            if (identity != null && entity.level() instanceof ServerLevel world) {
                PokemonEntity existing = find(world, identity.eventId(), entity.getPokemon().getUuid());
                if (entity.getPokemon().isPlayerOwned()) entity.getPokemon().getPersistentData().remove(KEY);
                else if (!active(world.getServer(), identity, entity.getPokemon())
                        || !listener.canSpawn(world, identity.eventId(), entity.getBoundingBox().expandTowards(0, -1, 0))
                        || (existing != null && existing != entity)) event.cancel();
                else {
                    protect(entity);
                    listener.onLoaded(world.getServer(), identity.eventId(), entity.getPokemon().getUuid(), entity);
                }
            }
            return Unit.INSTANCE;
        });
    }

    @Nullable
    public static PokemonEntity spawnVoid(ServerLevel world, Vec3 position, int level, UUID eventId, UUID issuedPokemonId) {
        PokemonEntity existing = find(world, eventId, issuedPokemonId);
        if (existing != null) return existing;
        Pokemon pokemon = VoidShadowService.create(level);
        identify(pokemon, eventId, issuedPokemonId, false);
        return spawnPrepared(world, position, pokemon);
    }

    /** Null species samples the local natural pool without spawning a template entity. */
    @Nullable
    public static PokemonEntity spawnAlpha(ServerLevel world, Vec3 position, int requestedLevel,
                                           @Nullable ResourceLocation species, UUID eventId, UUID issuedPokemonId) {
        PokemonEntity existing = find(world, eventId, issuedPokemonId);
        if (existing != null) return existing;
        return spawnPrepared(world, position, createAlpha(world, position, requestedLevel, species, eventId, issuedPokemonId));
    }

    /** Create once, then persist its NBT before spawnPrepared so recovery never rerolls the reward. */
    public static Pokemon createAlpha(ServerLevel world, Vec3 position, int requestedLevel,
                                      @Nullable ResourceLocation species, UUID eventId, UUID issuedPokemonId) {
        int level = Math.clamp(requestedLevel, 1, Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel());
        ResourceLocation selected = species == null ? FALLBACK_ALPHA : species;
        if (PokemonSpecies.INSTANCE.getByIdentifier(selected) == null || selected.equals(VoidShadowService.SPECIES)) {
            throw new IllegalArgumentException("Invalid Moonwound Alpha species: " + selected);
        }
        Pokemon pokemon = PokemonProperties.Companion.parse(selected + " level=" + level + " shiny=false").create();
        if (species == null) {
            PokemonEntity anchor = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
            anchor.setPos(position);
            Entity cause = world.players().stream()
                    .filter(player -> listener != null && listener.canParticipate(world.getServer(), eventId, player.getUUID()))
                    .min(java.util.Comparator.comparingDouble(anchor::distanceToSqr)).map(Entity.class::cast).orElse(anchor);
            Pokemon local = VoidShadowSpawnTemplates.select(anchor, cause);
            if (local != null && !local.isBattleClone() && !local.isLegendary() && !local.isMythical()) pokemon = local;
        }
        identify(pokemon, eventId, issuedPokemonId, true);
        pokemon.setLevel(level);
        pokemon.setAlpha(true);
        pokemon.initializeMovesetFromDefault();
        pokemon.setTradeable(true);
        UncatchableProperty.INSTANCE.catchable().apply(pokemon);
        ShadowService.fullyCorrupt(pokemon, null, false);
        ShadowMoveUtil.assignShadowMoves(pokemon);
        pokemon.heal();
        return pokemon;
    }

    /** Searches only loaded entities. A null result does not prove an unloaded enemy is gone. */
    @Nullable
    public static PokemonEntity find(ServerLevel world, UUID eventId, UUID issuedPokemonId) {
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof PokemonEntity pokemonEntity && !entity.isRemoved()
                    && !pokemonEntity.getPokemon().isPlayerOwned()
                    && issuedPokemonId.equals(pokemonEntity.getPokemon().getUuid())) {
                Identity identity = identity(pokemonEntity.getPokemon());
                if (identity != null && identity.eventId().equals(eventId)) return pokemonEntity;
            }
        }
        return null;
    }

    /** Call after removing issuance from the ledger, so a later-loaded copy is also rejected. */
    public static void discard(ServerLevel world, UUID eventId, UUID issuedPokemonId) {
        var matches = new java.util.ArrayList<PokemonEntity>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof PokemonEntity pokemonEntity && issuedPokemonId.equals(pokemonEntity.getPokemon().getUuid())) {
                Identity identity = identity(pokemonEntity.getPokemon());
                if (identity != null && identity.eventId().equals(eventId) && !pokemonEntity.getPokemon().isPlayerOwned()) {
                    matches.add(pokemonEntity);
                }
            }
        }
        for (PokemonEntity entity : matches) {
            discardEncounter(entity);
        }
    }

    /** Call after persisting departure. Other participants and unrelated battles remain untouched. */
    public static void leaveParticipant(ServerLevel world, UUID eventId, UUID playerId) {
        abortCaptures(world, eventId, playerId);
        stopOwnedBattles(world, eventId, playerId);
    }

    /** Call after persisting a terminal event; later-loaded wild copies are rejected by the ledger. */
    public static void cleanupEvent(ServerLevel world, UUID eventId) {
        abortCaptures(world, eventId, null);
        stopOwnedBattles(world, eventId, null);
        for (ServerLevel level : world.getServer().getAllLevels()) {
            var enemies = new ArrayList<PokemonEntity>();
            for (Entity candidate : level.getAllEntities()) {
                if (candidate instanceof PokemonEntity entity && belongsToEvent(entity.getPokemon(), eventId)) enemies.add(entity);
            }
            for (PokemonEntity entity : enemies) entity.discard();
        }
    }

    private static void stopOwnedBattles(ServerLevel world, UUID eventId, @Nullable UUID playerId) {
        Map<UUID, PokemonBattle> battles = new LinkedHashMap<>();
        // A native faint/capture animation can hide the enemy entity while its battle still exists.
        for (var entry : new ArrayList<>(BATTLE_PERMITS.entrySet())) {
            BattlePermit permit = entry.getValue();
            if (permit.server() != world.getServer() || !permit.players().containsKey(eventId)
                    || (playerId != null && !permit.players().get(eventId).contains(playerId))) continue;
            PokemonBattle battle = BattleRegistry.getBattle(entry.getKey());
            if (battle != null) battles.put(entry.getKey(), battle);
            else BATTLE_PERMITS.remove(entry.getKey());
        }
        // Also cover tagged native battles whose event callback has not installed a permit yet.
        for (ServerLevel level : world.getServer().getAllLevels()) {
            for (Entity candidate : level.getAllEntities()) {
                if (!(candidate instanceof PokemonEntity entity) || !belongsToEvent(entity.getPokemon(), eventId)) continue;
                PokemonBattle battle = entity.getBattle();
                if (battle != null && (playerId == null || hasPlayer(battle, playerId))) battles.put(battle.getBattleId(), battle);
            }
        }
        if (playerId != null) {
            PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayerId(playerId);
            if (battle != null) {
                for (var actor : battle.getActors()) {
                    if (actor.getType() != ActorType.WILD) continue;
                    for (var member : actor.getPokemonList()) {
                        if (belongsToEvent(member.getEffectedPokemon(), eventId)) {
                            battles.put(battle.getBattleId(), battle);
                            break;
                        }
                    }
                }
            }
        }
        for (PokemonBattle battle : battles.values()) {
            BATTLE_PERMITS.remove(battle.getBattleId());
            if (!battle.getEnded()) battle.stop();
        }
    }

    private static boolean hasPlayer(PokemonBattle battle, UUID playerId) {
        for (UUID participant : battle.getPlayerUUIDs()) if (participant.equals(playerId)) return true;
        return false;
    }

    private static void abortCaptures(ServerLevel world, UUID eventId, @Nullable UUID playerId) {
        for (var entry : new ArrayList<>(CAPTURE_PERMITS.entrySet())) {
            EmptyPokeBallEntity ball = entry.getKey();
            CapturePermit permit = entry.getValue();
            if (ball == null || ball.getServer() != world.getServer() || !permit.eventId().equals(eventId)
                    || (playerId != null && !permit.playerId().equals(playerId))) continue;
            abortCapture(world, ball, permit);
        }
    }

    /** Called before the ball's scheduled capture callback can commit ownership. */
    public static void recheckCapture(EmptyPokeBallEntity ball) {
        CapturePermit permit = CAPTURE_PERMITS.get(ball);
        if (permit == null || ball.getCaptureFuture().isDone() || !(ball.level() instanceof ServerLevel world)) return;
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(permit.playerId());
        PokemonEntity target = ball.getCapturingPokemon();
        if (target == null) target = find(world, permit.eventId(), permit.pokemonId());
        if (player == null || player != ball.getOwner() || target == null || listener == null
                || !listener.isActive(world.getServer(), permit.eventId(), permit.pokemonId())
                || !listener.canInteractEncounter(player, permit.eventId(), target.getBoundingBox())) {
            abortCapture(world, ball, permit);
        }
    }

    private static void abortCapture(ServerLevel world, EmptyPokeBallEntity ball, CapturePermit permit) {
        CAPTURE_PERMITS.remove(ball);
        // Native ownership commit checks !captureFuture.isDone() and target.isAlive(). Close
        // both gates before discarding the ball; merely revoking our permit is too late to
        // stop native party/PC insertion. Remaining members recover the same saved issuance.
        ball.getCaptureFuture().complete(false);
        PokemonEntity target = ball.getCapturingPokemon();
        if (target != null && permit.pokemonId().equals(target.getPokemon().getUuid())
                && belongsToEvent(target.getPokemon(), permit.eventId())) discardEncounter(target);
        for (ServerLevel level : world.getServer().getAllLevels()) discard(level, permit.eventId(), permit.pokemonId());
        ball.discard();
    }

    private static boolean belongsToEvent(Pokemon pokemon, UUID eventId) {
        Identity identity = identity(pokemon);
        return !pokemon.isPlayerOwned() && identity != null && identity.eventId().equals(eventId);
    }

    private static void discardEncounter(PokemonEntity entity) {
        PokemonBattle battle = entity.getBattle();
        if (battle != null) {
            BATTLE_PERMITS.remove(battle.getBattleId());
            if (!battle.getEnded()) battle.stop();
        }
        entity.discard();
    }

    public static void protect(PokemonEntity entity) {
        entity.setPersistenceRequired();
        entity.setInvulnerable(true);
        restoreLegacyMovement(entity);
    }

    /** Native battles, capture and recall own movement until their locks/animation have finished. */
    static boolean movementLocked(PokemonEntity entity) {
        if (entity.isBusy() || entity.isBattling() || entity.getBeamMode() != 0) return true;
        Identity identity = identity(entity.getPokemon());
        return identity != null && hasPendingCapture(identity.eventId(), entity.getPokemon().getUuid());
    }

    private static void restoreLegacyMovement(PokemonEntity entity) {
        if (identity(entity.getPokemon()) == null || movementLocked(entity)) return;
        CompoundTag data = entity.getPokemon().getPersistentData().getCompound(KEY);
        if (data.getInt(MOVEMENT_VERSION_KEY) >= MOVEMENT_VERSION) return;
        // Earlier Moonwounds persisted NoAI/NoGravity on every opponent. Migrate once, then leave
        // gravity to Cobblemon's pose changes (including flight/swimming) and its native animations.
        entity.setNoAi(false);
        entity.setNoGravity(PoseType.Companion.getNO_GRAV_POSES().contains(entity.getCurrentPoseType())
                && entity.getPassengers().isEmpty());
        data.putInt(MOVEMENT_VERSION_KEY, MOVEMENT_VERSION);
    }

    /** Battle-only player protection; the controller owns between-battle event risk. */
    public static boolean isMoonwoundBattle(ServerPlayer player) {
        if (MoonwoundRaidIntegration.isMoonwoundRaidBattle(player)) return true;
        var battle = BattleRegistry.getBattleByParticipatingPlayer(player);
        if (battle == null || battle.getEnded()) return false;
        BattlePermit permit = BATTLE_PERMITS.get(battle.getBattleId());
        if (permit == null) return false;
        for (var actor : battle.getActors()) {
            if (actor.getType() != ActorType.WILD) continue;
            for (var member : actor.getPokemonList()) {
                Pokemon pokemon = member.getEffectedPokemon();
                Identity identity = identity(pokemon);
                if (identity != null && active(player.server, identity, pokemon)
                        && permit.players().getOrDefault(identity.eventId(), Set.of()).contains(player.getUUID())) return true;
            }
        }
        return false;
    }

    @Nullable
    public static PokemonEntity spawnPrepared(ServerLevel world, Vec3 position, Pokemon pokemon) {
        Identity identity = Objects.requireNonNull(identity(pokemon), "Prepared Pokemon lacks Moonwound issuance");
        if (pokemon.isPlayerOwned()) throw new IllegalArgumentException("Cannot spawn a player-owned Moonwound reward");
        PokemonEntity existing = find(world, identity.eventId(), pokemon.getUuid());
        if (existing != null) return existing;
        PokemonEntity preview = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
        preview.refreshDimensions();
        AABB bounds = preview.getDimensions(preview.getPose()).makeBoundingBox(position).expandTowards(0, -1, 0);
        // Native sendOut can search up to four blocks sideways around a blocked position.
        // Reserve its read area without loading a neighboring chunk just for that search.
        if (!validFootprint(world, bounds.inflate(5, 0, 5))
                || listener == null || !listener.canSpawn(world, identity.eventId(), bounds)) return null;
        try {
            return pokemon.sendOut(world, position, null, entity -> {
                // Cobblemon adjusts the requested position before this callback. Check the
                // actual result again, after its PRE listeners and before finalizeSpawn/addFreshEntity.
                if (!listener.canSpawn(world, identity.eventId(), entity.getBoundingBox().expandTowards(0, -1, 0))) {
                    throw SpawnDenied.INSTANCE;
                }
                entity.setUUID(UUID.nameUUIDFromBytes((KEY + ":" + identity.eventId() + ":" + pokemon.getUuid())
                        .getBytes(StandardCharsets.UTF_8)));
                protect(entity);
                return Unit.INSTANCE;
            });
        } catch (SpawnDenied denied) {
            return null;
        }
    }

    private static void identify(Pokemon pokemon, UUID eventId, UUID issuedPokemonId, boolean alpha) {
        pokemon.setUuid(issuedPokemonId);
        CompoundTag identity = new CompoundTag();
        identity.putUUID("event", eventId);
        identity.putBoolean("alpha", alpha);
        identity.putInt(MOVEMENT_VERSION_KEY, MOVEMENT_VERSION);
        pokemon.getPersistentData().put(KEY, identity);
    }

    private static void guardBattle(BattleStartedEvent.Pre event) {
        for (var actor : event.getBattle().getActors()) {
            if (actor.getType() != ActorType.WILD) continue;
            for (var member : actor.getPokemonList()) {
                Pokemon pokemon = member.getEffectedPokemon();
                Identity identity = identity(pokemon);
                if (identity == null) continue;
                PokemonEntity entity = member.getEntity();
                if (entity == null || !(entity.level() instanceof ServerLevel world)
                        || !active(world.getServer(), identity, pokemon)
                        || event.getBattle().getPlayers().isEmpty()
                        || event.getBattle().getPlayers().stream().anyMatch(player ->
                         !listener.canParticipate(world.getServer(), identity.eventId(), player.getUUID())
                                 || !listener.canInteractEncounter(player, identity.eventId(), entity.getBoundingBox()))) {
                    event.setReason(Component.translatable("message.shadowedhearts.moonwound.encounter_party_required"));
                    event.cancel();
                    return;
                }
            }
        }
    }

    private static void guardCapture(ThrownPokeballHitEvent event) {
        Pokemon pokemon = event.getPokemon().getPokemon();
        Identity identity = identity(pokemon);
        if (identity == null || !(event.getPokemon().level() instanceof ServerLevel world)) return;
        pruneCaptures(world.getServer());
        if (!identity.alpha() || !active(world.getServer(), identity, pokemon)
                || !(event.getPokeBall().getOwner() instanceof ServerPlayer player)
                || !listener.canParticipate(world.getServer(), identity.eventId(), player.getUUID())
                || !listener.canInteractEncounter(player, identity.eventId(), event.getPokemon().getBoundingBox())) {
            CAPTURE_PERMITS.remove(event.getPokeBall());
            event.cancel();
        } else if (!event.isCanceled()) {
            if (CAPTURE_PERMITS.size() >= 256 && !CAPTURE_PERMITS.containsKey(event.getPokeBall())) event.cancel();
            else CAPTURE_PERMITS.put(event.getPokeBall(), new CapturePermit(identity.eventId(), pokemon.getUuid(),
                    player.getUUID(), world.getServer().getTickCount()));
        }
    }

    private static void rememberBattle(PokemonBattle battle) {
        Map<UUID, Set<UUID>> participants = new HashMap<>();
        Map<UUID, UUID> encounters = new HashMap<>();
        Map<UUID, PokemonEntity> entities = new HashMap<>();
        MinecraftServer server = null;
        for (var actor : battle.getActors()) {
            if (actor.getType() != ActorType.WILD) continue;
            for (var member : actor.getPokemonList()) {
                Pokemon pokemon = member.getEffectedPokemon();
                Identity identity = identity(pokemon);
                if (identity == null || member.getEntity() == null
                        || !(member.getEntity().level() instanceof ServerLevel world)
                        || !active(world.getServer(), identity, pokemon)) continue;
                Set<UUID> players = new HashSet<>();
                for (ServerPlayer player : battle.getPlayers()) {
                    if (listener.canParticipate(world.getServer(), identity.eventId(), player.getUUID())) players.add(player.getUUID());
                }
                if (!players.isEmpty()) {
                    server = world.getServer();
                    participants.put(identity.eventId(), Set.copyOf(players));
                    encounters.put(pokemon.getUuid(), identity.eventId());
                    entities.put(pokemon.getUuid(), member.getEntity());
                }
            }
        }
        if (server != null) BATTLE_PERMITS.put(battle.getBattleId(), new BattlePermit(server,
                Map.copyOf(participants), Map.copyOf(encounters), Map.copyOf(entities)));
    }

    private static void recheckBattles(MinecraftServer server) {
        for (var entry : new ArrayList<>(BATTLE_PERMITS.entrySet())) {
            BattlePermit permit = entry.getValue();
            PokemonBattle battle = BattleRegistry.getBattle(entry.getKey());
            if (permit.server() != server || battle == null || battle.getEnded()) continue;
            boolean revoked = permit.encounters().entrySet().stream().anyMatch(encounter -> {
                PokemonEntity entity = permit.entities().get(encounter.getKey());
                return entity == null || listener == null || battle.getPlayers().stream().anyMatch(player ->
                        !listener.canInteractEncounter(player, encounter.getValue(), entity.getBoundingBox()));
            });
            if (!revoked) continue;
            BATTLE_PERMITS.remove(entry.getKey());
            battle.stop();
            for (var encounter : permit.encounters().entrySet()) {
                for (ServerLevel world : server.getAllLevels()) discard(world, encounter.getValue(), encounter.getKey());
            }
        }
    }

    /** Native faint/recall animations can hide an enemy while its battle still owns it. */
    public static boolean hasActiveBattle(UUID eventId, UUID pokemonId) {
        return BATTLE_PERMITS.entrySet().stream().anyMatch(entry -> {
            PokemonBattle battle = BattleRegistry.getBattle(entry.getKey());
            return battle != null && !battle.getEnded() && eventId.equals(entry.getValue().encounters().get(pokemonId));
        });
    }

    /** Do not recover an absent finale while its already-authorized native ball is settling. */
    public static boolean hasPendingCapture(UUID eventId, UUID pokemonId) {
        return CAPTURE_PERMITS.entrySet().stream().anyMatch(entry -> {
            CapturePermit permit = entry.getValue();
            return eventId.equals(permit.eventId()) && pokemonId.equals(permit.pokemonId())
                    && capturePending(entry.getKey(), permit);
        });
    }

    private static void recordVictory(BattleVictoryEvent event) {
        BattlePermit permit = BATTLE_PERMITS.remove(event.getBattle().getBattleId());
        // end() heals wild Pokemon before this event. Native faint sequence evidence survives it.
        if (permit == null || event.getWasWildCapture() || event.getBattle().getEndResult() != BattleEndResult.VICTORY) return;
        for (var loser : event.getLosers()) {
            if (loser.getType() != ActorType.WILD) continue;
            for (var member : loser.getPokemonList()) {
                Pokemon pokemon = member.getEffectedPokemon();
                Identity identity = identity(pokemon);
                if (identity == null || member.getFaintedAt() == null || !active(permit.server(), identity, pokemon)) continue;
                PokemonEntity target = permit.entities().get(pokemon.getUuid());
                if (target == null) continue;
                Set<UUID> winners = new HashSet<>();
                for (var winner : event.getWinners()) {
                    if (winner instanceof PlayerBattleActor playerActor
                            && permit.players().getOrDefault(identity.eventId(), Set.of()).contains(playerActor.getUuid())) {
                        ServerPlayer player = permit.server().getPlayerList().getPlayer(playerActor.getUuid());
                        if (player != null && listener.canInteractEncounter(player, identity.eventId(), target.getBoundingBox())) {
                            winners.add(playerActor.getUuid());
                        }
                    }
                }
                if (!winners.isEmpty()) {
                    listener.onDefeated(permit.server(), identity.eventId(), pokemon.getUuid(), Set.copyOf(winners));
                }
            }
        }
    }

    private static void recordCapture(PokemonCapturedEvent event) {
        Pokemon pokemon = event.getPokemon();
        Identity identity = identity(pokemon);
        ServerPlayer player = event.getPlayer();
        CapturePermit permit = CAPTURE_PERMITS.get(event.getPokeBallEntity());
        // Native capture adds to party/PC before posting this event, so ownership is expected here.
        if (identity == null || !identity.alpha() || listener == null
                || !listener.isActive(player.server, identity.eventId(), pokemon.getUuid())
                || !pokemon.isPlayerOwned() || !player.getUUID().equals(pokemon.getOwnerUUID())
                || permit == null || !permit.eventId().equals(identity.eventId())
                || !permit.pokemonId().equals(pokemon.getUuid()) || !permit.playerId().equals(player.getUUID())
                || event.getPokeBallEntity().getCaptureFuture().isCompletedExceptionally()
                || !Boolean.TRUE.equals(event.getPokeBallEntity().getCaptureFuture().getNow(false))) return;
        CAPTURE_PERMITS.remove(event.getPokeBallEntity());
        // A permitted throw may finish after its player crosses the site's range boundary.
        // Keep that committed capture valid instead of reissuing an already-owned Alpha.
        // The caught Pokemon becomes ordinary player property, not an ongoing guarded encounter.
        pokemon.getPersistentData().remove(KEY);
        listener.onCaptured(player.server, identity.eventId(), pokemon.getUuid(), player.getUUID());
    }

    private static boolean active(MinecraftServer server, Identity identity, Pokemon pokemon) {
        return listener != null && !pokemon.isPlayerOwned()
                && listener.isActive(server, identity.eventId(), pokemon.getUuid());
    }

    private static boolean unclaimedFootprint(ServerLevel world, AABB bounds) {
        if (!validFootprint(world, bounds)) return false;
        Set<ChunkPos> checked = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(Math.nextDown(bounds.maxX), Math.nextDown(bounds.maxY), Math.nextDown(bounds.maxZ)))) {
            ChunkPos chunk = new ChunkPos(pos);
            if (!checked.add(chunk)) continue;
            var claim = FTBChunksClaimBridgeHolder.INSTANCE.lookupClaim(world, chunk);
            if (claim.isClaimed() || claim.isUnavailable()) return false;
        }
        return true;
    }

    private static boolean interactionFootprint(ServerPlayer player, AABB bounds) {
        ServerLevel world = player.serverLevel();
        if (!validFootprint(world, bounds)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(Math.nextDown(bounds.maxX), Math.nextDown(bounds.maxY), Math.nextDown(bounds.maxZ)))) {
            if (!FTBChunksClaimBridgeHolder.INSTANCE.canInteractBlock(player, pos, InteractionHand.MAIN_HAND)) return false;
        }
        return true;
    }

    private static boolean validFootprint(ServerLevel world, AABB bounds) {
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
                || bounds.getXsize() <= 0 || bounds.getXsize() > 64 || bounds.getYsize() <= 0 || bounds.getYsize() > 64
                || bounds.getZsize() <= 0 || bounds.getZsize() > 64 || bounds.minY < world.getMinBuildHeight()
                || bounds.maxY > world.getMaxBuildHeight() || bounds.minX < world.getWorldBorder().getMinX()
                || bounds.maxX > world.getWorldBorder().getMaxX() || bounds.minZ < world.getWorldBorder().getMinZ()
                || bounds.maxZ > world.getWorldBorder().getMaxZ()) return false;
        for (int x = (int) Math.floor(bounds.minX) >> 4; x <= (int) Math.floor(Math.nextDown(bounds.maxX)) >> 4; x++) {
            for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= (int) Math.floor(Math.nextDown(bounds.maxZ)) >> 4; z++) {
                if (world.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private static void pruneCaptures(MinecraftServer server) {
        CAPTURE_PERMITS.entrySet().removeIf(entry -> !capturePending(entry.getKey(), entry.getValue()));
    }

    private static boolean capturePending(EmptyPokeBallEntity ball, CapturePermit permit) {
        MinecraftServer server = ball.getServer();
        var future = ball.getCaptureFuture();
        return server != null && server.getTickCount() - permit.issuedTick() <= 20 * 60 * 5
                && !future.isCancelled() && !future.isCompletedExceptionally()
                && (!ball.isRemoved() || future.isDone())
                && (!future.isDone() || Boolean.TRUE.equals(future.getNow(false)));
    }

    @Nullable
    private static Identity identity(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag identity = data.getCompound(KEY);
        return identity.hasUUID("event") ? new Identity(identity.getUUID("event"), identity.getBoolean("alpha")) : null;
    }

    private record Identity(UUID eventId, boolean alpha) { }
    /** Native sendOut's post-position callback has no cancellation return value. */
    private static final class SpawnDenied extends RuntimeException {
        private static final SpawnDenied INSTANCE = new SpawnDenied();
        private SpawnDenied() { super(null, null, false, false); }
    }
    private record CapturePermit(UUID eventId, UUID pokemonId, UUID playerId, int issuedTick) { }
    private record BattlePermit(MinecraftServer server, Map<UUID, Set<UUID>> players, Map<UUID, UUID> encounters,
                                Map<UUID, PokemonEntity> entities) { }
}
