package com.jayemceekay.shadowedhearts.mixin.pokeball;

import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect;
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.MultiplierModifier;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.effects.DarkBallCaptureEffect;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = PokeBalls.class)
public abstract class MixinPokeBalls {

    @Shadow
    protected abstract PokeBall createDefault(
            String name,
            com.cobblemon.mod.common.api.pokeball.catching.CatchRateModifier modifier,
            List<CaptureEffect> effects,
            float waterDragValue,
            ResourceLocation model2d,
            ResourceLocation model3d,
            float throwPower,
            boolean ancient
    );

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void shadowedhearts$registerDarkBall(CallbackInfo ci) {
        // Build the Dark Ball using Cobblemon's helper that populates defaults
        ResourceLocation model2d = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "dark_ball");
        ResourceLocation model3d = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "item/dark_ball_model");
        var modifier = new MultiplierModifier(1.0f,
                new kotlin.jvm.functions.Function2<net.minecraft.world.entity.LivingEntity, com.cobblemon.mod.common.pokemon.Pokemon, Boolean>() {
                    @Override
                    public Boolean invoke(net.minecraft.world.entity.LivingEntity livingEntity, com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
                        return true;
                    }
                }
        );
        var effects = List.<CaptureEffect>of(new DarkBallCaptureEffect());

        // Call the shadowed private createDefault on the Kotlin object instance
        ((MixinPokeBalls) (Object) PokeBalls.INSTANCE).createDefault(
                "dark_ball",
                modifier,
                effects,
                0.8f,
                model2d,
                model3d,
                1.25f,
                false
        );
    }
}
