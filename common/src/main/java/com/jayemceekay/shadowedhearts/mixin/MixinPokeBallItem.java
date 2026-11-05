package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.jayemceekay.shadowedhearts.common.snag.SnagCaps;
import com.jayemceekay.shadowedhearts.common.snag.SnagThrowAttempt;
import com.jayemceekay.shadowedhearts.common.snag.SnagMachineBenefits;
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
    private void shadowedhearts$addTabToCustomBalls(PokeBall pokeBall, CallbackInfo ci) {
        String ballName = pokeBall.getName().getPath();
        if (ballName.equals("penumbra_ball") || ballName.equals("antumbra_ball") || ballName.equals("umbra_ball") || ballName.equals("dark_ball")) {
            CreativeTabRegistry.append(ModCreativeTabs.SHADOWED_HEARTS_TAB, (PokeBallItem) (Object) this);
        }
    }

    @Inject(method = "throwPokeBall", at = @At("RETURN"))
    public void shadowedhearts$throwSnagBall(Level world, ServerPlayer player, CallbackInfo ci, @Local(ordinal = 0) com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity pokeBallEntity) {
        if (player == null || world.isClientSide) return;

        if (!SnagMachineBenefits.canAttempt(player)) return;
        if (!SnagThrowAttempt.consumeArmedThrow(SnagCaps.get(player))) return;

        ShadowedHeartsNetwork.sendToPlayer(player, new SnagArmedPacket(false));

        // Mark the armed throw via aspects (auto-synced to the client).
        Set<String> aspects = new HashSet<>(pokeBallEntity.getAspects());
        aspects.add("snag_ball");
        pokeBallEntity.setAspects(aspects);
        SnagMachineBenefits.snapshotThrow(player, pokeBallEntity);
    }
}
