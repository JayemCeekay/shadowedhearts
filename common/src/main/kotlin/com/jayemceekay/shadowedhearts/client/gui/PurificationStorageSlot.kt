package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorage
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import org.joml.Quaternionf

class PurificationStorageSlot(
    x: Int, y: Int,
    private val parent: PurificationStorageWidget,
    private val storage: ClientPurificationStorage,
    val position: ClientPurificationStorage.PurificationPosition,
    onPress: Button.OnPress
) : Button(x, y, SIZE, SIZE, Component.literal("PurificationStorageSlot"), onPress, DEFAULT_NARRATION), CobblemonRenderable {

    companion object {
        const val SIZE = 25
        private val selectPointerResource = cobblemonResource("textures/gui/pc/pc_pointer.png")
    }

    private val state = FloatingState()
    private var isSlotSelected = false

    fun setSelected(selected: Boolean) {
        isSlotSelected = selected
    }

    override fun playDownSound(soundManager: SoundManager) { /* Silent to match PC slots */ }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (!shouldRender()) return
        renderSlot(context, x, y, delta)
    }

    private fun renderSlot(context: GuiGraphics, posX: Int, posY: Int, partialTicks: Float) {
        val pokemon = getPokemon() ?: return

        val matrices = context.pose()

        // Render the Pokémon similar to StorageSlot but scaled down for 19x19
        context.enableScissor(
            posX - 2,
            posY + 2,
            posX + SIZE + 4,
            posY + SIZE + 4
        )

        matrices.pushPose()
        matrices.translate(posX + (SIZE / 2.0), posY + 1.0, 0.0)
        matrices.scale(2.0F, 2.0F, 1F)

        drawProfilePokemon(
            renderablePokemon = pokemon.asRenderablePokemon(),
            matrixStack = matrices,
            rotation = Quaternionf().rotateXYZ(Math.toRadians(13.0).toFloat(), Math.toRadians(35.0).toFloat(), 0f),
            state = state,
            partialTicks = partialTicks,
            scale = 4.5F
        )
        matrices.popPose()

        context.disableScissor()

        // Simple selected/hover outline using a thin rectangle by drawing 4 1px lines via fill
        matrices.pushPose()
        matrices.translate(0.0, 0.0, 100.0)
        val outlineColor = if (isSlotSelected) 0xFFFFFFFF.toInt() else if (isHoveredOrFocused) 0x88FFFFFF.toInt() else 0
        if (outlineColor != 0) {
            // top
            context.fill(posX - 1, posY - 1, posX + SIZE + 1, posY, outlineColor)
            // bottom
            context.fill(posX - 1, posY + SIZE, posX + SIZE + 1, posY + SIZE + 1, outlineColor)
            // left
            context.fill(posX - 1, posY - 1, posX, posY + SIZE + 1, outlineColor)
            // right
            context.fill(posX + SIZE, posY - 1, posX + SIZE + 1, posY + SIZE + 1, outlineColor)
        }

        // PC-like select pointer when hovered or focused
        if (isHoveredOrFocused) {
            blitk(
                matrixStack = matrices,
                texture = selectPointerResource,
                x = (posX + 10) / PurificationChamberGUI.SCALE,
                y = ((posY - 3) / PurificationChamberGUI.SCALE) - parent.gui.selectPointerOffsetY,
                width = 11,
                height = 8,
                scale = PurificationChamberGUI.SCALE
            )
        }
        matrices.popPose()
    }

    open fun getPokemon(): Pokemon? = storage.get(position)

    open fun shouldRender(): Boolean {
        // Only render when a Pokémon exists in this slot
        return getPokemon() != null
    }

    open fun clickable(): Boolean = true

    override fun isHoveredOrFocused(): Boolean {
        // Mirror PC behavior: compare against GUI's previewPokemon for focus state
        return getPokemon() == parent.gui.previewPokemon
    }

    fun isHovered(mouseX: Int, mouseY: Int) = mouseX.toFloat() in (x.toFloat()..(x.toFloat() + SIZE)) && mouseY.toFloat() in (y.toFloat()..(y.toFloat() + SIZE))

}