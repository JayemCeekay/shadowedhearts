package com.jayemceekay.shadowedhearts.integration;

import dev.architectury.platform.Platform;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;

/** SOS 1.3.0's interpreter reserves the wild partner slot and performs its send-out in a turn dispatch. */
public final class CraterSosCompatibility {
    public static final String MOD_ID = "sosencounters";
    public static final String VERSION = "1.3.0+1.21.1";
    private static volatile boolean simulatorReady;

    private CraterSosCompatibility() {}

    public static boolean supports(String version) {
        return VERSION.equals(version);
    }

    public static boolean installed() {
        return Platform.getOptionalMod(MOD_ID).map(mod -> supports(mod.getVersion())).orElse(false);
    }

    public static void setSimulatorReady(boolean ready) {
        simulatorReady = ready;
    }

    public static boolean available() {
        return simulatorReady && ShowdownService.Companion.getService() instanceof GraalShowdownService;
    }
}
