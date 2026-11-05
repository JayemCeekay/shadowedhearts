package com.jayemceekay.shadowedhearts.client.particle;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

import java.util.ArrayList;
import java.util.List;

public class ModParticleRenderTypes {

    public static final ParticleRenderType PARTICLE_SHEET_ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager texManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.SRC_ALPHA); // additive
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() { return "PARTICLE_SHEET_ADDITIVE"; }
    };

    public static final ParticleRenderType LUMINOUS_MOTE_RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager texManager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); // Additive
            RenderSystem.setShader(() -> ModShaders.LUMINOUS_MOTE);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() { return "LUMINOUS_MOTE_RENDER_TYPE"; }
    };

    public static final ParticleRenderType PENUMBRA_TRAIL_RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager texManager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            // Keep a visible fallback pass while density FBO debugging is active.
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(() -> ModShaders.PENUMBRA_TRAIL);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() { return "PENUMBRA_TRAIL_RENDER_TYPE"; }
    };

    /** Fabric's particle engine visits only this list; NeoForge already visits every registered layer. */
    public static List<ParticleRenderType> appendMissingRenderTypes(List<ParticleRenderType> renderOrder) {
        List<ParticleRenderType> extended = new ArrayList<>(renderOrder);
        for (ParticleRenderType type : List.of(LUMINOUS_MOTE_RENDER_TYPE, PENUMBRA_TRAIL_RENDER_TYPE)) {
            if (!extended.contains(type)) extended.add(type);
        }
        return List.copyOf(extended);
    }

}
