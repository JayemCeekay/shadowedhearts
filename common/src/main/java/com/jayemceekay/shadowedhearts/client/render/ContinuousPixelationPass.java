package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundPixelationScale;
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundPixelationTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/** Exact coverage filtering of a completed, premultiplied effect, with independent world/GUI storage. */
public final class ContinuousPixelationPass {
    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION).build();
    private final Slot world = new Slot();
    private final Slot gui = new Slot();

    public boolean render(ShaderInstance resolve, ShaderInstance present, MoonwoundPixelationScale.Grid grid,
                          boolean inGui, int depthFramebuffer, int depthWidth, int depthHeight,
                          Runnable capture, float minU, float minV, float maxU, float maxV) {
        Slot slot = inGui ? gui : world;
        if (slot.failed || resolve == null || present == null || grid == null || capture == null
                || !Float.isFinite(grid.cellSize()) || grid.cellSize() <= 1
                || grid.width() <= 0 || grid.height() <= 0
                || !Float.isFinite(grid.originX()) || !Float.isFinite(grid.originY())
                || !Float.isFinite(grid.blend()) || grid.blend() < 0 || grid.blend() > 1
                || !Float.isFinite(minU) || !Float.isFinite(minV) || !Float.isFinite(maxU) || !Float.isFinite(maxV)
                || maxU <= minU || maxV <= minV) return false;
        int[] samplers = new int[5];
        for (int unit = 0; unit < samplers.length; unit++)
            samplers[unit] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit);
        boolean separable = MoonwoundPixelationTarget.requiresSeparableResolve(grid.cellSize());
        try (var saved = DensityFboPipeline.preserveRenderState();
             var target = slot.target.openFromDepth(grid.width(), grid.height(), separable,
                     depthFramebuffer, depthWidth, depthHeight)) {
            if (target == null) {
                slot.failed = true;
                return false;
            }
            for (int unit = 0; unit < samplers.length; unit++) GL33.glBindSampler(unit, 0);
            RenderSystem.viewport(0, 0, target.width(), target.height());
            capture.run();

            // Reduction writes only our private depth images. It must never test
            // against the current main target, whose depth may be invalid in Fabulous.
            RenderSystem.enableDepthTest();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            GL11.glDepthFunc(GL11.GL_ALWAYS);
            RenderSystem.depthMask(true);
            GL11.glDepthMask(true);
            RenderSystem.disableBlend();
            GL11.glDisable(GL11.GL_BLEND);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            bind(resolve, target.sourceColor(), target.sourceDepth(), grid.cellSize());
            resolve.safeGetUniform("SourceSize").set((float) target.width(), (float) target.height());
            resolve.safeGetUniform("GridOrigin").set(grid.originX(), grid.originY());
            resolve.safeGetUniform("EnergyCoverage").set(1);
            if (separable) {
                target.bindHorizontal();
                RenderSystem.viewport(0, 0, target.gridWidth(), target.height());
                resolve.safeGetUniform("ResolvePass").set(1);
                fullscreen();
                bind(resolve, target.horizontalColor(), target.horizontalDepth(), grid.cellSize());
            }
            target.bindResolve();
            RenderSystem.viewport(0, 0, target.gridWidth(), target.gridHeight());
            resolve.safeGetUniform("ResolvePass").set(separable ? 2 : 0);
            fullscreen();

            target.bindOutput();
            RenderSystem.viewport(target.viewportX(), target.viewportY(), target.width(), target.height());
            RenderSystem.disableDepthTest();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            RenderSystem.depthMask(false);
            GL11.glDepthMask(false);
            RenderSystem.enableBlend();
            GL11.glEnable(GL11.GL_BLEND);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            bind(present, target.pixelColor(), target.pixelDepth(), grid.cellSize());
            RenderSystem.setShaderTexture(2, target.sourceColor());
            RenderSystem.setShaderTexture(3, target.sourceDepth());
            present.setSampler("Sampler2", target.sourceColor());
            present.setSampler("Sampler3", target.sourceDepth());
            present.safeGetUniform("GridOrigin").set(target.viewportX() + grid.originX(),
                    target.viewportY() + grid.originY());
            present.safeGetUniform("GridSize").set((float) target.gridWidth(), (float) target.gridHeight());
            present.safeGetUniform("ViewportOrigin").set((float) target.viewportX(), (float) target.viewportY());
            present.safeGetUniform("PixelBlend").set(grid.blend());
            present.safeGetUniform("EnergyCoverage").set(1);
            present.safeGetUniform("SceneOcclusion").set(inGui ? 0 : 1);
            present.safeGetUniform("ClipEnabled").set(1);
            present.safeGetUniform("ClipBounds").set(target.viewportX() + Math.max(0, minU) * target.width(),
                    target.viewportY() + Math.max(0, minV) * target.height(),
                    target.viewportX() + Math.min(1, maxU) * target.width(),
                    target.viewportY() + Math.min(1, maxV) * target.height());
            fullscreen();
            return true;
        } catch (RuntimeException failure) {
            slot.failed = true;
            return false;
        } finally {
            for (int unit = 0; unit < samplers.length; unit++) GL33.glBindSampler(unit, samplers[unit]);
        }
    }

    public void destroy() {
        world.target.destroy();
        gui.target.destroy();
        world.failed = false;
        gui.failed = false;
    }

    private static void bind(ShaderInstance shader, int color, int depth, float size) {
        RenderSystem.setShaderTexture(0, color);
        RenderSystem.setShaderTexture(1, depth);
        shader.setSampler("Sampler0", color);
        shader.setSampler("Sampler1", depth);
        shader.safeGetUniform("PixelSize").set(size);
        RenderSystem.setShader(() -> shader);
    }

    private static void fullscreen() {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(4 * FORMAT.getVertexSize())) {
            BufferBuilder vertices = new BufferBuilder(memory, VertexFormat.Mode.QUADS, FORMAT);
            vertices.addVertex(-1, -1, 0);
            vertices.addVertex(1, -1, 0);
            vertices.addVertex(1, 1, 0);
            vertices.addVertex(-1, 1, 0);
            BufferUploader.drawWithShader(vertices.buildOrThrow());
        }
    }

    private static final class Slot {
        final MoonwoundPixelationTarget target = new MoonwoundPixelationTarget();
        boolean failed;
    }
}
