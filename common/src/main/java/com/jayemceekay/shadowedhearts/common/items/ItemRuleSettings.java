package com.jayemceekay.shadowedhearts.common.items;

import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.config.PresetCompatibilityConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable server rules shared by teaching previews, item tooltips and authoritative actions. */
public record ItemRuleSettings(boolean shadowTMsEnabled, Set<String> shadowTMAllowedMoves, Set<String> disabledEffects) {
    public static final List<String> EFFECT_IDS = List.of("shadowGem", "rivenOrb", "shadowCollar", "emWaveAmplifier",
            "dissonanceBand", "breachLens", "calmingSachet", "heartSootheBell", "ruinPolicy", "shadowiumZ", "purifiedGem", "bitterReawakening", "shadowScale");
    public static final Map<String, String> BATTLE_EFFECTS = Map.ofEntries(
            Map.entry("shadowgem", "shadowGem"), Map.entry("rivenorb", "rivenOrb"), Map.entry("shadowcollar", "shadowCollar"),
            Map.entry("emwaveamplifier", "emWaveAmplifier"), Map.entry("dissonanceband", "dissonanceBand"), Map.entry("breachlens", "breachLens"),
            Map.entry("calmingsachet", "calmingSachet"), Map.entry("heartsoothebell", "heartSootheBell"), Map.entry("ruinpolicy", "ruinPolicy"),
            Map.entry("shadowiumz", "shadowiumZ"));
    public static final ItemRuleSettings DEFAULT = new ItemRuleSettings(true, Set.of(ShadowMoveUtil.SHADOW_IDS), Set.of());

    public ItemRuleSettings {
        shadowTMAllowedMoves = Set.copyOf(shadowTMAllowedMoves);
        disabledEffects = Set.copyOf(disabledEffects);
    }
    public boolean permitsShadowTM(String move) { return shadowTMsEnabled && shadowTMAllowedMoves.contains(move); }
    public boolean effectEnabled(String id) { return !disabledEffects.contains(id); }
    public static ItemRuleSettings serverCurrent() { return PresetCompatibilityConfig.INSTANCE.itemRuleSettings(); }
    public static ItemRuleSettings forPokemon(com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
        return pokemon != null && pokemon.isClient$common()
                ? ItemRuleClientState.INSTANCE.currentSettings(ItemRuleSettings::serverCurrent) : serverCurrent();
    }
}
