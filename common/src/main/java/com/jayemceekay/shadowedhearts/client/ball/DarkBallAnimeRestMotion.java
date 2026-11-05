package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

/** Invertible elastic source envelope with a finite regional release boundary. */
final class DarkBallAnimeRestMotion {
    static final double REST_LOCAL_LIMIT = .58;
    static final double STREAM_SCALE_END = .85;
    // Keep thin limbs and characteristic gaps readable while the outer contour
    // rolls. The native nine-view sweep accepts .35; this leaves a small margin.
    private static final double ELASTIC_STRENGTH = .33;
    private static final double MINIMUM_AXIAL_DERIVATIVE = .35;
    private static final int INVERSE_STEPS = 24;

    private DarkBallAnimeRestMotion() { }

    /** Smooth, independently modulated phases shared by CPU bounds and the shader. */
    record Phases(float uAxial, float uCross, float vAxial, float vCross,
                  float squeezeAngle, float strain) { }

    record Frame(DarkBallAnimeRestVolume volume, Vec3 minimum, Vec3 maximum,
                 Vec3 center, Vec3 direction, Vec3 side, Vec3 up, Vec3 target, Vec3 bend,
                 float radius, float axialMinimum, float axialSpan, float pull,
                 float strength, float time, float squash, float inverseLipschitz,
                 float inflation, float travelScale, float radialDomainRadius,
                 float restAxialMaximum, float derivativeMinimum, float minimumWidth,
                 float sideSlope, float upSlope, Phases phases, boolean active) {
        float restAxialMinimum() {
            return (float) (projectedRange(volume.minimum(), volume.maximum(), center, direction)[0] - radius * .002);
        }
    }

