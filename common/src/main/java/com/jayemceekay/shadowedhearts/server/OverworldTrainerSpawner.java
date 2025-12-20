package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.config.ShadowSpawnConfig;
import com.jayemceekay.shadowedhearts.config.TrainerSpawnConfig;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource;

/**
 * Spawns random NPC trainers around each player with configured chance and limits.
 * Trainers can only be battled once; they despawn after defeat or timeout.
 */
public final class OverworldTrainerSpawner {
    private OverworldTrainerSpawner() {}

    private static final class SpawnedTrainer {
        final UUID entityId;
        final long spawnTick;
        volatile boolean battled;
        SpawnedTrainer(UUID entityId, long spawnTick) {
            this.entityId = entityId;
            this.spawnTick = spawnTick;
        }
    }

    private static final Map<UUID, List<SpawnedTrainer>> PER_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ATTEMPT = new ConcurrentHashMap<>();
    private static final Map<UUID, WeakReference<NPCEntity>> INDEX = new ConcurrentHashMap<>();

    public static void init() {
        // Tick on server post to attempt spawns and cleanup
        TickEvent.SERVER_POST.register(OverworldTrainerSpawner::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                attemptSpawn(level, player, now);
                cleanup(level, player, now);
            }
        }
    }

    private static void attemptSpawn(ServerLevel level, ServerPlayer player, long now) {
        int max = TrainerSpawnConfig.getMaxPerPlayer();
        if (max <= 0) return;
        List<SpawnedTrainer> list = PER_PLAYER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        // purge dead references
        list.removeIf(st -> resolve(st) == null);
        if (list.size() >= max) return;

        long last = LAST_ATTEMPT.getOrDefault(player.getUUID(), 0L);
        if (now - last < TrainerSpawnConfig.getSpawnIntervalTicks()) return;
        LAST_ATTEMPT.put(player.getUUID(), now);

        if (level.getRandom().nextInt(100) >= TrainerSpawnConfig.getSpawnChancePercent()) return;

        BlockPos pos = pickSpawnPos(level, player);
        if (pos == null) return;

        NPCEntity npc = new NPCEntity(level);
        // Give a basic class/appearance
        npc.setNpc(Objects.requireNonNull(NPCClasses.getByIdentifier(cobblemonResource("one_time_trainer"))));
        npc.setForcedResourceIdentifier(npc.getNpc().getResourceIdentifier());
        npc.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(npc);

        // Build party
        buildRandomParty(npc, level.getRandom());

        // Index for tracking
        INDEX.put(npc.getUUID(), new WeakReference<>(npc));
        list.add(new SpawnedTrainer(npc.getUUID(), now));
    }

    private static void cleanup(ServerLevel level, ServerPlayer player, long now) {
        List<SpawnedTrainer> list = PER_PLAYER.get(player.getUUID());
        if (list == null) return;
        int timeout = TrainerSpawnConfig.getDespawnAfterTicks();
        list.removeIf(st -> {
            NPCEntity npc = resolve(st);
            if (npc == null) return true;
            if (!npc.isAlive()) { removeIndex(npc); return true; }
            if (now - st.spawnTick > timeout) {
                npc.discard();
                removeIndex(npc);
                return true;
            }
            return false;
        });
    }

    private static void buildRandomParty(NPCEntity npc, RandomSource rand) {
        int avg = TrainerSpawnConfig.getAvgPartyLevel();
        int size = Math.max(TrainerSpawnConfig.getPartySizeMin(), Math.min(TrainerSpawnConfig.getPartySizeMax(), 3 + rand.nextInt(4)));
        NPCPartyStore party = new NPCPartyStore(npc);

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Pokemon p = randomPokemon(rand, avg);
            if (p == null) continue;
            party.add(p);
            indices.add(i);
        }
        npc.setParty(party);

        // Decide shadow count: prefer 2 if it rolls, else maybe 1
        int shadowCount = 0;
        if (rand.nextInt(100) < TrainerSpawnConfig.getShadow2Percent()) shadowCount = 2;
        else if (rand.nextInt(100) < TrainerSpawnConfig.getShadow1Percent()) shadowCount = 1;

        if (shadowCount > 0) {
            Collections.shuffle(indices, new Random(rand.nextLong()));
            int applied = 0;
            for (int idx : indices) {
                if (applied >= shadowCount) break;
                Pokemon p = party.get(idx);
                if (p == null) continue;
                if (ShadowSpawnConfig.isBlacklisted(p)) continue;
                try {
                    // Mark as Shadow and ensure required aspects exist
                    PokemonAspectUtil.setShadowAspect(p, true);
                    PokemonAspectUtil.ensureRequiredShadowAspects(p);
                    applied++;
                } catch (Throwable ignored) {}
            }
        }
    }

    private static Pokemon randomPokemon(RandomSource rand, int level) {
        try {
            var species = PokemonSpecies.random();
            PokemonProperties props = new PokemonProperties();
            props.setSpecies(species.toString());
            props.setLevel(level);
            return props.create();
        } catch (Throwable t) {
            return null;
        }
    }

    private static BlockPos pickSpawnPos(ServerLevel level, ServerPlayer player) {
        RandomSource rand = level.getRandom();
        int min = TrainerSpawnConfig.getRadiusMin();
        int max = Math.max(min + 1, TrainerSpawnConfig.getRadiusMax());
        for (int tries = 0; tries < 12; tries++) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            int dist = min + rand.nextInt(max - min + 1);
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            BlockPos base = player.blockPosition().offset(dx, 0, dz);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
            if (isSafe(level, pos)) return pos;
        }
        return null;
    }

    private static boolean isSafe(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos.below());
        return !state.isAir();
    }

    private static SpawnedTrainer findByNpc(NPCEntity npc) {
        WeakReference<NPCEntity> ref = INDEX.get(npc.getUUID());
        if (ref == null || ref.get() == null) return null;
        UUID owner = findOwnerByNpc(npc);
        if (owner == null) return null;
        List<SpawnedTrainer> list = PER_PLAYER.get(owner);
        if (list == null) return null;
        for (SpawnedTrainer st : list) {
            if (npc.getUUID().equals(st.entityId)) return st;
        }
        return null;
    }

    private static UUID findOwnerByNpc(NPCEntity npc) {
        // For now, infer by proximity: owner is closest player within 64 blocks
        if (!(npc.level() instanceof ServerLevel level)) return null;
        ServerPlayer closest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer sp : level.players()) {
            double d = sp.distanceToSqr(npc);
            if (d < 64.0 * 64.0 && d < best) { best = d; closest = sp; }
        }
        return closest != null ? closest.getUUID() : null;
    }

    private static void removeIndex(NPCEntity npc) { INDEX.remove(npc.getUUID()); }

    private static void removeFromOwnerList(NPCEntity npc) {
        UUID owner = findOwnerByNpc(npc);
        if (owner == null) return;
        List<SpawnedTrainer> list = PER_PLAYER.get(owner);
        if (list == null) return;
        list.removeIf(st -> st.entityId.equals(npc.getUUID()));
    }

    private static NPCEntity resolve(SpawnedTrainer st) {
        WeakReference<NPCEntity> ref = INDEX.get(st.entityId);
        NPCEntity npc = ref != null ? ref.get() : null;
        return npc != null && npc.isAlive() ? npc : null;
    }
}
