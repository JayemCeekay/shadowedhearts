package com.jayemceekay.shadowedhearts.client.particle;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.registry.util.ModParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;

public class LuminousMoteParticle extends TextureSheetParticle {
    private static final float MAX_COLOSSEUM_SIZE_SCALE = 8.0f;
    // ClientLevel.addParticle creates its particle synchronously on the render thread.
    private static PokemonEntity emissionSource;
    private final SpriteSet sprites;
    private WeakReference<PokemonEntity> auraSource;
    private boolean purplePalette;
    private float normalRed;
    private float normalBlue;
    private float baseSize;
    private boolean colosseum;
    private float colosseumMotionScale = 1.0f;
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
            this.purplePalette = true;
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
        normalRed = rCol;
        normalBlue = bCol;
        setAuraSource(emissionSource);
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

        if (colosseum) {
            // Each path has its own age, phase and curvature, independent of its
            // birth direction and the world's clock. The offset is bounded.
            var drift = ColosseumMoteMotion.driftDelta(age, seed, colosseumMotionScale);
            this.move(drift.x, drift.y, drift.z);
            this.yd += ColosseumMoteMotion.riseAcceleration(seed) * colosseumMotionScale;
        } else {
            // Preserve Signature's original motion.
            double theta = (seed * Math.PI * 2.0) + (this.level.getGameTime() / 20.0) * driftSpeed;
            double cx = Math.cos(theta) * driftAmp;
            double cz = Math.sin(theta) * driftAmp;
            // Keep the bounding box in sync so the next vanilla tick retains the drift.
            this.move(cx * 0.02, 0.0, cz * 0.02);
            this.yd += 0.0005;
        }

        // Size: ease-in then ease-out for organic feel
        float size = baseSize;
                //* (easeOutQuad(Math.min(t * 2f, 1f)) * (1f - smoothstep(0.6f, 1f, t)) + 0.2f);
        this.quadSize = size;

        // Twinkle/pulse in alpha
        if (com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() < 1) {
            this.alpha = 0.55f * Math.min(1f, t / .2f) * (1f - smoothstep(.75f, 1f, t));
        } else if (colosseum) {
            this.alpha = colosseumAlpha(age, lifetime, seed);
        } else {
            float twinkle = 1f + twinkleAmp * (float) Math.sin(2 * Math.PI * twinkleHz * (this.age / 20f + seed));
            float fade = Math.min(1f, t / 0.2f) * (1f - smoothstep(0.75f, 1f, t));
            this.alpha = Math.min(1f, twinkle * fade);
        }

        // Optional sprite animation (if you pack a tiny atlas strip)
        this.setSpriteFromAge(this.sprites);
    }

    /** Applied immediately after client creation; Signature keeps its original palette and lifetime. */
    void configureColosseum(float modelSize, float motionScale, float paletteRoll, float sizeRoll, int lifetimeRoll) {
        colosseum = true;
        colosseumMotionScale = Mth.clamp(motionScale, 0.7f, 1.6f);
        LuminousMotePalette.Color color = LuminousMotePalette.colosseum(paletteRoll);
        rCol = color.red();
        gCol = color.green();
        bCol = color.blue();
        purplePalette = !Float.isFinite(paletteRoll) || paletteRoll < 0.60f;
        normalRed = rCol;
        normalBlue = bCol;
        updateAuraColor();
        // Scale directly with the posed model's average dimension, independently of motion.
        float sizeScale = Mth.clamp(modelSize, 0.7f, MAX_COLOSSEUM_SIZE_SCALE);
        baseSize = (0.012f + 0.018f * Mth.clamp(sizeRoll, 0.0f, 1.0f)) * sizeScale;
        quadSize = baseSize;
        lifetime = 20 + Mth.clamp(lifetimeRoll, 0, 14);
    }

    /** Keeps Signature's normal distance and particle-setting checks while capturing its owner. */
    static void emitForPokemon(ClientLevel level, PokemonEntity source,
                               double x, double y, double z, double vx, double vy, double vz) {
        PokemonEntity previousSource = emissionSource;
        try {
            emissionSource = source;
            level.addParticle(ModParticleTypes.LUMINOUS_MOTE.get(), x, y, z, vx, vy, vz);
        } finally {
            emissionSource = previousSource;
        }
    }

    void setAuraSource(PokemonEntity source) {
        auraSource = source == null ? null : new WeakReference<>(source);
        updateAuraColor();
    }

    private void updateAuraColor() {
        if (!purplePalette) return;
        PokemonEntity source = auraSource == null ? null : auraSource.get();
        boolean modeTint = source != null && (source.getAspects().contains(SHAspects.HYPER_MODE)
                || source.getAspects().contains(SHAspects.REVERSE_MODE));
        rCol = modeTint ? normalBlue : normalRed;
        bCol = modeTint ? normalRed : normalBlue;
    }

    static float colosseumAlpha(float age, int lifetime, float seed) {
        float t = Mth.clamp(age / (float) Math.max(1, lifetime), 0, 1);
        float twinkle = .78f + .22f * (float) Math.sin(2 * Math.PI * (.9f + .5f * seed) * (age / 20f + seed));
        float fade = Math.min(1f, t / .08f) * (1f - smoothstep(.75f, 1f, t));
        return twinkle * fade;
    }

    @Override
    public float getQuadSize(float partialTicks) {
        if (!colosseum) return super.getQuadSize(partialTicks);
        float life = Mth.clamp((age + Mth.clamp(partialTicks, 0, 1)) / Math.max(1, lifetime), 0, 1);
        return baseSize * (1f - smoothstep(.75f, 1f, life));
    }

    @Override
    public void render(VertexConsumer vertices, Camera camera, float partialTicks) {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()
                || com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() == 0) return;
        // Colosseum draws after smoke. Signature keeps the engine pass except
        // for Fabulous, whose alpha-layer combine cannot preserve additive light.
        if (!colosseum) {
            if (LuminousMoteFabulousRenderer.queue(this)) return;
            updateAuraColor();
            super.render(vertices, camera, partialTicks);
        }
    }

    void renderFabulousLate(VertexConsumer vertices, Camera camera, float partialTicks) {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()
                || com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() == 0) return;
        if (colosseum || !isAlive()) return;
        updateAuraColor();
        super.render(vertices, camera, partialTicks);
    }

    void renderColosseumLate(VertexConsumer vertices, Camera camera, float partialTicks) {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()
                || com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() == 0) return;
        if (!colosseum || !isAlive()) return;
        updateAuraColor();
        float savedAlpha = alpha;
        try {
            // Evaluate presentation between engine ticks; do not mutate its
            // persistent alpha or movement when this frame is drawn again.
            if (com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() >= 1) {
                alpha = colosseumAlpha(age + Mth.clamp(partialTicks, 0, 1), lifetime, seed);
            }
            super.render(vertices, camera, partialTicks);
        } finally {
            alpha = savedAlpha;
        }
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
        // Block rendering can reseed level.random every frame and repeat the same sizes.
        private final RandomSource random = RandomSource.create();

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
            float size = (0.005f + random.nextFloat() * 0.05f) * scaleHint; // vary size
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
