package com.jayemceekay.shadowedhearts.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/** Matches scratch depth storage to a scene attachment before a depth blit. */
public final class DepthCopyFormat {
    private DepthCopyFormat() {}

    /**
     * Depth blits require matching source/destination internal formats. Iris can
     * use floating-point or packed depth/stencil storage while TextureTarget
     * initially allocates ordinary integer depth. Resize only the scratch
     * texture's storage, retaining its existing sampler parameters.
     *
     * <p>Only conventional 2D texture and renderbuffer attachments are supported.
     * The default framebuffer has no inspectable attachment, so its existing
     * scratch format is retained and the caller must validate the actual blit.
     * All raw bindings changed here are restored; RenderSystem caches are never
     * changed. Multisample/otherwise unsupported copies still fail at the blit.
     */
    public static boolean prepareDepthTexture(int sourceFramebuffer,
                                              int destinationDepthTexture,
                                              int width, int height) {
        if (destinationDepthTexture <= 0 || width <= 0 || height <= 0) {
            return false;
        }
        if (sourceFramebuffer == 0) {
            return true;
        }

        int savedReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int savedTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int savedRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        int savedUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            clearGlErrors();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            int sourceFormat = sourceDepthFormat();
            int pixelFormat;
            int pixelType;
            switch (sourceFormat) {
                case GL14.GL_DEPTH_COMPONENT16, GL14.GL_DEPTH_COMPONENT24,
                     GL14.GL_DEPTH_COMPONENT32 -> {
                    pixelFormat = GL11.GL_DEPTH_COMPONENT;
                    pixelType = GL11.GL_UNSIGNED_INT;
                }
                case GL11.GL_DEPTH_COMPONENT, GL30.GL_DEPTH_COMPONENT32F -> {
                    pixelFormat = GL11.GL_DEPTH_COMPONENT;
                    pixelType = GL11.GL_FLOAT;
                }
                case GL30.GL_DEPTH24_STENCIL8 -> {
                    pixelFormat = GL30.GL_DEPTH_STENCIL;
                    pixelType = GL30.GL_UNSIGNED_INT_24_8;
                }
                case GL30.GL_DEPTH32F_STENCIL8 -> {
                    pixelFormat = GL30.GL_DEPTH_STENCIL;
                    pixelType = GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
                }
                default -> {
                    return false;
                }
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, destinationDepthTexture);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                return false;
            }
            int destinationFormat = GL11.glGetTexLevelParameteri(
                    GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            if (destinationFormat != sourceFormat) {
                // Null is an allocation request only with no unpack PBO bound;
                // otherwise OpenGL interprets it as byte offset zero into it.
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, sourceFormat,
                        width, height, 0, pixelFormat, pixelType, (ByteBuffer) null);
            }
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, savedUnpackBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTexture);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, savedRenderbuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedReadFramebuffer);
        }
    }

    private static int sourceDepthFormat() {
        int attachmentType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int attachment = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (attachmentType == GL11.GL_TEXTURE) {
            int level = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, attachment);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                return 0;
            }
            return GL11.glGetTexLevelParameteri(
                    GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        }
        if (attachmentType == GL30.GL_RENDERBUFFER) {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, attachment);
            return GL30.glGetRenderbufferParameteri(
                    GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
        }
        return 0;
    }

    private static void clearGlErrors() {
        for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            // Attribute errors to this inspection/allocation rather than entry state.
        }
    }
}
