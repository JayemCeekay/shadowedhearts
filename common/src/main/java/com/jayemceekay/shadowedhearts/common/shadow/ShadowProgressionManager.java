package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.EvGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTBridgeHolder;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

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
                if (xp > 0) {
                    IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
                    if (config.rctIntegrationEnabled() && config.limitExpToRCTCap()) {
                        ServerPlayer player = p.getOwnerPlayer();
                        if (player != null) {
                            int levelCap = RCTBridgeHolder.INSTANCE.getLevelCap(player);
                            int currentBuffered = ShadowAspectUtil.getBufferedExp(p);
                            int xpToCap = p.getExperienceToLevel(levelCap + 1) - 1;

                            if (xpToCap > currentBuffered) {
                                xp = Math.min(xp, xpToCap - currentBuffered);
                            } else {
                                xp = 0;
                            }
                        }
                    }

                    if (xp > 0) ShadowAspectUtil.addBufferedExp(p, xp);
                }
                // Set experience to 1 then subtract 1 instead of cancelling — cancelling rolls back the entire
                // candy use transaction and prevents item consumption. Setting to 0 lets the
                // item be consumed while ensuring no EXP is actually applied to the Pokémon.
                e.setExperience(1);
                p.setExperience$common(p.getExperience()-1);
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
                e.setAmount(0);
            }
            return Unit.INSTANCE;
        });
    }
}
