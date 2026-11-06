package com.jayemceekay.shadowedhearts.client.particle;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisCompat;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Keeps additive Signature motes out of Fabulous' ordinary alpha-layer combine. */
public final class LuminousMoteFabulousRenderer {
    private static final int MAX_QUEUED_MOTES = 16384;
    private static final List<LuminousMoteParticle> MOTES = new ArrayList<>();
    private static ClientLevel sourceLevel;
    private static ResourceKey<Level> sourceDimension;
    private static long frameTimeNs = Long.MIN_VALUE;

    private LuminousMoteFabulousRenderer() { }

    /** ParticleEngine retains tick, lifetime and culling ownership. */
    static boolean queue(LuminousMoteParticle mote) {
        if (!usesLatePass() || ModShaders.LUMINOUS_MOTE_FABULOUS == null) return false;
        synchronizeFrame();
        if (sourceLevel == null || MOTES.size() >= MAX_QUEUED_MOTES) return false;
        MOTES.add(mote);
        return true;
    }

    public static boolean hasQueued() {
        synchronizeFrame();
        return usesLatePass() && !MOTES.isEmpty();
    }

    public static void clear() {
        MOTES.clear();
        sourceLevel = null;
        sourceDimension = null;
        frameTimeNs = Long.MIN_VALUE;
    }

    /** Caller installs matching world matrices and its retained world-depth output target. */
    public static void render(Camera camera, float partialTicks) {
        try {
            if (!hasQueued() || camera == null || ModShaders.LUMINOUS_MOTE_FABULOUS == null) return;
            try (var state = DensityFboPipeline.preserveRenderState()) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.disableScissor();
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.setShader(() -> ModShaders.LUMINOUS_MOTE_FABULOUS);
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
                var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                for (LuminousMoteParticle mote : MOTES) {
                    mote.renderFabulousLate(buffer, camera, partialTicks);
                }
                var mesh = buffer.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
        } finally {
            clear();
        }
    }

    private static boolean usesLatePass() {
        var iris = IrisCompat.IRIS_HANDLER;
        return Minecraft.useShaderTransparency()
                && (iris == null || (!iris.isShaderPackInUse() && !iris.isShadowRenderActive()));
    }

    private static void synchronizeFrame() {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var dimension = level == null ? null : level.dimension();
        long frame = minecraft.getFrameTimeNs();
        if (sourceLevel != level || sourceDimension != dimension || frameTimeNs != frame) {
            MOTES.clear();
            sourceLevel = level;
            sourceDimension = dimension;
            frameTimeNs = frame;
        }
    }
}
