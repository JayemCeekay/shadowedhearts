package com.jayemceekay.shadowedhearts.client.gui.preset;

import com.google.gson.JsonObject;

/** Correlates replies and keeps uncertain writes locked until the server responds. */
final class ConfigSnapshotSession {
    private String scope = "client", selected = "", revision = "", pending = "";
    private int requestId = -1;
    private boolean timedOut;
    private JsonObject response;
    private String message = "message.shadowedhearts.snapshots.listed";

    String scope() { return scope; }
    String selected() { return selected; }
    String message() { return message; }
    JsonObject response() { return response; }
    boolean waiting() { return !pending.isEmpty(); }
    boolean writing() { return pending.equals("snapshot_create") || pending.equals("snapshot_restore") || pending.equals("snapshot_delete"); }
    boolean timedOut() { return timedOut; }
    boolean canRestore() { return !waiting() && !revision.isEmpty(); }

    void scope(String value) { if (!waiting()) { scope = value; selected = ""; revision = ""; response = null; } }
    void select(String value) { if (!waiting()) { selected = value; revision = ""; response = null; } }

    JsonObject begin(int id, String action, String name) {
        if (waiting() || action.equals("snapshot_restore") && !canRestore()) throw new IllegalStateException("Request unavailable");
        JsonObject request = new JsonObject();
        request.addProperty("requestId", id);
        request.addProperty("presetId", scope);
        request.addProperty("action", action);
        request.addProperty("id", selected);
        request.addProperty("name", name);
        request.addProperty("revision", revision);
        pending = action;
        requestId = id;
        revision = "";
        timedOut = false;
        message = "screen.shadowedhearts.snapshots.loading";
        return request;
    }

    boolean receive(JsonObject reply) {
        if (!waiting() || !reply.has("requestId") || reply.get("requestId").getAsInt() != requestId
                || !reply.has("presetId") || !scope.equals(reply.get("presetId").getAsString())) return false;
        String status = reply.get("status").getAsString();
        String expected = pending.equals("snapshot_preview") ? "preview" : pending.equals("snapshot_restore") ? "applied" : "listed";
        if (!status.equals("error") && !status.equals(expected)) return false;
        String nextRevision = status.equals("preview") ? reply.get("revision").getAsString() : "";
        if (status.equals("preview") && !nextRevision.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid revision");
        message = reply.get("messageKey").getAsString();
        response = reply;
        revision = nextRevision;
        pending = "";
        timedOut = false;
        return true;
    }

    void timeout() {
        if (!waiting()) return;
        timedOut = true;
        message = writing() ? "screen.shadowedhearts.snapshots.write_pending" : "screen.shadowedhearts.snapshots.read_timeout";
        if (!writing()) pending = "";
    }

    void failed() {
        message = "message.shadowedhearts.snapshots.failed";
        if (!writing()) pending = "";
        revision = "";
    }

    void disconnect() { pending = ""; revision = ""; response = null; message = "message.shadowedhearts.snapshots.unavailable"; }
}
