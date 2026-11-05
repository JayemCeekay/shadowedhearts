package com.jayemceekay.shadowedhearts.common.moonwound.raid;

/** Keep optional mixins and the typed adapter on the same verified API boundary. */
public final class MoonwoundRaidCompatibility {
    public static final String MOD_ID = "cobblemonraiddens";
    public static final String VERSION = "0.13.5+1.21.1";

    private MoonwoundRaidCompatibility() { }

    public static boolean supportsVersion(String version) {
        return VERSION.equals(version);
    }
}
