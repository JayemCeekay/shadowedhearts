package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.client.render.DepthCopyFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;

/** Private effect images; scene depth is copied so geometry can write depth without modifying the world. */
public final class MoonwoundPixelationTarget {
    public static final float DIRECT_RESOLVE_LIMIT = 16;
    private int sourceFramebuffer;
    private int sourceColor;
    private int sourceDepth;
    private int width;
    private int height;
    // Active grid dimensions may change continuously. Keep one capacity that only
    // grows until the source viewport changes, so camera motion does not churn images.
    private PixelTarget pixelTarget;
    private PixelTarget horizontalTarget;
    private boolean inUse;

    /** Called on the render thread. A successful scope starts with the full-resolution effect target bound. */
    public Scope open(int gridWidth, int gridHeight) {
        return open(gridWidth, gridHeight, false);
    }

    /** Larger cells use two exact one-dimensional reductions instead of a long nested fragment loop. */
    public static boolean requiresSeparableResolve(float span) {
        return span > DIRECT_RESOLVE_LIMIT;
    }

    public Scope open(int gridWidth, int gridHeight, boolean separable) {
        return openInternal(gridWidth, gridHeight, separable, -1, null);
    }

    /** Captures the current output while copying pristine depth from a separately owned effect target. */
    public Scope openFromDepth(int gridWidth, int gridHeight, boolean separable,
                               int depthFramebuffer, int depthWidth, int depthHeight) {
        if (depthFramebuffer <= 0 || depthWidth <= 0 || depthHeight <= 0) return null;
        return openInternal(gridWidth, gridHeight, separable, depthFramebuffer,
                new int[] {0, 0, depthWidth, depthHeight});
    }

