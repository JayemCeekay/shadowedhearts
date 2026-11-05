package com.jayemceekay.shadowedhearts.integration.rctmod;

import net.minecraft.network.chat.Component;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.List;

public interface RCTBridge {
    int getLevelCap(ServerPlayer player);

    default boolean isTrainer(Entity entity) {
        return false;
    }

    default TrainerIdentity trainerIdentity(Entity entity) {
        return null;
    }

    default List<Component> diagnostics() {
        return List.of();
    }

    default RCTTrainerCatalog catalog(MinecraftServer server) {
        return RCTTrainerCatalog.unavailable(diagnostics());
    }

    record TrainerIdentity(String id, String type) {}
}
