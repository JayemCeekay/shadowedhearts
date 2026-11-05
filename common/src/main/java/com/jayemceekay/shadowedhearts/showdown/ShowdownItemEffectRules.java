package com.jayemceekay.shadowedhearts.showdown;

import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;
import com.jayemceekay.shadowedhearts.config.PresetCompatibilityConfig;
import java.io.IOException;

/** Retains harmless item identity while disabling callbacks and simulator flags for existing stacks. */
public final class ShowdownItemEffectRules {
    private ShowdownItemEffectRules() {}

    public static String filterExpression(String id, String expression) throws IOException {
        String effect = ItemRuleSettings.BATTLE_EFFECTS.get(id);
        return effect == null ? expression : filterExpression(id, expression, PresetCompatibilityConfig.INSTANCE.effectEnabled(effect));
    }

    static String filterExpression(String id, String expression, boolean enabled) throws IOException {
        if (!ItemRuleSettings.BATTLE_EFFECTS.containsKey(id)) return expression;
        return ShowdownInjectionSupport.resourceExpression("/data/shadowedhearts/showdown/item-effect-rules.js")
                + "(" + expression + ",\"" + id + "\"," + enabled + ")";
    }
}
