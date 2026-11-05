package com.jayemceekay.shadowedhearts.common.heart;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;

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
        if (delta == 0 || pokemon == null || !ShadowAspectUtil.hasShadowAspect(pokemon)) return;
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
        if (!p.isPlayerOwned()) return;
        int d = HeartGaugeDeltas.getDelta(p, HeartGaugeDeltas.EventType.BATTLE);
        apply(p, live, HeartGaugeProgressionMath.scaleReduction(d, GameplayTuningConfig.INSTANCE.battleMultiplier()));
    }

    public static void onCalledInBattle(PokemonEntity live) {
        if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().callButtonReducesHeartGauge()) return;
        Pokemon p = live.getPokemon();
        if (!p.isPlayerOwned()) return;
        int d = HeartGaugeDeltas.getDelta(p, HeartGaugeDeltas.EventType.CALL);
        apply(p, live, HeartGaugeProgressionMath.scaleReduction(d, GameplayTuningConfig.INSTANCE.callMultiplier()));
    }

    /** Apply walking/party step tick. Recommended to call every N blocks/steps moved. */
    public static void onPartyStep(Pokemon pokemon, PokemonEntity live) {
        onPartyStep(pokemon, live, true);
    }

    public static void onPartyStep(Pokemon pokemon, PokemonEntity live, boolean sync) {
        int d = HeartGaugeDeltas.getDelta(pokemon, HeartGaugeDeltas.EventType.PARTY);
        apply(pokemon, live, HeartGaugeProgressionMath.scaleReduction(d, GameplayTuningConfig.INSTANCE.walkingMultiplier()), sync);
    }

    /** Apply walking step reduction while in Purification Chamber. */
    public static void onChamberStep(Pokemon pokemon, PokemonEntity live) {
        onChamberStep(pokemon, live, true);
    }

    public static void onChamberStep(Pokemon pokemon, PokemonEntity live, boolean sync) {
        if (!GameplayTuningConfig.INSTANCE.chamberPurificationEnabled()) return;
        int d = HeartGaugeDeltas.getDelta(pokemon, HeartGaugeDeltas.EventType.CHAMBER);
        apply(pokemon, live, HeartGaugeProgressionMath.scaleReduction(d, GameplayTuningConfig.INSTANCE.chamberMultiplier()), sync);
    }
}
