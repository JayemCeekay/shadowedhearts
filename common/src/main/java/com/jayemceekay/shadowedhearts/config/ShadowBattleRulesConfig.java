package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;

/** Independent opt-in Orre battle rules; existing worlds retain the modern defaults. */
public final class ShadowBattleRulesConfig {
    public enum ShadowRushVariant { MODERN, COLOSSEUM }
    public enum HyperBehavior { MODERN, COLOSSEUM }
    public enum ReverseEntry { NATURE, XD_SPECIES }

    public static final ShadowBattleRulesConfig INSTANCE = new ShadowBattleRulesConfig();
    private ModConfigSpec.EnumValue<ShadowRushVariant> shadowRushVariant;
    private ModConfigSpec.BooleanValue unlimitedShadowPP, hyperShadowRushCrit;
    private ModConfigSpec.EnumValue<HyperBehavior> hyperBehavior;
    private ModConfigSpec.EnumValue<ReverseEntry> reverseEntry;
    private ModConfigSpec.BooleanValue hyperItemRefusal, reverseItemRefusal;
    private ModConfigSpec.ConfigValue<List<? extends String>> reverseSpeciesOverrides;
    private ModConfigSpec.ConfigValue<String> reverseFallback, reverseGaugeBands;
    private ModConfigSpec ownerSpec;

    ShadowBattleRulesConfig() {}

    void build(ModConfigSpec.Builder builder) {
        builder.translation("shadowedhearts.configuration.shadowBattleRules").push("shadowBattleRules");
        shadowRushVariant = builder.comment("MODERN keeps Shadow Rush at 55 power without recoil. COLOSSEUM uses 90 power and recoil of 1/16 base maximum HP after a damaging hit, rounded down (minimum 1), unaffected by Rock Head. Other battle-generation rules remain unchanged.")
                .translation("shadowedhearts.configuration.shadowBattleRules.shadowRushVariant")
                .defineEnum("shadowRushVariant", ShadowRushVariant.MODERN);
        unlimitedShadowPP = builder.comment("Shadow Pokemon can use their Shadow moves without spending PP, including moves already at zero PP. Ordinary moves and other move restrictions remain unchanged. Stored PP is preserved while enabled.")
                .translation("shadowedhearts.configuration.shadowBattleRules.unlimitedShadowPP")
                .define("unlimitedShadowPP", false);
        hyperShadowRushCrit = builder.comment("Give Shadow Rush used by a Shadow Pokemon in enabled Hyper Mode an extra 90% critical-hit roll, then the Generation III critical-stage roll (normally 1/16): 90.625% overall at the normal stage. Critical-hit prevention and the battle format's critical damage multiplier still apply.")
                .translation("shadowedhearts.configuration.shadowBattleRules.hyperShadowRushCrit")
                .define("hyperShadowRushCrit", false);
        hyperBehavior = builder.comment("MODERN preserves existing Hyper Mode behavior. COLOSSEUM restricts entry to player-controlled Shadow Rush and uses Nature-weighted disobedience, confusion self-damage, eligible held-item use and automatic switching.")
                .translation(key("hyperBehavior")).defineEnum("hyperBehavior", HyperBehavior.MODERN);
        reverseEntry = builder.comment("NATURE preserves existing Reverse Mode entry rates. XD_SPECIES uses the original XD species groups, species overrides and the configurable fallback for other species.")
                .translation(key("reverseEntry")).defineEnum("reverseEntry", ReverseEntry.NATURE);
        hyperItemRefusal = builder.comment("Shadow Pokemon in enabled Hyper Mode refuse ordinary directly applied items. Scents and healing facilities remain usable. Applies inside and outside battle.")
                .translation(key("hyperItemRefusal")).define("hyperItemRefusal", false);
        reverseItemRefusal = builder.comment("Shadow Pokemon in enabled Reverse Mode refuse ordinary directly applied items. Scents and healing facilities remain usable. Applies inside and outside battle.")
                .translation(key("reverseItemRefusal")).define("reverseItemRefusal", false);
        reverseGaugeBands = builder.comment("Remaining-gauge percentage boundaries for the six XD_SPECIES probability bands, high to low. Original species rates are known; this explicit configurable mapping uses a full-gauge gate of 99.9%, then 80/60/40/20%, repeating the final original rate in the last two bands.")
                .translation(key("reverseGaugeBands")).define("reverseGaugeBands", "99.9,80,60,40,20", value -> value instanceof String s && validBands(s));
        reverseFallback = builder.comment("Fallback percentages for XD_SPECIES entry, ordered from full to empty gauge using reverseGaugeBands. Six comma-separated percentages from 0 to 100.")
                .translation(key("reverseFallback")).define("reverseFallback", "0,10,15,20,30,30", value -> value instanceof String s && validRates(s));
        reverseSpeciesOverrides = builder.comment("XD_SPECIES overrides as species=rate5,rate4,rate3,rate2,rate1,rate0. Use normalized species IDs, e.g. pikachu=0,10,15,20,30,30. Exact form overrides precede base species; otherwise the original XD table or fallback applies.")
                .translation(key("reverseSpeciesOverrides")).defineListAllowEmpty("reverseSpeciesOverrides", List.of(), () -> "pikachu=0,10,15,20,30,30", value -> value instanceof String s && validOverride(s));
        builder.pop();
    }

