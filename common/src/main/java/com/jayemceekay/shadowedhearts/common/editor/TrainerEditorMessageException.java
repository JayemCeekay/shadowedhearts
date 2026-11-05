package com.jayemceekay.shadowedhearts.common.editor;

import net.minecraft.network.chat.Component;

/** Keeps a user-facing failure translatable across the editor's JSON response boundary. */
public class TrainerEditorMessageException extends IllegalArgumentException {
    private final Component message;

    public TrainerEditorMessageException(Component message) {
        this(message, null);
    }

    public TrainerEditorMessageException(Component message, Throwable cause) {
        super(message.getString(), cause);
        this.message = message;
    }

    public static IllegalStateException state(Component message) { return new State(message, null); }
    public static IllegalStateException state(Component message, Throwable cause) { return new State(message, cause); }

    public static Component component(Throwable failure) {
        if (failure instanceof TrainerEditorMessageException localized) return localized.message;
        if (failure instanceof State localized) return localized.message;
        String detail = failure.getMessage();
        return detail == null ? TrainerEditorMessages.message("request_failed")
                : TrainerEditorMessages.message("validation_detail", detail);
    }

    private static final class State extends IllegalStateException {
        private final Component message;
        private State(Component message, Throwable cause) {
            super(message.getString(), cause);
            this.message = message;
        }
    }
}
