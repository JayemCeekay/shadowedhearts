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

    public DissolveBufferSource(MultiBufferSource delegate, ResourceLocation entityTexture,
                                float dissolveProgress, float time) {
        this.delegate = delegate;
        this.entityTexture = entityTexture;
        this.dissolveProgress = dissolveProgress;
        this.time = time;
    }

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType renderType) {
        // Check if this is an entity render type we should redirect
        String name = renderType.toString();
        if (isEntityRenderType(name) && ModShaders.SNAG_DISSOLVE != null) {
            // Bind dissolve noise to texture unit 3 (Sampler3)
            bindDissolveNoise();
            // Upload dissolve uniforms
            applyDissolveUniforms();
            // Redirect to our dissolve render type
            RenderType dissolveType = BallRenderTypes.dissolve(entityTexture);
            return delegate.getBuffer(dissolveType);
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

    private void applyDissolveUniforms() {
        ShaderInstance shader = ModShaders.SNAG_DISSOLVE;
        if (shader == null) return;

        if (shader.getUniform("u_dissolveProgress") != null) {
            shader.getUniform("u_dissolveProgress").set(dissolveProgress);
        }
        if (shader.getUniform("u_dissolveColor") != null) {
            // Purple-magenta edge glow matching Colosseum snag energy
            shader.getUniform("u_dissolveColor").set(0.72f, 0.30f, 1.0f);
        }
        if (shader.getUniform("u_edgeWidth") != null) {
            shader.getUniform("u_edgeWidth").set(0.08f);
        }
        if (shader.getUniform("u_noiseScale") != null) {
            shader.getUniform("u_noiseScale").set(3.0f);
        }
        if (shader.getUniform("u_time") != null) {
            shader.getUniform("u_time").set(time);
        }
    }
}
