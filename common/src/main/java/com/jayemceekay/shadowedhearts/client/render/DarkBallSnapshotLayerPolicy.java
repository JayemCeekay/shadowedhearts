package com.jayemceekay.shadowedhearts.client.render;

import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Keeps discarded interpolated layers from allocating an ownerless texture. */
public final class DarkBallSnapshotLayerPolicy {
    private DarkBallSnapshotLayerPolicy() {
    }

    @Nullable
    public static <T> T createInterpolatedTexture(MultiBufferSource buffers, Supplier<T> factory) {
        // The snapshot cannot replay a dynamic texture. Cobblemon treats null
        // here as a skipped layer, before constructing its DynamicStateShard.
        // Ordinary rendering retains Cobblemon's allocation and close callback.
        return buffers instanceof DarkBallFieldMaskBufferSource ? null : factory.get();
    }
}
