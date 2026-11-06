package com.jayemceekay.shadowedhearts.client.aura;

/** Client presentation budgets; changing modes only changes future births and pose sampling. */
public final class AuraPerformanceBudget {
    public static final AuraPerformanceBudget QUALITY = new AuraPerformanceBudget(false);
    public static final AuraPerformanceBudget PERFORMANCE = new AuraPerformanceBudget(true);

    private final boolean performance;

    private AuraPerformanceBudget(boolean performance) {
        this.performance = performance;
    }

    public static AuraPerformanceBudget forMode(boolean performance) {
        return performance ? PERFORMANCE : QUALITY;
    }

    public int captureAnchors(int fullCount, int minimum) {
        return performance ? Math.min(fullCount, Math.max(minimum, Math.round(fullCount * .75f))) : fullCount;
    }

    /** Primary body, appendage and tip emitters retain their existing budgets. */
    public int signatureFillCount(int fullCount) {
        return scaledCount(fullCount, .50f);
    }

    public int signatureFallbackCount(int fullCount) {
        return scaledCount(fullCount, .75f);
    }

    public float signatureDetailScale() {
        return performance ? .50f : 1f;
    }

    public double colosseumSmokeRate(double fullRate) {
        return performance ? fullRate * .70 : fullRate;
    }

    public double colosseumInfillRate(double fullRate) {
        return performance ? fullRate * .50 : fullRate;
    }

    public int xdCellTarget(int fullCount) {
        return scaledCount(fullCount, .60f);
    }

    public int xdHazeTarget(int fullCount) {
        return scaledCount(fullCount, .75f);
    }

    public double xdHazeRate(double fullRate) {
        return performance ? fullRate * .75 : fullRate;
    }

    public double accentRate(double fullRate) {
        return performance ? fullRate * .50 : fullRate;
    }

    private int scaledCount(int fullCount, float scale) {
        return performance && fullCount > 0 ? Math.max(1, Math.round(fullCount * scale)) : fullCount;
    }
}
