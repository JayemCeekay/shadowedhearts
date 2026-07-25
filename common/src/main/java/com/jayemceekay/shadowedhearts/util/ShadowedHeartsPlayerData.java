package com.jayemceekay.shadowedhearts.util;

import com.cobblemon.mod.common.api.storage.player.PlayerDataExtension;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderLockType;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class ShadowedHeartsPlayerData implements PlayerDataExtension {
    public static final String NAME = "shadowedhearts";
    private long lastRelicStonePurify = 0;
    private AuraReaderMode auraReaderMode = AuraReaderMode.PASSIVE_AURA;
    private boolean auraReaderActive = false;
    private boolean auraReaderTracking = false;
    private String auraReaderSelectedSignal = "";
    private AuraReaderLockType auraReaderLockType = AuraReaderLockType.NONE;
    private String auraReaderLockedTarget = "";
    private String auraReaderLockDimension = "";
    private int auraReaderLockX = 0;
    private int auraReaderLockY = 0;
    private int auraReaderLockZ = 0;

    public ShadowedHeartsPlayerData() {
    }

    public long getLastRelicStonePurify() {
        return lastRelicStonePurify;
    }

    public void setLastRelicStonePurify(long lastRelicStonePurify) {
        this.lastRelicStonePurify = lastRelicStonePurify;
    }

    public AuraReaderMode getAuraReaderMode() {
        return auraReaderMode;
    }

    public void setAuraReaderMode(AuraReaderMode auraReaderMode) {
        this.auraReaderMode = auraReaderMode == null ? AuraReaderMode.PASSIVE_AURA : auraReaderMode;
    }

    public boolean isAuraReaderActive() {
        return auraReaderActive;
    }

    public void setAuraReaderActive(boolean auraReaderActive) {
        this.auraReaderActive = auraReaderActive;
    }

    public boolean isAuraReaderTracking() {
        return auraReaderTracking;
    }

    public void setAuraReaderTracking(boolean auraReaderTracking) {
        this.auraReaderTracking = auraReaderTracking;
        if (!auraReaderTracking) {
            clearAuraReaderLock();
        }
    }

    public String getAuraReaderSelectedSignal() {
        return auraReaderSelectedSignal;
    }

    public void setAuraReaderSelectedSignal(String auraReaderSelectedSignal) {
        this.auraReaderSelectedSignal = auraReaderSelectedSignal == null ? "" : auraReaderSelectedSignal;
    }

    public AuraReaderLockType getAuraReaderLockType() {
        return auraReaderLockType;
    }

    public void setAuraReaderLockType(AuraReaderLockType auraReaderLockType) {
        this.auraReaderLockType = auraReaderLockType == null ? AuraReaderLockType.NONE : auraReaderLockType;
    }

    public String getAuraReaderLockedTarget() {
        return auraReaderLockedTarget;
    }

    public void setAuraReaderLockedTarget(String auraReaderLockedTarget) {
        this.auraReaderLockedTarget = auraReaderLockedTarget == null ? "" : auraReaderLockedTarget;
    }

    public String getAuraReaderLockDimension() {
        return auraReaderLockDimension;
    }

    public void setAuraReaderLockDimension(String auraReaderLockDimension) {
        this.auraReaderLockDimension = auraReaderLockDimension == null ? "" : auraReaderLockDimension;
    }

    public int getAuraReaderLockX() {
        return auraReaderLockX;
    }

    public int getAuraReaderLockY() {
        return auraReaderLockY;
    }

    public int getAuraReaderLockZ() {
        return auraReaderLockZ;
    }

    public void setAuraReaderLockPos(int x, int y, int z) {
        this.auraReaderLockX = x;
        this.auraReaderLockY = y;
        this.auraReaderLockZ = z;
    }

    public void clearAuraReaderLock() {
        auraReaderLockType = AuraReaderLockType.NONE;
        auraReaderLockedTarget = "";
        auraReaderLockDimension = "";
        auraReaderLockX = 0;
        auraReaderLockY = 0;
        auraReaderLockZ = 0;
        auraReaderTracking = false;
    }

    @NotNull
    @Override
    public String name() {
        return NAME;
    }

    @NotNull
    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("name", NAME);
        json.addProperty("last_relic_stone_purify", lastRelicStonePurify);
        json.addProperty("aura_reader_mode", auraReaderMode.name());
        json.addProperty("aura_reader_active", auraReaderActive);
        json.addProperty("aura_reader_tracking", auraReaderTracking);
        json.addProperty("aura_reader_selected_signal", auraReaderSelectedSignal);
        json.addProperty("aura_reader_lock_type", auraReaderLockType.name());
        json.addProperty("aura_reader_locked_target", auraReaderLockedTarget);
        json.addProperty("aura_reader_lock_dimension", auraReaderLockDimension);
        json.addProperty("aura_reader_lock_x", auraReaderLockX);
        json.addProperty("aura_reader_lock_y", auraReaderLockY);
        json.addProperty("aura_reader_lock_z", auraReaderLockZ);
        return json;
    }

    @NotNull
    @Override
    public PlayerDataExtension deserialize(@NotNull JsonObject json) {
        ShadowedHeartsPlayerData data = new ShadowedHeartsPlayerData();
        if (json.has("last_relic_stone_purify")) {
            data.setLastRelicStonePurify(json.get("last_relic_stone_purify").getAsLong());
        }
        if (json.has("aura_reader_mode")) {
            data.setAuraReaderMode(AuraReaderMode.fromName(json.get("aura_reader_mode").getAsString()));
        }
        if (json.has("aura_reader_active")) {
            data.setAuraReaderActive(json.get("aura_reader_active").getAsBoolean());
        }
        if (json.has("aura_reader_tracking")) {
            data.auraReaderTracking = json.get("aura_reader_tracking").getAsBoolean();
        }
        if (json.has("aura_reader_selected_signal")) {
            data.setAuraReaderSelectedSignal(json.get("aura_reader_selected_signal").getAsString());
        }
        if (json.has("aura_reader_lock_type")) {
            data.setAuraReaderLockType(AuraReaderLockType.fromName(json.get("aura_reader_lock_type").getAsString()));
        }
        if (json.has("aura_reader_locked_target")) {
            data.setAuraReaderLockedTarget(json.get("aura_reader_locked_target").getAsString());
        }
        if (json.has("aura_reader_lock_dimension")) {
            data.setAuraReaderLockDimension(json.get("aura_reader_lock_dimension").getAsString());
        }
        if (json.has("aura_reader_lock_x") && json.has("aura_reader_lock_y") && json.has("aura_reader_lock_z")) {
            data.setAuraReaderLockPos(
                    json.get("aura_reader_lock_x").getAsInt(),
                    json.get("aura_reader_lock_y").getAsInt(),
                    json.get("aura_reader_lock_z").getAsInt()
            );
        }
        return data;
    }
}
