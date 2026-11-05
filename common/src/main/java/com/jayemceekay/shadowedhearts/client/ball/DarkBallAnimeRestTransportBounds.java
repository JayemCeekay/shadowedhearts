package com.jayemceekay.shadowedhearts.client.ball;

/** Finite interval bounds for the axial map, including its clamped material-order tails. */
final class DarkBallAnimeRestTransportBounds {
    private static final int INTERVAL_COUNT = 64;

    private DarkBallAnimeRestTransportBounds() { }

    record Slopes(double side, double up) { }

    /** Bounds the transverse inverse-chart slopes jointly with their local axial derivative. */
    static Slopes transverseSlopes(double materialMinimum, double materialSpan,
                                   double sourceMinimum, double sourceMaximum, double targetAxial,
                                   double pull, double travelScale, double knownDerivativeMinimum,
                                   double targetSide, double targetUp, double bendSide, double bendUp,
                                   double radialRadius) {
        if (!Double.isFinite(materialMinimum) || !Double.isFinite(materialSpan) || materialSpan <= 0
                || !Double.isFinite(sourceMinimum) || !Double.isFinite(sourceMaximum) || sourceMinimum > sourceMaximum
                || !Double.isFinite(targetAxial) || !Double.isFinite(pull) || pull < 0 || pull > 1
                || !Double.isFinite(travelScale) || travelScale < 0 || travelScale > 1
                || !Double.isFinite(knownDerivativeMinimum) || knownDerivativeMinimum <= 0
                || !Double.isFinite(targetSide) || !Double.isFinite(targetUp)
                || !Double.isFinite(bendSide) || !Double.isFinite(bendUp)
                || !Double.isFinite(radialRadius) || radialRadius < 0)
            return new Slopes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        double materialMaximum = materialMinimum + materialSpan;
        if (!Double.isFinite(materialMaximum) || !(materialMaximum > materialMinimum))
            return new Slopes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        double overlapMinimum = Math.max(sourceMinimum, materialMinimum);
        double overlapMaximum = Math.min(sourceMaximum, materialMaximum);
        // Clamped q tails have constant travel, hence no transverse slope.
        if (overlapMinimum > overlapMaximum) return new Slopes(0, 0);
        double first = clamp((overlapMinimum - materialMinimum) / materialSpan);
        double last = clamp((overlapMaximum - materialMinimum) / materialSpan);
        double numerator = .2392 + .02 * pull;
        double side = 0, up = 0;
        for (int i = 0; i < INTERVAL_COUNT; i++) {
            double q0 = first + (last - first) * i / INTERVAL_COUNT;
            double q1 = first + (last - first) * (i + 1) / INTERVAL_COUNT;
            double l0 = local(q0, pull), l1 = local(q1, pull);
            double travel0 = travelScale * smooth(l0), travel1 = travelScale * smooth(l1);
            double slope0 = 6 * l0 * (1 - l0), slope1 = 6 * l1 * (1 - l1);
            double maximumSlope = l0 <= .5 && l1 >= .5 ? 1.5 : Math.max(slope0, slope1);
            double denominator0 = .54 - .02 * q0, denominator1 = .54 - .02 * q1;
            double primeMinimum = travelScale * Math.min(slope0, slope1) * numerator
                    / (denominator0 * denominator0 * materialSpan);
            double primeMaximum = travelScale * maximumSlope * numerator
                    / (denominator1 * denominator1 * materialSpan);
            double distanceMinimum = targetAxial - (materialMinimum + materialSpan * q1);
            double productMinimum = distanceMinimum * (distanceMinimum >= 0 ? primeMinimum : primeMaximum);
            double localMinimum = downward(1 - travel1 + productMinimum, Math.max(1, Math.abs(productMinimum)));
            // The caller supplies an independently certified positive global bound.
            // An interval may certify a substantially larger stretch derivative.
            double derivativeMinimum = Math.max(knownDerivativeMinimum, localMinimum);
            if (!Double.isFinite(derivativeMinimum) || derivativeMinimum <= 0 || !Double.isFinite(primeMaximum))
                return new Slopes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            double ratio = primeMaximum / derivativeMinimum;
            double bendSlope = 4 * Math.max(Math.abs(1 - 2 * travel0), Math.abs(1 - 2 * travel1));
            double widthSlope = Math.max(Math.abs(.2 - 2.4 * travel0), Math.abs(.2 - 2.4 * travel1));
            side = Math.max(side, ratio * (Math.abs(targetSide) + Math.abs(bendSide) * bendSlope + radialRadius * widthSlope));
            up = Math.max(up, ratio * (Math.abs(targetUp) + Math.abs(bendUp) * bendSlope + radialRadius * widthSlope));
        }
        if (!Double.isFinite(side) || !Double.isFinite(up))
            return new Slopes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        return new Slopes(upward(side), upward(up));
    }

