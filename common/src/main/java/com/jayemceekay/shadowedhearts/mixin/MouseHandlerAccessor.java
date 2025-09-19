package com.jayemceekay.shadowedhearts.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for MouseHandler's cursor position fields so we can recenter
 * the cursor when opening the order wheel.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void shadowedhearts$setXpos(double x);

    @Accessor("ypos")
    void shadowedhearts$setYpos(double y);
}
