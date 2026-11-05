package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.pc.TextWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

/**
 * SetNameWidget — lightweight, read-only label modeled after Cobblemon's BoxNameWidget.
 * Displays "Set #" where # is provided by [getSetIndex] + 1, and centers text within the widget width.
 */
class SetNameWidget(
    pX: Int,
    pY: Int,
    private val getSetIndex: () -> Int,
) : TextWidget(pX, pY, text = Component.translatable("gui.shadowedhearts.purification_chamber.set_name", getSetIndex() + 1), update = {}), CobblemonRenderable {
    var overflowTooltip: Component? = null
        private set

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.shadowedhearts.purification_chamber.set_name", getSetIndex() + 1))
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        overflowTooltip = null
        val label = Component.translatable("gui.shadowedhearts.purification_chamber.set_name", getSetIndex() + 1)

        // Navigation buttons bound this row even when the translated set name grows.
        val fieldWidth = minOf(width, 94)
        val shown = BoundedGuiText.fit(label.bold().font(CobblemonResources.DEFAULT_LARGE),
            (fieldWidth - 4).coerceAtLeast(1), Minecraft.getInstance().font)
        val textWidth = Minecraft.getInstance().font.width(shown)
        val centerX = x + (fieldWidth / 2) - (textWidth / 2)
        if (startPosX != centerX) startPosX = centerX
        if (Minecraft.getInstance().font.width(label) > (fieldWidth - 4).coerceAtLeast(1)
            && mouseX >= x && mouseX < x + fieldWidth && mouseY >= y + 3 && mouseY < y + 12) {
            overflowTooltip = label
        }

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            x = startPosX,
            y = y + 3,
            text = shown
        )
        // No cursor or input rendering; this widget is read-only
    }

    // Make it effectively read-only/non-interactive
    override fun setFocused(focused: Boolean) { /* ignore focus */ }
}
