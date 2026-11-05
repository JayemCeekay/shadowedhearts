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
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

/** Ordinary alpha-blended smoke used briefly around a Void Shadow shapeshift. */
public final class VoidShadowCloudParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float phaseSeed;
    private final float curlDirection;
    private float baseDiameter;

    private VoidShadowCloudParticle(ClientLevel level, double x, double y, double z,
                                    double vx, double vy, double vz, SpriteSet sprites,
                                    float diameter, int lifetime, float seed, float direction, float tintRoll) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.phaseSeed = seed;
        this.curlDirection = direction;
        this.baseDiameter = VoidShadowCloudMotion.clampDiameter(diameter);
        this.lifetime = lifetime;
        this.gravity = 0;
        this.hasPhysics = false;
        this.friction = 0.90f;
        this.setSize(0.01f, 0.01f);
        this.xd = VoidShadowCloudMotion.velocity(vx);
        this.yd = VoidShadowCloudMotion.velocity(vy);
        this.zd = VoidShadowCloudMotion.velocity(vz);
        // Dark violet stays near black; the vanilla smoke alpha gives its soft edge.
        this.rCol = 0.035f + 0.020f * tintRoll;
        this.gCol = 0.018f + 0.010f * tintRoll;
        this.bCol = 0.055f + 0.025f * tintRoll;
        this.alpha = VoidShadowCloudMotion.alpha(0, lifetime);
        this.quadSize = VoidShadowCloudMotion.diameter(baseDiameter, 0, lifetime) * 0.5f;
        this.roll = seed * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.setSpriteFromAge(sprites);
    }

    /** Configure each puff after ParticleEngine.createParticle; units are full peak diameter in blocks. */
    public VoidShadowCloudParticle setCloudDiameter(float diameterBlocks) {
        this.baseDiameter = VoidShadowCloudMotion.clampDiameter(diameterBlocks);
        this.quadSize = VoidShadowCloudMotion.diameter(baseDiameter, age, lifetime) * 0.5f;
        return this;
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (!this.isAlive()) return;
        var drift = VoidShadowCloudMotion.driftDelta(age, phaseSeed, curlDirection, baseDiameter);
        // Particle.move keeps the bounding box synchronized with the extra curl.
        this.move(drift.x(), drift.y(), drift.z());
        this.yd = Math.min(0.03, this.yd + 0.0008);
        this.roll += curlDirection * (0.025f + 0.015f * phaseSeed);
        this.alpha = VoidShadowCloudMotion.alpha(age, lifetime);
        this.quadSize = VoidShadowCloudMotion.diameter(baseDiameter, age, lifetime) * 0.5f;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return VoidShadowCloudMotion.diameter(baseDiameter, age + Mth.clamp(partialTicks, 0, 1), lifetime) * 0.5f;
    }

    @Override
    public void render(VertexConsumer vertices, Camera camera, float partialTicks) {
        float savedAlpha = this.alpha;
        try {
            this.alpha = VoidShadowCloudMotion.alpha(age + Mth.clamp(partialTicks, 0, 1), lifetime);
            super.render(vertices, camera, partialTicks);
        } finally {
            this.alpha = savedAlpha;
        }
    }

    @Override
    protected int getLightColor(float partialTicks) {
        // Stable dark pigment under day/night lighting; low RGB remains dark even at full light.
        return 0xF000F0;
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        // Rendering may reseed level.random. This sequence belongs only to this effect.
        private final RandomSource effectRandom = RandomSource.create();

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public VoidShadowCloudParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                      double x, double y, double z,
                                                      double vx, double vy, double vz) {
            float diameter = 0.55f + effectRandom.nextFloat() * 0.40f;
            int lifetime = 18 + effectRandom.nextInt(9);
            float seed = effectRandom.nextFloat();
            float direction = effectRandom.nextBoolean() ? -1 : 1;
            float tint = effectRandom.nextFloat();
            return new VoidShadowCloudParticle(level, x, y, z, vx, vy, vz,
                    sprites, diameter, lifetime, seed, direction, tint);
        }
    }
}
