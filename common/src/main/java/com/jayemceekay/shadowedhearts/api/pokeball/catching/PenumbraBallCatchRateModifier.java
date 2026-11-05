package com.jayemceekay.shadowedhearts.api.pokeball.catching;

import com.cobblemon.mod.common.api.pokeball.catching.CatchRateModifier;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class PenumbraBallCatchRateModifier implements CatchRateModifier{

    @Override
    public boolean isGuaranteed() {
        return false;
    }

    @Override
    public float value(@NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        return (float) (ShadowAspectUtil.hasShadowAspect(pokemon)
                ? config.penumbraBallShadowCatchMultiplier() : config.penumbraBallCatchMultiplier());
    }

    @NotNull
    @Override
    public CatchRateModifier.Behavior behavior(@NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        return CatchRateModifier.Behavior.MULTIPLY;
    }

    @Override
    public boolean isValid(@NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        return true;
    }

    @Override
    public float modifyCatchRate(float currentCatchRate, @NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        return currentCatchRate * value(thrower, pokemon);
    }

}
