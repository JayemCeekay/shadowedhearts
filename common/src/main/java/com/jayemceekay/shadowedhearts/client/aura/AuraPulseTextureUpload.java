package com.jayemceekay.shadowedhearts.client.aura;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/** Owns the pulse data texture and isolates its uploads from other texture upload state. */
final class AuraPulseTextureUpload implements AutoCloseable {
    private static final int BYTES_PER_TEXEL = 4 * Float.BYTES;
    private static final BufferMapping GL_MAPPING = new BufferMapping() {
        @Override
        public ByteBuffer map(int bytes) {
            return GL30.glMapBufferRange(GL21.GL_PIXEL_UNPACK_BUFFER, 0, bytes,
                    GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_RANGE_BIT);
        }

        @Override
        public boolean unmap() {
            return GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER);
        }
    };

    /** Keeps mapping failures testable without forcing a driver allocation failure. */
    interface BufferMapping {
        ByteBuffer map(int bytes);
        boolean unmap();
    }

    private final BufferMapping mapping;
    private int texture;
    private int pbo;
    private int capacityTexels;
    private int textureWidth;

    AuraPulseTextureUpload() {
        this(GL_MAPPING);
    }

    AuraPulseTextureUpload(BufferMapping mapping) {
        this.mapping = mapping;
    }

    int textureId() {
        return texture;
    }

    /** Returns false when no valid data was uploaded; the caller must skip its draw. */
    boolean upload(int width, int maximumTextureSize, Consumer<ByteBuffer> writer) {
        if (width <= 0 || width > maximumTextureSize) return false;
        int previousPbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int swapBytes = GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES);
        try {
            if (pbo == 0) pbo = GL15.glGenBuffers();
            if (pbo == 0) return false;
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
            if (capacityTexels < width) {
                int newCapacity = Math.min(nextPowerOfTwo(width), maximumTextureSize);
                int bytes = Math.multiplyExact(newCapacity, BYTES_PER_TEXEL);
                GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, bytes, GL15.GL_STREAM_DRAW);
                if (GL15.glGetBufferParameteri(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_BUFFER_SIZE) != bytes) {
                    capacityTexels = 0;
                    return false;
                }
                capacityTexels = newCapacity;
            }

            ByteBuffer mapped = mapping.map(Math.multiplyExact(width, BYTES_PER_TEXEL));
            if (mapped == null) {
                capacityTexels = 0;
                return false;
            }
            boolean unmapped;
            try {
                writer.accept(mapped);
            } finally {
                // Even a failed writer must release the mapping before restoring the caller's PBO.
                unmapped = mapping.unmap();
                if (!unmapped) capacityTexels = 0;
            }
            if (!unmapped) return false;

            if (texture == 0) texture = GL11.glGenTextures();
            if (texture == 0) return false;
            // Raw, temporary binding: keep Minecraft's cached binding unchanged and restore the actual one.
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_FALSE);

            if (textureWidth != capacityTexels) {
                // A null data pointer allocates storage only when no unpack buffer is bound.
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F,
                        capacityTexels, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, 0L);
                if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != capacityTexels
                        || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != 1) {
                    textureWidth = 0;
                    return false;
                }
                textureWidth = capacityTexels;
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
            }
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, 1, GL11.GL_RGBA, GL11.GL_FLOAT, 0L);
            return true;
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousPbo);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, swapBytes);
        }
    }

    private static int nextPowerOfTwo(int value) {
        return value <= 1 ? 1 : 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    @Override
    public void close() {
        if (pbo != 0) GL15.glDeleteBuffers(pbo);
        if (texture != 0) GL11.glDeleteTextures(texture);
        pbo = texture = capacityTexels = textureWidth = 0;
    }
}
