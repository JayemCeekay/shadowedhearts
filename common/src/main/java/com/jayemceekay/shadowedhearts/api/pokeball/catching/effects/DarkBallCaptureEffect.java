package com.jayemceekay.shadowedhearts.api.pokeball.catching.effects;

import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Capture-side gameplay effect applied when a Dark Ball successfully catches a
 * Pokemon.
 *
 * <p>The Dark Ball does more than mark the Pokemon as shadow-corrupted: it also
 * forces the captured Pokemon to level 100 and expands the heart gauge so the
 * corrupted state has a much larger purification budget than a normal shadow
 * conversion.
 */
public class DarkBallCaptureEffect implements CaptureEffect {
    private static final int DARK_BALL_LEVEL = 100;
    private static final int MIN_HEART_GAUGE_MAX = 100_000;
    private static final double HEART_GAUGE_MAX_MULTIPLIER = 10.0;

    /**
     * Applies Dark Ball capture rules to the captured Pokemon.
     *
     * @param livingEntity entity that was captured; unused here because the
     *                     persistent Pokemon data owns the gameplay state
     * @param pokemon persistent Pokemon data to mutate
     */
    @Override
    public void apply(@NotNull LivingEntity livingEntity, @NotNull Pokemon pokemon) {
        pokemon.setLevel(DARK_BALL_LEVEL);

        // Use the configured gauge as a baseline, but enforce a high floor so
        // Dark Ball captures are always meaningfully harder to purify.
        int boostedMax = Math.max(
                MIN_HEART_GAUGE_MAX,
                (int) Math.ceil(HeartGaugeConfig.getMax(pokemon) * HEART_GAUGE_MAX_MULTIPLIER)
        );
        ShadowAspectUtil.setHeartGaugeMaxProperty(pokemon, boostedMax, false);
        ShadowService.corrupt(pokemon, null, boostedMax);
    }
}
