package com.jayemceekay.shadowedhearts.client.aura.shadow.style.classic;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraFade;
import net.minecraft.world.phys.Vec3;

/** One cellular puff opening away from its birth surface before rising freely. */
public final class ClassicAuraWisp {
    public static final double MOTION_SPEED = 1.20;
    private final Object anchorKey;
    private final float scale;
    private final float seed;
    private final int lifetime;
    private final float strength;
    private Vec3 root;
    private Vec3 previousRoot;
    private final Vec3 birth;
    private final Vec3 outwardDirection;
    private int age;

    public ClassicAuraWisp(Object anchorKey, Vec3 root, Vec3 outward, float localScale,
                           float seed, int lifetime, float strength) {
        this.anchorKey = anchorKey;
        this.root = finite(root) ? root : Vec3.ZERO;
        this.previousRoot = this.root;
        this.birth = this.root;
        Vec3 normal = normalize(outward);
        // Keep the posed surface direction, including upward-facing wings and
        // backs. Undersides get only a shallow downward excursion: buoyancy
        // must take over without sending a plume through the floor.
        this.outwardDirection = new Vec3(normal.x, Math.max(-0.30, normal.y), normal.z);
        this.scale = clamp(localScale, 0.10f, 1.80f);
        this.seed = Float.isFinite(seed) ? seed - (float) Math.floor(seed) : 0.0f;
        this.lifetime = Math.max(12, Math.min(90, lifetime));
        this.strength = clamp(strength, 0.0f, 1.0f);
    }

    /** Pose arguments remain compatible with the source tick path, but never drag an existing puff. */
    public void tick(Vec3 posedRoot, Vec3 outward) {
        previousRoot = root;
        age = Math.min(age + 1, lifetime);
        // Integrate a decaying outward velocity analytically. New material
        // opens away from the skin, then the existing buoyant rise dominates.
        // Position and velocity stay continuous; later bone poses never drag
        // the puff and there is no attachment/release boundary.
        // Speed transport independently of lifetime and the smooth entrance.
        double motionAge = age * MOTION_SPEED;
        double phase = seed * Math.PI * 2.0;
        double driftX = (Math.sin(phase + motionAge * 0.036) - Math.sin(phase)) * 0.12;
        double driftZ = (Math.cos(phase * 1.7 + motionAge * 0.029) - Math.cos(phase * 1.7)) * 0.12;
        double outwardTravel = 0.62 * -Math.expm1(-motionAge / 8.0);
        root = birth.add(outwardDirection.scale(outwardTravel * scale))
                .add(scale * driftX, scale * motionAge * (0.017 + seed * 0.006) * 1.20, scale * driftZ);
    }

    public Object anchorKey() { return anchorKey; }
    public boolean released() { return true; }
    public boolean alive() { return age < lifetime; }
    public float seed() { return seed; }
    /** Packed density role: dark filament or pink highlight; zero is reserved for flakes. */
    public float materialRole() { return seed < 0.56f ? 0.25f : 0.75f; }
    public float life(float partial) { return clamp((age + clamp(partial, 0.0f, 1.0f)) / lifetime, 0.0f, 1.0f); }

    public float opacity(float partial) {
        float life = life(partial);
        float ticks = age + clamp(partial, 0.0f, 1.0f);
        return strength * birthEnvelope(ticks) * (1.0f - smooth(0.08f, 1.0f, life));
    }

    public Vec3 point(float along, float partial) {
        float t = clamp(along, 0.0f, 1.0f);
        float interpolation = clamp(partial, 0.0f, 1.0f);
        Vec3 origin = previousRoot.lerp(root, interpolation);
        // The billboard center remains the drifting volume point, independent
        // of puff contraction or changes to the original anatomical normal.
        return origin.add(0, (t - 0.5) * scale * (0.86 + seed * 0.14) * sizeEnvelope(partial), 0);
    }

    public float halfWidth(float along, float partial) {
        float t = clamp(along, 0.0f, 1.0f);
        float shoulder = 0.90f + 0.10f * (float) Math.sin(Math.PI * t);
        float layerSpread = materialRole() < 0.5f ? 1.10f : 1.0f;
        return scale * (0.56f + seed * 0.12f) * shoulder * sizeEnvelope(partial) * layerSpread;
    }

    private float sizeEnvelope(float partial) {
        // Establish a faint broad footprint early, then settle over 150 ms.
        // Opacity rises separately so area and brightness do not both arrive late.
        float ticks = age + clamp(partial, 0.0f, 1.0f);
        return birthSizeEnvelope(ticks) * (1.0f - 0.18f * smooth(0.0f, 1.0f, life(partial)));
    }

    /** Shared opacity entrance for XD cellular puffs and their backing haze. */
    public static float birthEnvelope(float ageTicks) {
        return XdAuraFade.birthEnvelope(ageTicks);
    }

    /** Earlier size expansion with the same zero start and 150 ms completion. */
    public static float birthSizeEnvelope(float ageTicks) {
        return XdAuraFade.birthSizeEnvelope(ageTicks);
    }
    public static Vec3 transportTangent(Vec3 normal, Vec3 previous) {
        Vec3 projected = previous.subtract(normal.scale(previous.dot(normal)));
        if (projected.lengthSqr() <= 1.0e-8) {
            Vec3 reference = Math.abs(normal.x) < 0.75 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
            projected = reference.subtract(normal.scale(reference.dot(normal)));
        }
        projected = projected.normalize();
        return projected.dot(previous) < 0 ? projected.scale(-1) : projected;
    }
    private static Vec3 normalize(Vec3 value) {
        return finite(value) && value.lengthSqr() > 1.0e-8 ? value.normalize() : new Vec3(0.0, 1.0, 0.0);
    }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static float clamp(float value, float low, float high) {
        return Float.isFinite(value) ? Math.max(low, Math.min(high, value)) : low;
    }
    private static float smooth(float low, float high, float value) {
        float t = clamp((value - low) / (high - low), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
