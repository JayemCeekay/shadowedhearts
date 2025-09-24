package com.jayemceekay.shadowedhearts.showdown;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Parameters for a single-action micro-battle.
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public final class MicroRequest {
    private final String formatId; // default: gen9micro
    private final int[] seed;      // 4-int PRNG seed
    private final String attackerSetJson; // Showdown Set JSON object (single mon)
    private final String defenderSetJson; // Showdown Set JSON object (single mon)
    private final String move;            // move id or name usable by PS
    private final String envJson;         // optional env object {weather, terrain}

    private MicroRequest(Builder b) {
        this.formatId = b.formatId == null ? "gen9micro" : b.formatId;
        this.seed = b.seed == null ? new int[]{1,2,3,4} : b.seed;
        this.attackerSetJson = b.attackerSetJson == null ? "{}" : b.attackerSetJson;
        this.defenderSetJson = b.defenderSetJson == null ? "{}" : b.defenderSetJson;
        this.move = Objects.requireNonNullElse(b.move, "");
        this.envJson = b.envJson == null ? null : b.envJson;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"formatid\":\"").append(escape(formatId)).append('\"');
        sb.append(",\"seed\":[").append(Arrays.stream(seed).mapToObj(String::valueOf).collect(Collectors.joining(","))).append(']');
        sb.append(",\"move\":\"").append(escape(move)).append('\"');
        sb.append(",\"attacker\":").append(attackerSetJson);
        sb.append(",\"defender\":").append(defenderSetJson);
        if (envJson != null) {
            sb.append(",\"env\":").append(envJson);
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String formatId;
        private int[] seed;
        private String attackerSetJson;
        private String defenderSetJson;
        private String move;
        private String envJson;
        public Builder formatId(String v) { this.formatId = v; return this; }
        public Builder seed(int a, int b, int c, int d) { this.seed = new int[]{a,b,c,d}; return this; }
        public Builder seed(int[] s) { this.seed = s; return this; }
        public Builder attackerSetJson(String json) { this.attackerSetJson = json; return this; }
        public Builder defenderSetJson(String json) { this.defenderSetJson = json; return this; }
        public Builder move(String v) { this.move = v; return this; }
        public Builder envJson(String json) { this.envJson = json; return this; }
        public MicroRequest build() { return new MicroRequest(this); }
    }
}
