package com.jayemceekay.shadowedhearts.util;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.player.GeneralPlayerData;
import net.minecraft.server.level.ServerPlayer;

public class PlayerPersistentData {
    public static ShadowedHeartsPlayerData get(ServerPlayer player) {
        GeneralPlayerData generalData = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(player);
        ShadowedHeartsPlayerData shData = (ShadowedHeartsPlayerData) generalData.getExtraData().get(ShadowedHeartsPlayerData.NAME);
        if (shData == null) {
            shData = new ShadowedHeartsPlayerData();
            generalData.getExtraData().put(ShadowedHeartsPlayerData.NAME, shData);
        }
        return shData;
    }
}
