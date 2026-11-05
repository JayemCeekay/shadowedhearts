package com.jayemceekay.shadowedhearts.mixin.client;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface DarkBallCompositeRenderTypeAccessor {
    @Accessor("state")
    RenderType.CompositeState shadowedhearts$getCompositeState();
}
