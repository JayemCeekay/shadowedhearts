package com.jayemceekay.shadowedhearts.client.render.block;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;

/** Temporarily supplies a render context without requiring a shared model to have rendered before. */
final class PosableModelContextScope implements AutoCloseable {
    private final PosableModel model;
    private final RenderContext previousContext;

    PosableModelContextScope(PosableModel model, RenderContext context) {
        this.model = model;
        // Cobblemon's Kotlin lateinit getter throws on a fresh model. Its public backing field
        // also lets close() restore the original uninitialized state without a null-rejecting setter.
        this.previousContext = model.context;
        model.setContext(context);
    }

    @Override
    public void close() {
        model.context = previousContext;
    }
}
