package com.jayemceekay.shadowedhearts.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Presents full-depth-viewport core geometry into a scaled Iris fallback color
 * attachment. The caller first restricts the scene to color output zero with
 * {@link IrisWorldColorScope}; its draw-buffer mapping is never changed here.
 */
public final class IrisScaledWorldTarget implements AutoCloseable {
    private static int scratchFramebuffer;
    private static int scratchColor;
    private static int scratchDepth;
    private static int sceneColorFramebuffer;
    private static int sceneDepthFramebuffer;
    private static int scratchWidth;
    private static int scratchHeight;
    private static int scratchColorFormat;
    private static boolean scratchInUse;

    private final int sceneFramebuffer;
    private final int colorWidth;
    private final int colorHeight;
    private final int renderWidth;
    private final int renderHeight;
    private final boolean scaled;
    private DensityFboPipeline.RenderStateScope state;
    private boolean presented;
    private boolean closed;

    private IrisScaledWorldTarget(int sceneFramebuffer, int colorWidth, int colorHeight,
                                  int renderWidth, int renderHeight, boolean scaled,
                                  DensityFboPipeline.RenderStateScope state) {
        this.sceneFramebuffer = sceneFramebuffer;
        this.colorWidth = colorWidth;
        this.colorHeight = colorHeight;
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
        this.scaled = scaled;
        this.state = state;
    }

    /**
     * Returns null for an invalid/unsupported scene or failed prefill. Unscaled
     * targets return a no-op scope. A scaled target returns with a full-size
     * scratch scene bound and containing the original scene color and depth.
     */
    public static IrisScaledWorldTarget open(int renderWidth, int renderHeight,
                                             int expectedDepthTexture) {
        if (scratchInUse || renderWidth <= 0 || renderHeight <= 0 || expectedDepthTexture <= 0) {
            return null;
        }
        DensityFboPipeline.RenderStateScope state = DensityFboPipeline.preserveRenderState();
        boolean transferred = false;
        boolean attemptedScratch = false;
        try {
            clearGlErrors();
            int sceneFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int maximumSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            if (sceneFramebuffer == 0 || renderWidth > maximumSize || renderHeight > maximumSize
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || !hasSingleColorOutput()) {
                return null;
            }
            Attachment color = inspectAttachment(GL30.GL_COLOR_ATTACHMENT0);
            Attachment depth = inspectAttachment(GL30.GL_DEPTH_ATTACHMENT);
            if (color == null || depth == null || !isSupportedColorFormat(color.format())
                    || depth.texture() != expectedDepthTexture
                    || depth.width() != renderWidth || depth.height() != renderHeight) {
                return null;
            }
            if (color.width() == renderWidth && color.height() == renderHeight) {
                return new IrisScaledWorldTarget(sceneFramebuffer, color.width(), color.height(),
                        renderWidth, renderHeight, false, null);
            }

            attemptedScratch = true;
            if (!ensureScratch(renderWidth, renderHeight, color, depth)) {
                return null;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (!blit(sceneColorFramebuffer, scratchFramebuffer,
                    color.width(), color.height(), renderWidth, renderHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR)
                    || !blit(sceneDepthFramebuffer, scratchFramebuffer,
                    renderWidth, renderHeight, renderWidth, renderHeight,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST)) {
                return null;
            }

            IrisScaledWorldTarget result = new IrisScaledWorldTarget(sceneFramebuffer,
                    color.width(), color.height(), renderWidth, renderHeight, true, state);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratchFramebuffer);
            com.mojang.blaze3d.systems.RenderSystem.viewport(0, 0, renderWidth, renderHeight);
            scratchInUse = true;
            transferred = true;
            return result;
        } finally {
            if (!transferred) {
                try {
                    if (attemptedScratch) detachSceneAttachments();
                } finally {
                    state.close();
                }
            }
        }
    }

    /**
     * Presents the finished color at its actual scene attachment size and
     * restores the updated scene depth at its original full resolution.
     * Geometry remains in the scratch scene until close restores the caller.
     */
    public boolean present() {
        if (closed) return false;
        if (!scaled || presented) return true;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        // Check both copies before publishing either attachment. Source and
        // destination depth formats were matched during open and both targets
        // retain the same full-resolution depth dimensions throughout the pass.
        if (!isComplete(sceneFramebuffer) || !isComplete(scratchFramebuffer)
                || !isComplete(sceneColorFramebuffer) || !isComplete(sceneDepthFramebuffer)) {
            return false;
        }
        if (!blit(scratchFramebuffer, sceneDepthFramebuffer,
                renderWidth, renderHeight, renderWidth, renderHeight,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST)
                || !blit(scratchFramebuffer, sceneColorFramebuffer,
                renderWidth, renderHeight, colorWidth, colorHeight,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR)) {
            return false;
        }
        presented = true;
        return true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        DensityFboPipeline.RenderStateScope savedState = state;
        state = null;
        try {
            if (scaled) detachSceneAttachments();
        } finally {
            try {
                if (savedState != null) savedState.close();
            } finally {
                if (scaled) scratchInUse = false;
            }
        }
    }

