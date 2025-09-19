package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.client.render.AuraRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {

        @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    public void getRenderTypeForShadowPokemon(LivingEntity livingEntity, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (livingEntity instanceof PokemonEntity pokemonEntity) {
            if (PokemonAspectUtil.hasShadowAspect(pokemonEntity.getPokemon())) {
                cir.setReturnValue(AuraRenderTypes.shadow_darken_layer(((LivingEntityRenderer) (Object) this).getTextureLocation(pokemonEntity)));
            }
        }
    }
}
