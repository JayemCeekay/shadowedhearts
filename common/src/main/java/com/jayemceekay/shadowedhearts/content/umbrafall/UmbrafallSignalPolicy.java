package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.jayemceekay.shadowedhearts.config.IWorldAlterationConfig;
import com.jayemceekay.shadowedhearts.config.UmbrafallSignalMode;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;

public final class UmbrafallSignalPolicy {
    private UmbrafallSignalPolicy() { }

    public static boolean allowsTier(IWorldAlterationConfig config, UmbrafallTier tier) {
        return config.umbrafallSignalsEnabled() && config.umbrafallSignalAllowedTiers().contains(tier.id());
    }

    public static boolean canGenerate(IWorldAlterationConfig config, UmbrafallTier tier) {
        return allowsTier(config, tier) && config.umbrafallSignalMode() == UmbrafallSignalMode.LOCATE_OR_GENERATE;
    }

    public static boolean inLocateRange(IWorldAlterationConfig config, double distanceSquared) {
        int maximum = config.umbrafallSignalLocateMaxDistance();
        int minimum = Math.min(config.umbrafallSignalLocateMinDistance(), maximum);
        return distanceSquared >= (double) minimum * minimum && distanceSquared <= (double) maximum * maximum;
    }
}
