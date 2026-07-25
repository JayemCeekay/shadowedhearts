package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link MultiBufferSource} wrapper that intercepts {@code getBuffer()} calls
 * and redirects entity render types (entityCutout, entityCutoutNoCull, etc.)
 * to the snag dissolve shader, while passing through all other render types.
 *
 * <p>Before each draw call, this also binds the dissolve noise texture to
 * Sampler3 and uploads the dissolve progress uniform.
 */
public class DissolveBufferSource implements MultiBufferSource {

    private static final ResourceLocation DISSOLVE_NOISE_TEX =
            ResourceLocation.parse("shadowedhearts:textures/vfx/dissolve_noise.png");

    private final MultiBufferSource delegate;
    private final ResourceLocation entityTexture;
    private final float dissolveProgress;
    private final float time;
    private final float edgeR;
    private final float edgeG;
    private final float edgeB;
    private final float edgeWidth;
    private final float noiseScale;
    private final float conversionR;
    private final float conversionG;
    private final float conversionB;
    private final float conversionStrength;

    /**
     * Wraps a buffer source so eligible entity geometry is rendered with the
     * snag dissolve shader.
     *
     * @param delegate original buffer source supplied by the entity renderer
     * @param entityTexture texture normally used by the entity render type
     * @param dissolveProgress normalized dissolve amount, where {@code 0} is
     *                         intact and {@code 1} is fully dissolved
     * @param time animation time passed to the noise shader
     */
    public DissolveBufferSource(MultiBufferSource delegate, ResourceLocation entityTexture,
                                float dissolveProgress, float time) {
        this(delegate, entityTexture, dissolveProgress, time,
                0.72f, 0.30f, 1.0f,
                0.08f, 3.0f,
                0.0f, 0.0f, 0.0f, 0.0f);
    }

    public DissolveBufferSource(MultiBufferSource delegate, ResourceLocation entityTexture,
                                float dissolveProgress, float time,
                                float edgeR, float edgeG, float edgeB,
                                float edgeWidth, float noiseScale,
                                float conversionR, float conversionG, float conversionB,
                                float conversionStrength) {
        this.delegate = delegate;
        this.entityTexture = entityTexture;
        this.dissolveProgress = dissolveProgress;
        this.time = time;
        this.edgeR = edgeR;
        this.edgeG = edgeG;
        this.edgeB = edgeB;
        this.edgeWidth = edgeWidth;
        this.noiseScale = noiseScale;
        this.conversionR = conversionR;
        this.conversionG = conversionG;
        this.conversionB = conversionB;
        this.conversionStrength = conversionStrength;
    }

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType renderType) {
        // Only replace model/material buffers. Non-entity buffers continue to
        // the delegate so held items, debug overlays, and unrelated layers do
        // not inherit dissolve state accidentally.
        String name = renderType.toString();
        if (isEntityRenderType(name) && shaderAvailable()) {
            // Bind noise and uniforms before returning the replacement buffer;
            // RenderType setup will bind Sampler0 from the entity texture.
            bindDissolveNoise();
            applyShaderUniforms();
            return delegate.getBuffer(BallRenderTypes.dissolve(entityTexture));
        }
        return delegate.getBuffer(renderType);
    }

    private boolean isEntityRenderType(String name) {
        return name.contains("entity_cutout")
                || name.contains("entity_translucent")
                || name.contains("entity_solid");
    }

    private void bindDissolveNoise() {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(DISSOLVE_NOISE_TEX);
        RenderSystem.setShaderTexture(3, tex.getId());
    }

    private boolean shaderAvailable() {
        return ModShaders.SNAG_DISSOLVE != null;
    }

    private void applyShaderUniforms() {
        ShaderInstance shader = ModShaders.SNAG_DISSOLVE;
        if (shader == null) return;

        // Uniforms are optional so the wrapper remains compatible with shader
        // variants used while tuning the dissolve effect.
        if (shader.getUniform("u_dissolveProgress") != null) {
            shader.getUniform("u_dissolveProgress").set(dissolveProgress);
        }
        if (shader.getUniform("u_dissolveColor") != null) {
            shader.getUniform("u_dissolveColor").set(edgeR, edgeG, edgeB);
        }
        if (shader.getUniform("u_edgeWidth") != null) {
            shader.getUniform("u_edgeWidth").set(edgeWidth);
        }
        if (shader.getUniform("u_noiseScale") != null) {
            shader.getUniform("u_noiseScale").set(noiseScale);
        }
        if (shader.getUniform("u_time") != null) {
            shader.getUniform("u_time").set(time);
        }
        if (shader.getUniform("u_conversionColor") != null) {
            shader.getUniform("u_conversionColor").set(conversionR, conversionG, conversionB);
        }
        if (shader.getUniform("u_conversionStrength") != null) {
            shader.getUniform("u_conversionStrength").set(conversionStrength);
        }
    }
}
