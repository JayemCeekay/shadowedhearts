package com.jayemceekay.shadowedhearts.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderTarget.class)
public interface RenderTargetAccessor {

    @Accessor("frameBufferId") int getFrameBufferId();
    @Accessor("viewWidth") int getViewWidth();
    @Accessor("viewHeight") int getViewHeight();
    @Accessor("width") int getWidth();
    @Accessor("height") int getHeight();
}
