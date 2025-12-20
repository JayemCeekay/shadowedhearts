package com.jayemceekay.shadowedhearts.config;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.Locale;

/**
 * Global config facade for wild Shadow spawn chance and blacklist.
 * Values are now read from ModConfig (shadowedhearts-common.json).
 */
public final class ShadowSpawnConfig {
    private ShadowSpawnConfig() {}

    private static String keyFor(Species species) {
        try {
            return species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String altKeyFor(Species species) {
        try {
            return species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }


    public static double getChancePercent() {
        return ModConfig.getShadowSpawnChancePercent();
    }

    public static boolean isBlacklisted(Pokemon pokemon) {
        if (pokemon == null) return false;
        Species sp = pokemon.getSpecies();
        return isBlacklisted(sp);
    }

    public static boolean isBlacklisted(Species species) {
        if (species == null) return false;
        String full = keyFor(species);
        String alt = altKeyFor(species);
        var bl = ModConfig.getShadowSpawnBlacklist();
        return (full != null && bl.contains(full)) || (alt != null && bl.contains(alt));
    }
}
