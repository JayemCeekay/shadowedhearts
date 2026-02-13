package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonAspectsChangedEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import kotlin.Unit;

/**
 * Server-side safeguard to ensure Shadow-required aspects exist whenever a Pokémon's aspects change.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 *
 * This avoids needing any client -> server packets: when server-side mutations to aspects happen
 * (e.g., toggling Shadow state), we validate and add missing meter/buffer aspects.
 */
public final class ShadowAspectValidator {
    private ShadowAspectValidator() {}

    /** Call once during common init. */
    public static void init() {
        CobblemonEvents.POKEMON_ASPECTS_CHANGED.subscribe(Priority.NORMAL, (PokemonAspectsChangedEvent e) -> {
            try {
                Pokemon p = e.getPokemon();
                // Idempotent; will only add supporting aspects if the Shadow aspect is present
                ShadowAspectUtil.ensureRequiredShadowAspects(p);
            } catch (Throwable ignored) {
            }
            return Unit.INSTANCE;
        });
    }
}
