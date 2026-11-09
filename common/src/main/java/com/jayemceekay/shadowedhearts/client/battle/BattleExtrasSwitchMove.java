package com.jayemceekay.shadowedhearts.client.battle;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.resources.ResourceLocation;

/** Access to a move row in Battle Extras' switch tooltip. */
public interface BattleExtrasSwitchMove {
    void shadowedhearts$prepareSwitchMove(Pokemon pokemon);
    ResourceLocation shadowedhearts$switchIcon();
}
