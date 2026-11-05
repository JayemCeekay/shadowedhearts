package com.jayemceekay.shadowedhearts.client.ball;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/** Capture-local transport for the source mesh and converted energy body. */
final class DarkBallAnimeMotion {
    private static final double MAX_COORDINATE = 1_000_000;

    private DarkBallAnimeMotion() { }

    /** Source silhouette plus a separate coarse energy volume; no curl subdivision is needed. */
    static Binding bindEnergy(DarkBallSurfaceMesh mesh, Vec3 initialBallLocal) {
        return bindEnergy(mesh, initialBallLocal, null);
    }

    static Binding bindEnergy(DarkBallSurfaceMesh mesh, Vec3 initialBallLocal, BooleanSupplier cancelled) {
        return DarkBallPreparationCancellation.run(cancelled, () -> {
            if (mesh == null || !mesh.hasFiniteGeometry() || mesh.triangleCount() == 0)
                throw new IllegalArgumentException("Anime capture requires a finite source surface");
            requireFinite(initialBallLocal);
            float[] p = mesh.positions();
            for (int i = 0; i < p.length; i += 3) {
                DarkBallPreparationCancellation.checkEvery(i / 3);
                Vec3 v = new Vec3(p[i], p[i + 1], p[i + 2]);
                requireFinite(v);
            }
            // A captured alpha-edge sliver may narrow to zero area, and unused
            // vertices have no visible footprint. The energy fit excludes both;
            // its coordinate frame must use those same positive-area bounds.
            DarkBallAnimeEnergyShape energyShape = DarkBallAnimeEnergyShape.prepare(mesh);
            Vec3 center = energyShape.center();
            double radius = Math.max(energyShape.radius(), 0.0001);
            Vec3 toBall = initialBallLocal.subtract(center);
            // Vec3.normalize treats sub-0.0001 vectors as zero. Small captured
            // geometry still needs a true unit axis for the rolling frame.
            Vec3 direction = toBall.lengthSqr() > 1e-12
                    ? toBall.scale(1 / toBall.length()) : new Vec3(1, 0, 0);
            // Volume-local Z is the captured up axis. Keep the bow fixed when the ball moves.
            Vec3 preferredUp = Math.abs((float) direction.z) < .95f
                    ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            Vec3 bend = preferredUp.subtract(direction.scale(preferredUp.dot(direction))).normalize();
            // A visible arch on ordinary throws, with a smaller bow when the ball
            // is close. Pin both direction and height so live ball movement cannot
            // flip or pump the curve while material is already travelling on it.
            double arcHeight = Math.min(toBall.length() * .30, radius * .90);
            bend = bend.scale(arcHeight);
            DarkBallAnimeCoreMesh sourceCore = DarkBallAnimeCoreMesh.capture(mesh, radius);
            int[] sourceIndices = sourceCore.indices();
            int sourceSamples = sourceIndices.length;
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < sourceSamples; i++) {
                DarkBallPreparationCancellation.checkEvery(i);
                int at = sourceIndices[i] * 3;
                double axial = new Vec3(p[at], p[at + 1], p[at + 2]).subtract(center).dot(direction);
                min = Math.min(min, axial);
                max = Math.max(max, axial);
            }
            double span = Math.max(max - min, radius * .0001);
            // A sheet perpendicular to intake shares one material order instead of a singular divide.
            if (max - min < span) min = (max + min - span) * .5;
            return new Binding(center, direction, bend, radius, min, span, sourceCore,
                    energyShape, null, null);
        });
    }

    // bend is the ball-local midpoint offset of the quadratic intake arch.
    record Binding(Vec3 center, Vec3 direction, Vec3 bend, double radius,
                   double axialMin, double axialSpan, DarkBallAnimeCoreMesh coreMesh,
                   DarkBallAnimeEnergyShape energyShape,
                   DarkBallAnimeRestVolume restVolume, DarkBallAnimeTailProfile tailProfile) {
        Binding withRestVolume(DarkBallAnimeRestVolume rest) {
            return withRestVolume(rest, null);
        }

        Binding withRestVolume(DarkBallAnimeRestVolume rest, BooleanSupplier cancelled) {
            if (energyShape == null || rest == null)
                throw new IllegalArgumentException("Rest volume requires an energy binding");
            return DarkBallPreparationCancellation.run(cancelled, () -> {
                Vec3 hint = Math.abs(direction.z) < .95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
                Vec3 up = hint.subtract(direction.scale(hint.dot(direction))).normalize();
                DarkBallAnimeTailProfile profile = DarkBallAnimeTailProfile.prepare(rest, center,
                        direction, direction.cross(up), up, radius);
                return new Binding(center, direction, bend, radius, axialMin, axialSpan,
                        coreMesh, energyShape, rest, profile);
            });
        }

        Frame frame(Vec3 ballLocal, float pullProgress, float lobeBlend, float timeSeconds) {
            return frame(ballLocal, pullProgress, lobeBlend, timeSeconds, 0);
        }

        Frame frame(Vec3 ballLocal, float pullProgress, float lobeBlend, float timeSeconds, float squash) {
            requireFinite(ballLocal);
            if (!Float.isFinite(pullProgress) || !Float.isFinite(lobeBlend) || !Float.isFinite(timeSeconds)
                    || !Float.isFinite(squash))
                throw new IllegalArgumentException("Nonfinite anime capture frame");
            // Keep trigonometric arguments useful for long-running debug captures.
            return new Frame(this, ballLocal, clamp(pullProgress), clamp(lobeBlend),
                    Math.max(0, Math.min(3600, timeSeconds)), clamp(squash));
        }
    }

    record Frame(Binding binding, Vec3 target, float pullProgress, float lobeBlend, float timeSeconds,
                 float squash) {
        private double order(Vec3 p) {
            return smooth(clamp((p.subtract(binding.center).dot(binding.direction)
                    - binding.axialMin) / binding.axialSpan));
        }

        private double localProgress(Vec3 p) {
            double lag = 1 - order(p);
            // The leading surface gets ahead while the rear silhouette hangs
            // back. The last material still finishes at pull=1: .46 + .54.
            return clamp((pullProgress - .46 * lag) / (.52 + .02 * lag));
        }

        Vec3 deform(Vec3 p) {
            requireFinite(p);
            Vec3 relative = p.subtract(binding.center);
            double scale = binding.radius;
            double axial = relative.dot(binding.direction);
            Vec3 radial = relative.subtract(binding.direction.scale(axial));
            // Fixed capture-local axes avoid camera-dependent rolling and never
            // flip as the target moves. No normal or angular singularity is used.
            Vec3 preferredUp = Math.abs((float) binding.direction.z) < .95f
                    ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            Vec3 up = preferredUp.subtract(binding.direction.scale(preferredUp.dot(binding.direction))).normalize();
            Vec3 side = binding.direction.cross(up);
            double u = radial.dot(side) / scale, v = radial.dot(up) / scale, a = axial / scale;
            double energy = turbulenceStrength(p);

            // Compact counter-rotating curls bend the actual silhouette. Unlike
            // a small global rotation, each curl turns its inner material much
            // further than its rim, producing hooked edges and deep local folds.
            // Twist the fixed lattice along the source axis, never with the camera
            // or time: moving cell boundaries would make the material slip/pop.
            double latticeAngle = 1.7 * a + .4;
            double lc = Math.cos(latticeAngle), ls = Math.sin(latticeAngle);
            double gu = u * lc + v * ls, gv = -u * ls + v * lc;
            // Nearest of two staggered rows; triangular packing avoids the broad
            // untouched diagonal gaps of a square grid. Negative rows use floor
            // parity, matching GLSL mod rather than Java's signed remainder.
            double rowA = Math.floor(gv / .55425626), rowB = rowA + 1;
            double offsetA = .5 * (rowA - 2 * Math.floor(rowA * .5));
            double offsetB = .5 * (rowB - 2 * Math.floor(rowB * .5));
            double colA = Math.floor(gu / .64 - offsetA + .5);
            double colB = Math.floor(gu / .64 - offsetB + .5);
            double qaU = gu - .64 * (colA + offsetA), qaV = gv - .55425626 * rowA;
            double qbU = gu - .64 * (colB + offsetB), qbV = gv - .55425626 * rowB;
            boolean useA = qaU * qaU + qaV * qaV <= qbU * qbU + qbV * qbV;
            double cellU = useA ? colA : colB, cellV = useA ? rowA : rowB;
            double qu = useA ? qaU : qbU, qv = useA ? qaV : qbV;
            double kernel = Math.max(0, 1 - (qu * qu + qv * qv) / (.30 * .30));
            double parity = cellU + cellV - 2 * Math.floor((cellU + cellV) * .5);
            double phase = 1.35 * timeSeconds + 1.1 * a + 1.7 * cellU + 2.3 * cellV;
            double roll = (1 - 2 * parity) * (1.0 + .5 * Math.sin(phase)) * energy * kernel * kernel
                    * (Math.abs(cellU) > 2 || Math.abs(cellV) > 2 ? 1 : 0);
            double rc = Math.cos(roll), rs = Math.sin(roll);
            // Each support is disjoint and maps onto itself. Both displacement
            // and its slope vanish before a cell boundary. The radius-dependent
            // rotation preserves transverse area and cannot invert a cross-section.
            double du = qu * (rc - 1) - qv * rs, dv = qu * rs + qv * (rc - 1);
            // A tiny shared drift keeps the gaps between supports alive. It is
            // continuous across every cell and preserves the curl's area. Keep
            // drift and squeeze affine along the held body, including thin faces
            // where the local plan cannot resolve nonlinear curvature.
            double driftPhase = 1.35 * timeSeconds + .4;
            double x = (u + du * lc - dv * ls + .003 * energy * Math.sin(driftPhase)) * scale;
            double y = (v + du * ls + dv * lc + .003 * energy * Math.cos(driftPhase)) * scale;

            // Reciprocal scales compress one direction and bulge the other.
            // Reserve most displacement for curling, with a small authored squeeze.
            // Curl + squeeze remains inside the original .145-radius held bound;
            // the existing intake taper still owns the eventual collapse.
            double angle = .18 * timeSeconds;
            double strain = energy * (.003 + .009 * squash);
            double c = Math.cos(angle), s = Math.sin(angle);
            double compressed = (x * c + y * s) * Math.exp(-strain);
            double expanded = (-x * s + y * c) * Math.exp(strain);
            Vec3 transverse = side.scale(compressed * c - expanded * s)
                    .add(up.scale(compressed * s + expanded * c));
            Vec3 sourceAxis = binding.center.add(binding.direction.scale(axial));
            double travel = smooth(localProgress(p));
            double width = (1 - travel) * (1 + 1.2 * travel);
            Vec3 path = sourceAxis.scale(1 - travel).add(target.scale(travel))
                    .add(binding.bend.scale(4 * travel * (1 - travel)));
            return path.add(transverse.scale(width));
        }

        double turbulenceStrength(Vec3 p) {
            // Only the material approaching intake calms down. The delayed tail
            // retains its full rolling motion after the leading surface arrives.
            return lobeBlend * (1 - smooth(clamp((localProgress(p) - .20) / .70)));
        }

        double visibility(Vec3 p) {
            return 1 - smooth(clamp((localProgress(p) - .96) / .04));
        }

        /** Map the complete original footprint vector, preserving the common material field. */
        Vec3 deformVector(Vec3 p, Vec3 delta) {
            return deform(p.add(delta)).subtract(deform(p));
        }
    }

    static void apply(ShaderInstance shader, Frame frame) {
        if (shader == null) return;
        if (frame == null) return;
        Binding b = frame.binding;
        vector(shader, "AnimeCenter", b.center);
        vector(shader, "AnimeDirection", b.direction);
        vector(shader, "AnimeBend", b.bend);
        vector(shader, "AnimeTarget", frame.target);
        Uniform shape = shader.getUniform("AnimeShape");
        if (shape != null) shape.set((float) b.radius, (float) b.axialMin, (float) b.axialSpan, frame.lobeBlend);
        Uniform clock = shader.getUniform("AnimeClock");
        if (clock != null) clock.set(frame.pullProgress, frame.timeSeconds);
        Uniform squash = shader.getUniform("AnimeSquash");
        if (squash != null) squash.set(frame.squash);
    }

    private static void vector(ShaderInstance shader, String name, Vec3 value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set((float) value.x, (float) value.y, (float) value.z);
    }

    private static void requireFinite(Vec3 p) {
        if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)
                || Math.abs(p.x) > MAX_COORDINATE || Math.abs(p.y) > MAX_COORDINATE
                || Math.abs(p.z) > MAX_COORDINATE)
            throw new IllegalArgumentException("Invalid anime capture coordinate");
    }

    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static double smooth(double v) { return v * v * (3 - 2 * v); }
}
