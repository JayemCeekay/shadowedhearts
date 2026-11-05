package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.client.gui.ProfileTransformType
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.lwjgl.opengl.GL11
import java.util.UUID

/** Uses the same authored Pokemon profile framing as the purification PC's 25px slots. */
class UmbraInfuserPartyPreview {
    companion object {
        private const val SLOT_SIZE = 25
        private const val FRAME_INSET = 1
    }

    private var identity: UUID? = null
    private var species: ResourceLocation? = null
    private var aspects: Set<String> = emptySet()
    private var state = FloatingState()

    fun reset() {
        identity = null
        species = null
        aspects = emptySet()
        state = FloatingState()
    }

    /** [x] and [y] are the absolute GUI coordinates of the slot's top-left corner. */
    fun render(graphics: GuiGraphics, x: Int, y: Int, pokemon: Pokemon?, partialTick: Float) {
        if (pokemon == null) {
            if (identity != null) reset()
            return
        }
        if (identity != pokemon.uuid || species != pokemon.species.resourceIdentifier || aspects != pokemon.aspects) {
            identity = pokemon.uuid
            species = pokemon.species.resourceIdentifier
            aspects = pokemon.aspects.toSet()
            state = FloatingState()
        }

        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        val cameraOrientation = Quaternionf(dispatcher.cameraOrientation())
        val depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val matrices = graphics.pose()

        graphics.flush()
        // Keep native profile geometry, while protecting the slot art and neighboring labels.
        GuiPoseScissor.enable(graphics, x + FRAME_INSET, y + FRAME_INSET, x + SLOT_SIZE - FRAME_INSET, y + SLOT_SIZE - FRAME_INSET)
        matrices.pushPose()
        try {
            matrices.translate(x + SLOT_SIZE / 2.0, y + 1.0, 0.0)
            matrices.scale(2F, 2F, 1F)
            RenderSystem.enableDepthTest()
            RenderSystem.depthMask(true)
            drawProfilePokemon(
                renderablePokemon = pokemon.asRenderablePokemon(),
                matrixStack = matrices,
                rotation = Quaternionf().rotateXYZ(Math.toRadians(13.0).toFloat(), Math.toRadians(35.0).toFloat(), 0F),
                poseType = PoseType.PROFILE,
                state = state,
                partialTicks = partialTick,
                scale = 4.5F,
                profileTransformType = ProfileTransformType.PROFILE
            )
        } finally {
            try {
                graphics.flush()
            } finally {
                matrices.popPose()
                // Flush before removing the clip, then discard only this preview's depth.
                RenderSystem.depthMask(true)
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
                graphics.disableScissor()
                dispatcher.overrideCameraOrientation(cameraOrientation)
                Lighting.setupFor3DItems()
                RenderSystem.depthFunc(depthFunction)
                RenderSystem.depthMask(depthWrite)
                if (depthEnabled) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            }
        }
    }
}
