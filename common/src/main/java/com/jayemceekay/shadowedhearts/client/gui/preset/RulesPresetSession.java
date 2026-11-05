package com.jayemceekay.shadowedhearts.client.gui.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** A preview is permission to request one application of that exact server revision, never a mode. */
final class RulesPresetSession {
    enum Pending { NONE, PREVIEW, APPLY }
    record Row(String path, String labelKey, String currentValue, String proposedValue, boolean changed) {}
    record Preview(String revision, String source, List<Row> rows) {
        Preview { rows = List.copyOf(rows); }
        long changedCount() { return rows.stream().filter(Row::changed).count(); }
    }

    private String presetId;
    private Pending pending = Pending.NONE;
    private int requestId = -1;
    private Preview preview;
    private String status = "";
    private String messageKey = "screen.shadowedhearts.rules_presets.choose";
    private boolean timedOut;

    RulesPresetSession(String presetId) { this.presetId = presetId; }

    String presetId() { return presetId; }
    Pending pending() { return pending; }
    Preview preview() { return preview; }
    String status() { return status; }
    String messageKey() { return messageKey; }
    boolean timedOut() { return timedOut; }
    boolean waiting() { return pending != Pending.NONE; }
    boolean canApply() {
        return !waiting() && "preview".equals(status) && preview != null
                && !preview.revision().isBlank() && preview.changedCount() > 0;
    }

    void select(String selected) {
        if (waiting()) throw new IllegalStateException("A preset request is still pending.");
        presetId = selected;
        invalidate();
        messageKey = "screen.shadowedhearts.rules_presets.choose";
    }

    JsonObject beginPreview(int id) {
        if (waiting()) throw new IllegalStateException("A preset request is still pending.");
        invalidate();
        return begin(id, Pending.PREVIEW, "");
    }

    JsonObject beginApply(int id) {
        if (!canApply()) throw new IllegalStateException("Review a current server preview first.");
        return begin(id, Pending.APPLY, preview.revision());
    }

    private JsonObject begin(int id, Pending action, String revision) {
        pending = action;
        requestId = id;
        timedOut = false;
        messageKey = action == Pending.APPLY ? "screen.shadowedhearts.rules_presets.applying"
                : "screen.shadowedhearts.rules_presets.loading";
        JsonObject request = new JsonObject();
        request.addProperty("requestId", id);
        request.addProperty("action", action == Pending.APPLY ? "apply" : "preview");
        request.addProperty("presetId", presetId);
        request.addProperty("revision", revision);
        return request;
    }

    /** Ignore late/different-selection replies before touching any reviewed state. */
    boolean receive(JsonObject response) {
        if (!waiting() || !response.has("requestId") || response.get("requestId").getAsInt() != requestId
                || !presetId.equals(text(response, "presetId"))) return false;
        String result = text(response, "status");
        if (!result.equals("error") && !(pending == Pending.PREVIEW && result.equals("preview"))
                && !(pending == Pending.APPLY && result.equals("applied"))) return false;
        Preview received = null;
        if (!result.equals("error")) {
            String revision = text(response, "revision");
            if (result.equals("preview") && revision.isBlank()) throw new IllegalArgumentException("Missing preview revision.");
            List<Row> rows = new ArrayList<>();
            for (JsonElement entry : response.getAsJsonArray("rows")) {
                JsonObject row = entry.getAsJsonObject();
                rows.add(new Row(text(row, "path"), text(row, "labelKey"), text(row, "currentValue"),
                        text(row, "proposedValue"), row.get("changed").getAsBoolean()));
            }
            received = new Preview(revision, text(response, "source"), rows);
        }
        pending = Pending.NONE;
        timedOut = false;
        preview = received;
        status = result;
        messageKey = text(response, "messageKey");
        if (messageKey.isBlank()) messageKey = "screen.shadowedhearts.rules_presets." + result;
        return true;
    }

    /** A read can be retried. An uncertain write stays locked until its actual response arrives. */
    void timeout() {
        if (!waiting() || timedOut) return;
        timedOut = true;
        messageKey = pending == Pending.APPLY ? "screen.shadowedhearts.rules_presets.apply_pending"
                : "screen.shadowedhearts.rules_presets.preview_timeout";
        if (pending == Pending.PREVIEW) {
            pending = Pending.NONE;
            preview = null;
            status = "error";
        }
    }

    void responseFailed() {
        messageKey = "screen.shadowedhearts.rules_presets.invalid_response";
        if (pending == Pending.PREVIEW) {
            pending = Pending.NONE;
            preview = null;
            status = "error";
        }
    }

    void disconnect() {
        pending = Pending.NONE;
        invalidate();
        messageKey = "screen.shadowedhearts.rules_presets.world_hint";
    }

    private void invalidate() {
        preview = null;
        status = "";
        requestId = -1;
        timedOut = false;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }
}
