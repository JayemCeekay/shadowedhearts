package com.jayemceekay.shadowedhearts.common.snag;

import com.jayemceekay.shadowedhearts.config.PresetCompatibilityConfig;

import java.util.Objects;

/** Immutable server rules, also used to describe both machines to connected clients. */
public record SnagMachineSettings(TierSettings prototype, TierSettings advanced) {
    public static final SnagMachineSettings DEFAULT = new SnagMachineSettings(
            new TierSettings(2, 1.0, 0.0), new TierSettings(1, 1.25, 10.0));

    public SnagMachineSettings {
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(advanced, "advanced");
    }

    public static SnagMachineSettings serverCurrent() { return PresetCompatibilityConfig.INSTANCE.snagMachineSettings(); }
    public TierSettings forTier(SnagMachineTier tier) { return tier == SnagMachineTier.ADVANCED ? advanced : prototype; }

    public record TierSettings(int attemptTurnSpacing, double catchMultiplier, double gaugeReductionPercent) {
        public TierSettings {
            attemptTurnSpacing = Math.clamp(attemptTurnSpacing, 0, 1000);
            catchMultiplier = Double.isFinite(catchMultiplier) ? Math.clamp(catchMultiplier, 0.0, 100.0) : 1.0;
            gaugeReductionPercent = Double.isFinite(gaugeReductionPercent)
                    ? Math.clamp(gaugeReductionPercent, 0.0, 100.0) : 0.0;
        }

        public int nextEligibleTurn(int attemptTurn) {
            return (int) Math.min(Integer.MAX_VALUE, (long) attemptTurn + attemptTurnSpacing);
        }

        public int remainingGaugeAfterCapture(int currentGauge) {
            int remaining = Math.max(0, currentGauge);
            long reduction = (long) Math.floor(remaining * gaugeReductionPercent / 100.0);
            return (int) Math.max(0, remaining - reduction);
        }
    }
}
