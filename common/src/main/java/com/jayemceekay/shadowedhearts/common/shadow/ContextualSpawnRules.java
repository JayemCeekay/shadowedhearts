package com.jayemceekay.shadowedhearts.common.shadow;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Predicate;

/** Ordered policy for automatic wild conversion; authored Pokemon properties are independent. */
public final class ContextualSpawnRules {
    private ContextualSpawnRules() { }

    public static boolean validIdentifier(Object value, boolean tagsAllowed) {
        if (!(value instanceof String text) || !text.equals(text.trim())) return false;
        if (text.startsWith("#")) {
            if (!tagsAllowed) return false;
            text = text.substring(1);
        }
        // Require namespaces so an accidental bare species name cannot resolve as minecraft:species.
        int separator = text.indexOf(':');
        return separator > 0 && separator < text.length() - 1 && ResourceLocation.tryParse(text) != null;
    }

    public static boolean validChanceOverride(Object value) {
        return value instanceof String text && parseOverride(text) != null;
    }

    public static boolean allows(List<? extends String> allowlist, List<? extends String> denylist,
                                 Predicate<String> matches) {
        return (allowlist.isEmpty() || allowlist.stream().anyMatch(matches))
                && denylist.stream().noneMatch(matches);
    }

    /** First matching override wins. Percentages replace the global base before the core multiplier. */
    public static double chance(double basePercent, List<? extends String> overrides, Predicate<String> matches) {
        for (String entry : overrides) {
            ChanceOverride rule = parseOverride(entry);
            if (rule != null && matches.test(rule.selector())) return rule.percent();
        }
        return basePercent;
    }

    private static ChanceOverride parseOverride(String text) {
        if (text == null) return null;
        int separator = text.indexOf('=');
        if (separator < 1 || separator != text.lastIndexOf('=')) return null;
        String selector = text.substring(0, separator).trim();
        if (!validIdentifier(selector, true)) return null;
        String amount = text.substring(separator + 1).trim();
        if (amount.endsWith("%")) amount = amount.substring(0, amount.length() - 1).trim();
        try {
            double percent = Double.parseDouble(amount);
            return Double.isFinite(percent) && percent >= 0 && percent <= 100
                    ? new ChanceOverride(selector, percent) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ChanceOverride(String selector, double percent) { }
}
