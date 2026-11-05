package com.jayemceekay.shadowedhearts.integration.rctmod;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.platform.Platform;

import static com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.message;

public final class RCTBridgeHolder {
    public static RCTBridge INSTANCE = new NoopRCTBridge();

    public static void init() {
        INSTANCE = new NoopRCTBridge();
        if (Platform.isModLoaded("rctmod")) {
            try {
                INSTANCE = new ActualRCTBridge();
            } catch (RuntimeException | LinkageError failure) {
                Shadowedhearts.LOGGER.warn("Shadowed Hearts could not initialize its RCT integration", failure);
                INSTANCE = new NoopRCTBridge(message("rct.diagnostic.initialization_failed"));
            }
        }
    }
}
