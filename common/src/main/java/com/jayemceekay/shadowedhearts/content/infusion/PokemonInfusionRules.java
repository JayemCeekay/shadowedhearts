package com.jayemceekay.shadowedhearts.content.infusion;

import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.trade.TradeManager;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.InfuserConfig;
import com.jayemceekay.shadowedhearts.data.SpeciesTagManager;
import com.jayemceekay.shadowedhearts.integration.lostlore.LostLoreInfusion;
import net.minecraft.server.level.ServerPlayer;

/** Shared server rules for deterministic, paid conversion of a stored Pokemon. */
public final class PokemonInfusionRules {
    private PokemonInfusionRules() { }

    public static boolean eligible(Pokemon pokemon) {
        return eligibleBase(pokemon) && allowedSpecies(pokemon);
    }

    private static boolean eligibleBase(Pokemon pokemon) {
        return InfuserConfig.INSTANCE.pokemonEnabled() && pokemon != null && !pokemon.isBattleClone() && !isEgg(pokemon)
                && !ShadowAspectUtil.hasShadowAspect(pokemon)
                && !ShadowPokemonProperties.isImmunized(pokemon)
                && !VoidShadowService.isVoidShadow(pokemon);
    }

    private static boolean allowedSpecies(Pokemon pokemon) {
        var config = InfuserConfig.INSTANCE;
        if (config.speciesBlacklist().stream().anyMatch(entry -> matchesSpecies(pokemon, entry))) return false;
        return config.speciesWhitelist().isEmpty() || config.speciesWhitelist().stream().anyMatch(entry -> matchesSpecies(pokemon, entry));
    }

    private static boolean matchesSpecies(Pokemon pokemon, String selector) {
        if (selector.startsWith("#")) return SpeciesTagManager.INSTANCE.isInTag(pokemon.getSpecies(), selector);
        var id = pokemon.getSpecies().getResourceIdentifier();
        return selector.equals(id.toString()) || (!selector.contains(":") && selector.equals(id.getPath()));
    }

    public static boolean playerAvailable(ServerPlayer player) {
        return BattleRegistry.getBattleByParticipatingPlayer(player) == null
                && TradeManager.INSTANCE.getActiveTrade(player.getUUID()) == null;
    }

    public static int cost(Pokemon pokemon) {
        return cost(pokemon.getLevel(), pokemon.isLegendary() || pokemon.isMythical());
    }

    public static int duration(Pokemon pokemon) {
        // Use the same maximum that fullyCorrupt will fill, including individual overrides.
        return duration(pokemon.getLevel(), HeartGaugeConfig.getMax(pokemon));
    }

    public static int cost(int level, boolean rare) {
        return InfuserConfig.INSTANCE.pokemonSettings().cost(level, rare);
    }

    /** 45s base + 0.6s per level + 2.5s per 1,000 Heart Gauge capacity, at 20 TPS. */
    public static int duration(int level, int heartGaugeCapacity) {
        return InfuserConfig.INSTANCE.pokemonSettings().duration(level, heartGaugeCapacity);
    }

    /** Call only after validating and committing the server-side energy transaction. */
    public static boolean convert(Pokemon pokemon) {
        if (!eligible(pokemon)) return false;
        ShadowService.fullyCorrupt(pokemon, null, false);
        // Select the optional form after corruption protects learned moves, but before
        // the final assignment so the Lost Lore move preference can use its learnset.
        LostLoreInfusion.applyLugiaForm(pokemon);
        ShadowMoveUtil.assignShadowMoves(pokemon);
        ShadowService.syncAll(pokemon);
        return true;
    }

    private static boolean isEgg(Pokemon pokemon) {
        // Cobblemon has no native egg Pokemon API. These markers cover addon egg representations.
        if (pokemon.getSpecies().getResourceIdentifier().getPath().equals("egg")) return true;
        if (pokemon.getAspects().stream().anyMatch(aspect -> aspect.equals("egg") || aspect.endsWith(":egg"))) return true;
        return pokemon.getCustomProperties().stream().anyMatch(property -> {
            String marker = property.asString();
            return marker.equals("egg") || marker.equals("egg=true") || marker.endsWith(":egg=true");
        });
    }
}
