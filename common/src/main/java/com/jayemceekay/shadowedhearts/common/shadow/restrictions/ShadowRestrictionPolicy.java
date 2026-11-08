package com.jayemceekay.shadowedhearts.common.shadow.restrictions;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;

/** Shared Shadow restrictions for authoritative gameplay and synchronized client previews. */
public final class ShadowRestrictionPolicy {
    private ShadowRestrictionPolicy() {}

    public static boolean buffersExperience(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).bufferExperience();
    }

    public static boolean buffersEVs(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).bufferEVs();
    }

    public static boolean blocksFriendshipChanges(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).blockFriendshipChanges();
    }

    public static boolean blocksEvolution(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).blockEvolution();
    }

    public static boolean blocksNicknaming(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).blockNicknaming();
    }

    public static boolean blocksTrading(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).blockTrading();
    }

    public static boolean blocksMoveChanges(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).blockMoveChanges();
    }

    public static boolean blocksMoveTeaching(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).blockMoveTeaching();
    }

    /** Legacy assigned slots stay protected. Authored learnsets use the server's normal move-edit rule. */
    public static boolean blocksMoveReplacement(Pokemon pokemon, MoveTemplate oldMove) {
        return blocksMoveChanges(pokemon)
                || com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycle.blocksReplacement(pokemon, oldMove)
                || (ShadowAspectUtil.hasShadowAspect(pokemon) && ShadowMoveUtil.isShadowMove(oldMove)
                && ShadowLearnsets.get(pokemon) == null);
    }
}
