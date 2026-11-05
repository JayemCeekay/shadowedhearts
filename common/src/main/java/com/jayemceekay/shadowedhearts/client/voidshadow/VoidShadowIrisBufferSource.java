package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;

/** Frame-local tee: the shader pack still receives every native body/eye vertex. */
public final class VoidShadowIrisBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final VoidShadowSnapshotBufferSource body;
    private final VoidShadowSnapshotBufferSource eyes;
    final int entityId;
    final long frame;
    final PoseStack.Pose root;

    VoidShadowIrisBufferSource(int entityId, long frame, MultiBufferSource delegate) {
        this.entityId = entityId;
        this.frame = frame;
        // Vertices arrive after all entity/locator transforms. Keep those
        // camera-relative values exactly, avoiding a depth-sensitive inverse
        // entity-root roundtrip when replaying the pack's existing surface.
        this.root = new PoseStack().last().copy();
        this.delegate = delegate;
        body = new VoidShadowSnapshotBufferSource(delegate, this.root);
        MultiBufferSource eyeDelegate = delegate;
        while (eyeDelegate instanceof VoidShadowBufferSource liquid) eyeDelegate = liquid.nativeBuffers();
        eyes = new VoidShadowSnapshotBufferSource(eyeDelegate, this.root);
    }

    @Override public @NotNull VertexConsumer getBuffer(@NotNull RenderType type) {
        return body.getBuffer(type);
    }

    /** Only added pink eyes bypass the liquid replacement, retaining their own texture. */
    MultiBufferSource eyeBuffers() { return eyes; }

    MultiBufferSource nativeBuffers() { return delegate; }

    Captured finish() {
        var bodySnapshot = body.finish();
        return bodySnapshot == null ? null : new Captured(root.copy(), bodySnapshot, eyes.finish());
    }

    record Captured(PoseStack.Pose root, VoidShadowSnapshotBufferSource.Snapshot body,
                    VoidShadowSnapshotBufferSource.Snapshot eyes) {
        int vertexCount() {
            return count(body) + count(eyes);
        }
        private static int count(VoidShadowSnapshotBufferSource.Snapshot mesh) {
            return mesh == null ? 0 : mesh.layers().stream().mapToInt(layer -> layer.vertices().size()).sum();
        }
    }
}
