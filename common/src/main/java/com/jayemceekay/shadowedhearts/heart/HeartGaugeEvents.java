package com.jayemceekay.shadowedhearts.heart;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowService;

/**
 * Helper to apply Heart Gauge changes for various events.
 * Server-side only. All deltas are absolute meter changes (negative opens heart).
 */
public final class HeartGaugeEvents {
    private HeartGaugeEvents() {}

    private static void apply(Pokemon pokemon, PokemonEntity live, int delta) {
        if (pokemon == null || !PokemonAspectUtil.hasShadowAspect(pokemon)) return;
        // Ensure required aspects are present before we modify the gauge
        PokemonAspectUtil.ensureRequiredShadowAspects(pokemon);
        int cur = PokemonAspectUtil.getHeartGaugeMeter(pokemon);
        int next = cur + delta; // delta negative reduces meter
        ShadowService.setHeartGauge(pokemon, live, next);
        // Post-application validation (idempotent) to keep aspects consistent
        PokemonAspectUtil.ensureRequiredShadowAspects(pokemon);
    }

    public static void onBattleSentOut(PokemonEntity live) {
        Pokemon p = live.getPokemon();
        int d = HeartGaugeDeltas.getDelta(p, HeartGaugeDeltas.EventType.BATTLE);
        apply(p, live, d);
    }

    public static void onCalledInBattle(PokemonEntity live) {
        Pokemon p = live.getPokemon();
        int d = HeartGaugeDeltas.getDelta(p, HeartGaugeDeltas.EventType.CALL);
        apply(p, live, d);
    }

    /** Apply walking/party step tick. Recommended to call every N blocks/steps moved. */
    public static void onPartyStep(Pokemon pokemon, PokemonEntity live) {
        int d = HeartGaugeDeltas.getDelta(pokemon, HeartGaugeDeltas.EventType.PARTY);
        apply(pokemon, live, d);
    }

    /** Apply walking step reduction while in Purification Chamber. */
    public static void onChamberStep(Pokemon pokemon, PokemonEntity live) {
        int d = HeartGaugeDeltas.getDelta(pokemon, HeartGaugeDeltas.EventType.CHAMBER);
        apply(pokemon, live, d);
    }
}
