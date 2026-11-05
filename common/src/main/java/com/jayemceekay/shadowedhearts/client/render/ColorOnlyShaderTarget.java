package com.jayemceekay.shadowedhearts.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/** Borrows the caller's first color output without attaching any sampled scene depth. */
public final class ColorOnlyShaderTarget {
    private static int framebuffer;
    private static boolean inUse;

    private ColorOnlyShaderTarget() {}

    /** Returns null for unsupported outputs; never guesses a different scene target. */
    public static OutputScope open() {
        if (inUse) return null;
        var state = DensityFboPipeline.preserveRenderState();
        boolean opened = false;
        try {
            int sourceFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int sourceAttachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0);
            int maximumAttachments = GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
            if (sourceFramebuffer == 0 || sourceAttachment < GL30.GL_COLOR_ATTACHMENT0
                    || sourceAttachment >= GL30.GL_COLOR_ATTACHMENT0 + maximumAttachments
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return null;
            if (GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, sourceAttachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_TEXTURE) return null;
            int texture = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, sourceAttachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int level = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, sourceAttachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            if (texture <= 0 || level < 0
                    || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, sourceAttachment,
                    GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED) != 0
                    || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, sourceAttachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE) != 0) return null;

            // An array or multisample object cannot bind as TEXTURE_2D. Reject
            // those targets instead of borrowing a different layer or sample.
            clearErrors();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) return null;
            if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH) <= 0
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT) <= 0
                    || GL11.glGetError() != GL11.GL_NO_ERROR) return null;

            if (framebuffer == 0) framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, texture, level);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || GL11.glGetError() != GL11.GL_NO_ERROR) return null;
            inUse = true;
            opened = true;
            return new OutputScope(state);
        } finally {
            if (!opened) {
                try {
                    detachColor();
                } finally {
                    state.close();
                }
            }
        }
    }

    private static void detachColor() {
        if (framebuffer == 0) return;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, 0, 0);
    }

    public static void destroy() {
        if (inUse) throw new IllegalStateException("Color-only shader output is still in use");
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        framebuffer = 0;
    }

    public static final class OutputScope implements AutoCloseable {
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
