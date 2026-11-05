package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Cobblemon-styled action button used by the PurificationStorageWidget.
 * Mirrors the look & feel of Cobblemon's PC ReleaseButton (texture, font, hover behavior),
 * while letting the caller supply a dynamic label and visibility predicate.
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
    var overflowTooltip: Component? = null
        private set

    companion object {
        private const val WIDTH = 54
        private const val HEIGHT = 14

        // Reuse Cobblemon's PC button texture for consistent style
        private val buttonResource =
            cobblemonResource("textures/gui/pc/pc_release_button.png")
    }

    init {
        refreshState()
    }

    /** Keep vanilla input/focus/narration state in step with the same data we draw. */
    fun refreshState() {
        visible = visibleSupplier()
        message = labelSupplier()
        if (!visible || !active) {
            super.setFocused(false)
            isHovered = false
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        refreshState()
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        refreshState()
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun onPress() {
        refreshState()
        if (visible && active) {
            super.onPress()
            refreshState()
        }
    }

    override fun setFocused(focused: Boolean) {
        refreshState()
        super.setFocused(focused && visible && active)
    }

    override fun isFocused(): Boolean {
        refreshState()
        return super.isFocused()
    }

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? {
        refreshState()
        return super.nextFocusPath(event)
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        refreshState()
        return super.isMouseOver(mouseX, mouseY)
    }

    override fun isActive(): Boolean {
        refreshState()
        return super.isActive()
    }

    override fun narrationPriority(): NarrationPriority {
        refreshState()
        return if (visible && active) super.narrationPriority() else NarrationPriority.NONE
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        refreshState()
        if (visible) super.updateWidgetNarration(output)
    }

    override fun renderWidget(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        overflowTooltip = null
        refreshState()
        if (!visible) return

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

        // Fit the translated label inside the button's texture without changing its hitbox.
        val renderer = Minecraft.getInstance().font
        val full = message.copy().bold().font(CobblemonResources.DEFAULT_LARGE)
        val labelScale = minOf(1F, (WIDTH - 6).toFloat() / renderer.width(full).coerceAtLeast(1)).coerceAtLeast(0.7F)
        val shown = BoundedGuiText.fit(full, ((WIDTH - 6) / labelScale).toInt(), renderer)
        if (renderer.width(full) > ((WIDTH - 6) / labelScale).toInt()
            && isHovered(mouseX.toDouble(), mouseY.toDouble())) overflowTooltip = full
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = shown,
            x = x + (WIDTH / 2),
            y = y + 3.5 + (1F - labelScale) * renderer.lineHeight / 2,
            scale = labelScale,
            centered = true,
            shadow = true
        )
    }

    override fun playDownSound(pHandler: SoundManager) {
        // Silence default click; caller handles playing Cobblemon click sounds
    }

    fun isHovered(mouseX: Double, mouseY: Double) = isMouseOver(mouseX, mouseY)
}
