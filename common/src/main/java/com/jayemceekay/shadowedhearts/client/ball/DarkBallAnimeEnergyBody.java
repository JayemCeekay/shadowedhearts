package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

/** Bounded implicit mass animation. Its boundary is independent of captured mesh tessellation. */
final class DarkBallAnimeEnergyBody {
    static final int MAX_PRIMITIVES = 24;
    static final int MAX_NOTCHES = 4;
    private static final double NODE_WIDTH_CUTOFF = .00001;

    private DarkBallAnimeEnergyBody() { }

    record Frame(float[] primitives, int primitiveCount, float[] notches, int notchCount,
                 Vec3 minimum, Vec3 maximum, float smoothness, DarkBallAnimeRestMotion.Frame rest,
                 DarkBallAnimeTailCap.Frame tail) {
        Frame {
            if (tail != null && primitiveCount > MAX_PRIMITIVES / 2)
                throw new IllegalArgumentException("A pointed stream supports at most twelve primitives");
        }
        Frame(float[] primitives, int primitiveCount, float[] notches, int notchCount,
              Vec3 minimum, Vec3 maximum, float smoothness, DarkBallAnimeRestMotion.Frame rest) {
            this(primitives, primitiveCount, notches, notchCount, minimum, maximum, smoothness, rest, null);
        }
        Frame(float[] primitives, int primitiveCount, float[] notches, int notchCount,
              Vec3 minimum, Vec3 maximum, float smoothness) {
            this(primitives, primitiveCount, notches, notchCount, minimum, maximum, smoothness, null);
        }
    }

