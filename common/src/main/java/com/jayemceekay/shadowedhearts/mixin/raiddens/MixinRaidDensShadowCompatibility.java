package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.aura.AuraBroadcastQueue;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Raid Dens 0.13.5 still calls retired ShadowedHearts packages. Version-gated by both loaders. */
@Pseudo
@Mixin(targets = "com.necro.raid.dens.common.compat.shadowedhearts.RaidDensShadowedHeartsCompat", remap = false)
public abstract class MixinRaidDensShadowCompatibility {
    @Inject(method = "setShadowBoss", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$boss(Pokemon pokemon, PokemonEntity entity, CallbackInfo ci) {
        ShadowService.fullyCorrupt(pokemon, entity, false);
        ShadowMoveUtil.assignShadowMoves(pokemon);
        AuraBroadcastQueue.queueBroadcast(entity, 2.5F, Integer.MAX_VALUE);
        ci.cancel();
    }

    @Inject(method = "setShadowReward", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$reward(Pokemon pokemon, CallbackInfo ci) {
        ShadowService.fullyCorrupt(pokemon, null, false);
        ShadowMoveUtil.assignShadowMoves(pokemon);
        ci.cancel();
    }

    @Inject(method = "getShadowShard", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$shard(CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(ModItems.SHADOW_SHARD.get().getDefaultInstance());
    }

    @Inject(method = "getRandomScent", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$scent(CallbackInfoReturnable<ItemStack> cir) {
        List<Item> scents = new ArrayList<>(List.of(ModItems.JOY_SCENT.get(), ModItems.VIVID_SCENT.get(), ModItems.EXCITE_SCENT.get()));
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().expandedScentSystemEnabled()) {
            scents.addAll(List.of(ModItems.TRANQUIL_SCENT.get(), ModItems.MEADOW_SCENT.get(), ModItems.SPARK_SCENT.get(),
                    ModItems.FOCUS_SCENT.get(), ModItems.FAMILIAR_SCENT.get(), ModItems.COMFORT_SCENT.get(),
                    ModItems.HEARTH_SCENT.get(), ModItems.INSIGHT_SCENT.get(), ModItems.LUCID_SCENT.get(),
                    ModItems.GROUNDING_SCENT.get(), ModItems.STEADY_SCENT.get(), ModItems.RESOLVE_SCENT.get()));
        }
        cir.setReturnValue(scents.get(ThreadLocalRandom.current().nextInt(scents.size())).getDefaultInstance());
    }
}
