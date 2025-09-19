package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.poketoss.client.WhistleSelectionClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevent camera turning while the order wheel (tween menu) is active.
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$disableTurningWhenWheelActive(CallbackInfo ci) {
        if (WhistleSelectionClient.isWheelActive()) {
            ci.cancel();
        }
    }
}
