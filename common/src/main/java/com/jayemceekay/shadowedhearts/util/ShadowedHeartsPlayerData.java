package com.jayemceekay.shadowedhearts.util;

import com.cobblemon.mod.common.api.storage.player.PlayerDataExtension;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class ShadowedHeartsPlayerData implements PlayerDataExtension {
    public static final String NAME = "shadowedhearts";
    private long lastRelicStonePurify = 0;

    public ShadowedHeartsPlayerData() {
    }

    public long getLastRelicStonePurify() {
        return lastRelicStonePurify;
    }

    public void setLastRelicStonePurify(long lastRelicStonePurify) {
        this.lastRelicStonePurify = lastRelicStonePurify;
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
        return json;
    }

    @NotNull
    @Override
    public PlayerDataExtension deserialize(@NotNull JsonObject json) {
        ShadowedHeartsPlayerData data = new ShadowedHeartsPlayerData();
        if (json.has("last_relic_stone_purify")) {
            data.setLastRelicStonePurify(json.get("last_relic_stone_purify").getAsLong());
        }
        return data;
    }
}
