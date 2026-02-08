package com.jayemceekay.shadowedhearts.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class LuminousMoteParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseSize;
    private final float twinkleAmp;
    private final float twinkleHz;
    private final float driftAmp;
    private final float driftSpeed; // radians/sec
    private final float seed;       // stable per-particle (0..1)

    protected LuminousMoteParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz,
                                   SpriteSet sprites,
                                   float size,
                                   float twinkleAmp, float twinkleHz,
                                   float driftAmp, float driftSpeed) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.baseSize = size;
        this.twinkleAmp = twinkleAmp;
        this.twinkleHz = twinkleHz;
        this.driftAmp = driftAmp;
        this.driftSpeed = driftSpeed;

        // Color palette variation (lavender, white, orange/gold, pink) inspired by the reference GIF
        // Keep additive-friendly, bright tones.
        float roll = random.nextFloat();
        if (roll < 0.55f) {           // lavender/purple (most common)
            this.rCol = 0.78f;
            this.gCol = 0.58f;
            this.bCol = 1.00f;
        } else if (roll < 0.75f) {    // white
            this.rCol = 1.00f;
            this.gCol = 1.00f;
            this.bCol = 1.00f;
        } else if (roll < 0.90f) {    // orange/gold
            this.rCol = 1.00f;
            this.gCol = 0.68f;
            this.bCol = 0.26f;
        } else {                      // pink-magenta
            this.rCol = 1.00f;
            this.gCol = 0.55f;
            this.bCol = 0.86f;
        }
        this.alpha = 0.0f; // fade-in

        // Lifetime & size (scale lifetime modestly with initial speed magnitude)
        float spMag = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float lifeScale = Mth.clamp(spMag / 0.035f, 0.7f, 1.6f) * (size + random.nextFloat() * 0.75f);
        this.lifetime = Math.max(8, (int) ((20 + this.random.nextInt(20)) * lifeScale)); // ~0.7x..1.6x base lifetime
        this.quadSize = size;
        this.setSize(0.01f, 0.01f); // collision AABB tiny (not used visually)

        // Initial burst velocity with a subtle random variation
        this.xd = (float) vx + (random.nextFloat() - 0.5f) * 0.004;
        this.yd = (float) vy;
        this.zd = (float) vz + (random.nextFloat() - 0.5f) * 0.004;

        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.seed = random.nextFloat();

        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age >= this.lifetime) return;

        // Progress 0..1
        float t = (float) this.age / (float) this.lifetime;

        // Gentle circular drift (adds to the tiny velocity-based motion)
        double theta = (seed * Math.PI * 2.0) + (this.level.getGameTime() / 20.0) * driftSpeed;
        double cx = Math.cos(theta) * driftAmp;
        double cz = Math.sin(theta) * driftAmp;
        this.x += cx * 0.02;
        this.z += cz * 0.02;

        // Rise a touch
        this.yd += 0.0005;

        // Size: ease-in then ease-out for organic feel
        float size = baseSize;
                //* (easeOutQuad(Math.min(t * 2f, 1f)) * (1f - smoothstep(0.6f, 1f, t)) + 0.2f);
        this.quadSize = size;

        // Twinkle/pulse in alpha
        float twinkle = 1f + twinkleAmp * (float) Math.sin(2 * Math.PI * twinkleHz * (this.age / 20f + seed));
        // Fade in/out
        float fade = Math.min(1f, t / 0.2f) * (1f - smoothstep(0.75f, 1f, t));
        this.alpha = Math.min(1f, twinkle * fade);

        // Optional sprite animation (if you pack a tiny atlas strip)
        this.setSpriteFromAge(this.sprites);
    }

    private static float easeOutQuad(float x) {
        return 1f - (1f - x) * (1f - x);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ModParticleRenderTypes.LUMINOUS_MOTE_RENDER_TYPE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public LuminousMoteParticle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            // Derive a scale hint from initial velocity magnitude (proxy for entity bbox scale)
            float speedMag = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float scaleHint = Mth.clamp(speedMag / 0.035f, 0.7f, 2.0f);

            // Scale the base size by the hint so larger entities produce larger motes
            float size = (0.005f + level.random.nextFloat() * 0.05f) * scaleHint; // vary size
            float driftAmp = 0.06f * scaleHint;

            return new LuminousMoteParticle(
                    level, x, y, z,
                    vx, vy, vz,
                    this.sprites,
                    size,
                    0.60f,  // twinkle amplitude
                    1.5f,   // twinkle Hz
                    driftAmp,  // drift radius
                    0.9f    // drift speed
            );
        }
    }
}