    /**
     * Bounds d/da of a*(1-T(a)) + target*T(a) across the complete source chart.
     * Invalid or unrepresentable input returns negative infinity so callers can retain a
     * separately established fallback bound. No samples are used as a claimed minimum.
     */
    static double minimumDerivative(double materialMinimum, double materialSpan,
                                    double sourceMinimum, double sourceMaximum, double targetAxial,
                                    double pull, double travelScale) {
        if (!Double.isFinite(materialMinimum) || !Double.isFinite(materialSpan) || materialSpan <= 0
                || !Double.isFinite(sourceMinimum) || !Double.isFinite(sourceMaximum)
                || sourceMinimum > sourceMaximum || !Double.isFinite(targetAxial)
                || !Double.isFinite(pull) || pull < 0 || pull > 1
                || !Double.isFinite(travelScale) || travelScale < 0 || travelScale > 1)
            return Double.NEGATIVE_INFINITY;
        double materialMaximum = materialMinimum + materialSpan;
        if (!Double.isFinite(materialMaximum) || !(materialMaximum > materialMinimum))
            return Double.NEGATIVE_INFINITY;
        double minimum = Double.POSITIVE_INFINITY;
        // Outside q in [0,1], local progress is constant: its derivative is zero even
        // while that endpoint is travelling. These padded pieces must not inherit the
        // positive target-distance term belonging to the unclamped source interior.
        if (sourceMinimum <= materialMinimum)
            minimum = Math.min(minimum, downward(1 - travelScale * smooth(local(0, pull)), 1));
        if (sourceMaximum >= materialMaximum)
            minimum = Math.min(minimum, downward(1 - travelScale * smooth(local(1, pull)), 1));
        double overlapMinimum = Math.max(sourceMinimum, materialMinimum);
        double overlapMaximum = Math.min(sourceMaximum, materialMaximum);
        if (overlapMinimum <= overlapMaximum) {
            double first = clamp((overlapMinimum - materialMinimum) / materialSpan);
            double last = clamp((overlapMaximum - materialMinimum) / materialSpan);
            double numerator = .2392 + .02 * pull;
            for (int i = 0; i < INTERVAL_COUNT; i++) {
                double q0 = first + (last - first) * i / INTERVAL_COUNT;
                double q1 = first + (last - first) * (i + 1) / INTERVAL_COUNT;
                double l0 = local(q0, pull), l1 = local(q1, pull);
                double slope0 = 6 * l0 * (1 - l0), slope1 = 6 * l1 * (1 - l1);
                // 6*l*(1-l) is nonnegative and concave over [0,1]. Clamping to
                // local=0 or1 contributes zero slope, so crossing either threshold
                // cannot create an incorrectly positive lower derivative bound.
                double minimumSlope = Math.min(slope0, slope1);
                double maximumSlope = l0 <= .5 && l1 >= .5 ? 1.5 : Math.max(slope0, slope1);
                double denominator0 = .54 - .02 * q0, denominator1 = .54 - .02 * q1;
                double primeMinimum = travelScale * minimumSlope * numerator
                        / (denominator0 * denominator0 * materialSpan);
                double primeMaximum = travelScale * maximumSlope * numerator
                        / (denominator1 * denominator1 * materialSpan);
                double targetDistanceMinimum = targetAxial - (materialMinimum + materialSpan * q1);
                // The sign of target-a selects which derivative endpoint bounds its product.
                double productMinimum = targetDistanceMinimum
                        * (targetDistanceMinimum >= 0 ? primeMinimum : primeMaximum);
                double value = 1 - travelScale * smooth(l1) + productMinimum;
                if (!Double.isFinite(value)) return Double.NEGATIVE_INFINITY;
                minimum = Math.min(minimum, downward(value, Math.max(1, Math.abs(productMinimum))));
            }
        }
        return minimum;
    }

    private static double local(double order, double pull) {
        return clamp((pull - .46 + .46 * order) / (.54 - .02 * order));
    }

    private static double downward(double value, double magnitude) {
        // Bound arithmetic roundoff well below the render field's existing precision margin.
        return Math.nextDown(value - Math.ulp(magnitude) * 128);
    }

    private static double upward(double value) {
        return Math.nextUp(value + Math.ulp(Math.max(1, value)) * 128);
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double smooth(double value) { return value * value * (3 - 2 * value); }
}
