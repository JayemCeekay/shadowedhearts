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
        if(SnagCaps.hasMachineInOffhand(player)) {
            if(SnagCaps.get(player).isArmed()) {
                //consume energy
                SnagCaps.get((player)).consumeEnergy(0);
            }
        }
    }
}