    static Frame frame(DarkBallAnimeMotion.Frame motion, float activation, DarkBallAnimeRestVolume volume) {
        if (motion == null || volume == null || !Float.isFinite(activation))
            throw new IllegalArgumentException("Missing rest volume or invalid activation");
        var binding = motion.binding();
        Vec3 direction = binding.direction();
        Vec3 hint = Math.abs(direction.z) < .95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 up = unit(hint.subtract(direction.scale(hint.dot(direction))));
        Vec3 side = direction.cross(up);
        double radius = binding.radius();
        double strength = ELASTIC_STRENGTH * clamp(activation) * motion.lobeBlend();
        Phases phases = phases((float) strength, motion.timeSeconds(), motion.squash());
        double[] axial = projectedRange(volume.minimum(), volume.maximum(), binding.center(), direction);
        double materialMinimum = axial[0];
        double materialSpan = Math.max(axial[1] - axial[0], radius * .0001);
        double[] radialSide = projectedRange(volume.minimum(), volume.maximum(), binding.center(), side);
        double[] radialUp = projectedRange(volume.minimum(), volume.maximum(), binding.center(), up);
        double strain = phases.strain();
        double expansion = Math.exp(Math.abs(strain));
        // Both triangular inverse shears have exact reverse maps. These bounds
        // enclose their largest offsets, followed by the reciprocal squeeze.
        double uBound = Math.max(Math.abs(radialSide[0]), Math.abs(radialSide[1])) + radius * .28 * strength;
        double vBound = Math.max(Math.abs(radialUp[0]), Math.abs(radialUp[1])) + radius * .26 * strength;
        double squeezeCosine = Math.cos(phases.squeezeAngle()), squeezeSine = Math.sin(phases.squeezeAngle());
        double squeezeForward = Math.exp(strain), squeezeInverse = Math.exp(-strain);
        double s00 = squeezeCosine * squeezeCosine * squeezeForward + squeezeSine * squeezeSine * squeezeInverse;
        double s11 = squeezeSine * squeezeSine * squeezeForward + squeezeCosine * squeezeCosine * squeezeInverse;
        double s01 = squeezeCosine * squeezeSine * (squeezeForward - squeezeInverse);
        double radial = Math.max(Math.abs(s00) * uBound + Math.abs(s01) * vBound,
                Math.abs(s01) * uBound + Math.abs(s11) * vBound) + radius * .002;
        double frontOrder = (REST_LOCAL_LIMIT * .54 + .46 - motion.pullProgress())
                / (.46 + .02 * REST_LOCAL_LIMIT);
        double front = Math.min(axial[1] + radius * .002,
                materialMinimum + materialSpan * frontOrder);
        double axialLow = axial[0] - radius * .002;
        double targetAxial = motion.target().subtract(binding.center()).dot(direction);
        double localMaximum = Math.min(REST_LOCAL_LIMIT, motion.pullProgress() / .52);
        double tMaximum = smooth(localMaximum);
        double smoothSlope = localMaximum < .5 ? 6 * localMaximum * (1 - localMaximum) : 1.5;
        double tPrimeMaximum = smoothSlope * (.2392 + .02 * motion.pullProgress())
                / (.52 * .52 * materialSpan);
        double behind = Math.max(0, front - targetAxial);
        double travelScale = Math.min(1, (1 - MINIMUM_AXIAL_DERIVATIVE)
                / Math.max(tMaximum + behind * tPrimeMaximum, 1e-10));
        double minimumWidth = Math.min(1, width(tMaximum * travelScale));
        double derivativeMinimum = 1 - travelScale * tMaximum - travelScale * behind * tPrimeMaximum;
        // Positive axial stretch towards a leading ball offsets the radial
        // taper. Certify that benefit over intervals, including clamped tails,
        // without changing the already bounded transport itself.
        derivativeMinimum = Math.max(derivativeMinimum,
                DarkBallAnimeRestTransportBounds.minimumDerivative(materialMinimum, materialSpan,
                        axialLow, front, targetAxial, motion.pullProgress(), travelScale));
        derivativeMinimum = Math.nextDown((float) derivativeMinimum) * .999999;
        double ratio = travelScale * tPrimeMaximum / derivativeMinimum;
        if (targetAxial > front) ratio = Math.min(ratio, 1 / (targetAxial - front));
        Vec3 targetRadial = motion.target().subtract(binding.center()).subtract(direction.scale(targetAxial));
        double widthDerivative = Math.max(.2, Math.abs(.2 - 2.4 * tMaximum * travelScale));
        double kSide = ratio * (Math.abs(targetRadial.dot(side)) + 4 * Math.abs(binding.bend().dot(side))
                + radial * widthDerivative);
        double kUp = ratio * (Math.abs(targetRadial.dot(up)) + 4 * Math.abs(binding.bend().dot(up))
                + radial * widthDerivative);
        // Bow velocity, taper and axial stretch peak at different material
        // orders. Bound their correlated products over finite axial intervals.
        var slopes = DarkBallAnimeRestTransportBounds.transverseSlopes(materialMinimum, materialSpan,
                axialLow, front, targetAxial, motion.pullProgress(), travelScale, derivativeMinimum,
                targetRadial.dot(side), targetRadial.dot(up), binding.bend().dot(side),
                binding.bend().dot(up), radial);
        kSide = Math.nextUp((float) Math.min(kSide, slopes.side())) * 1.000001;
        kUp = Math.nextUp((float) Math.min(kUp, slopes.up())) * 1.000001;
        double transverseSlope = Math.hypot(kSide, kUp);
        double inverseTransport = Math.min(Math.sqrt(1 / (derivativeMinimum * derivativeMinimum)
                        + (2 + transverseSlope * transverseSlope) / (minimumWidth * minimumWidth)),
                Math.max(1 / derivativeMinimum, 1 / minimumWidth) + transverseSlope / minimumWidth);
        double warpLipschitz = expansion * shearNorm(strength * Math.hypot(.72, .30))
                * shearNorm(strength * Math.hypot(.684, .24));
        // The clamped source and chart constraints are independently normalized
        // and intersected below. Their derivatives need not be added together.
        float inverseLipschitz = (float) (warpLipschitz * inverseTransport * 1.0001);
        boolean active = motion.pullProgress() < .46 + .54 * REST_LOCAL_LIMIT && front > axialLow;
        Frame parameters = new Frame(volume, Vec3.ZERO, Vec3.ZERO, binding.center(), direction, side, up,
                motion.target(), binding.bend(), (float) radius, (float) materialMinimum,
                (float) materialSpan, motion.pullProgress(), (float) strength, motion.timeSeconds(),
                motion.squash(), inverseLipschitz, 0, (float) travelScale, (float) radial, (float) front,
                (float) derivativeMinimum, (float) minimumWidth, (float) kSide, (float) kUp, phases, active);
        if (!active) return withBounds(parameters, motion.target(), motion.target());
        double lowAxial = transportedAxial(parameters, axialLow);
        double highAxial = transportedAxial(parameters, front);
        double maximumWidth = 1.0 + 1.0 / 120.0;
        double maximumBend = tMaximum * travelScale < .5
                ? 4 * tMaximum * travelScale * (1 - tMaximum * travelScale) : 1;
        double sideExtent = Math.abs(targetRadial.dot(side)) * tMaximum * travelScale
                + Math.abs(binding.bend().dot(side)) * maximumBend + radial * maximumWidth;
        double upExtent = Math.abs(targetRadial.dot(up)) * tMaximum * travelScale
                + Math.abs(binding.bend().dot(up)) * maximumBend + radial * maximumWidth;
        Vec3 low = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 high = low.scale(-1);
        for (int corner = 0; corner < 8; corner++) {
            Vec3 point = binding.center().add(direction.scale((corner & 1) == 0 ? lowAxial : highAxial))
                    .add(side.scale((corner & 2) == 0 ? -sideExtent : sideExtent))
                    .add(up.scale((corner & 4) == 0 ? -upExtent : upExtent));
            low = min(low, point);
            high = max(high, point);
        }
        double margin = Math.max(radius * .0001, Math.max(maxUlp(low), maxUlp(high)) * 4);
        return withBounds(parameters, low.add(-margin, -margin, -margin), high.add(margin, margin, margin));
    }

