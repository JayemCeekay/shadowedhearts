package com.jayemceekay.shadowedhearts.worldgen;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerActivityHeatmap {
    private static final Map<ResourceKey<Level>, Map<ChunkPos, Double>> HEATMAP = new ConcurrentHashMap<>();
    private static int decayTimer = 0;

    public static void init() {
        load();

        TickEvent.SERVER_LEVEL_POST.register(level -> {
            if (level instanceof ServerLevel serverLevel) {
                // Decay
                int decayTicks = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().heatmapDecayTicks();
                if (++decayTimer >= decayTicks) {
                    decayTimer = 0;
                    decay(serverLevel);
                    save();
                }

                // Presence activity
                int range = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().heatmapPresenceRadius();
                for (ServerPlayer player : serverLevel.players()) {
                    ChunkPos center = player.chunkPosition();
                    for (int dx = -range; dx <= range; dx++) {
                        for (int dz = -range; dz <= range; dz++) {
                            double distance = Math.sqrt(dx * dx + dz * dz);
                            if (distance > range) continue;

                            double amount = 0.1 * (1.0 - (distance / (range + 1)));
                            if (amount > 0) {
                                addActivity(serverLevel, new ChunkPos(center.x + dx, center.z + dz), amount);
                            }
                        }
                    }
                }
            }
        });

        BlockEvent.PLACE.register((level, pos, state, entity) -> {
            if (level instanceof ServerLevel serverLevel) {
                addActivity(serverLevel, new ChunkPos(pos), 1.0);
            }
            return EventResult.pass();
        });

        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            if (level instanceof ServerLevel serverLevel) {
                addActivity(serverLevel, new ChunkPos(pos), 1.0);
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, direction) -> {
            if (player instanceof ServerPlayer sp && sp.level() instanceof ServerLevel serverLevel) {
                addActivity(serverLevel, new ChunkPos(pos), 0.5);
            }
            return EventResult.pass();
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> save());
    }

    public static void addActivity(ServerLevel level, ChunkPos pos, double amount) {
        HEATMAP.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
                .merge(pos, amount, Double::sum);
    }

    private static void decay(ServerLevel level) {
        Map<ChunkPos, Double> levelMap = HEATMAP.get(level.dimension());
        if (levelMap == null) return;

        double decayAmount = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().heatmapDecayAmount();
        levelMap.replaceAll((pos, val) -> val - decayAmount);
        levelMap.values().removeIf(val -> val <= 0);
    }

    public static double getActivity(ServerLevel level, ChunkPos pos) {
        Map<ChunkPos, Double> levelMap = HEATMAP.get(level.dimension());
        return levelMap == null ? 0 : levelMap.getOrDefault(pos, 0.0);
    }

    public static boolean isCivilized(ServerLevel level, ChunkPos pos) {
        return getActivity(level, pos) >= ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().civilizedHeatmapThreshold();
    }

    private static Path configPath() {
        return Path.of("config", "shadowedhearts", "player_activity_heatmap.json");
    }

    public static void save() {
        Path path = configPath();
        try {
            Path parent = path.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject root = new JsonObject();
            for (Map.Entry<ResourceKey<Level>, Map<ChunkPos, Double>> levelEntry : HEATMAP.entrySet()) {
                JsonObject levelObj = new JsonObject();
                for (Map.Entry<ChunkPos, Double> chunkEntry : levelEntry.getValue().entrySet()) {
                    levelObj.addProperty(chunkEntry.getKey().x + "," + chunkEntry.getKey().z, chunkEntry.getValue());
                }
                root.add(levelEntry.getKey().location().toString(), levelObj);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static void load() {
        Path path = configPath();
        if (!Files.exists(path)) return;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) return;

            JsonObject root = rootElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> levelEntry : root.entrySet()) {
                ResourceKey<Level> levelKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(levelEntry.getKey()));
                Map<ChunkPos, Double> levelMap = HEATMAP.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>());

                if (levelEntry.getValue().isJsonObject()) {
                    JsonObject levelObj = levelEntry.getValue().getAsJsonObject();
                    for (Map.Entry<String, JsonElement> chunkEntry : levelObj.entrySet()) {
                        String[] parts = chunkEntry.getKey().split(",");
                        if (parts.length == 2) {
                            try {
                                int x = Integer.parseInt(parts[0]);
                                int z = Integer.parseInt(parts[1]);
                                double value = chunkEntry.getValue().getAsDouble();
                                levelMap.put(new ChunkPos(x, z), value);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }
}