    static Frame frame(DarkBallAnimeMotion.Frame motion, float activation) {
        if (motion == null || motion.binding().energyShape() == null || !Float.isFinite(activation))
            throw new IllegalArgumentException("Missing energy shape or invalid activation");
        DarkBallAnimeMotion.Binding binding = motion.binding();
        if (binding.restVolume() != null) return silhouetteFrame(motion, activation);
        DarkBallAnimeEnergyShape shape = binding.energyShape();
        float[] primitives = new float[MAX_PRIMITIVES * 16];
        float[] notches = new float[MAX_NOTCHES * 16];
        if (motion.pullProgress() >= 1)
            return new Frame(primitives, 0, notches, 0, motion.target(), motion.target(), 0);

        double strength = clamp(activation) * motion.lobeBlend();
        double radius = binding.radius();
        Vec3 direction = binding.direction();
        Vec3 preferred = Math.abs(direction.z) < .95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 up = unit(preferred.subtract(direction.scale(preferred.dot(direction))));
        Vec3 side = direction.cross(up);
        Pose pose = fittedPose(shape, binding.center(), direction, radius);
        Ellipsoid[] nodes = new Ellipsoid[shape.nodeCount()];
        Ellipsoid[] all = new Ellipsoid[MAX_PRIMITIVES];
        int count = 0;
        double maxWidth = 0;
        double time = motion.timeSeconds();
        double strain = strength * (.19 * Math.sin(5.2 * time) + .21 * motion.squash());
        double squeezeAngle = 1.7 * time;
        double attachment = smooth(clamp(motion.pullProgress() / .16));
        for (int i = 0; i < nodes.length; i++) {
            Vec3 source = pose.centers[i];
            Vec3 relative = source.subtract(binding.center());
            double axial = relative.dot(direction);
            double order = pose.orders[i];
            Section section = section(motion, pose, side, up, order, strength);
            double width = section.width;
            maxWidth = Math.max(maxWidth, width);
            if (width < NODE_WIDTH_CUTOFF) continue;
            double energy = strength * (1 - smooth(clamp((section.local - .40) / .60)));
            double phase = 4.6 * time + 3.1 * axial / radius
                    + 2.4 * relative.dot(side) / radius;
            Vec3 radial = relative.subtract(direction.scale(axial));
            Vec3 offset = side.scale(.21 * Math.sin(phase) * radius * energy)
                    .add(up.scale(.19 * Math.cos(phase * 1.13 + .8) * radius * energy))
                    .add(direction.scale(.12 * Math.sin(phase * .83 - .4) * radius * energy));
            // Pose details become surface folds of a common body during intake.
            // Independent radial islands were the source of parallel cords and holes.
            Vec3 deviation = radial.subtract(section.sourceRadial).scale(1 - attachment);
            deviation = squeeze(deviation.add(offset.scale(1 - .65 * attachment)),
                    side, up, squeezeAngle, strain);
            Vec3 center = section.center.add(deviation.scale(width));
            double roll = energy * (.55 * Math.sin(phase * .81) + .32 * time);
            Vec3 fitRadii = pose.radii[i];
            double billow = 1 + energy * .27 * Math.sin(phase + 1.2);
            Vec3 a = axis(new Vec3(fitRadii.x * billow, 0, 0), direction, side, up, roll,
                    squeezeAngle, strain).scale(width);
            Vec3 b = axis(new Vec3(0, fitRadii.y / Math.sqrt(billow), 0), direction, side, up, roll,
                    squeezeAngle, strain).scale(width);
            Vec3 c = axis(new Vec3(0, 0, fitRadii.z / Math.sqrt(billow)), direction, side, up, roll,
                    squeezeAngle, strain).scale(width);
            // Once intake takes over, long pose axes must fold into the common
            // envelope too. Leaving their original lengths creates a second
            // branch beside the elbow even when their centers follow the arc.
            double maximumReach = Math.sqrt(a.lengthSqr() + b.lengthSqr() + c.lengthSqr());
            double envelope = section.radius * Math.pow(width, .68) * fullness(motion.pullProgress());
            double fold = 1 - attachment + attachment * Math.min(1, envelope * .95 / maximumReach);
            a = a.scale(fold);
            b = b.scale(fold);
            c = c.scale(fold);
            nodes[i] = new Ellipsoid(center, a, b, c);
            all[count++] = nodes[i];
        }
        // Twelve overlapping sections sweep one curved volume. Resample only
        // material still outside the ball, so the front never loses an entire
        // finite bridge when a pose seed arrives. The last material has lag=1.
        int sections = MAX_PRIMITIVES - nodes.length;
        double remainingOrder = clamp((1 - motion.pullProgress()) / .48);
        for (int i = 0; i < sections; i++) {
            double lowOrder = remainingOrder * i / sections;
            double highOrder = remainingOrder * (i + 1) / sections;
            Section a = section(motion, pose, side, up, lowOrder, strength);
            Section b = section(motion, pose, side, up, highOrder, strength);
            Section middle = section(motion, pose, side, up, (lowOrder + highOrder) * .5, strength);
            Vec3 separation = b.center.subtract(a.center);
            double length = separation.length();
            Vec3 along = length > radius * 1e-10 ? separation.scale(1 / length) : direction;
            Vec3 hint = Math.abs(along.z) < .9 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            Vec3 across = unit(along.cross(hint));
            // Broad elbows must stay filled even when the arch is seen nearly
            // end-on. The extra fullness fades at both transport endpoints.
            double thickness = middle.radius * Math.pow(middle.width, .68) * fullness(motion.pullProgress());
            thickness = Math.max(thickness, radius * 1e-10);
            maxWidth = Math.max(maxWidth, middle.width);
            Ellipsoid core = new Ellipsoid(a.center.add(b.center).scale(.5),
                    along.scale(length * .65 + thickness * .6), across.scale(thickness),
                    along.cross(across).scale(thickness));
            all[count++] = sourceFit(core, binding.center(), shape.sourceHalfExtent(), radius,
                    (1 - strength) * (1 - attachment));
        }
        if (count == 0) return new Frame(primitives, 0, notches, 0, motion.target(), motion.target(), 0);
        double smoothingRetirement = 1 - smooth(clamp((motion.pullProgress() - .40) / .12));
        double requestedSmoothness = radius * .045 * maxWidth * smoothingRetirement;
        double minimumDistanceScale = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            pack(all[i], primitives, i * 16);
            minimumDistanceScale = Math.min(minimumDistanceScale, primitives[i * 16 + 3]);
        }
        // Limit dilation by the smallest packed primitive. Smoothing retires
        // before the first pose seed arrives; core overlap keeps the body solid.
        float smoothness = (float) Math.min(requestedSmoothness, minimumDistanceScale * .6);
        Vec3 minimum = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 maximum = minimum.scale(-1);
        for (int i = 0; i < count; i++) {
            int at = i * 16;
            Ellipsoid body = unpack(primitives, at);
            double distanceScale = primitives[at + 3];
            // Polynomial smooth-min of any number of inputs is never below
            // min(inputs)-k. Enclose every d_i <= k dilated ellipsoid, including
            // the actual rounded center, inverse rows and distance scale uploaded
            // to the shader. Original double axes are not the rendered surface.
            Vec3 extent = body.extent().scale(1 + smoothness / distanceScale);
            Vec3 low = body.center.subtract(extent), high = body.center.add(extent);
            Vec3 margin = new Vec3(boundMargin(low.x, high.x, radius),
                    boundMargin(low.y, high.y, radius), boundMargin(low.z, high.z, radius));
            minimum = min(minimum, low.subtract(margin));
            maximum = max(maximum, high.add(margin));
        }
        int notchCount = 0;
        if (strength > .001) for (int i = 0; i < MAX_NOTCHES; i++) {
            double angle = time * (i % 2 == 0 ? 2.7 : -2.3) + i * Math.PI * .5;
            Vec3 outward = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            double farthest = -Double.MAX_VALUE;
            for (Ellipsoid node : all) if (node != null) {
                double reach = node.center.dot(outward) + node.support(outward).dot(outward);
                farthest = Math.max(farthest, reach);
            }
            Vec3 support = Vec3.ZERO;
            double weightSum = 0, supportSize = 0;
            for (Ellipsoid node : all) if (node != null) {
                Vec3 point = node.center.add(node.support(outward));
                double weight = Math.exp((point.dot(outward) - farthest) / (radius * .10 * maxWidth));
                support = support.add(point.scale(weight));
                supportSize += weight * node.maximumAxis();
                weightSum += weight;
            }
            support = support.scale(1 / weightSum);
            double bite = Math.min(radius * .27 * maxWidth, supportSize / weightSum * .55) * strength;
            if (bite < radius * .0001) continue;
            // Put the negative volume on the exterior support, so it opens an
            // inward rolling notch instead of drilling an invisible interior hole.
            // Soft support selection lets the notch travel between adjacent lobes
            // continuously rather than jumping when a different node becomes outermost.
            Vec3 center = support.add(outward.scale(bite * (.35 + .65 * attachment)));
            Vec3 tangent = direction.cross(outward);
            Ellipsoid notch = new Ellipsoid(center, outward.scale(bite * 1.25),
                    tangent.scale(bite * .65), direction.scale(bite * 1.3));
            pack(notch, notches, notchCount++ * 16);
        }
        return new Frame(primitives, count, notches, notchCount, minimum, maximum, smoothness);
    }

    /** Source-shaped material supplies the hold; only released material has a tube. */
    private static Frame silhouetteFrame(DarkBallAnimeMotion.Frame motion, float activation) {
        float[] primitives = new float[MAX_PRIMITIVES * 16], notches = new float[MAX_NOTCHES * 16];
        var binding = motion.binding();
        if (motion.pullProgress() >= 1)
            return new Frame(primitives, 0, notches, 0, motion.target(), motion.target(), 0);
        var rest = DarkBallAnimeRestMotion.frame(motion, activation, binding.restVolume());
        Vec3 low = rest.active() ? rest.minimum()
                : new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 high = rest.active() ? rest.maximum() : low.scale(-1);
        double radius = binding.radius();
        Pose fit = fittedPose(binding.energyShape(), binding.center(), binding.direction(), radius);
        double[] orders = new double[fit.centers.length];
        for (int i = 0; i < orders.length; i++)
            orders[i] = clamp((fit.centers[i].subtract(binding.center()).dot(binding.direction())
                    - rest.axialMinimum()) / rest.axialSpan());
        Pose pose = new Pose(fit.centers, fit.radii, orders, rest.axialMinimum(), rest.axialSpan());

        // local=(pull-.46*(1-q))/(.54-.02*q). Start the connecting volume
        // at local=.40 while the source volume still owns material through .58.
        // This overlap joins the stream without replacing the held silhouette.
        double firstOrder = clamp((.676 - motion.pullProgress()) / .468);
        double lastOrder = clamp((1 - motion.pullProgress()) / .48);
        double growth = smooth(clamp((motion.pullProgress() - .208) / .06));
        var tailProfile = binding.tailProfile();
        Vec3 rear = tailProfile != null ? tailProfile.rearPoint() : null;
        if (rear != null) {
            double rearOrder = clamp((rear.subtract(binding.center()).dot(binding.direction())
                    - rest.axialMinimum()) / rest.axialSpan());
            // Retain the shared material clock. Empty padding behind the actual
            // source is not a second last particle after that source has arrived.
            firstOrder = Math.max(firstOrder, rearOrder);
        }
        int count = 0;
        Vec3[] path = new Vec3[MAX_PRIMITIVES / 2 + 1];
        if (lastOrder > firstOrder && growth > 1e-6) {
            int sections = MAX_PRIMITIVES / 2;
            for (int i = 0; i < sections; i++) {
                double aOrder = firstOrder + (lastOrder - firstOrder) * i / sections;
                double bOrder = firstOrder + (lastOrder - firstOrder) * (i + 1) / sections;
                Profile a = sourceProfile(binding, pose, rest.side(), rest.up(), aOrder, motion.pullProgress());
                Profile b = sourceProfile(binding, pose, rest.side(), rest.up(), bOrder, motion.pullProgress());
                Profile middle = sourceProfile(binding, pose, rest.side(), rest.up(), (aOrder + bOrder) * .5,
                        motion.pullProgress());
                Vec3 aPoint = DarkBallAnimeRestMotion.streamPoint(rest, a.source);
                Vec3 bPoint = DarkBallAnimeRestMotion.streamPoint(rest, b.source);
                path[i] = aPoint;
                path[i + 1] = bPoint;
                Vec3 separation = bPoint.subtract(aPoint);
                double length = separation.length();
                Vec3 along = length > radius * 1e-10 ? separation.scale(1 / length) : binding.direction();
                Vec3 hint = Math.abs(along.z) < .9 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
                Vec3 across = unit(along.cross(hint));
                double width = DarkBallAnimeRestMotion.streamWidth(rest, middle.source);
                // Broad overlap keeps the forming elbow solid. Once the source
                // has released, retire that extra fullness so the rounded tube
                // shrinks at the same rate as material reaching the ball.
                double finalIntake = smooth(clamp((motion.pullProgress() - .78) / .16));
                double broadWidth = Math.pow(width, .68) * fullness(motion.pullProgress());
                // Retain the material following the point while it is visible;
                // finish with the same linear travel-width taper at .94.
                // The leading shoulder stays fuller where the stream narrows
                // into the ball. Its width remains below broadWidth, and the
                // rear sections and exact final travel-width endpoint agree.
                double leading = smooth(clamp(((i + .5) / sections - .35) / .30));
                double intakeTaper = finalIntake * finalIntake * finalIntake
                        * (1 - leading + leading * Math.pow(finalIntake, 1.25));
                double intakeWidth = broadWidth * (1 - intakeTaper) + width * intakeTaper;
                double thickness = Math.max(radius * 1e-10,
                        middle.radius * intakeWidth * growth);
                Ellipsoid body = new Ellipsoid(aPoint.add(bPoint).scale(.5),
                        along.scale(length * .65 + thickness * .6), across.scale(thickness),
                        along.cross(across).scale(thickness));
                int at = count++ * 16;
                pack(body, primitives, at);
                Ellipsoid packed = unpack(primitives, at);
                Vec3 extent = packed.extent();
                Vec3 aBound = packed.center.subtract(extent), bBound = packed.center.add(extent);
                Vec3 margin = new Vec3(boundMargin(aBound.x, bBound.x, radius),
                        boundMargin(aBound.y, bBound.y, radius), boundMargin(aBound.z, bBound.z, radius));
                low = min(low, aBound.subtract(margin));
                high = max(high, bBound.add(margin));
            }
        }
        if (count == 0 && !rest.active()) low = high = motion.target();
        DarkBallAnimeTailCap.Frame tail = null;
        if (rear != null && count > 0) {
            // A station one fifth into the remaining material supplies the
            // actual source width near the point. Distant stream sections must
            // not determine this width merely because their poles project back.
            double physicalMaximum = clamp((tailProfile.maximum() - rest.axialMinimum()) / rest.axialSpan());
            double available = Math.min(lastOrder, physicalMaximum);
            double physicalRear = clamp((tailProfile.minimum() - rest.axialMinimum()) / rest.axialSpan());
            double order = physicalRear + Math.max(0, available - physicalRear) * .20;
            var physical = tailProfile.sample(rest.axialMinimum() + rest.axialSpan() * order);
            double width = DarkBallAnimeRestMotion.streamWidth(rest, physical.source());
            double finalIntake = smooth(clamp((motion.pullProgress() - .78) / .16));
            double referenceWidth = Math.pow(width, .68) * fullness(motion.pullProgress()) * (1 - finalIntake)
                    + width * finalIntake;
            double referenceRadius = physical.radius() * referenceWidth * growth;
            tail = DarkBallAnimeTailCap.prepare(DarkBallAnimeRestMotion.streamPoint(rest, rear), path,
                    primitives, DarkBallAnimeRestMotion.streamPoint(rest, physical.source()), referenceRadius, count, radius);
        }
        return new Frame(primitives, count, notches, 0, low, high, 0, rest.active() ? rest : null, tail);
    }

    private record Profile(Vec3 source, double radius) { }

    private static Profile sourceProfile(DarkBallAnimeMotion.Binding binding, Pose pose,
                                         Vec3 side, Vec3 up, double order, double pull) {
        double weightSum = 0;
        Vec3 radial = Vec3.ZERO;
        for (int i = 0; i < pose.centers.length; i++) {
            double weight = Math.exp(-Math.pow((pose.orders[i] - order) / .24, 2));
            Vec3 offset = pose.centers[i].subtract(binding.center());
            radial = radial.add(offset.subtract(binding.direction().scale(offset.dot(binding.direction()))).scale(weight));
            weightSum += weight;
        }
        radial = radial.scale(1 / weightSum);
        double sideVariance = 0, upVariance = 0;
        for (int i = 0; i < pose.centers.length; i++) {
            double weight = Math.exp(-Math.pow((pose.orders[i] - order) / .24, 2));
            Vec3 offset = pose.centers[i].subtract(binding.center()).subtract(radial);
            double x = offset.dot(side), y = offset.dot(up);
            sideVariance += weight * (x*x + multiply(pose.radii[i], side).lengthSqr());
            upVariance += weight * (y*y + multiply(pose.radii[i], up).lengthSqr());
        }
        double radius = Math.max(binding.radius() * .025,
                Math.min(binding.radius() * .48, .88 * Math.sqrt(Math.min(sideVariance, upVariance) / weightSum)));
        Vec3 source = binding.center().add(binding.direction().scale(pose.first + pose.span * order)).add(radial);
        var tail = binding.tailProfile();
        if (tail != null) {
            double axial = source.subtract(binding.center()).dot(binding.direction());
            double blend = 1 - smooth(clamp((axial - tail.minimum())
                    / Math.max((tail.maximum() - tail.minimum()) * .22, binding.radius() * .0001)));
            var physical = tail.sample(axial);
            source = source.scale(1 - blend).add(physical.source().scale(blend));
            // Once the physical source releases, the cone owns its point.
            // Retire the extra source-radius taper with the same final-intake
            // transition: the carrier then shrinks once, with remaining travel,
            // instead of collapsing thinner than its packed center precision.
            double remainingSource = 1 - smooth(clamp((pull - .78) / .16));
            double radiusBlend = blend * remainingSource;
            radius = radius * (1 - radiusBlend)
                    + Math.max(binding.radius() * .001, physical.radius()) * radiusBlend;
        }
        return new Profile(source, radius);
    }

    private record Pose(Vec3[] centers, Vec3[] radii, double[] orders, double first, double span) { }
    private record Section(Vec3 center, Vec3 sourceRadial, double radius, double width, double local) { }

    private static Ellipsoid sourceFit(Ellipsoid body, Vec3 origin, Vec3 halfExtent, double radius, double amount) {
        if (amount <= 0) return body;
        Vec3 half = max(halfExtent, new Vec3(radius * .025, radius * .025, radius * .025));
        Vec3 offset = body.center.subtract(origin);
        Vec3 fitted = new Vec3(Math.max(-half.x * .9, Math.min(half.x * .9, offset.x)),
                Math.max(-half.y * .9, Math.min(half.y * .9, offset.y)),
                Math.max(-half.z * .9, Math.min(half.z * .9, offset.z)));
        Vec3 extent = body.extent();
        Vec3 scale = new Vec3(Math.min(1, (half.x - Math.abs(fitted.x)) / extent.x),
                Math.min(1, (half.y - Math.abs(fitted.y)) / extent.y),
                Math.min(1, (half.z - Math.abs(fitted.z)) / extent.z));
        scale = new Vec3(1, 1, 1).scale(1 - amount).add(scale.scale(amount));
        return new Ellipsoid(origin.add(offset.scale(1 - amount)).add(fitted.scale(amount)),
                multiply(body.a, scale), multiply(body.b, scale), multiply(body.c, scale));
    }

    private static Pose fittedPose(DarkBallAnimeEnergyShape shape, Vec3 center, Vec3 direction, double radius) {
        Vec3 extent = Vec3.ZERO;
        for (int i = 0; i < shape.nodeCount(); i++) {
            Vec3 offset = vector(shape.centers(), i * 3).subtract(center);
            Vec3 size = vector(shape.radii(), i * 3);
            extent = max(extent, new Vec3(Math.abs(offset.x) + size.x,
                    Math.abs(offset.y) + size.y, Math.abs(offset.z) + size.z));
        }
        // The coarse fit intentionally pads clusters. Normalize that padding
        // before animation so conversion does not immediately inflate the pose.
        Vec3 half = shape.sourceHalfExtent();
        Vec3 scale = new Vec3(Math.min(1, Math.max(half.x, radius * .025) / (extent.x + radius * .025)),
                Math.min(1, Math.max(half.y, radius * .025) / (extent.y + radius * .025)),
                Math.min(1, Math.max(half.z, radius * .025) / (extent.z + radius * .025)));
        Vec3[] centers = new Vec3[shape.nodeCount()], radii = new Vec3[shape.nodeCount()];
        double[] orders = new double[shape.nodeCount()];
        double first = Double.POSITIVE_INFINITY, last = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < centers.length; i++) {
            Vec3 offset = multiply(vector(shape.centers(), i * 3).subtract(center), scale);
            centers[i] = center.add(offset);
            radii[i] = multiply(vector(shape.radii(), i * 3), scale);
            orders[i] = offset.dot(direction);
            first = Math.min(first, orders[i]);
            last = Math.max(last, orders[i]);
        }
        double span = Math.max(last - first, radius * .0001);
        if (last - first < span) first = (first + last - span) * .5;
        for (int i = 0; i < orders.length; i++) orders[i] = clamp((orders[i] - first) / span);
        return new Pose(centers, radii, orders, first, span);
    }

    private static Section section(DarkBallAnimeMotion.Frame motion, Pose pose, Vec3 side, Vec3 up,
                                   double order, double strength) {
        var binding = motion.binding();
        double radius = binding.radius();
        Vec3 direction = binding.direction();
        double weightSum = 0;
        Vec3 radial = Vec3.ZERO;
        for (int i = 0; i < pose.centers.length; i++) {
            double weight = Math.exp(-Math.pow((pose.orders[i] - order) / .24, 2));
            Vec3 offset = pose.centers[i].subtract(binding.center());
            radial = radial.add(offset.subtract(direction.scale(offset.dot(direction))).scale(weight));
            weightSum += weight;
        }
        radial = radial.scale(1 / weightSum);
        double sideVariance = 0, upVariance = 0;
        for (int i = 0; i < pose.centers.length; i++) {
            double weight = Math.exp(-Math.pow((pose.orders[i] - order) / .24, 2));
            Vec3 offset = pose.centers[i].subtract(binding.center()).subtract(radial);
            double x = offset.dot(side), y = offset.dot(up);
            sideVariance += weight * (x * x + multiply(pose.radii[i], side).lengthSqr());
            upVariance += weight * (y * y + multiply(pose.radii[i], up).lengthSqr());
        }
        double profileRadius = Math.max(radius * .025,
                Math.min(radius * .48, .88 * Math.sqrt(Math.min(sideVariance, upVariance) / weightSum)));
        double lag = 1 - order;
        double local = clamp((motion.pullProgress() - .46 * lag) / (.52 + .02 * lag));
        double travel = smooth(local);
        double width = (1 - travel) * (1 + 1.2 * travel);
        Vec3 sourceAxis = binding.center().add(direction.scale(pose.first + pose.span * order));
        Vec3 center = sourceAxis.scale(1 - travel).add(motion.target().scale(travel))
                .add(binding.bend().scale(4 * travel * (1 - travel))).add(radial.scale(width));
        double wave = 5.0 * motion.timeSeconds() - 7.0 * order;
        double energy = strength * (1 - smooth(clamp((local - .4) / .6)));
        center = center.add(side.scale(.055 * radius * energy * width * Math.sin(wave)))
                .add(up.scale(.045 * radius * energy * width * Math.cos(wave * .87)));
        profileRadius *= 1 + .18 * energy * Math.sin(wave + .7);
        return new Section(center, radial, profileRadius, width, local);
    }

    /** CPU equivalent of the production shader field, for bounds and contour checks. */
    static double sample(Frame frame, Vec3 point) {
        double value = frame.primitiveCount == 0 ? Double.POSITIVE_INFINITY : ellipsoid(frame.primitives, 0, point);
        for (int i = 1; i < frame.primitiveCount; i++) {
            double next = ellipsoid(frame.primitives, i * 16, point);
            if (frame.smoothness <= 0) value = Math.min(value, next);
            else {
                double h = clamp(.5 + .5 * (next - value) / frame.smoothness);
                value = next * (1 - h) + value * h - frame.smoothness * h * (1 - h);
            }
        }
        for (int i = 0; i < frame.notchCount; i++) value = Math.max(value, -ellipsoid(frame.notches, i * 16, point));
        if (frame.tail != null) value = Math.max(value, DarkBallAnimeTailCap.sample(frame.tail, point));
        if (frame.rest != null) value = Math.min(value, DarkBallAnimeRestMotion.sample(frame.rest, point));
        return value;
    }

    private static double ellipsoid(float[] packed, int at, Vec3 point) {
        Vec3 offset = point.subtract(vector(packed, at));
        double x = vector(packed, at + 4).dot(offset), y = vector(packed, at + 8).dot(offset);
        double z = vector(packed, at + 12).dot(offset);
        return (Math.sqrt(x * x + y * y + z * z) - 1) * packed[at + 3];
    }

    private record Ellipsoid(Vec3 center, Vec3 a, Vec3 b, Vec3 c) {
        Vec3 extent() { return new Vec3(Math.sqrt(a.x*a.x+b.x*b.x+c.x*c.x),
                Math.sqrt(a.y*a.y+b.y*b.y+c.y*c.y), Math.sqrt(a.z*a.z+b.z*b.z+c.z*c.z)); }
        double minimumAxis() { return Math.min(a.length(), Math.min(b.length(), c.length())); }
        double maximumAxis() { return Math.max(a.length(), Math.max(b.length(), c.length())); }
        Vec3 support(Vec3 direction) {
            double x = a.dot(direction), y = b.dot(direction), z = c.dot(direction);
            return a.scale(x).add(b.scale(y)).add(c.scale(z)).scale(1 / Math.sqrt(x*x+y*y+z*z));
        }
    }

    private static double pack(Ellipsoid body, float[] target, int at) {
        Vec3 row0 = body.b.cross(body.c);
        double determinant = body.a.dot(row0);
        if (!Double.isFinite(determinant) || determinant <= 0)
            throw new IllegalArgumentException("Singular energy primitive");
        row0 = row0.scale(1 / determinant);
        Vec3 row1 = body.c.cross(body.a).scale(1 / determinant);
        Vec3 row2 = body.a.cross(body.b).scale(1 / determinant);
        double scale = 1 / Math.sqrt(row0.lengthSqr() + row1.lengthSqr() + row2.lengthSqr());
        put(target, at, body.center); target[at + 3] = (float) scale;
        put(target, at + 4, row0); put(target, at + 8, row1); put(target, at + 12, row2);
        return scale;
    }

    private static Ellipsoid unpack(float[] packed, int at) {
        Vec3 row0 = vector(packed, at + 4), row1 = vector(packed, at + 8), row2 = vector(packed, at + 12);
        double determinant = row0.dot(row1.cross(row2));
        if (!(determinant > 0) || !Double.isFinite(determinant) || !(packed[at + 3] > 0))
            throw new IllegalArgumentException("Unrepresentable packed energy primitive");
        return new Ellipsoid(vector(packed, at), row1.cross(row2).scale(1 / determinant),
                row2.cross(row0).scale(1 / determinant), row0.cross(row1).scale(1 / determinant));
    }

    private static double boundMargin(double minimum, double maximum, double radius) {
        // Also survive narrowing the proxy bounds themselves to GPU floats.
        return Math.max(radius * .0001,
                Math.max(Math.ulp((float) minimum), Math.ulp((float) maximum)) * 4.0);
    }

    private static Vec3 axis(Vec3 value, Vec3 direction, Vec3 side, Vec3 up, double roll, double angle, double strain) {
        double cosine = Math.cos(roll), sine = Math.sin(roll);
        Vec3 rotated = value.scale(cosine).add(direction.cross(value).scale(sine))
                .add(direction.scale(direction.dot(value) * (1 - cosine)));
        return squeeze(rotated, side, up, angle, strain);
    }

    private static Vec3 squeeze(Vec3 value, Vec3 side, Vec3 up, double angle, double strain) {
        double x = value.dot(side), y = value.dot(up), c = Math.cos(angle), s = Math.sin(angle);
        double u = (x*c+y*s)*Math.exp(-strain), v = (-x*s+y*c)*Math.exp(strain);
        return value.add(side.scale(u*c-v*s-x)).add(up.scale(u*s+v*c-y));
    }

    private static Vec3 unit(Vec3 value) { return value.scale(1 / value.length()); }
    private static Vec3 multiply(Vec3 a, Vec3 b) { return new Vec3(a.x*b.x, a.y*b.y, a.z*b.z); }
    private static Vec3 vector(float[] values, int at) { return new Vec3(values[at], values[at+1], values[at+2]); }
    private static void put(float[] values, int at, Vec3 value) {
        values[at]=(float)value.x; values[at+1]=(float)value.y; values[at+2]=(float)value.z;
    }
    private static Vec3 min(Vec3 a, Vec3 b) { return new Vec3(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z)); }
    private static Vec3 max(Vec3 a, Vec3 b) { return new Vec3(Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z)); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double fullness(double pull) { return 1 + .55 * Math.sin(Math.PI * pull); }
    private static double smooth(double value) { return value*value*(3-2*value); }
}
