package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.common.snag.SnagMachineSettings;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Independently configurable item effects; existing worlds keep their original behavior. */
public final class PresetCompatibilityConfig {
    public static final PresetCompatibilityConfig INSTANCE = new PresetCompatibilityConfig();

    private ModConfigSpec.BooleanValue darkBallCorruptionEnabled, nullCharmCancelsGoBonus, expandedScentItemsEnabled;
    private ModConfigSpec.BooleanValue shadowTMsEnabled, nullCharmWardEnabled, nullCharmDamageGuardEnabled;
    private ModConfigSpec.ConfigValue<List<? extends String>> shadowTMAllowedMoves;
    private final Map<String, ModConfigSpec.BooleanValue> effects = new LinkedHashMap<>();
    private final MachineSection prototype = new MachineSection();
    private final MachineSection advanced = new MachineSection();
    private ModConfigSpec ownerSpec;

    PresetCompatibilityConfig() {}

    public void build(ModConfigSpec.Builder builder) {
        builder.translation("shadowedhearts.configuration.compatibilityItems").push("compatibilityItems");
        darkBallCorruptionEnabled = builder.comment("Dark Ball captures corrupt Pokemon and replace their Heart Gauge maximum. Disabling this leaves the separately configured level bonus available and does not change existing Pokemon.")
                .translation("shadowedhearts.configuration.compatibilityItems.darkBallCorruptionEnabled")
                .define("darkBallCorruptionEnabled", true);
        nullCharmCancelsGoBonus = builder.comment("Allow Null Charm to cancel the attacking Shadow Pokemon's GO damage bonus. Independent of its Shadow effectiveness ward and non-GO damage protection.")
                .translation("shadowedhearts.configuration.compatibilityItems.nullCharmCancelsGoBonus")
                .define("nullCharmCancelsGoBonus", true);
        expandedScentItemsEnabled = builder.comment("Allow scents other than Joy, Excite and Vivid to be used. Disabled items remain registered and existing stacks are preserved. Independent of expanded nature affinities.")
                .translation("shadowedhearts.configuration.compatibilityItems.expandedScentItemsEnabled")
                .define("expandedScentItemsEnabled", true);
        shadowTMsEnabled = builder.comment("Allow Shadow TMs to replace an existing Shadow move slot. Checked again when a pending selection is confirmed; existing moves and discs are preserved.")
                .translation("shadowedhearts.configuration.compatibilityItems.shadowTMsEnabled").define("shadowTMsEnabled", true);
        shadowTMAllowedMoves = builder.comment("Shadow move IDs that may be taught with a Shadow TM. An empty list permits none. Does not change automatic Shadow move assignment or existing moves.")
                .translation("shadowedhearts.configuration.compatibilityItems.shadowTMAllowedMoves")
                .defineListAllowEmpty("shadowTMAllowedMoves", List.of(ShadowMoveUtil.SHADOW_IDS), () -> "shadowrush",
                        value -> value instanceof String id && Set.of(ShadowMoveUtil.SHADOW_IDS).contains(id));
        nullCharmWardEnabled = builder.comment("Allow Null Charm to neutralize super-effective Shadow attacks. Independent of its GO bonus cancellation and non-GO damage guard.")
                .translation("shadowedhearts.configuration.compatibilityItems.nullCharmWardEnabled").define("nullCharmWardEnabled", true);
        nullCharmDamageGuardEnabled = builder.comment("Allow Null Charm's 20% Shadow damage protection when neither super-effective Shadow attacks nor GO damage rules are enabled.")
                .translation("shadowedhearts.configuration.compatibilityItems.nullCharmDamageGuardEnabled").define("nullCharmDamageGuardEnabled", true);
        builder.translation("shadowedhearts.configuration.compatibilityItems.effects").push("effects");
        for (String id : ItemRuleSettings.EFFECT_IDS) {
            effects.put(id, builder.comment("Enable this item's custom battle or care effect. Disabled items remain registered and existing stacks are preserved. Existing permanent benefits are not revoked.")
                    .translation("shadowedhearts.configuration.compatibilityItems.effects." + id).define(id, true));
        }
        builder.pop();
        builder.pop();
        builder.translation("shadowedhearts.configuration.snagMachines").push("snagMachines");
        prototype.build(builder, "prototype", SnagMachineSettings.DEFAULT.prototype());
        advanced.build(builder, "advanced", SnagMachineSettings.DEFAULT.advanced());
        builder.pop();
    }

