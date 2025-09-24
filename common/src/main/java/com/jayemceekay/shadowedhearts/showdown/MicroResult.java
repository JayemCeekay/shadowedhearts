package com.jayemceekay.shadowedhearts.showdown;

/**
 * Result of a micro-battle run. MVP version stores raw JSON for downstream parsing.
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public final class MicroResult {
    private final String rawJson;

    public MicroResult(String rawJson) {
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    public String getRawJson() { return rawJson; }

    public static MicroResult fromJson(String json) {
        return new MicroResult(json);
    }

    @Override
    public String toString() {
        return rawJson;
    }
}
