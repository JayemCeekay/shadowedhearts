package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.SnagArmedPacket;
import com.jayemceekay.shadowedhearts.snag.SnagCaps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(PokeBallItem.class)
public class MixinPokeBallItem {

    @Inject(method = "throwPokeBall", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), locals = org.spongepowered.asm.mixin.injection.callback.LocalCapture.CAPTURE_FAILHARD)
    public void shadowedhearts$throwSnagBall(Level world, ServerPlayer player, CallbackInfo ci, com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity pokeBallEntity) {
        // Consume energy only when an empty Poké Ball is thrown while the Snag Machine is armed.
        // Also auto-disarm on throw regardless of energy consumption result.
        if (player != null && SnagCaps.hasMachineAvailable(player)) {
            var cap = SnagCaps.get(player);
            if (cap.isArmed()) {
                cap.consumeEnergy(ShadowedHeartsConfigs.getInstance().getSnagConfig().energyPerAttempt());
                cap.setArmed(false);
                ShadowedHeartsNetwork.sendToPlayer(player, new SnagArmedPacket(false));

                // Add "snag_ball" aspect to the entity for client-side emitter attachment
                Set<String> aspects = new HashSet<>(pokeBallEntity.getAspects());
                aspects.add("snag_ball");
                pokeBallEntity.setAspects(aspects);
            }
        }
    }
}
