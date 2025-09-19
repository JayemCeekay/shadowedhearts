package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Small utility to introspect the main framebuffer's depth attachment and related GL state.
 * Helps understand how Minecraft configures its depth buffer on the running system.
 */
public final class DepthInfoUtil {
    private DepthInfoUtil() {}

    private static volatile boolean pending = false;

    public static void requestDump() {
        pending = true;
    }

    public static boolean pollPending() {
        if (!pending) return false;
        pending = false;
        return true;
    }

    public static void dumpMainDepthInfo() {
        RenderSystem.assertOnRenderThread();

        var mc = Minecraft.getInstance();
        var rt = mc.getMainRenderTarget();
        var acc = (RenderTargetAccessor)(Object)rt;
        int fbo = acc.getFrameBufferId();
        int w = acc.getWidth();
        int h = acc.getHeight();
        int vw = acc.getViewWidth();
        int vh = acc.getViewHeight();

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);

        int objType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int objName = 0;
        if (objType != GL11.GL_NONE) {
            objName = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        }

        String attachStr;
        String detStr = "";
        if (objType == GL11.GL_NONE || objName == 0) {
            attachStr = "none";
        } else if (objType == GL30.GL_RENDERBUFFER) {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, objName);
            int internal = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
            int rw = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_WIDTH);
            int rh = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_HEIGHT);
            int samples = 0;
            if (GL.getCapabilities().OpenGL30) {
                samples = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_SAMPLES);
            }
            attachStr = "renderbuffer";
            detStr = String.format("id=%d fmt=0x%X size=%dx%d samples=%d", objName, internal, rw, rh, samples);
        } else if (objType == GL11.GL_TEXTURE) {
            // Try as 2D first
            int internal = 0, tw = 0, th = 0, samples = 0;
            boolean isMS = false;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, objName);
            internal = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            tw = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            th = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

            if (tw == 0) {
                // Could be a multisample texture or unbound level; skip MS queries to be profile-safe.
            }

            attachStr = isMS ? "texture2DMS" : "texture2D";
            detStr = String.format("id=%d fmt=0x%X size=%dx%d samples=%d", objName, internal, tw, th, samples);
        } else {
            attachStr = "unknown(0x" + Integer.toHexString(objType) + ")";
        }

        // Global state
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean msaa = GL11.glIsEnabled(GL13.GL_MULTISAMPLE);
        int samplesGlobal = 0;
        try {
            samplesGlobal = GL11.glGetInteger(GL13.GL_SAMPLES);
        } catch (Throwable ignored) {}
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

        // Report
        toast(String.format("Main FBO=%d size=%dx%d view=%dx%d", fbo, w, h, vw, vh));
        toast(String.format("Depth attachment: %s %s", attachStr, detStr));
        toast(String.format("DepthTest=%s func=0x%X Scissor=%s MSAA=%s(samples=%d) Viewport=[%d,%d %dx%d]",
                depthTest, depthFunc, scissor, msaa, samplesGlobal, vp[0], vp[1], vp[2], vp[3]));

        // Restore bindings
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
    }

    private static void toast(String msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[ShadowedHearts] " + msg), false);
        }
    }
}
