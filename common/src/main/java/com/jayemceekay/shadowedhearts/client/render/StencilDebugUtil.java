package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility to dump the current main framebuffer's stencil buffer to a PNG for debugging.
 * Mirrors DepthDebugUtil flow: request -> poll in render stage -> save PNG.
 */
public final class StencilDebugUtil {
    private StencilDebugUtil() {}

    private static volatile boolean pending = false;
    private static volatile Integer pendingEntityId = null;

    public static void requestStencilDump() {
        pending = true;
    }

    public static void requestEntityStencilDump(int entityId) {
        pendingEntityId = entityId;
    }

    public static boolean pollPending() {
        if (!pending) return false;
        pending = false;
        return true;
    }

    public static Integer pollPendingEntity() {
        Integer id = pendingEntityId;
        pendingEntityId = null;
        return id;
    }

    /** Save stencil buffer as an 8-bit grayscale PNG where white (255) indicates stencil value 255/1. */
    public static void saveStencilPng() {
        // Must be on render thread for GL calls
        RenderSystem.assertOnRenderThread();

        var mc = Minecraft.getInstance();
        if (mc == null) return;
        var main = mc.getMainRenderTarget();
        int srcFbo = ((RenderTargetAccessor)(Object)main).getFrameBufferId();
        int w = ((RenderTargetAccessor)(Object)main).getWidth();
        int h = ((RenderTargetAccessor)(Object)main).getHeight();
        if (w <= 0 || h <= 0) {
            toast("Invalid framebuffer size");
            return;
        }

        // Detect whether a stencil attachment exists
        int prevReadFB = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int prevPack = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            int objType = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            boolean hasStencil = (objType != GL11.GL_NONE);
            if (!hasStencil) {
                toast("No stencil attachment present; saving empty mask.");
            }

            // Read stencil as 8-bit index
            if (scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);

            ByteBuffer buf = BufferUtils.createByteBuffer(w * h);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, buf);

            writeBufferAsStencilPng(mc, w, h, buf, "stencil");
        } finally {
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevPack);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFB);
        }
    }

    public static void saveEntityStencilPng(int entityId) {
        // Build a fresh stencil mask for a specific entity, then dump it.
        RenderSystem.assertOnRenderThread();
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        var main = mc.getMainRenderTarget();
        int w = ((RenderTargetAccessor)(Object)main).getWidth();
        int h = ((RenderTargetAccessor)(Object)main).getHeight();
        if (w <= 0 || h <= 0) { toast("Invalid framebuffer size"); return; }

        var entity = mc.level.getEntity(entityId);
        if (entity == null) { toast("Entity not found for stencil dump"); return; }

        // Ensure main FBO bound for draw operations
        int srcFbo = ((RenderTargetAccessor)(Object)main).getFrameBufferId();
        int prevDrawFB = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevReadFB = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int prevStencilMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        boolean prevStencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, srcFbo);
            // Verify stencil attachment
            int objType = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (objType == GL11.GL_NONE) {
                toast("No stencil attachment on main FBO; dump will be empty");
            }
            // Prepare: enable stencil and clear to 0 (full buffer, ignore scissor)
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            if (scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);

            // Configure write: force replace regardless of depth/face; color writes off
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0xFF, 0xFF);
            GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
            GL11.glStencilMask(0xFF);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            // Disable depth testing to ensure full silhouette capture regardless of scene depth
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            // Disable face culling so both front/back contribute
            boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            if (prevCull) GL11.glDisable(GL11.GL_CULL_FACE);

            // Render the single entity
            var dispatcher = mc.getEntityRenderDispatcher();
            var camera = dispatcher.camera;
            var camPos = camera.getPosition();
            double x = entity.getX() - camPos.x;
            double y = entity.getY() - camPos.y;
            double z = entity.getZ() - camPos.z;
            var buffers = mc.renderBuffers().bufferSource();
            var ps = new com.mojang.blaze3d.vertex.PoseStack();
            dispatcher.render(entity, x, y, z, entity.getYRot(), 0.0f, ps, buffers, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
            buffers.endBatch();

            // Restore color writes and culling state
            RenderSystem.colorMask(true, true, true, true);
            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE);
            // Read back stencil
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            ByteBuffer buf = BufferUtils.createByteBuffer(w * h);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, buf);
            writeBufferAsStencilPng(mc, w, h, buf, "stencil-entity");
        } finally {
            // Restore state
            GL11.glStencilMask(prevStencilMask);
            if (!prevStencilTest) GL11.glDisable(GL11.GL_STENCIL_TEST); else GL11.glEnable(GL11.GL_STENCIL_TEST);
            // Restore depth testing enable and function
            if (prevDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(prevDepthFunc);
            RenderSystem.depthMask(prevDepthMask);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFB);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFB);
        }
    }

    private static void writeBufferAsStencilPng(Minecraft mc, int w, int h, ByteBuffer buf, String baseName) {
        buf.rewind();
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        try {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int s = buf.get() & 0xFF;
                    int g = s;
                    int argb = (0xFF << 24) | (g << 16) | (g << 8) | g;
                    img.setPixelRGBA(x, h - 1 - y, argb);
                }
            }
            File screenshots = new File(mc.gameDirectory, "screenshots");
            if (!screenshots.exists()) screenshots.mkdirs();
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File out = new File(screenshots, baseName + "-" + ts + ".png");
            img.writeToFile(out);
            toast("Saved " + out.getName());
        } catch (IOException ioe) {
            toast("Failed to save stencil PNG: " + ioe.getMessage());
        } finally {
            img.close();
        }
    }

    private static void toast(String msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[ShadowedHearts] " + msg), false);
        }
    }
}
