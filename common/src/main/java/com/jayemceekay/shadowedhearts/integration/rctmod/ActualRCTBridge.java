package com.jayemceekay.shadowedhearts.integration.rctmod;

import com.gitlab.srcmc.rctmod.api.utils.LevelUtils;
import net.minecraft.server.level.ServerPlayer;

public class ActualRCTBridge implements RCTBridge {
    @Override
    public int getLevelCap(ServerPlayer player) {
        return LevelUtils.levelCap(player);
    }
}
