package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

/** Native PC dot masks drawn over the supplied infuser frame without replacing its artwork. */
object UmbraInfuserDecorations {
    private val TOP = cobblemonResource("textures/gui/pc/pc_spacer_top.png")
    private val RIGHT = cobblemonResource("textures/gui/pc/pc_spacer_right.png")
    private val EMPTY_TYPES = cobblemonResource("textures/gui/pc/type_spacer.png")

    /** Draw after the infuser base, before button labels and other foreground content. */
    fun renderFrame(graphics: GuiGraphics, originX: Int, originY: Int) {
        halfSize(graphics, TOP, originX + 86.5, originY + 13.0, 342, 14)
        halfSize(graphics, RIGHT, originX + 275.5, originY + 184.0, 64, 24)
    }

    /** Use only when no Pokemon is previewed; occupied previews draw their typed spacer. */
    fun renderEmptyTypeSpacer(graphics: GuiGraphics, originX: Int, originY: Int) {
        // Match the PC's 63px display width while retaining the complete 128px source.
        halfSize(graphics, EMPTY_TYPES, originX + 7.0, originY + 118.5, 126, 12, 128)
    }

    private fun halfSize(
        graphics: GuiGraphics,
        texture: ResourceLocation,
        x: Double,
        y: Double,
        width: Int,
        height: Int,
        sourceWidth: Int = width
    ) {
        val matrices = graphics.pose()
        matrices.pushPose()
        try {
            matrices.translate(x, y, 0.0)
            matrices.scale(0.5F, 0.5F, 1F)
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            graphics.blit(texture, 0, 0, width, height, 0F, 0F,
                sourceWidth, height, sourceWidth, height)
        } finally {
            matrices.popPose()
        }
    }
}
