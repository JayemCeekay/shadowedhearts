package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;

@Deprecated
@Mixin(PokemonRenderer.class)
public class MixinPokemonRenderer {

    @WrapMethod(method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    public void createStencil(PokemonEntity entity, float entityYaw, float partialTicks, PoseStack poseMatrix, MultiBufferSource buffer, int packedLight, Operation<Void> original) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Ensure main FBO has stencil
        DepthCapture.captureIfNeeded();

        // Verify stencil attachment exists on main FBO (optional, fail gracefully)
        try {
            var mainRT = mc.getMainRenderTarget();
            int srcFbo = ((com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor) mainRT).getFrameBufferId();
            int prevFB = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, srcFbo);

            // Check attachment type
            int objType = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);

            // Check stencil bits on that attachment
            int stencilSize = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE);

            int objName = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);


            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFB);
            boolean hasStencil = (objType != GL30.GL_NONE);
            if (!hasStencil) {
                //ShadowAuraRenderer.earlyStencilValid = false;
                return;
            }
        } catch (Throwable t) {
            // if querying fails, proceed best-effort
        }

        // Save relevant GL state
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int prevStencilMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        try {

            // Prepare stencil clear
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            if (scissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);

            // Configure: write ref=255 on any passing fragment; force replace regardless of depth/face
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0xFF, 0xFF);
            GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
            GL11.glStencilMask(0xFF);

            // Render all relevant Pokemon entities into the stencil
            var dispatcher = mc.getEntityRenderDispatcher();
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            var camPos = camera.getPosition();
            var buffers = mc.renderBuffers().bufferSource();


            if (entity instanceof PokemonEntity pokemonEntity) {
                try {
                    if(PokemonAspectUtil.hasShadowAspect(pokemonEntity.getPokemon())) {
                        dispatcher.setRenderShadow(false);
                        original.call(entity, entityYaw, partialTicks, poseMatrix, buffer, packedLight);
                    }
                    //buffers.endBatch();
                } catch (Throwable ignored) {
                }
            }

           // ShadowAuraRenderer.earlyStencilValid = true;
           // S/hadowAuraRenderer.earlyStencilTick = mc.level.getGameTime();
        } finally {
            GL11.glDepthFunc(prevDepthFunc);

            com.mojang.blaze3d.systems.RenderSystem.depthMask(prevDepthMask);

            // leave stencil test disabled for now and reset mask to non-restrictive
            GL11.glStencilMask(prevStencilMask);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }

    }

}
