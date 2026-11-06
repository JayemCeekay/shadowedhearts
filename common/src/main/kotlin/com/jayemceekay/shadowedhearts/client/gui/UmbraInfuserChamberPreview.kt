package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraGuiRenderer
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraSystem
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserLayout as Layout
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import java.util.UUID

/** A full-body preview on the same native platform used by the purification chamber. */
class UmbraInfuserChamberPreview {
    companion object {
        private val PLATFORM = cobblemonResource("textures/gui/pokedex/platform_base.png")
        private const val VIEW_X = 127
        private const val VIEW_Y = 47
        private const val VIEW_WIDTH = 90
        private const val VIEW_HEIGHT = 82
        private const val PLATFORM_WIDTH = 56
        private const val PLATFORM_HEIGHT = 15
        private const val PLATFORM_CENTER_Y = 120
    }

    private var identity: UUID? = null
    private var species: ResourceLocation? = null
    private var aspects: Set<String> = emptySet()
    private var state = FloatingState()
    private val auraPreview = ShadowPokemonAuraSystem.PreviewInstance()

    fun reset() {
        deactivateAura()
        identity = null
        species = null
        aspects = emptySet()
        state = FloatingState()
    }

    /** Suspend the preview while the inventory drawer covers the chamber. */
    fun deactivateAura() {
        ShadowPokemonAuraGuiRenderer.deactivate(auraPreview)
    }

    /** Coordinates are relative to the infuser's unscaled 349 by 205 GUI frame. */
    fun render(
        graphics: GuiGraphics,
        originX: Int,
        originY: Int,
        pokemon: Pokemon?,
        partialTick: Float
    ) {
        if (pokemon == null) {
            if (identity != null) reset()
            return
        }
        if (identity != pokemon.uuid || species != pokemon.species.resourceIdentifier || aspects != pokemon.aspects) {
            deactivateAura()
            identity = pokemon.uuid
            species = pokemon.species.resourceIdentifier
            aspects = pokemon.aspects.toSet()
            state = FloatingState()
        }

        val renderablePokemon = pokemon.asRenderablePokemon()
        // The synchronized Shadow aspect appears only after the server completes infusion.
        val hasShadowAura = com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible() &&
            ShadowAspectUtil.hasShadowAspect(pokemon)
        if (!hasShadowAura) deactivateAura()
        var auraCapturePending = false

        val minecraft = Minecraft.getInstance()
        val dispatcher = minecraft.entityRenderDispatcher
        val cameraOrientation = Quaternionf(dispatcher.cameraOrientation())
        val depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val matrices = graphics.pose()
        // Aura compositing consumes actual GUI-space bounds, independently of the model pose.
        val auraClip = GuiPoseScissor.bounds(
            matrices.last().pose(), originX + VIEW_X, originY + VIEW_Y,
            originX + VIEW_X + VIEW_WIDTH, originY + VIEW_Y + VIEW_HEIGHT
        )

        graphics.flush()
        GuiPoseScissor.enable(graphics,
            originX + VIEW_X, originY + VIEW_Y,
            originX + VIEW_X + VIEW_WIDTH, originY + VIEW_Y + VIEW_HEIGHT
        )
        matrices.pushPose()
        try {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            graphics.blit(
                PLATFORM, originX + Layout.CENTER_X - PLATFORM_WIDTH / 2,
                originY + PLATFORM_CENTER_Y - PLATFORM_HEIGHT / 2,
                PLATFORM_WIDTH, PLATFORM_HEIGHT, 0F, 0F, 113, 30, 113, 30
            )
            graphics.flush()

            // Match the purification PC's center Pokemon placement and native profile sizing.
            matrices.translate(
                (originX + Layout.CENTER_X).toDouble(),
                originY + PLATFORM_CENTER_Y - 53.0,
                0.0
            )
            matrices.scale(1.5F, 1.5F, 1.5F)
            RenderSystem.enableDepthTest()
            RenderSystem.depthMask(true)
            matrices.pushPose()
            try {
                if (hasShadowAura) {
                    auraCapturePending = true
                    ShadowPokemonAuraGuiRenderer.beginCapture(
                        auraPreview, renderablePokemon, state, pokemon.uuid.toString()
                    )
                }
                drawProfilePokemon(
                    renderablePokemon = renderablePokemon,
                    matrixStack = matrices,
                    rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 325F, 0F)),
                    state = state,
                    partialTicks = partialTick
                )
            } finally {
                matrices.popPose()
            }
            if (hasShadowAura) {
                graphics.flush()
                ShadowPokemonAuraGuiRenderer.renderInPurificationGUI(
                    graphics, matrices, minecraft.renderBuffers().bufferSource(),
                    ShadowHeartGauge.getHeartGaugeValue(pokemon) / 100F,
                    partialTick, renderablePokemon, state,
                    auraClip.left().toFloat(), auraClip.top().toFloat(),
                    (auraClip.right() - auraClip.left()).toFloat(),
                    (auraClip.bottom() - auraClip.top()).toFloat(), auraPreview
                )
                auraCapturePending = false
            }
        } finally {
            // A failed model/composite render must not leave a capture armed for another GUI.
            if (auraCapturePending) deactivateAura()
            try {
                graphics.flush()
            } finally {
                matrices.popPose()
                // Keep model depth inside this viewport from occluding later GUI overlays.
                // GuiGraphics' scissor remains active, so no other GUI region is cleared.
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
