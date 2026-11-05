package com.jayemceekay.shadowedhearts.common.shadow.battle;

import com.jayemceekay.shadowedhearts.config.ShadowBattleRulesConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;

/** Small authoritative presentation snapshot; never mutates move templates or saved PP. */
public record ShadowBattleSettings(boolean colosseumRush, boolean unlimitedShadowPP, boolean hyperShadowRushCrit) {
    public static ShadowBattleSettings serverCurrent() {
        var rules = ShadowBattleRulesConfig.INSTANCE;
        return new ShadowBattleSettings(rules.shadowRushVariant() == ShadowBattleRulesConfig.ShadowRushVariant.COLOSSEUM,
                rules.unlimitedShadowPP(), rules.hyperShadowRushCrit()
                && ShadowedHeartsConfigs.getInstance().getShadowConfig().hyperModeEnabled());
    }
}
