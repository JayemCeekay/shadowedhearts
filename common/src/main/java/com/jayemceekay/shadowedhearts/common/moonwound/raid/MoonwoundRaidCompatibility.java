package com.jayemceekay.shadowedhearts.common.moonwound.raid;

/** Selects the verified Raid Dens version for each optional integration. */
public final class MoonwoundRaidCompatibility {
    public static final String MOD_ID = "cobblemonraiddens";
    public static final String VERSION = "0.14.1+1.21.1";

    private MoonwoundRaidCompatibility() { }

    public static boolean supportsVersion(String version) {
        return VERSION.equals(version);
    }

    public static boolean supportsMixin(String mixinClassName, String version) {
        if (mixinClassName.endsWith(".MixinRaidDensEmptyTeam")) return "0.13.5+1.21.1".equals(version);
        return supportsVersion(version);
    }
}
