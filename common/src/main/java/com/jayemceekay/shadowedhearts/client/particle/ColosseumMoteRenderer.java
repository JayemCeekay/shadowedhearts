package com.jayemceekay.shadowedhearts.client.particle;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.ArrayList;
import java.util.List;

/** Retains only the late draw ownership; ParticleEngine still ticks and expires each mote. */
final class ColosseumMoteRenderer {
    private static final int MAX_RETAINED_PARTICLES = 768;
    private static final List<LuminousMoteParticle> PARTICLES = new ArrayList<>();
    private static ClientLevel sourceLevel;

    private ColosseumMoteRenderer() {}

    static void track(ClientLevel level, LuminousMoteParticle particle) {
        if (sourceLevel != level) clear();
        sourceLevel = level;
        PARTICLES.removeIf(mote -> !mote.isAlive());
        if (PARTICLES.size() >= MAX_RETAINED_PARTICLES) {
            particle.remove();
            return;
        }
        PARTICLES.add(particle);
    }

    static boolean hasParticles() {
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || sourceLevel != minecraft.level) clear();
        PARTICLES.removeIf(mote -> !mote.isAlive());
        return !PARTICLES.isEmpty();
    }

    static void clear() {
        for (LuminousMoteParticle particle : PARTICLES) particle.remove();
        PARTICLES.clear();
        sourceLevel = null;
    }

    static void render(Camera camera, float partialTicks, int sceneDepthTexture, int width, int height) {
        if (!hasParticles() || camera == null || sceneDepthTexture <= 0 || width <= 0 || height <= 0
                || ModShaders.LUMINOUS_MOTE_COLOSSEUM == null) return;
        // The caller has installed the matching world matrices, main target and viewport.
        // Scene depth is sampled explicitly because Iris final output depth may not be world depth.
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.setShader(() -> ModShaders.LUMINOUS_MOTE_COLOSSEUM);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.setShaderTexture(1, sceneDepthTexture);
            var size = ModShaders.LUMINOUS_MOTE_COLOSSEUM.getUniform("ScreenSize");
            if (size != null) size.set((float) width, (float) height);
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            for (LuminousMoteParticle particle : PARTICLES) {
                particle.renderColosseumLate(buffer, camera, partialTicks);
            }
            var mesh = buffer.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }
    }
}