    private static Frame withBounds(Frame f, Vec3 minimum, Vec3 maximum) {
        return new Frame(f.volume, minimum, maximum, f.center, f.direction, f.side, f.up,
                f.target, f.bend, f.radius, f.axialMinimum, f.axialSpan, f.pull, f.strength,
                f.time, f.squash, f.inverseLipschitz, f.inflation, f.travelScale,
                f.radialDomainRadius, f.restAxialMaximum, f.derivativeMinimum, f.minimumWidth,
                f.sideSlope, f.upSlope, f.phases, f.active);
    }

    private static Phases phases(float strength, float time, float squash) {
        double t = time;
        // Time-only phase offsets preserve the existing displacement and spatial
        // derivative limits. Opposed axial waves and reversing cross folds vary
        // independently instead of following one uniformly rotating cycle.
        return new Phases(
                (float) (-6.4 * t + .8 * Math.sin(3.7 * t + .25)),
                (float) (2.1 * t + 1.05 * Math.sin(5.3 * t + .7)),
                (float) (5.7 * t + 1.1 + .95 * Math.sin(3.1 * t + 2)),
                (float) (-2.5 * t + .8 * Math.sin(4.7 * t + 1.4)),
                (float) (1.7 * t + .30 * Math.sin(4.1 * t + .6)),
                (float) (strength * (.19 * Math.sin(6.1 * t + .65 * Math.sin(3.3 * t + 1.2))
                        + .21 * squash)));
    }

    static double sample(Frame f, Vec3 point) {
        return sampleWithDivisor(f, point, f == null ? 1 : f.inverseLipschitz);
    }

    static double sampleAlongRay(Frame f, Vec3 point, Vec3 unitDirection) {
        return sampleWithDivisor(f, point, directionalLipschitz(f, unitDirection));
    }

    /** Constant for the whole ray; this is not a spatial distance-field bound. */
    static double directionalLipschitz(Frame f, Vec3 unitDirection) {
        if (f == null) return 1;
        double aRay = Math.abs(unitDirection.dot(f.direction));
        double uRay = Math.abs(unitDirection.dot(f.side));
        double vRay = Math.abs(unitDirection.dot(f.up));
        double strain = f.phases.strain();
        double c = Math.cos(f.phases.squeezeAngle()), s = Math.sin(f.phases.squeezeAngle());
        double forward = Math.exp(strain), inverse = Math.exp(-strain);
        double s00 = c*c*inverse + s*s*forward, s11 = s*s*inverse + c*c*forward;
        double s01 = c*s*(inverse-forward);
        // Every chart clamp can only remove a derivative component. The full
        // unclamped chain therefore bounds all eight states of the inverse.
        double a = aRay / f.derivativeMinimum;
        double u = (uRay + f.sideSlope * aRay) / f.minimumWidth;
        double v = (vRay + f.upSlope * aRay) / f.minimumWidth;
        double squeezedU = Math.abs(s00)*u + Math.abs(s01)*v;
        double squeezedV = Math.abs(s01)*u + Math.abs(s11)*v;
        double shearedU = squeezedU + f.strength*(.72*a + .30*squeezedV);
        double shearedV = squeezedV + f.strength*(.684*a + .24*shearedU);
        double source = Math.sqrt(a*a + shearedU*shearedU + shearedV*shearedV);
        return Math.min(f.inverseLipschitz, source*1.0001);
    }

