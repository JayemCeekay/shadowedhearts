package com.jayemceekay.shadowedhearts.client.tm;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.tm.ShadowTMTeaching;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleClientState;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;

/** Contextual TM previews without adding Shadow moves to ordinary species learnsets. */
public final class ShadowTMClientEligibility {
    private ShadowTMClientEligibility() {}

    public static boolean isCompatible(Pokemon pokemon, MoveTemplate move) {
        ItemRuleSettings rules = ItemRuleClientState.INSTANCE.currentSettings(ItemRuleSettings::serverCurrent);
        // Native lists include moves already learned, so they remain visible for crafting.
        return ShadowTMTeaching.permitsSpecies(pokemon, move)
                && rules != null && rules.permitsShadowTM(move.getName())
                && (ShadowTMTeaching.canReplace(pokemon, move, rules)
                || ShadowTMTeaching.alreadyLearned(pokemon, move));
    }
}
