package com.jayemceekay.shadowedhearts.client.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GL13;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.IntBuffer;
import java.nio.ByteBuffer;

/** Restricts single-output core shaders to the bound Iris world's color output. */
public final class IrisWorldColorScope implements AutoCloseable {
    private final int framebuffer;
    private final IntBuffer drawBuffers;
    private final boolean[] blendEnabled;
    private final ByteBuffer[] colorMasks;
    private final int[][] indexedBlend;
    private final DensityFboPipeline.RenderStateScope state;

    private IrisWorldColorScope(int framebuffer, IntBuffer drawBuffers,
                                boolean[] blendEnabled, ByteBuffer[] colorMasks,
                                int[][] indexedBlend,
                                DensityFboPipeline.RenderStateScope state) {
        this.framebuffer = framebuffer;
        this.drawBuffers = drawBuffers;
        this.blendEnabled = blendEnabled;
        this.colorMasks = colorMasks;
        this.indexedBlend = indexedBlend;
        this.state = state;
    }

    /** The caller must first bind Iris' default world target. */
    public static IrisWorldColorScope open() {
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (framebuffer == 0 || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                != GL30.GL_FRAMEBUFFER_COMPLETE
                || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)
                == GL11.GL_NONE) {
            return null;
        }
        int count = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        IntBuffer buffers = BufferUtils.createIntBuffer(count);
        boolean[] blends = new boolean[count];
        ByteBuffer[] masks = new ByteBuffer[count];
        var capabilities = GL.getCapabilities();
        int[][] indexed = capabilities.OpenGL40 || capabilities.GL_ARB_draw_buffers_blend
                ? new int[count][6] : null;
        int[] blendKeys = {org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB,
                org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB, org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA,
                org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA, GL20.GL_BLEND_EQUATION_RGB, GL20.GL_BLEND_EQUATION_ALPHA};
        for (int index = 0; index < count; index++) {
            buffers.put(index, GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + index));
            blends[index] = GL30.glIsEnabledi(GL11.GL_BLEND, index);
            masks[index] = BufferUtils.createByteBuffer(4);
            GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, index, masks[index]);
            if (indexed != null) {
                for (int key = 0; key < blendKeys.length; key++) {
                    indexed[index][key] = GL30.glGetIntegeri(blendKeys[key], index);
                }
            }
        }
        var state = DensityFboPipeline.preserveRenderState();
        try {
            // Other G-buffer attachments contain pack-defined data. A core
            // shader with one vec4 output must not write undefined values there.
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            // Establish one coherent sampler cache before ShaderInstance.apply.
            // Iris can leave raw active/bound textures different from vanilla.
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            return new IrisWorldColorScope(framebuffer, buffers, blends, masks, indexed, state);
        } catch (RuntimeException | Error failure) {
            state.close();
            throw failure;
        }
    }

    @Override
    public void close() {
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL20.glDrawBuffers(drawBuffers);
        } finally {
            try {
                state.close();
            } finally {
                // Global RenderSystem blend/mask calls affect every index.
                // Restore pack-specific attachment masks after the cached state.
                for (int index = 0; index < colorMasks.length; index++) {
                    ByteBuffer mask = colorMasks[index];
                    GL30.glColorMaski(index, mask.get(0) != 0, mask.get(1) != 0,
                            mask.get(2) != 0, mask.get(3) != 0);
                    if (blendEnabled[index]) GL30.glEnablei(GL11.GL_BLEND, index);
                    else GL30.glDisablei(GL11.GL_BLEND, index);
                    if (indexedBlend != null) {
                        int[] blend = indexedBlend[index];
                        if (GL.getCapabilities().OpenGL40) {
                            GL40.glBlendFuncSeparatei(index, blend[0], blend[1], blend[2], blend[3]);
                            GL40.glBlendEquationSeparatei(index, blend[4], blend[5]);
                        } else {
                            ARBDrawBuffersBlend.glBlendFuncSeparateiARB(index, blend[0], blend[1], blend[2], blend[3]);
                            ARBDrawBuffersBlend.glBlendEquationSeparateiARB(index, blend[4], blend[5]);
                        }
                    }
                }
            }
        }
    }
}
