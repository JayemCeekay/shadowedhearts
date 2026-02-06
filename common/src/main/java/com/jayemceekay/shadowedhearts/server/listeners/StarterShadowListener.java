package com.jayemceekay.shadowedhearts.server.listeners;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.starter.StarterChosenEvent;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import kotlin.Unit;

public class StarterShadowListener {
    public static void init() {
        CobblemonEvents.STARTER_CHOSEN.subscribe(Priority.NORMAL, (StarterChosenEvent event) -> {
            if (event.getPlayer().level().getGameRules().getBoolean(Shadowedhearts.RULE_SHADOW_STARTERS)) {
                PokemonAspectUtil.setShadowAspect(event.getPokemon(), true, true);
            }
            return Unit.INSTANCE;
        });
    }
}
