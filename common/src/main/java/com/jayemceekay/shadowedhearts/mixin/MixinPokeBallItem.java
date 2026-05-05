package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.jayemceekay.shadowedhearts.common.snag.SnagCaps;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.snag.SnagArmedPacket;
import com.jayemceekay.shadowedhearts.registry.ModCreativeTabs;
import dev.architectury.registry.CreativeTabRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = PokeBallItem.class, remap = false)
public class MixinPokeBallItem {

    @Inject(method = "<init>(Lcom/cobblemon/mod/common/pokeball/PokeBall;)V", at = @At("TAIL"))
    private void shadowedhearts$addTabToDarkBall(PokeBall pokeBall, CallbackInfo ci) {
        if (pokeBall.getName().getPath().equals("dark_ball")) {
            CreativeTabRegistry.append(ModCreativeTabs.SHADOWED_HEARTS_TAB, (PokeBallItem) (Object) this);
        }
    }

    @Inject(method = "throwPokeBall", at = @At("RETURN"))
    public void shadowedhearts$throwSnagBall(Level world, ServerPlayer player, CallbackInfo ci, @Local(ordinal = 0) com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity pokeBallEntity) {
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
