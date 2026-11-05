package com.jayemceekay.shadowedhearts.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

public class RelicStoneMoteParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseSize;
    private final float twinkleAmp;
    private final float twinkleHz;
    private final float driftAmp;
    private final float seed;       // stable per-particle (0..1)

    protected RelicStoneMoteParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz,
                                   SpriteSet sprites,
                                   float size,
                                   float twinkleAmp, float twinkleHz,
                                   float driftAmp) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.baseSize = size;
        this.twinkleAmp = twinkleAmp;
        this.twinkleHz = twinkleHz;
        this.driftAmp = driftAmp;

        // Bright green color as requested
        this.rCol = 0.4f;
        this.gCol = 1.0f;
        this.bCol = 0.4f;
        
        this.alpha = 0.0f; // fade-in

        // Every mote needs enough time to show its curl, including the smaller sprites.
        this.lifetime = 24 + random.nextInt(13);
        this.quadSize = size;
        this.setSize(0.01f, 0.01f);

        // Gentle outward travel lets the curl remain visible from different viewpoints.
        this.xd = vx * 0.5 + (random.nextFloat() - 0.5f) * 0.004;
        this.yd = (float) vy;
        this.zd = vz * 0.5 + (random.nextFloat() - 0.5f) * 0.004;

        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.seed = random.nextFloat();

        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) return;

        float t = (float) this.age / (float) this.lifetime;

        var drift = RelicStoneMoteMotion.driftDelta(age, lifetime, seed, driftAmp);
        // Keep the bounding box in sync so the next vanilla tick retains the drift.
        this.move(drift.x, drift.y, drift.z);

        this.yd += 0.0005;

        this.quadSize = baseSize;

        float twinkle = 1f + twinkleAmp * (float) Math.sin(2 * Math.PI * twinkleHz * (this.age / 20f + seed));
        float fade = Math.min(1f, t / 0.2f) * (1f - smoothstep(0.75f, 1f, t));
        this.alpha = Math.min(1f, twinkle * fade);

        this.setSpriteFromAge(this.sprites);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        // Block rendering can reseed level.random every frame and repeat the same sizes.
        private final RandomSource random = RandomSource.create();

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public RelicStoneMoteParticle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            float speedMag = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float scaleHint = Mth.clamp(speedMag / 0.035f, 0.7f, 2.0f);

            // Increase base size slightly: from 0.005-0.055 to 0.01-0.08
            float size = (0.01f + random.nextFloat() * 0.07f) * scaleHint;
            float driftAmp = 0.08f * scaleHint;

            return new RelicStoneMoteParticle(
                    level, x, y, z,
                    vx, vy, vz,
                    this.sprites,
                    size,
                    0.60f,
                    1.5f,
                    driftAmp
            );
        }
    }
}
