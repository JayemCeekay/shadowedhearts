package com.jayemceekay.shadowedhearts.mixin.pokeball;

import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect;
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.MultiplierModifier;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.AntumbraBallCatchRateModifier;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.PenumbraBallCatchRateModifier;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.UmbraBallCatchRateModifier;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.effects.AntumbraBallCaptureEffect;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.effects.DarkBallCaptureEffect;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.effects.PenumbraBallCaptureEffect;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.effects.UmbraBallCaptureEffect;
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

        // Call the shadowed private createDefault on the Kotlin object instance
        ((MixinPokeBalls) (Object) PokeBalls.INSTANCE).createDefault(
                "penumbra_ball",
                new PenumbraBallCatchRateModifier(),
                List.of(new PenumbraBallCaptureEffect()),
                0.8f,
                ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "penumbra_ball"),
                ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "item/penumbra_ball_model"),
                1.25f,
                false
        );

        ((MixinPokeBalls) (Object) PokeBalls.INSTANCE).createDefault(
                "antumbra_ball",
                new AntumbraBallCatchRateModifier(),
                List.of(new AntumbraBallCaptureEffect()),
                0.8f,
                ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "antumbra_ball"),
                ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "item/antumbra_ball_model"),
                1.25f,
                false
        );

        ((MixinPokeBalls) (Object) PokeBalls.INSTANCE).createDefault(
                "umbra_ball",
                new UmbraBallCatchRateModifier(),
                List.<CaptureEffect>of(new UmbraBallCaptureEffect()),
                0.8f,
                ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "umbra_ball"),
                ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "item/umbra_ball_model"),
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
