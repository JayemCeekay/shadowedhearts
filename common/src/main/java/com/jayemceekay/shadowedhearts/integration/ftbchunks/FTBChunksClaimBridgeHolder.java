package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.platform.Platform;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FTBChunksClaimBridgeHolder {
    public static FTBChunksClaimBridge INSTANCE = new NoopFTBChunksClaimBridge();

    private FTBChunksClaimBridgeHolder() {
    }

    public static void init() {
        INSTANCE = create(Platform.isModLoaded("ftbchunks"), () -> new FTBChunksClaimBridgeImpl(),
                error -> Shadowedhearts.LOGGER.error(
                        "FTB Chunks claim protection is unavailable; protected operations will wait until it recovers", error));
    }

    static FTBChunksClaimBridge create(boolean installed, Supplier<FTBChunksClaimBridge> initializer,
                                       Consumer<Throwable> reportFailure) {
        return installed ? new GuardedFTBChunksClaimBridge(initializer, reportFailure) : new NoopFTBChunksClaimBridge();
    }
}
