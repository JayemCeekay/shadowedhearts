package com.jayemceekay.shadowedhearts.api.pokeball.catching.effects;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.capture.DarkBallCaptureRules;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.PresetCompatibilityConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTBridgeHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Capture-side gameplay effect applied when a Dark Ball successfully catches a
 * Pokemon.
 *
 * <p>Grants a configurable one-time level bonus and corrupts the Pokemon with
 * a configurable purification cost based on its normal species Heart Gauge.
 */
public class DarkBallCaptureEffect implements CaptureEffect {
    /**
     * Applies Dark Ball capture rules to the captured Pokemon.
     *
     * @param thrower capturing player, supplied before the Pokemon enters their party
     * @param pokemon persistent Pokemon data to mutate
     */
    @Override
    public void apply(@NotNull LivingEntity thrower, @NotNull Pokemon pokemon) {
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        if (!pokemon.getPersistentData().getBoolean(DarkBallCaptureRules.NBT_LEVEL_BOOSTED)) {
            int levelCap = Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel();
            // The captured Pokemon is not owned yet, so use the thrower for RCT's cap.
            if (config.rctIntegrationEnabled() && config.limitExpToRCTCap()
                    && thrower instanceof ServerPlayer player) {
                levelCap = Math.min(levelCap, RCTBridgeHolder.INSTANCE.getLevelCap(player));
            }
            int boostedLevel = DarkBallCaptureRules.boostedLevel(pokemon.getLevel(), config.darkBallLevelBoost(), levelCap);
            if (boostedLevel > pokemon.getLevel()) {
                pokemon.setLevel(boostedLevel);
                pokemon.getPersistentData().putBoolean(DarkBallCaptureRules.NBT_LEVEL_BOOSTED, true);
            }
        }

        if (!PresetCompatibilityConfig.INSTANCE.darkBallCorruptionEnabled()) return;

        // Keep the purification penalty separate from the ordinary scaled Shadow IV reward.
        // This provenance survives purification, trading and save/load with the Pokemon.
        pokemon.getPersistentData().putBoolean(DarkBallCaptureRules.NBT_CORRUPTED, true);
        int boostedMax = DarkBallCaptureRules.heartGaugeMaximum(
                HeartGaugeConfig.getMax(pokemon.getSpecies()), config.darkBallHeartGaugeMultiplier());
        ShadowAspectUtil.setHeartGaugeMaxProperty(pokemon, boostedMax, false);
        ShadowService.corrupt(pokemon, null, boostedMax);
    }
}
