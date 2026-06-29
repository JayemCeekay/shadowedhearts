package com.jayemceekay.shadowedhearts.api.pokeball.catching.effects;

import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class PenumbraBallCaptureEffect implements CaptureEffect {
    private static final double SHADOW_CONVERSION_CHANCE = 0.10;
    private static final double HEART_GAUGE_MAX_BONUS = 0.05;

    @Override
    public void apply(@NotNull LivingEntity livingEntity, @NotNull Pokemon pokemon) {
        boolean isShadow = pokemon.getAspects().contains(SHAspects.SHADOW);

        if (isShadow) {
            // Shadow pokemon caught with penumbra ball get 5% higher heart gauge maximum
            int baseMax = HeartGaugeConfig.getMax(pokemon);
            int boostedMax = (int) Math.ceil(baseMax * (1.0 + HEART_GAUGE_MAX_BONUS));
            ShadowAspectUtil.setHeartGaugeMaxProperty(pokemon, boostedMax);
        } else {
            // 10% chance to turn non-shadow pokemon into shadow pokemon upon capture
            if (ThreadLocalRandom.current().nextDouble() < SHADOW_CONVERSION_CHANCE) {
                int heartGaugeMax = HeartGaugeConfig.getMax(pokemon);
                ShadowService.corrupt(pokemon, null, heartGaugeMax);
            }
        }
    }
}
