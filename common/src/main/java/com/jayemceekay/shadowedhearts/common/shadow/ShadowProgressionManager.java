package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.pokemon.stats.EvSource;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionPolicy;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ProgressionEconomyConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTBridgeHolder;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Commits gains only after Cobblemon's PRE event has finished successfully. */
public final class ShadowProgressionManager {
    private ShadowProgressionManager() {}

    public static int bufferExperience(Pokemon pokemon, int requested) {
        requested = earnedExperience(pokemon, requested);
        int buffered = ShadowAspectUtil.getBufferedExp(pokemon);
        int accepted = ShadowProgressionRules.acceptedExperience(requested, buffered,
                experienceCapacity(pokemon, null));
        if (accepted > 0) ShadowAspectUtil.setBufferedExp(pokemon, buffered + accepted);
        return accepted;
    }

    public static int earnedExperience(Pokemon pokemon, int requested) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return requested;
        var config = ProgressionEconomyConfig.INSTANCE;
        if (!belowThreshold(pokemon, config.experienceStartBelowPercent())) return 0;
        return ShadowProgressionRules.scaledExperience(requested, config.experienceMultiplier());
    }

    public static boolean belowThreshold(Pokemon pokemon, int percent) {
        return ShadowProgressionRules.belowThreshold(ShadowAspectUtil.getHeartGaugeMeter(pokemon),
                HeartGaugeConfig.getMax(pokemon), percent);
    }

    public static ProgressionEconomyConfig.EvPolicy evPolicy(EvSource source) {
        if (!ShadowAspectUtil.hasShadowAspect(source.getPokemon())) return ProgressionEconomyConfig.EvPolicy.ALLOW;
        var config = ProgressionEconomyConfig.INSTANCE;
        var policy = source.isBattle() ? config.battleEvPolicy() : config.otherEvPolicy();
        if (policy == ProgressionEconomyConfig.EvPolicy.FOLLOW_RESTRICTIONS) {
            return ShadowRestrictionPolicy.buffersEVs(source.getPokemon())
                    ? ProgressionEconomyConfig.EvPolicy.BUFFER : ProgressionEconomyConfig.EvPolicy.ALLOW;
        }
        return policy;
    }

    /** Called after removing the Shadow aspect and before releasing EXP. */
    public static void releaseFriendship(Pokemon pokemon, boolean wasShadow) {
        ShadowFriendshipProgression.release(pokemon, wasShadow);
    }

    /** Total pending EXP that can be released under the current owner's caps. */
    public static int experienceCapacity(Pokemon pokemon, @Nullable ServerPlayer authorizedOwner) {
        int ceilingLevel = Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel();
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        if (config.rctIntegrationEnabled() && config.limitExpToRCTCap()) {
            ServerPlayer player = authorizedOwner != null ? authorizedOwner : pokemon.getOwnerPlayer();
            if (player != null) ceilingLevel = Math.min(ceilingLevel, RCTBridgeHolder.INSTANCE.getLevelCap(player));
        }
        if (pokemon.getLevel() >= ceilingLevel) return 0;
        // RCT stops at the beginning of the capped level. Do not accept EXP which its native
        // listener would discard at purification, or overflow an old oversized saved buffer.
        long capacity = (long) pokemon.getExperienceGroup().getExperience(ceilingLevel) - pokemon.getExperience();
        long integerRoom = (long) Integer.MAX_VALUE - Math.max(0, pokemon.getExperience());
        return (int) Math.max(0L, Math.min(capacity, integerRoom));
    }
}