    private static double sampleWithDivisor(Frame f, Vec3 point, double divisor) {
        if (f == null || !f.active) return Double.POSITIVE_INFINITY;
        double sourceLow = f.restAxialMinimum();
        double outputAxial = point.subtract(f.center).dot(f.direction);
        double low = sourceLow, high = f.restAxialMaximum;
        for (int i = 0; i < INVERSE_STEPS; i++) {
            double middle = (low + high) * .5;
            if (transportedAxial(f, middle) < outputAxial) low = middle;
            else high = middle;
        }
        double axial = (low + high) * .5;
        double travel = smooth(localProgress(f, axial)) * f.travelScale;
        double width = width(travel);
        Vec3 curve = curve(f, axial, travel);
        Vec3 difference = point.subtract(curve);
        double u = Math.max(-f.radialDomainRadius, Math.min(f.radialDomainRadius, difference.dot(f.side) / width));
        double v = Math.max(-f.radialDomainRadius, Math.min(f.radialDomainRadius, difference.dot(f.up) / width));
        Vec3 rest = inverseElastic(f, f.center.add(f.direction.scale(axial)).add(f.side.scale(u)).add(f.up.scale(v)));
        double sourceField = f.volume.sample(rest);
        // Every saturated band and the full field are proven signed distance
        // bounds with the same zero set. Keep the strongest magnitude: narrow
        // bands improve convergence while wider bands cross empty gaps faster.
        double bandField = Math.min(Math.abs(sourceField), f.volume.distanceBand())
                / f.volume.distanceBandGradient();
        Vec3 bands = f.volume.distanceOuterBands(), gradients = f.volume.distanceOuterBandGradients();
        bandField = Math.max(bandField, Math.min(Math.abs(sourceField), bands.x) / gradients.x);
        bandField = Math.max(bandField, Math.min(Math.abs(sourceField), bands.y) / gradients.y);
        bandField = Math.max(bandField, Math.min(Math.abs(sourceField), bands.z) / gradients.z);
        sourceField = Math.copySign(Math.max(Math.abs(sourceField), bandField), sourceField);
        double field = (sourceField - f.inflation) / divisor;
        // The padded radial and lower chart boundaries enclose all physical
        // support. Intersecting their signed constraints preserves the source
        // zero set, while the upper plane performs the intended regional cut.
        double sideBoundary = (Math.abs(difference.dot(f.side)) - f.radialDomainRadius * width)
                / Math.sqrt(1 + f.sideSlope * f.sideSlope);
        double upBoundary = (Math.abs(difference.dot(f.up)) - f.radialDomainRadius * width)
                / Math.sqrt(1 + f.upSlope * f.upSlope);
        double axialBoundary = Math.max(transportedAxial(f, sourceLow) - outputAxial,
                outputAxial - transportedAxial(f, f.restAxialMaximum));
        return Math.max(Math.max(Math.max(field, sideBoundary), Math.max(upBoundary, axialBoundary)),
                boxDistance(point, f.minimum, f.maximum));
    }

    static Vec3 forward(Frame f, Vec3 source) {
        Vec3 elastic = forwardElastic(f, source);
        double axial = elastic.subtract(f.center).dot(f.direction);
        double travel = smooth(localProgress(f, axial)) * f.travelScale;
        Vec3 radial = elastic.subtract(f.center).subtract(f.direction.scale(axial));
        return curve(f, axial, travel).add(radial.scale(width(travel)));
    }

    static Vec3 streamPoint(Frame f, Vec3 source) {
        Vec3 elastic = forwardElastic(f, source);
        double axial = elastic.subtract(f.center).dot(f.direction);
        double travel = streamTravel(f, axial);
        Vec3 radial = elastic.subtract(f.center).subtract(f.direction.scale(axial));
        return curve(f, axial, travel).add(radial.scale(width(travel)));
    }

    static double streamWidth(Frame f, Vec3 source) {
        double axial = source.subtract(f.center).dot(f.direction);
        return width(streamTravel(f, axial));
    }

    private static double streamTravel(Frame f, double axial) {
        double local = localProgress(f, axial);
        // Keep this interpolation in double precision. Float (1-scale) can
        // round up, overshoot the ball, and give a negative fractional-power
        // tube width in the final subpixel approach.
        double scale = f.travelScale + (1.0 - f.travelScale) * smooth(clamp((local - REST_LOCAL_LIMIT)
                / (STREAM_SCALE_END - REST_LOCAL_LIMIT)));
        return clamp(smooth(local) * scale);
    }

    static double localProgress(Frame f, double sourceAxial) {
        double order = clamp((sourceAxial - f.axialMinimum) / f.axialSpan);
        double lag = 1 - order;
        return clamp((f.pull - .46 * lag) / (.52 + .02 * lag));
    }

