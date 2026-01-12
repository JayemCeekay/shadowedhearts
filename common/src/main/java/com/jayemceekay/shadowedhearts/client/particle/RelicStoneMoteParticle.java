package com.jayemceekay.shadowedhearts.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class RelicStoneMoteParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseSize;
    private final float twinkleAmp;
    private final float twinkleHz;
    private final float driftAmp;
    private final float driftSpeed; // radians/sec
    private final float seed;       // stable per-particle (0..1)

    protected RelicStoneMoteParticle(ClientLevel level, double x, double y, double z,
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

        // Bright green color as requested
        this.rCol = 0.4f;
        this.gCol = 1.0f;
        this.bCol = 0.4f;
        
        this.alpha = 0.0f; // fade-in

        float spMag = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float lifeScale = Mth.clamp(spMag / 0.035f, 0.7f, 1.6f) * (size + random.nextFloat() * 0.75f);
        this.lifetime = Math.max(8, (int) ((20 + this.random.nextInt(20)) * lifeScale));
        this.quadSize = size;
        this.setSize(0.01f, 0.01f);

        // Increased horizontal motion as requested
        this.xd = (float) vx * 1.5f + (random.nextFloat() - 0.5f) * 0.01;
        this.yd = (float) vy;
        this.zd = (float) vz * 1.5f + (random.nextFloat() - 0.5f) * 0.01;

        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.seed = random.nextFloat();

        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age >= this.lifetime) return;

        float t = (float) this.age / (float) this.lifetime;

        double theta = (seed * Math.PI * 2.0) + (this.level.getGameTime() / 20.0) * driftSpeed;
        double cx = Math.cos(theta) * driftAmp;
        double cz = Math.sin(theta) * driftAmp;
        this.x += cx * 0.03; // Slightly more drift
        this.z += cz * 0.03;

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
            float size = (0.01f + level.random.nextFloat() * 0.07f) * scaleHint;
            float driftAmp = 0.08f * scaleHint;

            return new RelicStoneMoteParticle(
                    level, x, y, z,
                    vx, vy, vz,
                    this.sprites,
                    size,
                    0.60f,
                    1.5f,
                    driftAmp,
                    0.9f
            );
        }
    }
}
