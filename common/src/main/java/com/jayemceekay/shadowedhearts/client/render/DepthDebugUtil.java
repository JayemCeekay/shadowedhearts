package com.jayemceekay.shadowedhearts.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.BufferUtils;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility to dump the captured depth texture to a PNG for debugging/learning.
 * Assumes DepthCapture.captureIfNeeded() has been called this frame or will be called within the render thread call.
 */
public final class DepthDebugUtil {
    private DepthDebugUtil() {}

    private static volatile boolean pending = false;
    private static volatile boolean pendingLinear = false;

    public static void requestDepthDump(boolean linearize) {
        pending = true;
        pendingLinear = linearize;
    }

    public static Boolean pollPending() {
        if (!pending) return null;
        pending = false;
        return pendingLinear;
    }

    public static void saveDepthPng(boolean linearize) {
        // Ensure we are on the render thread to safely touch GL state.
        RenderSystem.assertOnRenderThread();

        DepthCapture.captureIfNeeded();
        int texId = DepthCapture.textureId();
        int w = DepthCapture.width();
        int h = DepthCapture.height();
        if (texId == 0 || w <= 0 || h <= 0) {
            toast("Depth texture not available");
            return;
        }

        // Read back depth as float32 from our FBO (more robust than glGetTexImage on the texture)
        FloatBuffer buf = BufferUtils.createFloatBuffer(w * h);
        if (!DepthCapture.readDepth(buf)) {
            toast("Depth read failed; trying main FBO depth");
            buf.rewind();
            if (!DepthCapture.readMainDepth(buf)) {
                toast("Main FBO depth read also failed");
                return;
            }
        }

        // Compute min/max for debugging
        buf.rewind();
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY, sum = 0f;
        for (int i = 0; i < w * h; i++) {
            float z = buf.get();
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
            sum += z;
        }
        float avg = sum / Math.max(1, w * h);
        buf.rewind();

        // Convert to an easily viewable 8-bit grayscale image (optionally linearized)
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        try {
            // Prepare normalization for non-linear display
            float minZLocal = minZ;
            float maxZLocal = maxZ;
            float range = maxZLocal - minZLocal;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float z = buf.get(); // [0,1] non-linear depth from post-resolve texture
                    float v;
                    if (linearize) {
                        float n = 0.05f, f = 1000.0f;
                        float zndc = z * 2.0f - 1.0f;
                        v = (2.0f * n * f) / (f + n - zndc * (f - n));
                        // remap typical 0..far range to 0..1 for display; clamp
                        // divide by some distance to improve contrast (e.g., 20m makes nearer geometry brighter)
                        v = Math.min(v / 20.0f, 1.0f);
                    } else {
                        // Non-linear visualization:
                        // 1) Auto-normalize using frame's min/max to stretch contrast if range is meaningful.
                        // 2) Invert so nearer fragments appear brighter.
                        if (range > 1e-6f) {
                            float norm = (z - minZLocal) / range; // 0..1 where 1 ~ far
                            v = 1.0f - norm; // near bright
                        } else {
                            v = 1.0f - z; // fallback when depth is flat
                        }
                        // Optional gentle gamma to boost mid-range visibility
                        float gamma = 0.8f;
                        v = (float) Math.pow(Math.max(0.0f, Math.min(1.0f, v)), gamma);
                    }
                    int g = (int)(Math.max(0.0f, Math.min(1.0f, v)) * 255.0f);
                    int argb = (0xFF << 24) | (g << 16) | (g << 8) | g; // opaque grayscale
                    img.setPixelRGBA(x, h - 1 - y, argb); // flip Y for conventional top-left origin
                }
            }
            // Log min/max/avg to chat
            toast(String.format("Depth z range: min=%.4f max=%.4f avg=%.4f", minZ, maxZ, avg));

            File screenshots = new File(Minecraft.getInstance().gameDirectory, "screenshots");
            if (!screenshots.exists()) screenshots.mkdirs();
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String name = String.format("depth-%s%s.png", ts, linearize ? "-lin" : "");
            File out = new File(screenshots, name);
            img.writeToFile(out);
            toast("Saved " + out.getName());
        } catch (IOException ioe) {
            toast("Failed to save depth PNG: " + ioe.getMessage());
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
