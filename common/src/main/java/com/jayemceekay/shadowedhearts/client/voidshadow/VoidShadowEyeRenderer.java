package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallCaptureVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Uses the supplied blob's pink eye patch on the copied model's animated eye locators. */
public final class VoidShadowEyeRenderer {
    private static final ResourceLocation BLOB = ResourceLocation.parse("shadowedhearts:void_shadow");
    private static final ResourceLocation TEXTURE = ResourceLocation.parse(
            "shadowedhearts:textures/pokemon/void_shadow/void_shadow.png");
    // Original opaque 3x3 eye: highlight #F36AC4, pink #F14FAA, shadow #D34187.
    private static final float U0 = 0, U1 = 3f / 128f, V0 = 61f / 128f, V1 = 64f / 128f;

    private VoidShadowEyeRenderer() { }

    public static boolean isDisguised(PokemonEntity entity) {
        return entity.getAspects().contains("void-shadow")
                && !entity.getPokemon().getSpecies().getResourceIdentifier().equals(BLOB);
    }

    public static void render(PokemonEntity entity, PoseStack poses, MultiBufferSource buffers) {
        if (!isDisguised(entity) || entity.isInvisible()
                || entity.getBeamMode() == 3 || DarkBallCaptureVfx.getByPokemonId(entity.getId()) != null
                || !(entity.getDelegate() instanceof PokemonClientDelegate delegate)) return;

        // The body wrapper recolors native emissive layers too. Bypass it only
        // for these added eyes, retaining vanilla/Iris' normal eyes pipeline.
        if (buffers instanceof VoidShadowIrisBufferSource iris) buffers = iris.eyeBuffers();
        else while (buffers instanceof VoidShadowBufferSource liquid) buffers = liquid.nativeBuffers();
        VertexConsumer consumer = null;
        for (var entry : delegate.getLocatorStates().entrySet()) {
            String name = entry.getKey();
            if (!VoidShadowEyeGeometry.isEyeLocator(name) || !delegate.isLocatorVisible(name)) continue;
            var matrix = entry.getValue().getMatrix();
            var quad = VoidShadowEyeGeometry.quad(matrix);
            if (quad == null) continue;
            Vector3f normal = quad.normal();
            if (!normal.isFinite()) continue;
            if (consumer == null) consumer = buffers.getBuffer(RenderType.eyes(TEXTURE));
            // These matrices already include body rotation, model scale and
            // parent animation. The supplied stack is entity-relative here.
            vertex(consumer, poses.last(), quad.bottomLeft(), U0, V1, normal);
            vertex(consumer, poses.last(), quad.topLeft(), U0, V0, normal);
            vertex(consumer, poses.last(), quad.topRight(), U1, V0, normal);
            vertex(consumer, poses.last(), quad.bottomRight(), U1, V1, normal);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, VoidShadowEyeGeometry.Point point,
                               float u, float v, Vector3f normal) {
        consumer.addVertex(pose.pose(), point.x(), point.y(), point.z()).setColor(255, 255, 255, 255)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }
}