    void bind(ModConfigSpec spec) { ownerSpec = spec; }
    private boolean loaded() { return ownerSpec != null && ownerSpec.isLoaded(); }
    public ShadowRushVariant shadowRushVariant() { return loaded() ? shadowRushVariant.get() : ShadowRushVariant.MODERN; }
    public boolean unlimitedShadowPP() { return loaded() && unlimitedShadowPP.get(); }
    public boolean hyperShadowRushCrit() { return loaded() && hyperShadowRushCrit.get(); }
    public HyperBehavior hyperBehavior() { return loaded() ? hyperBehavior.get() : HyperBehavior.MODERN; }
    public ReverseEntry reverseEntry() { return loaded() ? reverseEntry.get() : ReverseEntry.NATURE; }
    public boolean hyperItemRefusal() { return loaded() && hyperItemRefusal.get(); }
    public boolean reverseItemRefusal() { return loaded() && reverseItemRefusal.get(); }
    private static String key(String name) { return "shadowedhearts.configuration.shadowBattleRules." + name; }
    static boolean validRates(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 6) return false;
        try { for (String part : parts) { int rate = Integer.parseInt(part.trim()); if (rate < 0 || rate > 100) return false; } }
        catch (NumberFormatException ex) { return false; }
        return true;
    }
    static boolean validOverride(String value) {
        String[] parts = value.split("=", -1);
        return parts.length == 2 && parts[0].matches("[a-z0-9]+") && validRates(parts[1]);
    }
    static boolean validBands(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 5) return false;
        double previous = 101;
        try { for (String part : parts) { double n = Double.parseDouble(part.trim()); if (!Double.isFinite(n) || n <= 0 || n > 100 || n >= previous) return false; previous = n; } }
        catch (NumberFormatException ex) { return false; }
        return true;
    }
    private static List<Integer> rates(String value) {
        return java.util.Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }
    public String modeRulesJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hyperBehavior", hyperBehavior().name());
        result.put("reverseEntry", reverseEntry().name());
        result.put("reverseGaugeBands", java.util.Arrays.stream((loaded() ? reverseGaugeBands.get() : "99.9,80,60,40,20").split(",")).map(String::trim).map(Double::parseDouble).toList());
        result.put("reverseFallback", rates(loaded() ? reverseFallback.get() : "0,10,15,20,30,30"));
        Map<String, List<Integer>> overrides = new LinkedHashMap<>();
        if (loaded()) for (String row : reverseSpeciesOverrides.get()) {
            String[] parts = row.split("=", 2); overrides.put(parts[0], rates(parts[1]));
        }
        result.put("reverseSpeciesOverrides", overrides);
        return new Gson().toJson(result);
    }
}
