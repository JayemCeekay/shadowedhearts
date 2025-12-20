package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Cobblemon-styled action button used by the PurificationStorageWidget.
 * Mirrors the look & feel of Cobblemon's PC ReleaseButton (texture, font, hover behavior),
 * while letting the caller supply a dynamic label and visibility predicate.
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
class PurificationActionButton(
    x: Int,
    y: Int,
    private val labelSupplier: () -> MutableComponent,
    private val visibleSupplier: () -> Boolean = { true },
    onPress: OnPress
) : Button(
    x,
    y,
    WIDTH,
    HEIGHT,
    Component.literal(""),
    onPress,
    DEFAULT_NARRATION
), CobblemonRenderable {

    companion object {
        private const val WIDTH = 54
        private const val HEIGHT = 14

        // Reuse Cobblemon's PC button texture for consistent style
        private val buttonResource =
            cobblemonResource("textures/gui/pc/pc_release_button.png")
    }

    override fun renderWidget(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!visibleSupplier()) return

        // Draw base/hover state from texture (two rows; second is hovered)
        blitk(
            matrixStack = context.pose(),
            texture = buttonResource,
            x = x,
            y = y,
            width = WIDTH,
            height = HEIGHT,
            vOffset = if (isHovered(
                    mouseX.toDouble(),
                    mouseY.toDouble()
                )
            ) HEIGHT else 0,
            textureHeight = HEIGHT * 2
        )

        // Draw centered label using Cobblemon's large font for consistency
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = labelSupplier().bold(),
            x = x + (WIDTH / 2),
            y = y + 3.5,
            centered = true,
            shadow = true
        )
    }

    override fun playDownSound(pHandler: SoundManager) {
        // Silence default click; caller handles playing Cobblemon click sounds
    }

    fun isHovered(mouseX: Double, mouseY: Double) =
        mouseX.toFloat() in (x.toFloat()..(x.toFloat() + WIDTH)) &&
                mouseY.toFloat() in (y.toFloat()..(y.toFloat() + HEIGHT))
}
