package com.jayemceekay.shadowedhearts.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevent camera turning while the order wheel (tween menu) is active.
 * Also lock hotbar scrolling while the wheel is open so scroll is consumed by the wheel UI instead.
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$disableTurningWhenWheelActive(CallbackInfo ci) {
        /*if (WhistleSelectionClient.isWheelActive()) {
            ci.cancel();
        }*/
    }

    // Forward raw scroll deltas to the TossOrderWheel so it can drive the bar UI reliably
    @Inject(method = "onScroll", at = @At("HEAD"))
    private void shadowedhearts$forwardWheelScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        /*if (ModConfig.get().tossOrderBarUI) {
            TossOrderBarUI.onScrollDelta(vertical);
        } else {
            TossOrderRadialWheel.onScrollDelta(vertical);
        }*/
    }

    @Inject(
        method = "onScroll",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;swapPaint(D)V"
        ),
        cancellable = true
    )
    private void shadowedhearts$lockHotbarScrollWhenWheelOpen(long window, double horizontal, double vertical, CallbackInfo ci) {
        /*if (WhistleSelectionClient.isWheelActive()) {
            // Cancel vanilla hotbar swapping. The accumulated scroll value remains available
            // for TossOrderWheel to read and consume via MouseHandlerAccessor.
            ci.cancel();
        }*/
    }
}
