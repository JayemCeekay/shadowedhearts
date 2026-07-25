package com.jayemceekay.shadowedhearts.common.aura;

import com.jayemceekay.shadowedhearts.util.PlayerPersistentData;
import com.jayemceekay.shadowedhearts.util.ShadowedHeartsPlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class AuraReaderPlayerState {
    private AuraReaderPlayerState() {
    }

    public static AuraReaderMode getMode(ServerPlayer player) {
        return PlayerPersistentData.get(player).getAuraReaderMode();
    }

    public static void setMode(ServerPlayer player, AuraReaderMode mode) {
        PlayerPersistentData.get(player).setAuraReaderMode(mode);
    }

    public static boolean isActive(ServerPlayer player) {
        return PlayerPersistentData.get(player).isAuraReaderActive();
    }

    public static void setActive(ServerPlayer player, boolean active) {
        PlayerPersistentData.get(player).setAuraReaderActive(active);
    }

    public static boolean isTracking(ServerPlayer player) {
        return PlayerPersistentData.get(player).isAuraReaderTracking();
    }

    public static void setTracking(ServerPlayer player, boolean tracking) {
        PlayerPersistentData.get(player).setAuraReaderTracking(tracking);
    }

    public static String getSelectedSignal(ServerPlayer player) {
        return PlayerPersistentData.get(player).getAuraReaderSelectedSignal();
    }

    public static void setSelectedSignal(ServerPlayer player, String signalId) {
        PlayerPersistentData.get(player).setAuraReaderSelectedSignal(signalId);
    }

    public static void clearSelectedSignal(ServerPlayer player) {
        setSelectedSignal(player, "");
    }

    public static AuraReaderLockType getLockType(ServerPlayer player) {
        return PlayerPersistentData.get(player).getAuraReaderLockType();
    }

    public static String getLockedTarget(ServerPlayer player) {
        return PlayerPersistentData.get(player).getAuraReaderLockedTarget();
    }

    public static String getLockDimension(ServerPlayer player) {
        return PlayerPersistentData.get(player).getAuraReaderLockDimension();
    }

    public static void lockEntity(ServerPlayer player, UUID entityId, ResourceLocation dimension) {
        ShadowedHeartsPlayerData data = PlayerPersistentData.get(player);
        data.setAuraReaderLockType(AuraReaderLockType.ENTITY);
        data.setAuraReaderLockedTarget(entityId == null ? "" : entityId.toString());
        data.setAuraReaderLockDimension(dimension == null ? "" : dimension.toString());
        data.setAuraReaderTracking(true);
    }

    public static void lockUmbrafallSite(ServerPlayer player, String siteId) {
        ShadowedHeartsPlayerData data = PlayerPersistentData.get(player);
        data.setAuraReaderLockType(AuraReaderLockType.UMBRAFALL_SITE);
        data.setAuraReaderLockedTarget(siteId);
        data.setAuraReaderLockDimension("");
        data.setAuraReaderTracking(true);
    }

    public static void lockBlock(ServerPlayer player, BlockPos pos, ResourceLocation dimension) {
        ShadowedHeartsPlayerData data = PlayerPersistentData.get(player);
        data.setAuraReaderLockType(AuraReaderLockType.BLOCK);
        data.setAuraReaderLockedTarget("");
        data.setAuraReaderLockDimension(dimension == null ? "" : dimension.toString());
        if (pos != null) {
            data.setAuraReaderLockPos(pos.getX(), pos.getY(), pos.getZ());
        }
        data.setAuraReaderTracking(true);
    }

    public static void clearLock(ServerPlayer player) {
        PlayerPersistentData.get(player).clearAuraReaderLock();
    }
}
