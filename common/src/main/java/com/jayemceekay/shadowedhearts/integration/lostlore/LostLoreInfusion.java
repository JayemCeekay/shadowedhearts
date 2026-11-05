package com.jayemceekay.shadowedhearts.integration.lostlore;

import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature;
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeatureProvider;
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import dev.architectury.platform.Platform;

/** Optional infusion form selection using loaded species data, without linking Lost Lore classes. */
public final class LostLoreInfusion {
    private static final String SHADOW_FORM_FEATURE = "shadows";

    private LostLoreInfusion() {}

    /** Apply after Shadow corruption, so our form-change guard preserves learned moves. */
    public static void applyLugiaForm(Pokemon pokemon) {
        if (!Platform.isModLoaded("lost_lore")) return;
        var species = pokemon.getSpecies();
        var id = species.getResourceIdentifier();
        if (!"cobblemon".equals(id.getNamespace()) || !"lugia".equals(id.getPath())) return;

        var provider = SpeciesFeatures.getFeature(SHADOW_FORM_FEATURE);
        if (!(provider instanceof FlagSpeciesFeatureProvider flag) || !flag.isAspect()
                || !SpeciesFeatures.getFeaturesFor(species).contains(provider)) return;
        if (species.getForms().stream().noneMatch(form -> form.getAspects().contains(SHADOW_FORM_FEATURE)
                && ShadowMoveUtil.isShadowType(form.getPrimaryType()))) return;

        // Use the persistent native feature rather than forcing its derived aspect.
        new FlagSpeciesFeature(SHADOW_FORM_FEATURE, true).apply(pokemon);
    }
}
