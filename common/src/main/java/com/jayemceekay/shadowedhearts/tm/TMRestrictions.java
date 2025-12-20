package com.jayemceekay.shadowedhearts.tm;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, generic adapter for third-party TM systems to query whether a TM can be applied.
 * Cobblemon currently has no native TMs; this provides a small API surface that other mods
 * can optionally integrate with. By default, Shadow Pokémon are denied.
 *
 * Usage by third-parties: if (TMRestrictions.canApplyTM(pokemon)) { ...apply... }
 */
public final class TMRestrictions {

    private TMRestrictions() {}

    /**
     * A functional check that can allow/deny TM application.
     * Return null to express "no opinion" and defer to other checks; return Boolean.TRUE/FALSE otherwise.
     */
    @FunctionalInterface
    public interface Checker {
        Boolean allow(Pokemon pokemon, String tmId);
    }

    private static final List<Checker> CHECKERS = new ArrayList<>();

    /** Register an additional checker (third-party mods can call this). */
    public static void register(Checker checker) {
        if (checker != null) CHECKERS.add(checker);
    }

    /**
     * Returns whether a TM identified by tmId may be applied to this Pokemon.
     * Default policy: deny if Shadow; otherwise allow unless a checker denies.
     */
    public static boolean canApplyTM(Pokemon pokemon, String tmId) {
        if (pokemon == null) return false;
        // Primary restriction: Shadow Pokémon cannot use TMs until purified.
        if (PokemonAspectUtil.hasShadowAspect(pokemon)) return false;

        // Allow third-party hooks to intervene.
        for (Checker c : CHECKERS) {
            try {
                Boolean res = c.allow(pokemon, tmId);
                if (res != null) return res;
            } catch (Throwable ignored) { }
        }
        return true;
    }
}