    private static void detachSceneAttachments() {
        // These cached FBOs are views of Iris-owned images. Release those
        // references before leaving a pass so a pack reload can free its old
        // storage even when Snag does not run again in the replacement pack.
        try {
            if (sceneColorFramebuffer != 0) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneColorFramebuffer);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, 0, 0);
            }
        } finally {
            if (sceneDepthFramebuffer != 0) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneDepthFramebuffer);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                        GL11.GL_TEXTURE_2D, 0, 0);
            }
        }
    }

    private static boolean hasSingleColorOutput() {
        if (GL11.glGetInteger(GL20.GL_DRAW_BUFFER0) != GL30.GL_COLOR_ATTACHMENT0) return false;
        int count = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        for (int index = 1; index < count; index++) {
            if (GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + index) != GL11.GL_NONE) return false;
        }
        return true;
    }

    private static Attachment inspectAttachment(int point) {
        if (GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, point,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_TEXTURE) {
            return null;
        }
        int texture = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                point, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        int level = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                point, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (GL11.glGetError() != GL11.GL_NO_ERROR) return null;
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        return width > 0 && height > 0 && GL11.glGetError() == GL11.GL_NO_ERROR
                ? new Attachment(texture, level, width, height, format) : null;
    }

    private static boolean ensureScratch(int width, int height, Attachment color, Attachment depth) {
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        try {
            if (scratchFramebuffer == 0 || scratchColor == 0 || scratchDepth == 0
                    || scratchWidth != width || scratchHeight != height || scratchColorFormat != color.format()) {
                destroyScratch();
                scratchFramebuffer = GL30.glGenFramebuffers();
                scratchColor = createTexture(GL11.GL_LINEAR);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, color.format(), width, height, 0,
                        GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
                if (GL11.glGetError() != GL11.GL_NO_ERROR) return false;
                scratchDepth = createTexture(GL11.GL_NEAREST);
                scratchWidth = width;
                scratchHeight = height;
                scratchColorFormat = color.format();
                sceneColorFramebuffer = GL30.glGenFramebuffers();
                sceneDepthFramebuffer = GL30.glGenFramebuffers();
            }
            // Mixed-size color/depth attachments limit the effective framebuffer
            // extent to their intersection. Separate views access every source
            // color pixel and the full depth image without clipping either copy.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sceneColorFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, color.texture(), color.level());
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return false;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sceneDepthFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depth.texture(), depth.level());
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || !DepthCopyFormat.prepareDepthTexture(sceneDepthFramebuffer, scratchDepth, width, height)) return false;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratchFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, scratchColor, 0);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, scratchDepth, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            return GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE
                    && GL11.glGetError() == GL11.GL_NO_ERROR;
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    private static int createTexture(int filter) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static boolean isComplete(int framebuffer) {
        int savedDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            return GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDraw);
        }
    }

    private static boolean blit(int source, int destination, int sourceWidth, int sourceHeight,
                                 int destinationWidth, int destinationHeight, int mask, int filter) {
        int savedRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int savedDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int sourceReadBuffer = GL11.GL_NONE;
        boolean colorCopy = (mask & GL11.GL_COLOR_BUFFER_BIT) != 0;
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
            if (colorCopy) {
                sourceReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination);
            clearGlErrors();
            GL30.glBlitFramebuffer(0, 0, sourceWidth, sourceHeight,
                    0, 0, destinationWidth, destinationHeight, mask, filter);
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        } finally {
            if (colorCopy) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
                GL11.glReadBuffer(sourceReadBuffer);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDraw);
        }
    }

    private static boolean isSupportedColorFormat(int format) {
        return switch (format) {
            case GL11.GL_RGB, GL11.GL_RGBA, GL11.GL_RGB8, GL11.GL_RGBA8,
                 GL11.GL_RGB16, GL11.GL_RGBA16, GL11.GL_RGB10_A2,
                 GL21.GL_SRGB8, GL21.GL_SRGB8_ALPHA8,
                 GL30.GL_R8, GL30.GL_R16, GL30.GL_RG8, GL30.GL_RG16,
                 GL30.GL_R16F, GL30.GL_R32F, GL30.GL_RG16F, GL30.GL_RG32F,
                 GL30.GL_RGB16F, GL30.GL_RGBA16F, GL30.GL_RGB32F, GL30.GL_RGBA32F,
                 GL30.GL_R11F_G11F_B10F -> true;
            default -> false;
        };
    }

    private static void clearGlErrors() {
        for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            // Report only the current storage/copy operation's result.
        }
    }

    private static void destroyScratch() {
        if (scratchFramebuffer != 0) GL30.glDeleteFramebuffers(scratchFramebuffer);
        if (scratchColor != 0) GL11.glDeleteTextures(scratchColor);
        if (scratchDepth != 0) GL11.glDeleteTextures(scratchDepth);
        if (sceneColorFramebuffer != 0) GL30.glDeleteFramebuffers(sceneColorFramebuffer);
        if (sceneDepthFramebuffer != 0) GL30.glDeleteFramebuffers(sceneDepthFramebuffer);
        scratchFramebuffer = 0;
        scratchColor = 0;
        scratchDepth = 0;
        sceneColorFramebuffer = 0;
        sceneDepthFramebuffer = 0;
        scratchWidth = 0;
        scratchHeight = 0;
        scratchColorFormat = 0;
    }

    /** Releases cached scratch resources during shader/resource reload. */
    public static void destroy() {
        if (scratchInUse) return;
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            destroyScratch();
        }
    }

    private record Attachment(int texture, int level, int width, int height, int format) { }
}
