package com.jayemceekay.shadowedhearts.integration.yacl;

import java.lang.module.ModuleDescriptor.Version;

/** These checks also run during mixin selection, before client screen classes are available. */
public final class YaclCompatibility {
    public static final String MOD_ID = "yet_another_config_lib_v3";
    private static final Version MINIMUM = Version.parse("3.8.2");

    private YaclCompatibility() {}

    public static boolean supportsEditor(String version) {
        try {
            return Version.parse(release(version)).compareTo(MINIMUM) >= 0;
        } catch (IllegalArgumentException unknownVersion) {
            return false;
        }
    }

    public static boolean supportsHeadingLayout(String version) {
        return "3.8.2".equals(release(version));
    }

    private static String release(String version) {
        int metadata = version.indexOf('+');
        return metadata < 0 ? version : version.substring(0, metadata);
    }
}
