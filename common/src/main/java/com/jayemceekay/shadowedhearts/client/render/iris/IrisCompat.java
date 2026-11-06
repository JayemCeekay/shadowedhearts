package com.jayemceekay.shadowedhearts.client.render.iris;

import dev.architectury.platform.Platform;

/** Shared access to optional Iris rendering state. */
public final class IrisCompat {
    public static final IrisHandler IRIS_HANDLER = Platform.isModLoaded("iris") ? new IrisHandlerImpl() : null;

    private IrisCompat() {
    }
}
