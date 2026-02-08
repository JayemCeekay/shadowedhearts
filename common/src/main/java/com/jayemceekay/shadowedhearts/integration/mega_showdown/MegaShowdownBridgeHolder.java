package com.jayemceekay.shadowedhearts.integration.mega_showdown;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.platform.Platform;

public final class MegaShowdownBridgeHolder {
    public static MegaShowdownBridge INSTANCE = new NoopMegaShowdownBridge();

    public static void init() {
        if (Platform.isModLoaded("mega_showdown")) {
            try {
                INSTANCE = new MegaShowdownBridgeImpl();
            } catch (Throwable t) {
                INSTANCE = new NoopMegaShowdownBridge();
                Shadowedhearts.LOGGER.error("Failed to initialize Mega Showdown bridge even though mod is loaded: " + t.getMessage());
            }
        }
    }
}
