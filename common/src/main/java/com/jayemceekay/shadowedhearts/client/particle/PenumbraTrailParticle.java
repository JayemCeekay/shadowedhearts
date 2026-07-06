package com.jayemceekay.shadowedhearts.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Billboard particle that renders a soft smoke puff for the penumbra density trail.
 * Spawned behind the Penumbra Ball when thrown and during capture.
 * These are re-rendered as density splats by the mixin pipeline for the metaball merge effect.
 */
public class PenumbraTrailParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseSize;

    protected PenumbraTrailParticle(ClientLevel level, double x, double y, double z,
                                    double vx, double vy, double vz,
                                    SpriteSet sprites, float size) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.baseSize = size;

        // White vertex color — all coloring is done in the composite shader.
        // Alpha is used as densityStrength and lifecycle fade.
        this.rCol = 1.0f;
        this.gCol = 1.0f;
        this.bCol = 1.0f;

        this.alpha = 0.0f;
        this.lifetime = 12 + random.nextInt(20); // 12–32 ticks per spec
        this.quadSize = size;
        this.setSize(0.01f, 0.01f);

        // Inherit a small fraction of ball velocity with wider random spread for billowy shape
        this.xd = vx * -0.06 + (random.nextFloat() - 0.5) * 0.06;
        this.yd = vy * -0.06 + (random.nextFloat() - 0.5) * 0.05;
        this.zd = vz * -0.06 + (random.nextFloat() - 0.5) * 0.06;

        this.gravity = 0.0f;
        this.hasPhysics = false;

        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age >= this.lifetime) return;

        // Slow down over time (drag)
        this.xd *= 0.94;
        this.yd *= 0.94;
        this.zd *= 0.94;

        // Gentle upward drift like smoke
        this.yd += 0.0006;

        this.setSpriteFromAge(this.sprites);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float t = Mth.clamp((this.age + partialTicks) / (float) this.lifetime, 0.0f, 1.0f);

        // Size: grow slightly then shrink (interpolated)
        float growPhase = Math.min(t * 3.0f, 1.0f);
        float shrinkPhase = 1.0f - smoothstep(0.6f, 1.0f, t);
        this.quadSize = baseSize * (0.7f + 0.3f * growPhase) * shrinkPhase;

        // Alpha (densityStrength): quick fade-in, slow fade-out (interpolated)
        float fadeIn = Math.min(1.0f, t / 0.1f);
        float fadeOut = 1.0f - smoothstep(0.5f, 1.0f, t);
        this.alpha = Math.min(1.0f, fadeIn * fadeOut);

        super.render(buffer, camera, partialTicks);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ModParticleRenderTypes.PENUMBRA_TRAIL_RENDER_TYPE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public PenumbraTrailParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                    double x, double y, double z,
                                                    double vx, double vy, double vz) {
            // Size range: 0.4–1.1 blocks — larger for billowy overlap
            float size = 0.4f + level.random.nextFloat() * 0.7f;
            return new PenumbraTrailParticle(level, x, y, z, vx, vy, vz, this.sprites, size);
        }
    }
}
