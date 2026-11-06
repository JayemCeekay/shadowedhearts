package com.jayemceekay.shadowedhearts.api.pokeball.catching.effects;

import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties;

import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class AntumbraBallCaptureEffect implements CaptureEffect {
    @Override
    public void apply(@NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) {
            // Preserve partial purification, as with Penumbra: raise the maximum only.
            int max = HeartGaugeConfig.getMax(pokemon);
            int boostedMax = (int) Math.min(Integer.MAX_VALUE,
                    Math.ceil(max * (1.0 + config.antumbraBallHeartGaugeMaxBonus())));
            ShadowPokemonProperties.setHeartGaugeMaxProperty(pokemon, boostedMax);
        } else if (thrower.getRandom().nextDouble() < config.antumbraBallShadowConversionChance()) {
            ShadowService.corrupt(pokemon, null, HeartGaugeConfig.getMax(pokemon));
        }
    }
}
