package com.jayemceekay.shadowedhearts.integration.rctmod;

import net.minecraft.server.level.ServerPlayer;

public class NoopRCTBridge implements RCTBridge {
    @Override
    public int getLevelCap(ServerPlayer player) {
        return 100; // Default max level in Cobblemon
    }
}
