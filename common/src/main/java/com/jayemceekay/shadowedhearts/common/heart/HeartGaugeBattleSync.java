package com.jayemceekay.shadowedhearts.common.heart;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import org.jetbrains.annotations.Nullable;

/** Keeps Showdown's exact gauge in step with authoritative server gauge changes. */
public final class HeartGaugeBattleSync {
    private HeartGaugeBattleSync() {}

    public static double fraction(Pokemon pokemon) {
        return fraction(ShadowHeartGauge.getHeartGaugeMeter(pokemon), HeartGaugeConfig.getMax(pokemon));
    }

    static double fraction(int meter, int maximum) {
        if (maximum <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (double) meter / maximum));
    }

    public static void sync(Pokemon pokemon, @Nullable PokemonEntity live) {
        PokemonEntity entity = live != null ? live : pokemon.getEntity();
        if (entity != null && entity.level().isClientSide) return;

        PokemonBattle battle = entity != null && entity.getBattleId() != null
                ? BattleRegistry.getBattle(entity.getBattleId()) : null;
        if (battle == null) {
            // A benched party member has no live entity but may still belong to the battle team.
            var owner = pokemon.getOwnerPlayer();
            if (owner != null) battle = BattleRegistry.getBattleByParticipatingPlayer(owner);
        }
        if (battle == null || battle.getEnded()) return;

        for (var actor : battle.getActors()) {
            for (var member : actor.getPokemonList()) {
                if (member.getUuid().equals(pokemon.getUuid())) {
                    // Use the same service/ordering path as native battle actions, including socket runtimes.
                    battle.writeShowdownAction(">shheartgauge " + member.getUuid() + " " + fraction(pokemon));
                    return;
                }
            }
        }
    }
}
