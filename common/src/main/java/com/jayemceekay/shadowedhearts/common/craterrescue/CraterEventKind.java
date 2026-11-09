package com.jayemceekay.shadowedhearts.common.craterrescue;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

enum CraterEventKind {
    RESCUE, OUTBREAK, HOWL;

    /** A single site-wide chance followed by one weighted choice among enabled encounters. */
    static @Nullable CraterEventKind roll(RandomSource random, double chance, int rescueWeight, int outbreakWeight, int howlWeight) {
        int total = rescueWeight + outbreakWeight + howlWeight;
        if (total == 0 || random.nextDouble() >= chance) return null;
        int draw = random.nextInt(total);
        if (draw < rescueWeight) return RESCUE;
        return draw < rescueWeight + outbreakWeight ? OUTBREAK : HOWL;
    }
}
