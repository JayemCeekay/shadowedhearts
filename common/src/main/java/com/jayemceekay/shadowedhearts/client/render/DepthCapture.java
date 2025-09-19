package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public final class DepthCapture {
    private static int depthTex = 0;   // GL texture id
    private static int blitFbo  = 0;   // FBO whose depth attachment is depthTex
    private static int w = 0, h = 0;
    private static int lastFrameCopied = -1; // unused; kept for potential future frame gating
    private static boolean blitCompatible = true; // skip blit when source uses unsized depth format
    private static int srcDepthInternalCached = 0;
    // Ensure the main render target has a stencil attachment early in the frame,
    // before any code tries to use stencil in later passes (e.g., ShadowAuraRenderer).
    private static boolean stencilEnsured = false;

    private DepthCapture() {}

    public static void init() {
        //ensureAllocated(currentWidth(), currentHeight());
    }

    public static void onResize(int width, int height) {
        destroy();
        ensureAllocated(width, height);
        stencilEnsured = false;
    }

    public static void destroy() {
        if (blitFbo != 0) { GL30.glDeleteFramebuffers(blitFbo); blitFbo = 0; }
        if (depthTex != 0) { GL11.glDeleteTextures(depthTex);   depthTex = 0; }
        w = h = 0;
        stencilEnsured = false;
    }

    private static void ensureAllocated(int width, int height) {
        if (width <= 0 || height <= 0) return;
        w = width; h = height;

        // Detect source depth internal format so our destination matches for blit compatibility
        int srcDepthInternal = detectMainDepthInternalFormat();
        int[] fmt = chooseDepthTexFormat(srcDepthInternal);
        int internalFormat = fmt[0];
        int pixelFormat    = fmt[1];
        int pixelType      = fmt[2];
        int attachment     = fmt[3];

        srcDepthInternalCached = srcDepthInternal;
        blitCompatible = (srcDepthInternal != GL11.GL_DEPTH_COMPONENT);

        depthTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0,
                pixelFormat, pixelType, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        blitFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blitFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment,
                GL11.GL_TEXTURE_2D, depthTex, 0);
        // Ensure no color buffers are active for this depth-only FBO
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("DepthCapture FBO incomplete: 0x" + Integer.toHexString(status));
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static int currentWidth() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        return ((RenderTargetAccessor)(Object)main).getWidth();
    }
    private static int currentHeight() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        return ((RenderTargetAccessor)(Object)main).getHeight();
    }

    // Detect the internal format of the main framebuffer's depth attachment
    private static int detectMainDepthInternalFormat() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int srcFbo = ((RenderTargetAccessor)(Object)main).getFrameBufferId();

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevRb = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        int prevTex2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            int objType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (objType == GL11.GL_NONE) return 0;
            int objName = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (objName == 0) return 0;

            if (objType == GL30.GL_RENDERBUFFER) {
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, objName);
                return GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
            } else if (objType == GL11.GL_TEXTURE) {
                // Probe non-multisample texture level parameters only; avoid MS targets to stay profile-safe
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, objName);
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int internal = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
                if (width != 0) return internal;
                // If querying failed or was not applicable, fall back to unknown
            }
        } catch (Throwable ignored) {
        } finally {
            // restore
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, prevRb);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2D);
        }
        return 0;
    }

    // Choose depth texture format: returns {internalFormat, pixelFormat, pixelType, attachment}
    private static int[] chooseDepthTexFormat(int srcInternal) {
        if (srcInternal == 0) {
            return new int[] { GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, GL30.GL_DEPTH_ATTACHMENT };
        }
        switch (srcInternal) {
            case GL30.GL_DEPTH32F_STENCIL8:
                // allocate depth-stencil with matching 32F depth, attach as depth-stencil
                return new int[] { GL30.GL_DEPTH32F_STENCIL8, GL30.GL_DEPTH_STENCIL, GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV, GL30.GL_DEPTH_STENCIL_ATTACHMENT };
            case GL30.GL_DEPTH_COMPONENT32F:
                return new int[] { GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, GL30.GL_DEPTH_ATTACHMENT };
            case GL30.GL_DEPTH24_STENCIL8:
                // depth-stencil 24/8
                return new int[] { GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, GL30.GL_DEPTH_STENCIL_ATTACHMENT };
            case GL14.GL_DEPTH_COMPONENT24:
                return new int[] { GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, GL30.GL_DEPTH_ATTACHMENT };
            case GL14.GL_DEPTH_COMPONENT16:
                return new int[] { GL14.GL_DEPTH_COMPONENT16, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_SHORT, GL30.GL_DEPTH_ATTACHMENT };
            default:
                // Fallback to a widely supported format
                return new int[] { GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, GL30.GL_DEPTH_ATTACHMENT };
        }
    }

    /** Copy depth from the main FBO -> our depthTex. Safe to call multiple times per frame. */
    public static void captureIfNeeded() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        // In MC 1.21.1 (Architectury common), RenderTarget has no enableStencil().
        // Proceed without forcing stencil; downstream code checks attachments as needed.
        stencilEnsured = true;
        int srcFbo = ((RenderTargetAccessor)(Object)main).getFrameBufferId();
        int mw = ((RenderTargetAccessor)(Object)main).getWidth();
        int mh = ((RenderTargetAccessor)(Object)main).getHeight();

        if (mw != w || mh != h || depthTex == 0 || blitFbo == 0) {
            onResize(mw, mh);
        }

        // If formats are incompatible (e.g., source uses unsized GL_DEPTH_COMPONENT), use a fallback copy.
        if (!blitCompatible) {
            // Fallback: copy depth directly into our texture using glCopyTexSubImage2D.
            // This avoids the need for matching internal formats and still works with renderbuffers.
            int prevReadFB = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevTex2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0;
            boolean scissorEnabledFB = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            try {
                if (scissorEnabledFB) GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
                // Bind our destination depth texture and copy from the current read framebuffer's depth buffer
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
                // Ensure the texture storage exists at the correct size
                if (mw != w || mh != h) {
                    // reallocate to match source if something changed unexpectedly
                    onResize(mw, mh);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
                }
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            } finally {
                if (scissorEnabledFB) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFB);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2D);
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + Math.max(prevActive, 0));
            }
            return;
        }

        // Blit depth (handles MSAA resolve if the source is multisampled)
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            // Scissor test affects glBlitFramebuffer for depth/stencil; disable to copy the full buffer
            if (scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blitFbo);
            GL30.glBlitFramebuffer(0, 0, mw, mh, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            // Restore previous GL state
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        }
    }

    /** Bind the captured depth texture on a texture unit for your shader.
     * Restores the previously active texture unit to avoid affecting subsequent rendering. */
    public static void bind(int textureUnit) {
        if (depthTex == 0) return;
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0;
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
        // Restore previous active texture unit so vanilla bindings are unaffected
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + Math.max(prevActive, 0));
    }

    public static int width()  { return w; }
    public static int height() { return h; }
    public static int textureId() { return depthTex; }

    /** Read the captured depth buffer into the provided FloatBuffer (size >= w*h). */
    public static boolean readDepth(java.nio.FloatBuffer out) {
        if (!blitCompatible) return false; // force fallback when we skipped blit
        if (blitFbo == 0 || w <= 0 || h <= 0 || out == null || out.remaining() < w * h) return false;
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevPack = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, blitFbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, out);
            return true;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevPack);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        }
    }

    /** Debug: Read directly from the main render target's depth buffer into out (size >= w*h). */
    public static boolean readMainDepth(java.nio.FloatBuffer out) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int srcFbo = ((RenderTargetAccessor)(Object)main).getFrameBufferId();
        int mw = ((RenderTargetAccessor)(Object)main).getWidth();
        int mh = ((RenderTargetAccessor)(Object)main).getHeight();
        if (out == null || out.remaining() < mw * mh) return false;
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevPack = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, mw, mh, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, out);
            return true;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevPack);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        }
    }
}