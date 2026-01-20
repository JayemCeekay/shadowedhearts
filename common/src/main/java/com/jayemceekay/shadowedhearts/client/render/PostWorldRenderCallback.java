package com.jayemceekay.shadowedhearts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public interface PostWorldRenderCallback {
    List<PostWorldRenderCallback> EVENTS = new ArrayList<>();

    void onWorldRendered(PoseStack matrices, Matrix4f projectionMatrix, Matrix4f modelViewMatrix, Camera camera, float tickDelta);

    static void register(PostWorldRenderCallback callback) {
        EVENTS.add(callback);
    }

    static void invoke(PoseStack matrices, Matrix4f projectionMatrix, Matrix4f modelViewMatrix, Camera camera, float tickDelta) {
        for (PostWorldRenderCallback event : EVENTS) {
            event.onWorldRendered(matrices, projectionMatrix, modelViewMatrix, camera, tickDelta);
        }
    }
}
