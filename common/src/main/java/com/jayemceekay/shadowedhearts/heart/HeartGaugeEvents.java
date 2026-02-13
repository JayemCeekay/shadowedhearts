package com.jayemceekay.shadowedhearts.heart;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowService;

/**
 * Helper to apply Heart Gauge changes for various events.
 * Server-side only. All deltas are absolute meter changes (negative opens heart).
 */
public final class HeartGaugeEvents {
    private HeartGaugeEvents() {}

    private static void apply(Pokemon pokemon, PokemonEntity live, int delta) {
        apply(pokemon, live, delta, true);
    }

    private static void apply(Pokemon pokemon, PokemonEntity live, int delta, boolean sync) {
        if (pokemon == null || !ShadowAspectUtil.hasShadowAspect(pokemon)) return;
        // Ensure required aspects are present before we modify the gauge
        ShadowAspectUtil.ensureRequiredShadowAspects(pokemon);
        int cur = ShadowAspectUtil.getHeartGaugeMeter(pokemon);
        int next = cur + delta; // delta negative reduces meter
        ShadowService.setHeartGauge(pokemon, live, next, sync);
        // Post-application validation (idempotent) to keep aspects consistent
        ShadowAspectUtil.ensureRequiredShadowAspects(pokemon);
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
        onPartyStep(pokemon, live, true);
    }

    public static void onPartyStep(Pokemon pokemon, PokemonEntity live, boolean sync) {
        int d = HeartGaugeDeltas.getDelta(pokemon, HeartGaugeDeltas.EventType.PARTY);
        apply(pokemon, live, d, sync);
    }

    /** Apply walking step reduction while in Purification Chamber. */
    public static void onChamberStep(Pokemon pokemon, PokemonEntity live) {
        onChamberStep(pokemon, live, true);
    }

    public static void onChamberStep(Pokemon pokemon, PokemonEntity live, boolean sync) {
        int d = HeartGaugeDeltas.getDelta(pokemon, HeartGaugeDeltas.EventType.CHAMBER);
        apply(pokemon, live, d, sync);
    }
}
