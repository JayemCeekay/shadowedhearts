package com.jayemceekay.shadowedhearts.mixin.pokeball;

import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect;
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.MultiplierModifier;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.effects.DarkBallCaptureEffect;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.effects.PenumbraBallCaptureEffect;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
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
    private static void shadowedhearts$registerCustomBalls(CallbackInfo ci) {
        // Build the Penumbra Ball using Cobblemon's helper that populates defaults
        ResourceLocation model2d = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "penumbra_ball");
        ResourceLocation model3d = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "item/penumbra_ball_model");
        var modifier = new MultiplierModifier(1.5f,
                (livingEntity, pokemon) -> pokemon.getAspects().contains(SHAspects.SHADOW)
        );
        var effects = List.<CaptureEffect>of(new PenumbraBallCaptureEffect());

        // Call the shadowed private createDefault on the Kotlin object instance
        ((MixinPokeBalls) (Object) PokeBalls.INSTANCE).createDefault(
                "penumbra_ball",
                modifier,
                effects,
                0.8f,
                model2d,
                model3d,
                1.25f,
                false
        );

        ResourceLocation darkModel2d = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "dark_ball");
        ResourceLocation darkModel3d = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "item/dark_ball_model");
        var darkModifier = new MultiplierModifier(1.0f, (livingEntity, pokemon) -> true);
        var darkEffects = List.<CaptureEffect>of(new DarkBallCaptureEffect());

        ((MixinPokeBalls) (Object) PokeBalls.INSTANCE).createDefault(
                "dark_ball",
                darkModifier,
                darkEffects,
                0.8f,
                darkModel2d,
                darkModel3d,
                1.25f,
                false
        );
    }
}
