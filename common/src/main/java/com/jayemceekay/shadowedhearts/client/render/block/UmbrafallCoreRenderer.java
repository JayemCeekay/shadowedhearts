package com.jayemceekay.shadowedhearts.client.render.block;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisCompat;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisHandler;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Local, depth-tested seep over the ordinary six-sided fractured core model. */
public final class UmbrafallCoreRenderer implements BlockEntityRenderer<UmbrafallCoreBlockEntity> {
    public UmbrafallCoreRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(UmbrafallCoreBlockEntity core, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float density = com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity();
        // The normal baked block already provides a stable, readable core aperture.
        if (density < 1) return;
        Level level = core.getLevel();
        if (level == null || core.isRemoved() || ModShaders.UMBRAFALL_CORE_SEEP == null) return;
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        if (iris != null && iris.isShadowRenderActive()) return;
        BlockPos pos = core.getBlockPos();
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(Vec3.atLowerCornerOf(pos));
        double seconds = (level.getGameTime() + (double) partialTick) / 20.0;
        List<UmbrafallCoreSeepGeometry.Quad> quads = new ArrayList<>();
        for (Direction face : Direction.values()) {
            // Never load a neighboring chunk to draw an effect.
            if (level.hasChunkAt(pos.relative(face)) && UmbrafallCoreExposure.isExposed(level, pos, face)) {
                quads.addAll(UmbrafallCoreSeepGeometry.buildFace(face, seconds, pos.asLong(), eye));
            }
        }
        if (quads.isEmpty()) return;
        if (iris != null && iris.isShaderPackInUse()) {
            // Unsupported/reloading pack frames retain the static aperture; never draw into a guessed target.
            UmbrafallCoreSeepIrisRenderer.capture(pos, poses.last(), quads, packedLight);
            return;
        }
        var material = Minecraft.useShaderTransparency()
                ? UmbrafallCoreSeepRenderTypes.FABULOUS_SEEP : UmbrafallCoreSeepRenderTypes.SEEP;
        UmbrafallCoreSeepRenderTypes.emit(buffers.getBuffer(material), poses.last(), quads, packedLight);
    }

    @Override
    public boolean shouldRenderOffScreen(UmbrafallCoreBlockEntity core) {
        // A tip can remain visible just beyond the base block's frustum box.
        return true;
    }

    @Override
    public int getViewDistance() { return 48; }
}
