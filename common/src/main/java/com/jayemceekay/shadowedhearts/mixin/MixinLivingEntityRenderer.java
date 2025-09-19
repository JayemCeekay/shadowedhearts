package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.poketoss.client.WhistleSelectionClient;
import com.jayemceekay.shadowedhearts.poketoss.client.TargetSelectionClient;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity> {

    @ModifyArg(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;" +
                            "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                            "Lcom/mojang/blaze3d/vertex/VertexConsumer;II I)V"
            ),
            index = 4 // the packed 'color' arg
    )
    private int shadowedhearts$applyTint(
            int originalColor, @Local(argsOnly = true) LivingEntity livingEntity
    ) {
        int color = originalColor;
        if (shadowedhearts$shouldDarken(livingEntity)) {
            color = shadowedhearts$mulRGB1(color, 0.65f); // 35% darker
        }
        if (livingEntity instanceof PokemonEntity) {
            // Red highlight when in target selection mode and under crosshair
            if (TargetSelectionClient.isHighlighted(livingEntity.getId())) {
                color = shadowedhearts$mulRGB3(color, TargetSelectionClient.HIL_R, TargetSelectionClient.HIL_G, TargetSelectionClient.HIL_B);
            }
            // Green tint for whistle brush-selected allies
            if (WhistleSelectionClient.isSelected(livingEntity.getId())) {
                color = shadowedhearts$mulRGB3(color, WhistleSelectionClient.SEL_R, WhistleSelectionClient.SEL_G, WhistleSelectionClient.SEL_B);
            }
        }
        return color;
    }

    @Unique
    private boolean shadowedhearts$shouldDarken(LivingEntity e) {
        // Your condition (type/UUID/tag/capability/etc.)
        return e instanceof PokemonEntity pokemonEntity && PokemonAspectUtil.hasShadowAspect(pokemonEntity.getPokemon());
    }

    @Unique
    private static int shadowedhearts$mulRGB1(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>>  8) & 0xFF;
        int b = (argb       ) & 0xFF;
        r = Math.min(255, Math.round(r * f));
        g = Math.min(255, Math.round(g * f));
        b = Math.min(255, Math.round(b * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Unique
    private static int shadowedhearts$mulRGB3(int argb, float rf, float gf, float bf) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>>  8) & 0xFF;
        int b = (argb       ) & 0xFF;
        r = Math.min(255, Math.round(r * rf));
        g = Math.min(255, Math.round(g * gf));
        b = Math.min(255, Math.round(b * bf));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
