package com.jayemceekay.shadowedhearts.api.pokeball.catching;

import com.cobblemon.mod.common.api.pokeball.catching.CatchRateModifier;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class ShadowCatchRateModifier implements CatchRateModifier {

    @Override
    public float value(@NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        if (!pokemon.getAspects().contains(SHAspects.SHADOW)) {
            return 1.0f;
        }

        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        if (!config.shadowCatchRateScaleEnabled()) {
            return 1.0f;
        }

        int maxHeartGauge = HeartGaugeConfig.getMax(pokemon);
        if (maxHeartGauge <= 0) {
            return 1.0f;
        }

        // We use the current max heart gauge to determine the difficulty
        // normalized = heartGaugeMax / maxPossibleHeartGauge
        float normalized = Math.min(1.0f, (float) maxHeartGauge / (float) HeartGaugeConfig.getGlobalMax());
        
        double minMultiplier = config.shadowCatchRateMinMultiplier();
        double exponent = config.shadowCatchRateExponent();

        // difficultyMultiplier = lerp(1.0, minCatchMultiplier, normalized ^ exponent)
        return (float) Mth.lerp(Math.pow(normalized, exponent), 1.0, minMultiplier);
    }

    @NotNull
    @Override
    public Behavior behavior(@NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        return Behavior.MULTIPLY;
    }

    @Override
    public boolean isValid(@NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        return pokemon.getAspects().contains(SHAspects.SHADOW) && ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowCatchRateScaleEnabled();
    }

    @Override
    public float modifyCatchRate(float currentCatchRate, @NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        if (isValid(thrower, pokemon)) {
            return currentCatchRate * value(thrower, pokemon);
        }
        return currentCatchRate;
    }
}