    public void bind(ModConfigSpec spec) { ownerSpec = spec; }
    private boolean loaded() { return ownerSpec != null && ownerSpec.isLoaded(); }
    public boolean darkBallCorruptionEnabled() { return !loaded() || darkBallCorruptionEnabled.get(); }
    public boolean nullCharmCancelsGoBonus() { return !loaded() || nullCharmCancelsGoBonus.get(); }
    public boolean expandedScentItemsEnabled() { return !loaded() || expandedScentItemsEnabled.get(); }
    public boolean nullCharmWardEnabled() { return !loaded() || nullCharmWardEnabled.get(); }
    public boolean nullCharmDamageGuardEnabled() { return !loaded() || nullCharmDamageGuardEnabled.get(); }
    public boolean effectEnabled(String id) { return !loaded() || !effects.containsKey(id) || effects.get(id).get(); }
    public ItemRuleSettings itemRuleSettings() {
        return loaded() ? new ItemRuleSettings(shadowTMsEnabled.get(), Set.copyOf(shadowTMAllowedMoves.get()),
                effects.entrySet().stream().filter(entry -> !entry.getValue().get()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet()))
                : ItemRuleSettings.DEFAULT;
    }
    /** IDs are fixed schema constants, never user-provided JavaScript. */
    public String battleItemEffectsJson() {
        return ItemRuleSettings.BATTLE_EFFECTS.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + effectEnabled(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    public SnagMachineSettings snagMachineSettings() {
        return loaded() ? new SnagMachineSettings(prototype.current(), advanced.current()) : SnagMachineSettings.DEFAULT;
    }

    public static boolean isClassicScent(String id) {
        return "joy".equals(id) || "excite".equals(id) || "vivid".equals(id);
    }

    private static final class MachineSection {
        private ModConfigSpec.IntValue spacing;
        private ModConfigSpec.DoubleValue catchMultiplier, gaugeReductionPercent;

        private void build(ModConfigSpec.Builder builder, String name, SnagMachineSettings.TierSettings defaults) {
            String translation = "shadowedhearts.configuration.snagMachines." + name;
            builder.translation(translation).push(name);
            spacing = builder.comment("Minimum battle turn difference between snag attempts. 1 permits an attempt each turn; 2 requires an intervening turn; 0 adds no turn recovery restriction.")
                    .translation(translation + ".attemptTurnSpacing")
                    .defineInRange("attemptTurnSpacing", defaults.attemptTurnSpacing(), 0, 1000);
            catchMultiplier = builder.comment("Multiplier applied only to eligible trainer snag catch calculations made with this machine. 1 is neutral; guaranteed captures retain their native rules.")
                    .translation(translation + ".catchMultiplier")
                    .defineInRange("catchMultiplier", defaults.catchMultiplier(), 0.0, 100.0);
            gaugeReductionPercent = builder.comment("Percentage of the remaining Heart Gauge removed after a successful eligible trainer snag. Fractional points removed round down; 0 preserves the current gauge. Capture benefits are fixed when its attempt is committed.")
                    .translation(translation + ".gaugeReductionPercent")
                    .defineInRange("gaugeReductionPercent", defaults.gaugeReductionPercent(), 0.0, 100.0);
            builder.pop();
        }

        private SnagMachineSettings.TierSettings current() {
            return new SnagMachineSettings.TierSettings(spacing.get(), catchMultiplier.get(), gaugeReductionPercent.get());
        }
    }
}
