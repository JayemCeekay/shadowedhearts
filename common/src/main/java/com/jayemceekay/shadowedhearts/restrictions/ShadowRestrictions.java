package com.jayemceekay.shadowedhearts.restrictions;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonNicknamedEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;

/**
 * Server-side rules to restrict interactions with Shadow Pokémon until purified.
 * Implements: no leveling (battle, candies), no stone evolution, no move changes (handled elsewhere),
 * no nicknaming, no TMs (adapter provided elsewhere), and prepares for trade blocking.
 *
 * Canonical ref: 01_Core_Mechanics_Shadow_Purity (§ Shadow restrictions)
 */
public final class ShadowRestrictions {

    private ShadowRestrictions() {}

    public static void init() {
        // Block acceptance of any evolution while Shadow (covers stones and other triggers)
        CobblemonEvents.EVOLUTION_ACCEPTED.subscribe(Priority.NORMAL, event -> {
            Pokemon pokemon = event.getPokemon();
            if (pokemon == null) return kotlin.Unit.INSTANCE;
            if (PokemonAspectUtil.hasShadowAspect(pokemon)) {
                event.cancel();
            }
            return kotlin.Unit.INSTANCE;
        });

        // Block nickname changes while Shadow
        CobblemonEvents.POKEMON_NICKNAMED.subscribe(Priority.NORMAL, (PokemonNicknamedEvent e) -> {
            Pokemon pokemon = e.getPokemon();
            if (pokemon == null) return kotlin.Unit.INSTANCE;
            if (PokemonAspectUtil.hasShadowAspect(pokemon)) {
                e.cancel();
                // Optionally give feedback to player
               // e.getPlayer().sendSystemMessage(Component.translatable("shadowedhearts.shadow.nickname_blocked"));
            }
            return kotlin.Unit.INSTANCE;
        });

        // Block trading Shadow Pokémon
        CobblemonEvents.TRADE_EVENT_PRE.subscribe(Priority.NORMAL, pre -> {
            var p1 = pre.getTradeParticipant1Pokemon();
            var p2 = pre.getTradeParticipant2Pokemon();
            if ((p1 != null && PokemonAspectUtil.hasShadowAspect(p1)) || (p2 != null && PokemonAspectUtil.hasShadowAspect(p2))) {
                pre.cancel();
            }
            return kotlin.Unit.INSTANCE;
        });
    }
}