    static double transportedAxial(Frame f, double sourceAxial) {
        double travel = smooth(localProgress(f, sourceAxial)) * f.travelScale;
        return sourceAxial * (1 - travel) + f.target.subtract(f.center).dot(f.direction) * travel;
    }

    private static Vec3 curve(Frame f, double axial, double travel) {
        return f.center.add(f.direction.scale(axial)).scale(1 - travel).add(f.target.scale(travel))
                .add(f.bend.scale(4 * travel * (1 - travel)));
    }

    static Vec3 inverseElastic(Frame f, Vec3 point) {
        Vec3 relative = point.subtract(f.center);
        double axial = relative.dot(f.direction), u = relative.dot(f.side), v = relative.dot(f.up);
        double angle = f.phases.squeezeAngle();
        double strain = f.phases.strain();
        double c = Math.cos(angle), s = Math.sin(angle);
        double x = (u * c + v * s) * Math.exp(-strain);
        double y = (-u * s + v * c) * Math.exp(strain);
        u = x * c - y * s;
        v = x * s + y * c;
        u += f.radius * f.strength * (.18 * Math.sin(4 * axial / f.radius + f.phases.uAxial())
                + .10 * Math.sin(3 * v / f.radius + f.phases.uCross()));
        v += f.radius * f.strength * (.18 * Math.sin(3.8 * axial / f.radius + f.phases.vAxial())
                + .08 * Math.sin(3 * u / f.radius + f.phases.vCross()));
        return f.center.add(f.direction.scale(axial)).add(f.side.scale(u)).add(f.up.scale(v));
    }

    private static Vec3 forwardElastic(Frame f, Vec3 point) {
        Vec3 relative = point.subtract(f.center);
        double axial = relative.dot(f.direction), u = relative.dot(f.side), v = relative.dot(f.up);
        v -= f.radius * f.strength * (.18 * Math.sin(3.8 * axial / f.radius + f.phases.vAxial())
                + .08 * Math.sin(3 * u / f.radius + f.phases.vCross()));
        u -= f.radius * f.strength * (.18 * Math.sin(4 * axial / f.radius + f.phases.uAxial())
                + .10 * Math.sin(3 * v / f.radius + f.phases.uCross()));
        double angle = f.phases.squeezeAngle();
        double strain = f.phases.strain();
        double c = Math.cos(angle), s = Math.sin(angle);
        double x = (u * c + v * s) * Math.exp(strain);
        double y = (-u * s + v * c) * Math.exp(-strain);
        return f.center.add(f.direction.scale(axial)).add(f.side.scale(x * c - y * s))
                .add(f.up.scale(x * s + y * c));
    }

    private static double[] projectedRange(Vec3 minimum, Vec3 maximum, Vec3 center, Vec3 direction) {
        Vec3 middle = minimum.add(maximum).scale(.5).subtract(center);
        Vec3 half = maximum.subtract(minimum).scale(.5);
        double extent = Math.abs(direction.x) * half.x + Math.abs(direction.y) * half.y + Math.abs(direction.z) * half.z;
        double projected = middle.dot(direction);
        return new double[]{projected - extent, projected + extent};
    }
    private static double maxUlp(Vec3 p) { return Math.max(Math.ulp((float) p.x), Math.max(Math.ulp((float) p.y), Math.ulp((float) p.z))); }
    private static double shearNorm(double slope) { return (Math.sqrt(4 + slope * slope) + slope) * .5; }
    private static double boxDistance(Vec3 point, Vec3 minimum, Vec3 maximum) {
        Vec3 middle = minimum.add(maximum).scale(.5), half = maximum.subtract(minimum).scale(.5);
        Vec3 q = point.subtract(middle);
        double x = Math.abs(q.x) - half.x, y = Math.abs(q.y) - half.y, z = Math.abs(q.z) - half.z;
        return Math.sqrt(Math.max(x, 0) * Math.max(x, 0) + Math.max(y, 0) * Math.max(y, 0)
                + Math.max(z, 0) * Math.max(z, 0)) + Math.min(Math.max(x, Math.max(y, z)), 0);
    }
    private static double width(double travel) { return (1 - travel) * (1 + 1.2 * travel); }
    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    private static double smooth(double x) { return x * x * (3 - 2 * x); }
    private static Vec3 unit(Vec3 x) { return x.scale(1 / x.length()); }
    private static Vec3 min(Vec3 a, Vec3 b) { return new Vec3(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z)); }
    private static Vec3 max(Vec3 a, Vec3 b) { return new Vec3(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z)); }
}
