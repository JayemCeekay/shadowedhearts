package com.jayemceekay.shadowedhearts.worldgen;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerActivityHeatmap {
    private static final Map<ResourceKey<Level>, Long2FloatOpenHashMap> HEATMAPS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long2FloatOpenHashMap> DIRTY_ENTRIES = new ConcurrentHashMap<>();
    private static int decayTimer = 0;
    private static int flushTimer = 0;

    public static void init() {
        LifecycleEvent.SERVER_STARTED.register(server -> {
            loadAll(server);
        });

        TickEvent.SERVER_LEVEL_POST.register(level -> {
            if (level instanceof ServerLevel serverLevel) {
                // Decay
                int decayTicks = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().heatmapDecayTicks();
                if (++decayTimer >= decayTicks) {
                    decayTimer = 0;
                    decay(serverLevel);
                }

                // Presence activity
                int range = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().heatmapPresenceRadius();
                for (ServerPlayer player : serverLevel.players()) {
                    ChunkPos center = player.chunkPosition();
                    for (int dx = -range; dx <= range; dx++) {
                        for (int dz = -range; dz <= range; dz++) {
                            double distance = Math.sqrt(dx * dx + dz * dz);
                            if (distance > range) continue;

                            float amount = (float) (0.1 * (1.0 - (distance / (range + 1))));
                            if (amount > 0) {
                                addActivity(serverLevel, center.x + dx, center.z + dz, amount);
                            }
                        }
                    }
                }

                if (++flushTimer >= ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().heatmapFlushIntervalTicks()) {
                    flushTimer = 0;
                    flushDirty(serverLevel.getServer());
                }
            }
        });

        BlockEvent.PLACE.register((level, pos, state, entity) -> {
            if (level instanceof ServerLevel serverLevel) {
                addActivity(serverLevel, pos.getX() >> 4, pos.getZ() >> 4, 1.0f);
            }
            return EventResult.pass();
        });

        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            if (level instanceof ServerLevel serverLevel) {
                addActivity(serverLevel, pos.getX() >> 4, pos.getZ() >> 4, 1.0f);
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, direction) -> {
            if (player instanceof ServerPlayer sp && sp.level() instanceof ServerLevel serverLevel) {
                addActivity(serverLevel, pos.getX() >> 4, pos.getZ() >> 4, 0.5f);
            }
            return EventResult.pass();
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            flushDirty(server);
        });
    }

    private static long packPos(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    public static void addActivity(ServerLevel level, int chunkX, int chunkZ, float amount) {
        long pos = packPos(chunkX, chunkZ);
        Long2FloatOpenHashMap map = HEATMAPS.computeIfAbsent(level.dimension(), k -> new Long2FloatOpenHashMap());
        float newVal = map.get(pos) + amount;
        map.put(pos, newVal);

        Long2FloatOpenHashMap dirtyMap = DIRTY_ENTRIES.computeIfAbsent(level.dimension(), k -> new Long2FloatOpenHashMap());
        dirtyMap.put(pos, newVal);
    }

    private static void decay(ServerLevel level) {
        Long2FloatOpenHashMap levelMap = HEATMAPS.get(level.dimension());
        if (levelMap == null || levelMap.isEmpty()) return;

        float decayAmount = (float) ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().heatmapDecayAmount();
        Long2FloatOpenHashMap dirtyMap = DIRTY_ENTRIES.computeIfAbsent(level.dimension(), k -> new Long2FloatOpenHashMap());

        Long2FloatMap.FastEntrySet entries = levelMap.long2FloatEntrySet();
        var iterator = entries.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            float newVal = entry.getFloatValue() - decayAmount;
            long key = entry.getLongKey();
            if (newVal <= 0) {
                iterator.remove();
                // For SQLite, we might want to delete or just set to 0. 
                // Let's set to 0 and we can prune occasionally, or just put 0.
                dirtyMap.put(key, 0.0f);
            } else {
                entry.setValue(newVal);
                dirtyMap.put(key, newVal);
            }
        }
    }

    public static double getActivity(ServerLevel level, int chunkX, int chunkZ) {
        Long2FloatOpenHashMap levelMap = HEATMAPS.get(level.dimension());
        return levelMap == null ? 0 : levelMap.get(packPos(chunkX, chunkZ));
    }

    public static boolean isCivilized(ServerLevel level, int chunkX, int chunkZ) {
        return getActivity(level, chunkX, chunkZ) >= ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().civilizedHeatmapThreshold();
    }

    private static Path getDatabasePath(MinecraftServer server, ResourceKey<Level> dimension) {
        Path basePath = server.getWorldPath(LevelResource.ROOT).resolve("shadowedhearts").resolve("heatmap");
        String dimName = dimension.location().getPath();
        if (!dimension.location().getNamespace().equals("minecraft")) {
            dimName = dimension.location().getNamespace() + "_" + dimName;
        }
        return basePath.resolve(dimName).resolve("heatmap.db");
    }

    private static Connection getConnection(Path dbPath) throws SQLException {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException e) {
            throw new SQLException("Could not create directories for database", e);
        }

        try {
            // Register the driver
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            throw new SQLException("SQLite JDBC driver could not be initialized", e);
        }

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        Connection conn = DriverManager.getConnection(url);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("CREATE TABLE IF NOT EXISTS heatmap (pos INTEGER PRIMARY KEY, value REAL);");
        }
        return conn;
    }

    private static void flushDirty(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Long2FloatOpenHashMap> entry : DIRTY_ENTRIES.entrySet()) {
            ResourceKey<Level> dimension = entry.getKey();
            Long2FloatOpenHashMap dirtyMap = entry.getValue();
            if (dirtyMap.isEmpty()) continue;

            Path dbPath = getDatabasePath(server, dimension);
            try (Connection conn = getConnection(dbPath)) {
                conn.setAutoCommit(false);
                try (PreparedStatement upsertStmt = conn.prepareStatement(
                        "INSERT INTO heatmap(pos, value) VALUES(?, ?) ON CONFLICT(pos) DO UPDATE SET value=excluded.value")) {
                    try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM heatmap WHERE pos = ?")) {
                        for (var dirtyEntry : dirtyMap.long2FloatEntrySet()) {
                            if (dirtyEntry.getFloatValue() <= 0) {
                                deleteStmt.setLong(1, dirtyEntry.getLongKey());
                                deleteStmt.addBatch();
                            } else {
                                upsertStmt.setLong(1, dirtyEntry.getLongKey());
                                upsertStmt.setFloat(2, dirtyEntry.getFloatValue());
                                upsertStmt.addBatch();
                            }
                        }
                        upsertStmt.executeBatch();
                        deleteStmt.executeBatch();
                    }
                }
                conn.commit();
                dirtyMap.clear();
            } catch (SQLException e) {
                Shadowedhearts.LOGGER.error("Failed to flush heatmap for dimension {}", dimension.location(), e);
            }
        }
    }

    private static void loadAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dimension = level.dimension();
            Path dbPath = getDatabasePath(server, dimension);
            if (!Files.exists(dbPath)) continue;

            Long2FloatOpenHashMap map = HEATMAPS.computeIfAbsent(dimension, k -> new Long2FloatOpenHashMap());
            try (Connection conn = getConnection(dbPath);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT pos, value FROM heatmap")) {
                while (rs.next()) {
                    map.put(rs.getLong("pos"), rs.getFloat("value"));
                }
            } catch (SQLException e) {
                Shadowedhearts.LOGGER.error("Failed to load heatmap for dimension {}", dimension.location(), e);
            }
        }
    }
}
