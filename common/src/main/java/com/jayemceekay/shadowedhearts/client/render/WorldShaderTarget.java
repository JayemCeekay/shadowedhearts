package com.jayemceekay.shadowedhearts.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/** A temporary view of finished world color with scene depth saved before transparency composition. */
public final class WorldShaderTarget implements AutoCloseable {
    private int depthTexture;
    private int depthFramebuffer;
    private int outputFramebuffer;
    private int width;
    private int height;
    private boolean ready;
    private boolean inUse;

    public boolean captureDepth(int sourceFramebuffer, int sourceWidth, int sourceHeight) {
        ready = false;
        if (inUse || sourceFramebuffer <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return false;
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            if (sourceWidth > GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)
                    || sourceHeight > GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)) return false;
            if (width != sourceWidth || height != sourceHeight || depthTexture == 0) {
                close();
                width = sourceWidth;
                height = sourceHeight;
                depthTexture = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
                int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                try {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F,
                            width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
                } finally {
                    GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
                }
                depthFramebuffer = GL30.glGenFramebuffers();
                outputFramebuffer = GL30.glGenFramebuffers();
            }
            if (!DepthCopyFormat.prepareDepthTexture(sourceFramebuffer, depthTexture, width, height)) return false;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depthTexture, 0);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthFramebuffer);
            GL11.glReadBuffer(GL11.GL_NONE);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return false;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            clearErrors();
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            ready = GL11.glGetError() == GL11.GL_NO_ERROR;
            return ready;
        }
    }

    /** Borrows only world color; draws can never replace the main target's final depth. */
    public OutputScope open(int worldColorTexture) {
        if (!ready || inUse || worldColorTexture <= 0) return null;
        var state = DensityFboPipeline.preserveRenderState();
        boolean opened = false;
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldColorTexture);
            if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != width
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != height) return null;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outputFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, worldColorTexture, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depthTexture, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return null;
            RenderSystem.viewport(0, 0, width, height);
            inUse = true;
            opened = true;
            return new OutputScope(state);
        } finally {
            if (!opened) {
                detachColor();
                state.close();
            }
        }
    }

    public int depthTextureId() { return ready ? depthTexture : 0; }

    public void invalidate() { ready = false; }

    private void detachColor() {
        if (outputFramebuffer == 0) return;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, outputFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, 0, 0);
    }

    @Override
    public void close() {
        if (inUse) throw new IllegalStateException("World shader output is still in use");
        if (depthFramebuffer != 0) GL30.glDeleteFramebuffers(depthFramebuffer);
        if (outputFramebuffer != 0) GL30.glDeleteFramebuffers(outputFramebuffer);
        if (depthTexture != 0) GL11.glDeleteTextures(depthTexture);
        depthFramebuffer = outputFramebuffer = depthTexture = width = height = 0;
        ready = false;
    }

    public final class OutputScope implements AutoCloseable {
        private DensityFboPipeline.RenderStateScope state;

        private OutputScope(DensityFboPipeline.RenderStateScope state) { this.state = state; }

        @Override
        public void close() {
            if (state == null) return;
            var saved = state;
            state = null;
            try {
                detachColor();
            } finally {
                inUse = false;
                saved.close();
            }
        }
    }

    private static void clearErrors() {
        for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {}
    }
}
