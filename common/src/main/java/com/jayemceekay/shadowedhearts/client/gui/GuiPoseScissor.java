package com.jayemceekay.shadowedhearts.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Minecraft 1.21's GUI scissors use screen coordinates, independently of the drawing pose. */
public final class GuiPoseScissor {
    private GuiPoseScissor() {}

    public record Bounds(int left, int top, int right, int bottom) {}

    public static void enable(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Bounds bounds = bounds(graphics.pose().last().pose(), left, top, right, bottom);
        graphics.enableScissor(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    /** Round outward so fractional fit scales do not clip the last row or column of a preview. */
    public static Bounds bounds(Matrix4fc pose, int left, int top, int right, int bottom) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        Vector3f point = new Vector3f();
        for (int x : new int[]{left, right}) for (int y : new int[]{top, bottom}) {
            pose.transformPosition(x, y, 0, point);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return new Bounds((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }
}
