package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.EvGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.interaction.ExperienceCandyUseEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import kotlin.Unit;

/**
 * Holds EXP and EV gains for Shadow Pokémon until purification.
 */
public final class ShadowProgressionManager {
    private ShadowProgressionManager() {}

    public static void init() {
        // Intercept any EXP gain for shadow mons and buffer it instead
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL, (ExperienceGainedEvent.Pre e) -> {
            Pokemon p = e.getPokemon();
            if (p == null) return Unit.INSTANCE;
            if (ShadowAspectUtil.hasShadowAspect(p)) {
                int xp = e.getExperience();
                if (xp > 0) ShadowAspectUtil.addBufferedExp(p, xp);
                e.cancel();
            }
            return Unit.INSTANCE;
        });

        // Prevent consuming candies on shadow mons; buffer their yield instead
        CobblemonEvents.EXPERIENCE_CANDY_USE_PRE.subscribe(Priority.NORMAL, (ExperienceCandyUseEvent.Pre e) -> {
            Pokemon p = e.getPokemon();
            if (p == null) return Unit.INSTANCE;
            if (ShadowAspectUtil.hasShadowAspect(p)) {
                int yield = Math.max(0, e.getExperienceYield());
                if (yield > 0) ShadowAspectUtil.addBufferedExp(p, yield);
                e.cancel();
            }
            return Unit.INSTANCE;
        });

        // Intercept any EV gain for shadow mons and buffer it per-stat instead
        CobblemonEvents.EV_GAINED_EVENT_PRE.subscribe(Priority.NORMAL, (EvGainedEvent.Pre e) -> {
            Pokemon p = e.getPokemon();
            if (p == null) return Unit.INSTANCE;
            if (ShadowAspectUtil.hasShadowAspect(p)) {
                int amt = e.getAmount();
                if (amt > 0) ShadowAspectUtil.addBufferedEv(p, e.getStat(), amt);
                e.cancel();
            }
            return Unit.INSTANCE;
        });
    }
}
