package com.jayemceekay.shadowedhearts.common.moonwound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable level tuning captured when a lobby is created, independent of later server settings. */
public record MoonwoundDifficulty(int strongWaveLevel, int resonantWaveLevel,
                                 int strongAlphaLevel, int resonantAlphaLevel, int raidBossLevel) {
    /** Historical levels for events saved before explicit difficulty snapshots. */
    public static final MoonwoundDifficulty DEFAULT = new MoonwoundDifficulty(40, 55, 50, 65, 0);

    public MoonwoundDifficulty {
        level(strongWaveLevel, 1);
        level(resonantWaveLevel, 1);
        level(strongAlphaLevel, 1);
        level(resonantAlphaLevel, 1);
        level(raidBossLevel, 0);
    }

    public int waveLevel(boolean resonant) { return resonant ? resonantWaveLevel : strongWaveLevel; }
    public int alphaLevel(boolean resonant) { return resonant ? resonantAlphaLevel : strongAlphaLevel; }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("StrongWaveLevel", strongWaveLevel);
        tag.putInt("ResonantWaveLevel", resonantWaveLevel);
        tag.putInt("StrongAlphaLevel", strongAlphaLevel);
        tag.putInt("ResonantAlphaLevel", resonantAlphaLevel);
        tag.putInt("RaidBossLevel", raidBossLevel);
        return tag;
    }

    static MoonwoundDifficulty load(CompoundTag tag) {
        return new MoonwoundDifficulty(integer(tag, "StrongWaveLevel"), integer(tag, "ResonantWaveLevel"),
                integer(tag, "StrongAlphaLevel"), integer(tag, "ResonantAlphaLevel"), integer(tag, "RaidBossLevel"));
    }

    private static int integer(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) throw new IllegalArgumentException("Missing or invalid difficulty " + key);
        return tag.getInt(key);
    }

    private static void level(int value, int minimum) {
        if (value < minimum || value > 100) throw new IllegalArgumentException("Invalid Moonwound level " + value);
    }
}
