package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/** Pokédex-style GUI help, with standard Minecraft tooltips for actual items. */
object UmbraInfuserTooltip {
    private val BACKGROUND = cobblemonResource("textures/gui/pokedex/tooltip_background.png")
    private val EDGE = cobblemonResource("textures/gui/pokedex/tooltip_edge.png")
    private const val LINE_HEIGHT = 10

    fun render(graphics: GuiGraphics, text: Component, mouseX: Int, mouseY: Int, partialTick: Float) =
        renderLines(graphics, listOf(text), mouseX, mouseY, partialTick)

    fun renderItem(graphics: GuiGraphics, item: ItemStack, mouseX: Int, mouseY: Int) {
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val width = minOf(240, minecraft.window.guiScaledWidth - 24).coerceAtLeast(1)
        // Vanilla Fabric tooltips do not wrap individual item description lines.
        // Retain the item's native tooltip image while bounding the styled text explicitly.
        val lines = Screen.getTooltipFromItem(minecraft, item).flatMap { BoundedGuiText.wrap(it, width, font) }
        graphics.renderTooltip(font, lines, item.tooltipImage, mouseX, mouseY)
    }

    fun renderHover(graphics: GuiGraphics, style: Style, mouseX: Int, mouseY: Int, partialTick: Float): Boolean {
        val hover = style.hoverEvent ?: return false
        hover.getValue(HoverEvent.Action.SHOW_TEXT)?.let {
            render(graphics, it, mouseX, mouseY, partialTick)
            return true
        }
        hover.getValue(HoverEvent.Action.SHOW_ITEM)?.let {
            renderItem(graphics, it.itemStack, mouseX, mouseY)
            return true
        }
        if (Minecraft.getInstance().options.advancedItemTooltips) {
            hover.getValue(HoverEvent.Action.SHOW_ENTITY)?.let {
                renderLines(graphics, it.tooltipLines, mouseX, mouseY, partialTick)
                return true
            }
        }
        return false
    }

    private fun renderLines(graphics: GuiGraphics, text: List<Component>, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (text.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val screenWidth = minecraft.window.guiScaledWidth
        val screenHeight = minecraft.window.guiScaledHeight
        val styled = text.map { it.copy().withStyle(it.style.withFont(CobblemonResources.DEFAULT_LARGE)) }
        fun wrapped(width: Int) = styled.flatMap { font.split(it, width) }
        val placement = UmbraInfuserTextLayout.tooltip(screenWidth, screenHeight, mouseX, mouseY) { width ->
            val measured = wrapped(width)
            UmbraInfuserTextLayout.Size(measured.maxOfOrNull(font::width) ?: 0, measured.size * LINE_HEIGHT + 1)
        }
        val lines = wrapped(placement.wrapWidth())
        if (lines.isEmpty()) return
        val width = placement.contentWidth()
        val height = placement.contentHeight()

        // Flush models, slots and labels before sampling the tooltip's blurred backdrop.
        graphics.flush()
        val pose = graphics.pose()
        pose.pushPose()
        try {
            pose.translate(placement.left().toDouble(), placement.top().toDouble(), 1000.0)
            pose.scale(placement.scale().toFloat(), placement.scale().toFloat(), 1F)
            minecraft.mainRenderTarget.bindWrite(false)
            GuiPoseScissor.enable(graphics, 1, 1, width - 1, height - 1)
            try {
                minecraft.gameRenderer.processBlurEffect(partialTick)
            } finally {
                graphics.disableScissor()
                minecraft.mainRenderTarget.bindWrite(true)
            }
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            strip(graphics, BACKGROUND, 1, 0, width - 2, height)
            strip(graphics, EDGE, 0, 0, 1, height)
            strip(graphics, EDGE, width - 1, 0, 1, height)
            graphics.flush()
            lines.forEachIndexed { index, line ->
                graphics.drawString(font, line, 4, 1 + index * LINE_HEIGHT, -1, true)
            }
            graphics.flush()
        } finally {
            pose.popPose()
        }
    }

    /** Stretch the native one-pixel texture through the body, keeping its top and bottom borders. */
    private fun strip(graphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int) {
        graphics.blit(texture, x, y, width, 1, 0F, 0F, 1, 1, 1, 11)
        graphics.blit(texture, x, y + 1, width, height - 2, 0F, 1F, 1, 9, 1, 11)
        graphics.blit(texture, x, y + height - 1, width, 1, 0F, 10F, 1, 1, 1, 11)
    }
}
