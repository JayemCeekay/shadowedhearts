package com.jayemceekay.shadowedhearts.npc;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit opt-in and deterministic move policy for the authored battle test fixtures. */
public final class BattleTestNpcGimmicks {
    private static final Map<String, String> GIMMICKS = Map.ofEntries(
            Map.entry("shadowedhearts:test_mega_evolution", "mega"),
            Map.entry("shadowedhearts:test_mega_evolution_shadow", "mega"),
            Map.entry("shadowedhearts:test_z_move", "zmove"),
            Map.entry("shadowedhearts:test_z_move_shadow", "zmove"),
            Map.entry("shadowedhearts:test_dynamax", "max"),
            Map.entry("shadowedhearts:test_dynamax_shadow", "max"),
            Map.entry("shadowedhearts:test_gigantamax", "max"),
            Map.entry("shadowedhearts:test_gigantamax_shadow", "max"),
            Map.entry("shadowedhearts:test_terastallization", "terastal"),
            Map.entry("shadowedhearts:test_terastallization_shadow", "terastal"),
            Map.entry("shadowedhearts:test_ultra_burst", "ultra"),
            Map.entry("shadowedhearts:test_ultra_burst_shadow", "ultra")
    );

    private BattleTestNpcGimmicks() {}

    public static Set<String> classIds() {
        return GIMMICKS.keySet();
    }

    public static String gimmickFor(String npcClassId) {
        return GIMMICKS.get(npcClassId);
    }

    public record Target(String pnx, boolean allied) {}

    public record Candidate(String baseMoveId, boolean baseUsable, int pp, boolean forced,
                            boolean variantUsable, boolean requiresTarget, List<Target> targets) {}

    public record Choice(String baseMoveId, String targetPnx, String gimmickId) {}

    /** Variant moves keep the base move ID: Showdown chooses Z/Max by its original move slot. */
    public static Choice selectMove(String gimmick, List<Candidate> candidates) {
        boolean variant = "zmove".equals(gimmick) || "max".equals(gimmick);
        for (Candidate candidate : candidates) {
            if (candidate.forced() || candidate.pp() <= 0) continue;
            if (variant ? !candidate.variantUsable() : !candidate.baseUsable()) continue;
            if (!candidate.requiresTarget()) return new Choice(candidate.baseMoveId(), null, gimmick);
            Target target = candidate.targets().stream().filter(value -> !value.allied()).findFirst()
                    .orElse(candidate.targets().isEmpty() ? null : candidate.targets().getFirst());
            if (target != null) return new Choice(candidate.baseMoveId(), target.pnx(), gimmick);
        }
        return null;
    }
}
