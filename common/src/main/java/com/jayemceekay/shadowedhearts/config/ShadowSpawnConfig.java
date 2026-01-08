package com.jayemceekay.shadowedhearts.config;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.jayemceekay.shadowedhearts.server.SpeciesTagManager;

import java.util.List;
import java.util.Locale;

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
        return ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowSpawnChancePercent();
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
        List<? extends String> bl = ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowSpawnBlacklist();

        for (String entry : bl) {
            if (entry.startsWith("#")) {
                if (SpeciesTagManager.INSTANCE.isInTag(species, entry)) return true;
            } else {
                if (entry.equals(full) || entry.equals(alt)) return true;
            }
        }

        return false;
    }
}
