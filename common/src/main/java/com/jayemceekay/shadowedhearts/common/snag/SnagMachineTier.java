package com.jayemceekay.shadowedhearts.common.snag;

/** Benefits of the machine used to launch an armed snag ball. */
public enum SnagMachineTier {
    PROTOTYPE,
    ADVANCED;

    public SnagMachineSettings.TierSettings settings() {
        return SnagMachineSettings.serverCurrent().forTier(this);
    }

    public int nextEligibleTurn(int attemptTurn) {
        return settings().nextEligibleTurn(attemptTurn);
    }

    public double catchRateMultiplier() {
        return settings().catchMultiplier();
    }

    public int remainingGaugeAfterCapture(int currentGauge) {
        return settings().remainingGaugeAfterCapture(currentGauge);
    }
}
