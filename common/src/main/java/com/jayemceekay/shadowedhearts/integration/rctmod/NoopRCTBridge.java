package com.jayemceekay.shadowedhearts.integration.rctmod;

import net.minecraft.network.chat.Component;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class NoopRCTBridge implements RCTBridge {
    private final List<Component> diagnostics;

    public NoopRCTBridge() {
        this.diagnostics = List.of();
    }

    public NoopRCTBridge(Component diagnostic) {
        this.diagnostics = List.of(diagnostic);
    }

    @Override
    public int getLevelCap(ServerPlayer player) {
        return 100; // Default max level in Cobblemon
    }

    @Override
    public List<Component> diagnostics() {
        return diagnostics;
    }
}
