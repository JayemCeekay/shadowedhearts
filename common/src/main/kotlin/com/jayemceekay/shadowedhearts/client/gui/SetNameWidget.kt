package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.pc.TextWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * SetNameWidget — lightweight, read-only label modeled after Cobblemon's BoxNameWidget.
 * Displays "Set #" where # is provided by [getSetIndex] + 1, and centers text within the widget width.
 */
class SetNameWidget(
    pX: Int,
    pY: Int,
    private val getSetIndex: () -> Int,
) : TextWidget(pX, pY, text = "SetNameWidget".text(), update = {}), CobblemonRenderable {

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val label = Component.translatable("shadowedhearts.set_name", getSetIndex() + 1)

        // Center text inside the widget bounds similar to BoxNameWidget
        val textWidth = Minecraft.getInstance().font.width(label.bold().font(CobblemonResources.DEFAULT_LARGE))
        val centerX = x + (width / 2) - (textWidth / 2)
        if (startPosX != centerX) startPosX = centerX

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            x = startPosX,
            y = y + 3,
            text = label
        )
        // No cursor or input rendering; this widget is read-only
    }

    // Make it effectively read-only/non-interactive
    override fun setFocused(focused: Boolean) { /* ignore focus */ }
}
