package com.jayemceekay.shadowedhearts.network.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Bounded transport errors, including requests rejected before the service's envelope validation. */
public final class RulesPresetReplies {
    private RulesPresetReplies() {}

    public static String error(String request, String messageKey) {
        JsonObject response = new JsonObject();
        try {
            if (shallowEnvelope(request)) {
                JsonObject input = JsonParser.parseString(request).getAsJsonObject();
                if (input.has("requestId") && input.get("requestId").isJsonPrimitive()
                        && input.getAsJsonPrimitive("requestId").isNumber()) {
                    int id = input.get("requestId").getAsBigDecimal().intValueExact();
                    if (id >= 0) response.addProperty("requestId", id);
                }
                if (input.has("presetId") && input.get("presetId").isJsonPrimitive()
                        && input.getAsJsonPrimitive("presetId").isString()) {
                    String id = input.get("presetId").getAsString();
                    if (id.length() <= 40) response.addProperty("presetId", id);
                }
            }
        } catch (RuntimeException invalid) { /* An invalid envelope needs no echoed payload. */ }
        response.addProperty("status", "error");
        response.addProperty("messageKey", messageKey);
        return response.toString();
    }

    private static boolean shallowEnvelope(String request) {
        if (request == null || request.length() > 4096) return false;
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < request.length(); i++) {
            char character = request.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            } else if (character == '"') quoted = true;
            else if (character == '{' || character == '[') { if (++depth > 4) return false; }
            else if (character == '}' || character == ']') depth--;
        }
        return true;
    }
}
