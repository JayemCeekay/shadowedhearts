package com.jayemceekay.shadowedhearts.common.aura;

import com.jayemceekay.shadowedhearts.network.PokemonStateSync;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.pokemon.properties.AuraStyleProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonical aura state and migration from legacy forced style aspects. */
public final class AuraStyleProperties {
    private AuraStyleProperties() {}

    /** Implemented by the Pokemon mixin to avoid recursive updateAspects calls during normalization. */
    public interface ForcedAspectAccess {
        void shadowedhearts$setAuraForcedAspects(Set<String> aspects);
    }

    public static AuraStyleSelection get(Pokemon pokemon) {
        return pokemon == null ? AuraStyleSelection.DEFAULT
                : lastSelection(pokemon.getCustomProperties()).orElse(AuraStyleSelection.DEFAULT);
    }

    public static AuraStyleSelection get(PokemonProperties properties) {
        return properties == null ? AuraStyleSelection.DEFAULT
                : lastSelection(properties.getCustomProperties()).orElse(AuraStyleSelection.DEFAULT);
    }

    /** Replaces any previous selection; an explicit DEFAULT prevents stale flags reviving an override. */
    public static void set(Pokemon pokemon, AuraStyleSelection selection) {
        Objects.requireNonNull(pokemon, "pokemon");
        Objects.requireNonNull(selection, "selection");
        List<CustomPokemonProperty> properties = pokemon.getCustomProperties();
        properties.removeIf(AuraStyleProperty.class::isInstance);
        properties.add(new AuraStyleProperty(selection));
        normalize(pokemon);
        pokemon.updateAspects();
        PokemonStateSync.syncProperties(pokemon);
    }

    /**
     * Keeps one stored property and removes legacy forced style flags and non-authoritative roll flags.
     * Existing properties win over flags;
     * duplicate properties keep the last value, matching repeated property application order. This method
     * does not recalculate aspects, send packets, or mark the Pokemon dirty; its caller owns those steps.
     */
    public static boolean normalize(Pokemon pokemon) {
        Objects.requireNonNull(pokemon, "pokemon");
        boolean changed = initializeRoll(pokemon);
        List<CustomPokemonProperty> properties = pokemon.getCustomProperties();
        Optional<AuraStyleSelection> stored = lastSelection(properties);
        Set<String> forcedAspects = pokemon.getForcedAspects();
        Optional<AuraStyleSelection> legacy = legacySelection(forcedAspects);
        Optional<AuraStyleSelection> selected = stored.isPresent() ? stored : legacy;

        if (selected.isPresent()) {
            long count = properties.stream().filter(AuraStyleProperty.class::isInstance).count();
            if (count != 1) {
                properties.removeIf(AuraStyleProperty.class::isInstance);
                properties.add(new AuraStyleProperty(selected.get()));
                changed = true;
            }
        }

        if (legacy.isPresent() || AuraStyleRoll.fromAspects(forcedAspects).isPresent()) {
            Set<String> cleanedForcedAspects = new HashSet<>(forcedAspects);
            cleanedForcedAspects.removeIf(aspect -> AuraStyleSelection.fromAspect(aspect).isPresent()
                    || AuraStyleRoll.fromAspect(aspect).isPresent());
            ((ForcedAspectAccess) pokemon).shadowedhearts$setAuraForcedAspects(cleanedForcedAspects);
            changed = true;
        }
        return changed;
    }

    public static Set<String> derivedAspects(Pokemon pokemon) {
        if (pokemon == null) return Set.of();
        AuraStyleSelection selected = get(pokemon);
        if (selected == AuraStyleSelection.DEFAULT) {
            // Always project the roll, so changing the server policy needs no Pokemon rewrites.
            return Set.of(AuraStyleRoll.aspectId(rolledStyle(pokemon)));
        }
        return aspectFor(selected);
    }

    public static Set<String> derivedAspects(PokemonProperties properties) {
        // A properties-only preview has no Pokemon UUID; an actual Pokemon adds its stable roll.
        return aspectFor(get(properties));
    }

    /** Stored independently of the explicit selection so battle clones and Agate Dew retain the roll. */
    public static AuraStyleSelection rolledStyle(Pokemon pokemon) {
        if (pokemon == null) return AuraStyleSelection.SIGNATURE;
        var data = pokemon.getPersistentData();
        Optional<AuraStyleSelection> stored = data == null ? Optional.empty()
                : AuraStyleRoll.storedStyle(data.getString(AuraStyleRoll.PERSISTENT_KEY));
        return stored.orElseGet(() -> pokemon.getUuid() == null ? AuraStyleSelection.SIGNATURE
                : AuraStyleRoll.forPokemon(pokemon.getUuid()));
    }

    private static boolean initializeRoll(Pokemon pokemon) {
        // Native loading can update aspects before persistentData is restored. The final initialization
        // projects the restored value; a missing temporary field must not break construction.
        var data = pokemon.getPersistentData();
        if (data == null || pokemon.getUuid() == null
                || AuraStyleRoll.storedStyle(data.getString(AuraStyleRoll.PERSISTENT_KEY)).isPresent()) return false;
        data.putString(AuraStyleRoll.PERSISTENT_KEY, AuraStyleRoll.forPokemon(pokemon.getUuid()).serializedName());
        return true;
    }

    private static Set<String> aspectFor(AuraStyleSelection selection) {
        return selection.aspectId().map(Set::of).orElseGet(Set::of);
    }

    private static Optional<AuraStyleSelection> lastSelection(Iterable<CustomPokemonProperty> properties) {
        AuraStyleSelection selection = null;
        for (CustomPokemonProperty property : properties) {
            if (property instanceof AuraStyleProperty auraStyle) {
                selection = auraStyle.getValue();
            }
        }
        return Optional.ofNullable(selection);
    }

    /** Retains the old resolver's deterministic XD, Colosseum, Signature conflict priority. */
    private static Optional<AuraStyleSelection> legacySelection(Iterable<String> aspects) {
        AuraStyleSelection selected = null;
        for (String aspect : aspects) {
            AuraStyleSelection candidate = AuraStyleSelection.fromAspect(aspect).orElse(null);
            if (candidate == AuraStyleSelection.XD_FAITHFUL) return Optional.of(candidate);
            if (candidate == AuraStyleSelection.COLOSSEUM
                    || (candidate == AuraStyleSelection.SIGNATURE && selected == null)) {
                selected = candidate;
            }
        }
        return Optional.ofNullable(selected);
    }
}
