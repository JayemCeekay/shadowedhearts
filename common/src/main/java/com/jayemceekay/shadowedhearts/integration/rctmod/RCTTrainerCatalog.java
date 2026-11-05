package com.jayemceekay.shadowedhearts.integration.rctmod;

import java.util.List;
import net.minecraft.network.chat.Component;

/** A copied, server-owned menu snapshot with no optional-mod or client types in its wire format. */
public record RCTTrainerCatalog(boolean available, boolean loading, List<Entry> trainers,
                                List<Entry> types, List<Component> diagnostics) {
    public RCTTrainerCatalog {
        trainers = List.copyOf(trainers);
        types = List.copyOf(types);
        diagnostics = bounded(diagnostics, 16);
    }

    public record Entry(String id, String name, String type, String sourcePack,
                        List<Component> details, List<Component> diagnostics, Component displayName) {
        public Entry(String id, String name, String type, String sourcePack,
                     List<Component> details, List<Component> diagnostics) {
            this(id, name, type, sourcePack, details, diagnostics, null);
        }

        public Entry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Catalog ID is required.");
            // IDs remain exact registry keys: labels and descriptive text may be abbreviated, IDs may not.
            name = text(name == null || name.isBlank() ? id : name, 160);
            displayName = displayName == null ? Component.literal(name) : displayName.copy();
            type = type == null ? "" : type;
            sourcePack = text(sourcePack == null ? "" : sourcePack, 256);
            details = details.stream().filter(java.util.Objects::nonNull).limit(10).map(Component::copy).map(value -> (Component) value).toList();
            diagnostics = bounded(diagnostics, 8);
        }
    }

    public static RCTTrainerCatalog unavailable(List<Component> diagnostics) {
        return new RCTTrainerCatalog(false, false, List.of(), List.of(), diagnostics);
    }

    RCTTrainerCatalog withStatus(boolean loading, Component diagnostic) {
        var messages = new java.util.ArrayList<>(diagnostics);
        if (diagnostic != null && !messages.contains(diagnostic)) messages.add(0, diagnostic);
        return new RCTTrainerCatalog(available, loading, trainers, types, messages);
    }

    private static List<Component> bounded(List<Component> values, int count) {
        // Keep components intact until the receiving client resolves its language.
        return values.stream().filter(java.util.Objects::nonNull).limit(count)
                .map(Component::copy).map(value -> (Component) value).toList();
    }

    private static String text(String value, int length) {
        if (value.length() <= length) return value;
        int end = length - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }
}
