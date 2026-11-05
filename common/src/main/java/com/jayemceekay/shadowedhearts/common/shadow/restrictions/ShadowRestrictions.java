package com.jayemceekay.shadowedhearts.common.shadow.restrictions;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonNicknamedEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.mixin.AccessorCancelable;

/**
 * Server-side rules to restrict interactions with Shadow Pokémon until purified.
 * Applies the configured evolution, nickname and trade restrictions.
 */
public final class ShadowRestrictions {

    private ShadowRestrictions() {}

    public static void init() {
        // Block acceptance of any evolution while Shadow (covers stones and other triggers)
        CobblemonEvents.EVOLUTION_ACCEPTED.subscribe(Priority.NORMAL, event -> {
            Pokemon pokemon = event.getPokemon();
            if (pokemon == null) return kotlin.Unit.INSTANCE;
            if (ShadowRestrictionPolicy.blocksEvolution(pokemon)) {
                event.cancel();
            }
            return kotlin.Unit.INSTANCE;
        });

        // Block nickname changes while Shadow
        CobblemonEvents.POKEMON_NICKNAMED.subscribe(Priority.NORMAL, (PokemonNicknamedEvent e) -> {
            Pokemon pokemon = e.getPokemon();
            if (pokemon == null) return kotlin.Unit.INSTANCE;
            if (ShadowRestrictionPolicy.blocksNicknaming(pokemon)) {
                e.cancel();
            }
            return kotlin.Unit.INSTANCE;
        });

        // Block trading Shadow Pokémon
        CobblemonEvents.TRADE_EVENT_PRE.subscribe(Priority.NORMAL, pre -> {
            var p1 = pre.getTradeParticipant1Pokemon();
            var p2 = pre.getTradeParticipant2Pokemon();
            if (ShadowRestrictionPolicy.blocksTrading(p1) || ShadowRestrictionPolicy.blocksTrading(p2)) {
                pre.cancel();
            }
            return kotlin.Unit.INSTANCE;
        });

        // Uncancel pokeball hit for Shadow Pokemon (overrides TimCore reservation)
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.LOW, (ThrownPokeballHitEvent event) -> {
            if (event.isCanceled() && ShadowAspectUtil.hasShadowAspect(event.getPokemon().getPokemon())) {
                ((AccessorCancelable) (Object) event).setCanceled(false);
            }
            return kotlin.Unit.INSTANCE;
        });
    }
}
