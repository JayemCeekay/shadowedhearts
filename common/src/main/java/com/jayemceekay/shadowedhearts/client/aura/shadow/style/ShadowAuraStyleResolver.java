package com.jayemceekay.shadowedhearts.client.aura.shadow.style;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleDefault;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleRoll;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Resolves synchronized sh_aura_style render aspects over the server's configured default. */
public final class ShadowAuraStyleResolver {
    /**
     * Canonical conflict priority, from highest to lowest.
     *
     * <p>The property provider emits at most one style aspect. If a legacy
     * client snapshot contains several, the more specific faithful variants
     * win deterministically: XD, then Colosseum, then Signature. This does not
     * depend on the iteration order of the aspect collection.
     */
    public static final List<ShadowAuraStyle> ASPECT_PRIORITY = List.of(
            ShadowAuraStyle.XD_FAITHFUL,
            ShadowAuraStyle.COLOSSEUM,
            ShadowAuraStyle.SIGNATURE
    );

    private ShadowAuraStyleResolver() {}

    public static ShadowAuraStyle resolve(PokemonEntity entity) {
        // Entity aspects are synchronized independently of the backing Pokemon.
        return resolve(entity == null ? null : entity.getAspects());
    }

    public static ShadowAuraStyle resolve(RenderablePokemon pokemon) {
        return resolve(pokemon == null ? null : pokemon.getAspects());
    }

    public static ShadowAuraStyle resolve(Iterable<String> aspects) {
        return explicitStyle(aspects).orElseGet(() -> inheritedStyle(aspects, configuredDefault()));
    }

    /** Resolves a delivered server policy without requiring a client runtime or loaded config. */
    public static ShadowAuraStyle resolve(Iterable<String> aspects, AuraStyleDefault serverDefault) {
        return explicitStyle(aspects).orElseGet(() -> inheritedStyle(aspects, serverDefault));
    }

    public static ShadowAuraStyle resolve(PokemonEntity entity,
                                          ShadowAuraStyle configDefault) {
        if (entity == null || entity.getPokemon() == null) {
            return defaultStyle(configDefault);
        }
        // PokemonEntity.ASPECTS is the client-synchronized render-facing set.
        // The backing Pokemon may remain stale after a live aspect mutation.
        return resolve(entity.getAspects(), configDefault);
    }

    public static ShadowAuraStyle resolve(RenderablePokemon pokemon,
                                          ShadowAuraStyle configDefault) {
        if (pokemon == null) {
            return defaultStyle(configDefault);
        }
        return resolve(pokemon.getAspects(), configDefault);
    }

    public static ShadowAuraStyle resolve(Iterable<String> aspects,
                                          ShadowAuraStyle configDefault) {
        return explicitStyle(aspects).orElse(defaultStyle(configDefault));
    }

    public static Optional<ShadowAuraStyle> explicitStyle(Iterable<String> aspects) {
        if (aspects == null) {
            return Optional.empty();
        }
        Set<String> normalizedAspects = new HashSet<>();
        for (String aspect : aspects) {
            if (aspect != null && !aspect.isBlank()) {
                normalizedAspects.add(aspect.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (ShadowAuraStyle candidate : ASPECT_PRIORITY) {
            if (normalizedAspects.contains(candidate.aspectId())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static ShadowAuraStyle defaultStyle(ShadowAuraStyle configDefault) {
        return configDefault == null ? ShadowAuraStyle.DEFAULT : configDefault;
    }

    private static ShadowAuraStyle inheritedStyle(Iterable<String> aspects, AuraStyleDefault serverDefault) {
        AuraStyleDefault policy = serverDefault == null ? AuraStyleDefault.SIGNATURE : serverDefault;
        return ShadowAuraStyle.fromSelection(policy.resolve(AuraStyleRoll.fromAspects(aspects).orElse(null)));
    }

    private static AuraStyleDefault configuredDefault() {
        return AuraStyleClientState.INSTANCE.currentDefault(
                () -> ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowAuraStyle());
    }
}
