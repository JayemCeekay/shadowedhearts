package com.jayemceekay.shadowedhearts.integration.rctmod;

import dev.architectury.platform.Platform;

public final class RCTBridgeHolder {
    public static RCTBridge INSTANCE = new NoopRCTBridge();

    public static void init() {
        if (Platform.isModLoaded("rctmod")) {
            try {
                INSTANCE = new ActualRCTBridge();
            } catch (Throwable t) {
                INSTANCE = new NoopRCTBridge();
            }
        }
    }
}