    private Scope openInternal(int gridWidth, int gridHeight, boolean separable,
                               int explicitDepthFramebuffer, int[] explicitDepthViewport) {
        if (inUse || gridWidth <= 0 || gridHeight <= 0) return null;
        SavedState saved = SavedState.capture();
        boolean opened = false;
        try {
            clearErrors();
            int outputWidth = saved.viewport[2], outputHeight = saved.viewport[3];
            int depthFramebuffer = explicitDepthFramebuffer > 0 ? explicitDepthFramebuffer : saved.drawFramebuffer;
            int[] depthViewport = explicitDepthViewport != null ? explicitDepthViewport : saved.viewport;
            int maximumSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            if (saved.drawFramebuffer <= 0 || saved.viewport[0] < 0 || saved.viewport[1] < 0
                    || outputWidth <= 0 || outputHeight <= 0
                    || outputWidth > maximumSize || outputHeight > maximumSize
                    || gridWidth > maximumSize || gridHeight > maximumSize
                    || gridWidth > (long) outputWidth + 4 || gridHeight > (long) outputHeight + 4
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return null;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthFramebuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || !containsDepthViewport(depthViewport)) return null;
            if (!ensureSource(outputWidth, outputHeight)) return null;
            if (!DepthCopyFormat.prepareDepthTexture(depthFramebuffer, sourceDepth, width, height)) return null;
            PixelTarget reduced = ensurePixelTarget(gridWidth, gridHeight, maximumSize);
            if (reduced == null) return null;
            PixelTarget horizontal = separable ? ensureHorizontalTarget(gridWidth, maximumSize) : null;
            if (separable && horizontal == null) return null;

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return null;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthFramebuffer);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            clearErrors();
            GL30.glBlitFramebuffer(depthViewport[0], depthViewport[1],
                    depthViewport[0] + depthViewport[2], depthViewport[1] + depthViewport[3],
                    0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) return null;

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sourceFramebuffer);
            GL11.glViewport(0, 0, width, height);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) return null;
            Scope scope = new Scope(saved, gridWidth, gridHeight, reduced, horizontal);
            inUse = true;
            opened = true;
            return scope;
        } catch (RuntimeException failure) {
            return null;
        } finally {
            if (!opened) saved.restore();
        }
    }

    /** Inspect only the source depth attachment; its buffer mappings and attachment ownership remain untouched. */
    private static boolean containsDepthViewport(int[] viewport) {
        int kind = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int object = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (object <= 0) return false;
        int depthWidth, depthHeight;
        if (kind == GL11.GL_TEXTURE) {
            int level = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            if (level < 0 || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED) != 0
                    || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE) != 0) return false;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, object);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) return false;
            depthWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
            depthHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
        } else if (kind == GL30.GL_RENDERBUFFER) {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, object);
            if (GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_SAMPLES) != 0) return false;
            depthWidth = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_WIDTH);
            depthHeight = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_HEIGHT);
        } else {
            return false;
        }
        return GL11.glGetError() == GL11.GL_NO_ERROR
                && (long) viewport[0] + viewport[2] <= depthWidth
                && (long) viewport[1] + viewport[3] <= depthHeight;
    }

    private boolean ensureSource(int newWidth, int newHeight) {
        if (sourceFramebuffer != 0 && width == newWidth && height == newHeight) return true;
        destroy();
        boolean complete = false;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            sourceFramebuffer = GL30.glGenFramebuffers();
            sourceColor = createTexture(newWidth, newHeight, false);
            sourceDepth = createTexture(newWidth, newHeight, true);
            if (!attach(sourceFramebuffer, sourceColor, sourceDepth)
                    || GL11.glGetError() != GL11.GL_NO_ERROR) return false;
            width = newWidth;
            height = newHeight;
            complete = true;
            return true;
        } finally {
            if (!complete) destroy();
        }
    }

    private PixelTarget ensurePixelTarget(int gridWidth, int gridHeight, int maximumSize) {
        PixelTarget cached = pixelTarget;
        if (cached != null && cached.width >= gridWidth && cached.height >= gridHeight) return cached;
        int reducedWidth = Math.max(cached == null ? 0 : cached.width, capacity(gridWidth, maximumSize));
        int reducedHeight = Math.max(cached == null ? 0 : cached.height, capacity(gridHeight, maximumSize));
        PixelTarget result = createPixelTarget(reducedWidth, reducedHeight, GL30.GL_RGBA16F);
        if (result == null) return null;
        if (cached != null) cached.destroy();
        pixelTarget = result;
        return result;
    }

    private PixelTarget ensureHorizontalTarget(int gridWidth, int maximumSize) {
        PixelTarget cached = horizontalTarget;
        if (cached != null && cached.width >= gridWidth) return cached;
        int rowWidth = Math.max(cached == null ? 0 : cached.width, capacity(gridWidth, maximumSize));
        PixelTarget result = createPixelTarget(rowWidth, height, GL30.GL_RGBA32F);
        if (result == null) return null;
        if (cached != null) cached.destroy();
        horizontalTarget = result;
        return result;
    }

    private static PixelTarget createPixelTarget(int targetWidth, int targetHeight, int colorFormat) {
        int framebuffer = 0, color = 0, depth = 0;
        boolean complete = false;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            framebuffer = GL30.glGenFramebuffers();
            color = createTexture(targetWidth, targetHeight, false, colorFormat);
            depth = createTexture(targetWidth, targetHeight, true);
            if (!attach(framebuffer, color, depth) || GL11.glGetError() != GL11.GL_NO_ERROR) return null;
            PixelTarget result = new PixelTarget(framebuffer, color, depth, targetWidth, targetHeight);
            complete = true;
            return result;
        } finally {
            if (!complete) {
                if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
                if (color != 0) GL11.glDeleteTextures(color);
                if (depth != 0) GL11.glDeleteTextures(depth);
            }
        }
    }

    private static int capacity(int requested, int maximumSize) {
        return (int) Math.min(maximumSize, ((long) requested + 63) / 64 * 64);
    }

    private record PixelTarget(int framebuffer, int color, int depth, int width, int height) {
        void destroy() {
            GL30.glDeleteFramebuffers(framebuffer);
            GL11.glDeleteTextures(color);
            GL11.glDeleteTextures(depth);
        }
    }

    private static int createTexture(int width, int height, boolean depth) {
        return createTexture(width, height, depth, GL30.GL_RGBA16F);
    }

    private static int createTexture(int width, int height, boolean depth, int colorFormat) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, depth ? GL30.GL_DEPTH_COMPONENT32F : colorFormat,
                width, height, 0, depth ? GL11.GL_DEPTH_COMPONENT : GL11.GL_RGBA,
                GL11.GL_FLOAT, (ByteBuffer) null);
        return texture;
    }

    private static boolean attach(int framebuffer, int color, int depth) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    /** Releases only owned images and FBOs; the caller must be on the render thread with no open scope. */
    public void destroy() {
        if (inUse) throw new IllegalStateException("Moonwound pixelation target is still in use");
        if (sourceFramebuffer != 0) GL30.glDeleteFramebuffers(sourceFramebuffer);
        if (sourceColor != 0) GL11.glDeleteTextures(sourceColor);
        if (sourceDepth != 0) GL11.glDeleteTextures(sourceDepth);
        if (pixelTarget != null) pixelTarget.destroy();
        if (horizontalTarget != null) horizontalTarget.destroy();
        pixelTarget = null;
        horizontalTarget = null;
        sourceFramebuffer = sourceColor = sourceDepth = 0;
        width = height = 0;
    }

    public final class Scope implements AutoCloseable {
        private SavedState saved;
        private final int gridWidth;
        private final int gridHeight;
        private final PixelTarget pixelTarget;
        private final PixelTarget horizontalTarget;

        private Scope(SavedState saved, int gridWidth, int gridHeight, PixelTarget pixelTarget,
                      PixelTarget horizontalTarget) {
            this.saved = saved;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
            this.pixelTarget = pixelTarget;
            this.horizontalTarget = horizontalTarget;
        }

        public int sourceColor() { requireOpen(); return sourceColor; }
        public int sourceDepth() { requireOpen(); return sourceDepth; }
        public int pixelColor() { requireOpen(); return pixelTarget.color; }
        public int pixelDepth() { requireOpen(); return pixelTarget.depth; }
        public int horizontalColor() { requireHorizontal(); return horizontalTarget.color; }
        public int horizontalDepth() { requireHorizontal(); return horizontalTarget.depth; }
        public int width() { return width; }
        public int height() { return height; }
        public int gridWidth() { return gridWidth; }
        public int gridHeight() { return gridHeight; }
        public int viewportX() { requireOpen(); return saved.viewport[0]; }
        public int viewportY() { requireOpen(); return saved.viewport[1]; }

        /** The resolve shader writes averaged premultiplied color and the nearest contributing effect depth. */
        public void bindResolve() {
            requireOpen();
            bindReduction(pixelTarget, gridWidth, gridHeight);
        }

        /** One output column per logical cell, retaining one row per source pixel. */
        public void bindHorizontal() {
            requireHorizontal();
            bindReduction(horizontalTarget, gridWidth, height);
        }

        private void bindReduction(PixelTarget target, int activeWidth, int activeHeight) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebuffer);
            GL11.glViewport(0, 0, activeWidth, activeHeight);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClearDepth(1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        }

        /** Restores the original output and exact viewport without changing any scene buffer mappings. */
        public void bindOutput() {
            requireOpen();
            saved.bindOutput();
        }

        private void requireOpen() {
            if (saved == null) throw new IllegalStateException("Moonwound pixelation scope is closed");
        }

        private void requireHorizontal() {
            requireOpen();
            if (horizontalTarget == null) throw new IllegalStateException("Separable Moonwound resolve was not requested");
        }

        @Override
        public void close() {
            if (saved == null) return;
            SavedState state = saved;
            saved = null;
            try {
                state.restore();
            } finally {
                inUse = false;
            }
        }
    }

    /** Raw state changed by allocation, depth copying, clears, or target switching. Draw state belongs to the caller. */
    private record SavedState(int drawFramebuffer, int readFramebuffer, int[] viewport,
                              int activeTexture, int texture, int renderbuffer, int unpackBuffer,
                              boolean[] colorMask, boolean depthMask, boolean scissor,
                              float[] clearColor, double clearDepth) {
        static SavedState capture() {
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            ByteBuffer mask = ByteBuffer.allocateDirect(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            float[] clearColor = new float[4];
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            return new SavedState(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), viewport,
                    GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                    GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING), GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),
                    new boolean[] {mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0},
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    clearColor, GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE));
        }

        void bindOutput() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }

        void restore() {
            bindOutput();
            GL13.glActiveTexture(activeTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            GL11.glDepthMask(depthMask);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GL11.glClearDepth(clearDepth);
        }
    }

    private static void clearErrors() {
        for (int attempt = 0; attempt < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; attempt++) { }
    }
}
