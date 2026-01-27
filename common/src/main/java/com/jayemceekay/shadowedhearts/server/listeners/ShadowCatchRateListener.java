package com.jayemceekay.shadowedhearts.server.listeners;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.jayemceekay.shadowedhearts.api.pokeball.catching.ShadowCatchRateModifier;
import com.jayemceekay.shadowedhearts.config.ISnagConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.snag.SnagCaps;
import kotlin.Unit;
import net.minecraft.world.entity.player.Player;

public final class ShadowCatchRateListener {
    private static final ShadowCatchRateModifier MODIFIER = new ShadowCatchRateModifier();

    public static void init() {
        CobblemonEvents.POKEMON_CATCH_RATE.subscribe(Priority.NORMAL, (PokemonCatchRateEvent e) -> {
            if (e.getPokemonEntity().getPokemon().getAspects().contains(SHAspects.SHADOW)) {
                // Apply scaling modifier
                float rate = e.getCatchRate();
                rate = MODIFIER.modifyCatchRate(rate, e.getThrower(), e.getPokemonEntity().getPokemon());

                // Apply pity bonus
                if (e.getThrower() instanceof Player player) {
                    var cap = SnagCaps.get(player);
                    ISnagConfig snagConfig = ShadowedHeartsConfigs.getInstance().getSnagConfig();
                    if (snagConfig.failStackingBonus() && cap.failedSnagAttempts() > 0) {
                        double bonus = cap.failedSnagAttempts() * snagConfig.failBonusPerAttempt();
                        bonus = Math.min(bonus, snagConfig.maxFailBonus());
                        rate += (float) bonus;
                    }
                }
                e.setCatchRate(rate);
            }
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, (PokemonCapturedEvent e) -> {
            if (e.getPokemon().getAspects().contains(SHAspects.SHADOW)) {
                SnagCaps.get(e.getPlayer()).resetFailedSnagAttempts();
            }
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.NORMAL, (PokeBallCaptureCalculatedEvent e) -> {
            if (e.getPokemonEntity().getPokemon().getAspects().contains(SHAspects.SHADOW)) {
                if (!e.getCaptureResult().isSuccessfulCapture()) {
                    if (e.getThrower() instanceof Player player) {
                        SnagCaps.get(player).incrementFailedSnagAttempts();
                    }
                }
            }
            return Unit.INSTANCE;
        });
    }
}
