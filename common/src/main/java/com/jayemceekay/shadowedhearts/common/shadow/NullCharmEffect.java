package com.jayemceekay.shadowedhearts.common.shadow;

import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.PresetCompatibilityConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;

import java.util.Objects;

/** The active Null Charm effect, selected by the server's Shadow battle rules. */
public enum NullCharmEffect {
    SHADOW_WARD("item.shadowedhearts.null_charm.tooltip_1"),
    ATTACKER_BONUS_GUARD("item.shadowedhearts.null_charm.tooltip_go"),
    SHADOW_DAMAGE_GUARD("item.shadowedhearts.null_charm.tooltip_shadow"),
    SHADOW_WARD_AND_BONUS_GUARD("item.shadowedhearts.null_charm.tooltip_combined"),
    INACTIVE("item.shadowedhearts.null_charm.tooltip_inactive");

    private final String tooltipKey;

    NullCharmEffect(String tooltipKey) {
        this.tooltipKey = tooltipKey;
    }

    public String tooltipKey() {
        return tooltipKey;
    }

    public static NullCharmEffect fromConfig(IShadowConfig config) {
        return fromConfig(config, PresetCompatibilityConfig.INSTANCE.nullCharmCancelsGoBonus(),
                PresetCompatibilityConfig.INSTANCE.nullCharmWardEnabled(), PresetCompatibilityConfig.INSTANCE.nullCharmDamageGuardEnabled());
    }

    public static NullCharmEffect fromConfig(IShadowConfig config, boolean cancelsGoBonus) {
        return fromConfig(config, cancelsGoBonus, true, true);
    }

    public static NullCharmEffect fromConfig(IShadowConfig config, boolean cancelsGoBonus, boolean ward, boolean damageGuard) {
        Objects.requireNonNull(config, "config");
        if (config.superEffectiveShadowMovesEnabled() && ward) {
            return config.goDamageModifierEnabled() && cancelsGoBonus ? SHADOW_WARD_AND_BONUS_GUARD : SHADOW_WARD;
        }
        return config.goDamageModifierEnabled() ? (cancelsGoBonus ? ATTACKER_BONUS_GUARD : INACTIVE)
                : !config.superEffectiveShadowMovesEnabled() && damageGuard ? SHADOW_DAMAGE_GUARD : INACTIVE;
    }

    public static NullCharmEffect serverCurrent() {
        return fromConfig(ShadowedHeartsConfigs.getInstance().getShadowConfig());
    }
}
