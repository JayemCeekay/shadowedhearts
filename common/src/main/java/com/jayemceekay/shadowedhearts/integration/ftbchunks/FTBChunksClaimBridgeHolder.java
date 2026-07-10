package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.platform.Platform;

public final class FTBChunksClaimBridgeHolder {
    public static FTBChunksClaimBridge INSTANCE = new NoopFTBChunksClaimBridge();

    private FTBChunksClaimBridgeHolder() {
    }

    public static void init() {
        if (Platform.isModLoaded("ftbchunks")) {
            try {
                INSTANCE = new FTBChunksClaimBridgeImpl();
            } catch (Throwable t) {
                INSTANCE = new NoopFTBChunksClaimBridge();
                Shadowedhearts.LOGGER.error("Failed to initialize FTB Chunks claim bridge even though mod is loaded: " + t.getMessage());
            }
        }
    }
}
