package com.jayemceekay.shadowedhearts.data;

import net.minecraft.network.chat.Component;

/** Keeps authoring diagnostics structured until the command/editor reaches its client. */
final class DefinitionMessages {
    private DefinitionMessages() {}

    static Component text(String key, Object... arguments) {
        // Paths and resource IDs are diagnostic data; encode them as text for the client packet.
        for (int i = 0; i < arguments.length; i++) {
            Object value = arguments[i];
            if (!(value instanceof Component || value instanceof String || value instanceof Number || value instanceof Boolean)) {
                arguments[i] = String.valueOf(value);
            }
        }
        return Component.translatable("message.shadowedhearts.definitions." + key, arguments);
    }
}
