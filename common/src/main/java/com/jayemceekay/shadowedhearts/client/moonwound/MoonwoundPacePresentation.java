package com.jayemceekay.shadowedhearts.client.moonwound;

import static com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundText.tr;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Layer;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;

/** Soft pace targets and server-earned assistance. This presentation never awards a bonus. */
public final class MoonwoundPacePresentation {
    private MoonwoundPacePresentation() { }

    public static String targetLine(MoonwoundSnapshot snapshot) {
        if (snapshot.pace().targetTicks() == 0) return "";
        if (snapshot.pace().targetPassed()) return tr("gui.shadowedhearts.moonwound.pace.target_passed_label");
        return tr("gui.shadowedhearts.moonwound.pace.target_left", MoonwoundRecoveryPresentation.duration(snapshot.pace().targetTicks() - snapshot.pace().elapsedTicks()));
    }

    public static String nextBonusLine(MoonwoundSnapshot snapshot) {
        if (snapshot.pace().targetTicks() == 0 || snapshot.pace().targetPassed()) return tr("gui.shadowedhearts.moonwound.pace.no_penalty_for_taking_longer");
        if (snapshot.phase().isExcavation()) return tr("gui.shadowedhearts.moonwound.pace.quick_clear_25_party_recovery");
        if (snapshot.phase().isWave() && snapshot.phase().layer() == Layer.CASING) return tr("gui.shadowedhearts.moonwound.pace.quick_clear_overload_recovery");
        if (snapshot.phase().isWave()) return tr("gui.shadowedhearts.moonwound.pace.quick_clear_haste_i_for_60s");
        return "";
    }

    public static String bonusLine(MoonwoundSnapshot snapshot) {
        return switch (snapshot.pace().bonus()) {
            case NONE -> "";
            case COMBAT_HEAL -> snapshot.phase() == Phase.VOTE
                    ? tr("gui.shadowedhearts.moonwound.pace.overload_bonus_25_party_recovery") : tr("gui.shadowedhearts.moonwound.pace.earned_25_party_recovery");
            case MINING_HASTE -> tr("gui.shadowedhearts.moonwound.pace.earned_haste_i_for_60s");
        };
    }

    public static String bonusExplanation(MoonwoundSnapshot snapshot) {
        return switch (snapshot.pace().bonus()) {
            case NONE -> "";
            case COMBAT_HEAL -> snapshot.phase() == Phase.VOTE ? tr("gui.shadowedhearts.moonwound.pace.overload_explanation") : tr("gui.shadowedhearts.moonwound.pace.recovery_explanation");
            case MINING_HASTE -> tr("gui.shadowedhearts.moonwound.pace.haste_explanation");
        };
    }

    public static String targetExplanation(MoonwoundSnapshot snapshot) {
        if (snapshot.phase().isWave() && snapshot.phase().layer() == Layer.CASING) return tr("gui.shadowedhearts.moonwound.pace.target_final_wave");
        return snapshot.phase().isExcavation() ? tr("gui.shadowedhearts.moonwound.pace.target_excavation") : tr("gui.shadowedhearts.moonwound.pace.target_wave");
    }

    /** HUD has one compact suffix; full assistance conditions are available in the console. */
    public static String hudSuffix(MoonwoundSnapshot snapshot) {
        if (snapshot.pace().targetTicks() > 0) return snapshot.pace().targetPassed() ? tr("gui.shadowedhearts.moonwound.pace.target_passed")
                : tr("gui.shadowedhearts.moonwound.pace.hud_target", MoonwoundRecoveryPresentation.duration(snapshot.pace().targetTicks() - snapshot.pace().elapsedTicks()));
        return chatSuffix(snapshot);
    }

    /** Stable across every countdown tick, including the exact target boundary. */
    public static String chatSuffix(MoonwoundSnapshot snapshot) {
        String bonus = switch (snapshot.pace().bonus()) {
            case NONE -> "";
            case COMBAT_HEAL -> snapshot.phase() == Phase.VOTE ? tr("gui.shadowedhearts.moonwound.pace.recovery_if_overload_wins") : tr("gui.shadowedhearts.moonwound.pace.25_party_recovery_earned");
            case MINING_HASTE -> tr("gui.shadowedhearts.moonwound.pace.haste_i_earned");
        };
        if (!snapshot.pace().targetPassed()) return bonus;
        return bonus.isEmpty() ? tr("gui.shadowedhearts.moonwound.pace.target_passed_message") : tr("gui.shadowedhearts.moonwound.pace.target_passed_with_bonus", bonus);
    }
}
