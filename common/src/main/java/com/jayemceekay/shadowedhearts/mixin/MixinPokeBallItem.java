package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.jayemceekay.shadowedhearts.snag.SnagCaps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokeBallItem.class)
public class MixinPokeBallItem {

    @Inject(method = "throwPokeBall", at = @At("HEAD"))
    public void shadowedhearts$throwSnagBall(Level world, ServerPlayer player, CallbackInfo ci) {
        // Consume energy only when an empty Poké Ball is thrown while the Snag Machine is armed.
        // Also auto-disarm on throw regardless of energy consumption result.
        if (player != null && SnagCaps.hasMachineAvailable(player)) {
            var cap = SnagCaps.get(player);
            if (cap.isArmed()) {
                cap.consumeEnergy(com.jayemceekay.shadowedhearts.snag.SnagConfig.ENERGY_PER_ATTEMPT);
                cap.setArmed(false);
            }
        }
    }
}
