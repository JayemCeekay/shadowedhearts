package com.jayemceekay.shadowedhearts.integration.accessories;

import dev.architectury.platform.Platform;

public final class SnagAccessoryBridgeHolder {
    public static SnagAccessoryBridge INSTANCE = new NoopSnagAccessoryBridge();

    public static void init() {
        if (Platform.isModLoaded("accessories")) {
            try {
                INSTANCE = new AccessoriesSnagAccessoryBridge();
            } catch (Throwable t) {
                System.err.println("Failed to initialize Accessories snag bridge even though mod is loaded: " + t.getMessage());
            }
        }
    }
}
