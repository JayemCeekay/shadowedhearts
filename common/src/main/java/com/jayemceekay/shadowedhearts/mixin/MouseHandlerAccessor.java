package com.jayemceekay.shadowedhearts.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for MouseHandler internals used by our wheel UI.
 * - Allows recentering the cursor when opening the order wheel.
 * - Allows consuming the accumulated vertical scroll while the wheel is open.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void shadowedhearts$setXpos(double x);

    @Accessor("ypos")
    void shadowedhearts$setYpos(double y);

    @Accessor("accumulatedScrollY")
    double shadowedhearts$getAccumulatedScrollY();

    @Accessor("accumulatedScrollY")
    void shadowedhearts$setAccumulatedScrollY(double y);
}
