package com.jayemceekay.shadowedhearts.common.aura;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record AuraScanContext(
        ServerPlayer player,
        ServerLevel level,
        ItemStack auraReader,
        AuraReaderMode mode,
        long gameTime,
        boolean pulseScan
) {
    public AuraScanContext(
            ServerPlayer player,
            ServerLevel level,
            ItemStack auraReader,
            AuraReaderMode mode,
            long gameTime
    ) {
        this(player, level, auraReader, mode, gameTime, false);
    }

    public AuraScanContext {
        mode = mode == null ? AuraReaderMode.PASSIVE_AURA : mode;
    }
}
