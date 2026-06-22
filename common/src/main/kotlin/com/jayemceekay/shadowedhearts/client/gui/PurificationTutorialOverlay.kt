package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Which side of the tooltip the arrow originates from.
 */
enum class ArrowSide {
    TOP, BOTTOM, LEFT, RIGHT
}

/**
 * A single step in the purification chamber tutorial sequence.
 *
 * @param text      The tooltip text to display for this step.
 * @param posX      Screen X position (center of the tooltip).
 * @param posY      Screen Y position (top of the tooltip).
 * @param offsetY   Optional vertical offset applied on top of [posY].
 * @param width     Maximum width of the tooltip in pixels. Text will word-wrap to fit. If null, auto-sizes to text width.
 * @param height    Explicit height of the tooltip in pixels. If null, auto-sizes based on wrapped line count.
 * @param arrowSide The side of the tooltip the arrow starts from. If null, no arrow is drawn.
 * @param arrowTargetX Screen X position the arrow points to.
 * @param arrowTargetY Screen Y position the arrow points to.
 * @param highlightX Screen X position (left edge) of the highlight box. If null, no highlight box is drawn.
 * @param highlightY Screen Y position (top edge) of the highlight box.
 * @param highlightWidth Width of the highlight box in pixels.
 * @param highlightHeight Height of the highlight box in pixels.
 */
data class TutorialStep(
    val text: MutableComponent,
    val posX: Int,
    val posY: Int,
    val offsetY: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val arrowSide: ArrowSide? = null,
    val arrowTargetX: Int = 0,
    val arrowTargetY: Int = 0,
    val highlightX: Int? = null,
    val highlightY: Int = 0,
    val highlightWidth: Int = 0,
    val highlightHeight: Int = 0
)

/**
 * Overlay that manages and renders a sequence of tutorial popup steps for
 * the Purification Chamber GUI. Each popup uses the same visual style as
 * the Pokédex / center-platform tooltip (tooltip_edge + tooltip_background).
 *
 * Usage:
 *  1. Call [start] to begin the tutorial from the first step.
 *  2. Call [render] every frame from the parent GUI's render method.
 *  3. Call [advance] (e.g., on mouse click or key press) to move to the next step.
 *  4. The overlay auto-hides after the last step, or call [stop] to dismiss early.
 *
 * The list of [TutorialStep]s is intentionally left as a mutable property so the
 * caller can configure steps, positions, and text without modifying this class.
 */
class PurificationTutorialOverlay {

    companion object {
        /** Edge/border color: #84FEFF */
        private const val EDGE_COLOR: Int = 0xFF84FEFF.toInt()
        /** Background fill color: #4F9297 */
        private const val BG_COLOR: Int = 0xFF4F9297.toInt()
    }

    /** The ordered list of tutorial steps. Populate before calling [start]. */
    var steps: List<TutorialStep> = emptyList()

    /** Index of the currently displayed step, or -1 when inactive. */
    var currentIndex: Int = -1
        private set

    /** Whether the tutorial overlay is currently being shown. */
    val isActive: Boolean get() = currentIndex in steps.indices

    /** Begin the tutorial from the first step. */
    fun start() {
        if (steps.isNotEmpty()) currentIndex = 0
    }

    /** Advance to the next step. Hides the overlay if already on the last step. */
    fun advance() {
        if (!isActive) return
        currentIndex++
        if (currentIndex >= steps.size) {
            stop()
        }
    }

    /** Go back to the previous step if possible. */
    fun back() {
        if (!isActive) return
        if (currentIndex > 0) currentIndex--
    }

    /** Immediately dismiss the tutorial overlay. */
    fun stop() {
        currentIndex = -1
    }

    /**
     * Render the current tutorial popup using the Pokédex tooltip visual style.
     * Call this from the parent screen's [render] method, typically after all
     * other widgets so the popup draws on top.
     */
    fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!isActive) return
        val step = steps.getOrNull(currentIndex) ?: return

        val poseStack = context.pose()
        val font = Minecraft.getInstance().font
        val lineHeight = 10
        val padding = 6

        // Word-wrap text if a width is specified, otherwise single line
        val wrappedLines: List<MutableComponent> = if (step.width != null) {
            wrapText(step.text.string, step.width - padding, font)
        } else {
            listOf(step.text)
        }

        val maxLineWidth = wrappedLines.maxOf { font.width(it.font(CobblemonResources.DEFAULT_LARGE)) }
        val tooltipWidth = step.width ?: (maxLineWidth + padding)
        val contentHeight = wrappedLines.size * lineHeight + 1
        val tooltipHeight = step.height ?: contentHeight
        val tooltipTop = step.posY + step.offsetY

        val leftX = step.posX - (tooltipWidth / 2)

        poseStack.pushPose()
        poseStack.translate(0.0, 0.0, 1000.0)

        // Apply the same blur backdrop as the Pokédex tooltip
        Minecraft.getInstance().let {
            it.mainRenderTarget.bindWrite(false)
            context.enableScissor(
                leftX,
                tooltipTop + 1,
                leftX + tooltipWidth,
                tooltipTop + tooltipHeight - 1
            )
            it.gameRenderer.processBlurEffect(partialTick)
            context.disableScissor()
            it.mainRenderTarget.bindWrite(true)
        }

        // Dynamically drawn tooltip box — edges (#84FEFF) and background (#4F9297)
        // Background fill
        context.fill(leftX - 1, tooltipTop - 1, leftX + tooltipWidth + 1, tooltipTop + tooltipHeight + 1, BG_COLOR)
        // Left edge (1px)
        context.fill(leftX - 1, tooltipTop, leftX, tooltipTop + tooltipHeight, EDGE_COLOR)
        // Right edge (1px)
        context.fill(leftX + tooltipWidth, tooltipTop, leftX + tooltipWidth + 1, tooltipTop + tooltipHeight, EDGE_COLOR)
        // Top edge (1px)
        context.fill(leftX, tooltipTop - 1, leftX + tooltipWidth, tooltipTop, EDGE_COLOR)
        // Bottom edge (1px)
        context.fill(leftX, tooltipTop + tooltipHeight, leftX + tooltipWidth, tooltipTop + tooltipHeight + 1, EDGE_COLOR)

        // Draw each wrapped line, centered
        for ((i, line) in wrappedLines.withIndex()) {
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = line,
                x = step.posX,
                y = tooltipTop + 1 + (i * lineHeight),
                shadow = true,
                centered = true
            )
        }

        // Step indicator (e.g., "1 / 5") rendered just below the tooltip
        if (steps.size > 1) {
            val indicator = Component.literal("${currentIndex + 1} / ${steps.size}")
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = indicator,
                x = step.posX,
                y = tooltipTop + tooltipHeight + 2,
                shadow = true,
                centered = true,
                scale = 1.0F
            )
        }

        // Draw Manhattan-distance arrow if configured
        if (step.arrowSide != null) {
            drawManhattanArrow(context, step.arrowSide, leftX, tooltipTop, tooltipWidth, tooltipHeight, step.arrowTargetX, step.arrowTargetY)
        }

        // Draw highlight box around a UI element if configured
        if (step.highlightX != null) {
            drawHighlightBox(context, step.highlightX, step.highlightY, step.highlightWidth, step.highlightHeight)
        }

        poseStack.popPose()
    }

    /**
     * Draws a Manhattan-distance (right-angle) arrow from the center of the specified
     * [side] of the tooltip to the target point ([targetX], [targetY]).
     *
     * The arrow consists of two segments that form an L-shape:
     *  - From TOP/BOTTOM: first vertical, then horizontal.
     *  - From LEFT/RIGHT: first horizontal, then vertical.
     *
     * The line is 1px wide and uses [EDGE_COLOR].
     */
    private fun drawManhattanArrow(
        context: GuiGraphics,
        side: ArrowSide,
        leftX: Int,
        tooltipTop: Int,
        tooltipWidth: Int,
        tooltipHeight: Int,
        targetX: Int,
        targetY: Int
    ) {
        // Determine the origin point at the center of the specified side
        val originX: Int
        val originY: Int
        when (side) {
            ArrowSide.TOP -> {
                originX = leftX + tooltipWidth / 2
                originY = tooltipTop - 1
            }
            ArrowSide.BOTTOM -> {
                originX = leftX + tooltipWidth / 2
                originY = tooltipTop + tooltipHeight + 1
            }
            ArrowSide.LEFT -> {
                originX = leftX - 1
                originY = tooltipTop + tooltipHeight / 2
            }
            ArrowSide.RIGHT -> {
                originX = leftX + tooltipWidth + 1
                originY = tooltipTop + tooltipHeight / 2
            }
        }

        // Draw two segments forming an L-shape (Manhattan path)
        when (side) {
            ArrowSide.TOP, ArrowSide.BOTTOM -> {
                // Vertical segment first: originX stays, move to targetY
                val minY = minOf(originY, targetY)
                val maxY = maxOf(originY, targetY)
                context.fill(originX, minY, originX + 1, maxY + 1, EDGE_COLOR)
                // Horizontal segment: from originX to targetX at targetY
                val minX = minOf(originX, targetX)
                val maxX = maxOf(originX, targetX)
                context.fill(minX, targetY, maxX + 1, targetY + 1, EDGE_COLOR)
            }
            ArrowSide.LEFT, ArrowSide.RIGHT -> {
                // Horizontal segment first: originY stays, move to targetX
                val minX = minOf(originX, targetX)
                val maxX = maxOf(originX, targetX)
                context.fill(minX, originY, maxX + 1, originY + 1, EDGE_COLOR)
                // Vertical segment: from originY to targetY at targetX
                val minY = minOf(originY, targetY)
                val maxY = maxOf(originY, targetY)
                context.fill(targetX, minY, targetX + 1, maxY + 1, EDGE_COLOR)
            }
        }
    }

    /**
     * Draws a highlight/selection box around a UI element using [EDGE_COLOR] for the border.
     * The box is 1px wide on each side, matching the tooltip edge style.
     */
    private fun drawHighlightBox(
        context: GuiGraphics,
        x: Int,
        y: Int,
        boxWidth: Int,
        boxHeight: Int
    ) {
        // Left edge
        context.fill(x - 1, y, x, y + boxHeight, EDGE_COLOR)
        // Right edge
        context.fill(x + boxWidth, y, x + boxWidth + 1, y + boxHeight, EDGE_COLOR)
        // Top edge
        context.fill(x, y - 1, x + boxWidth, y, EDGE_COLOR)
        // Bottom edge
        context.fill(x, y + boxHeight, x + boxWidth, y + boxHeight + 1, EDGE_COLOR)
    }

    /**
     * Splits [text] into multiple [MutableComponent] lines that each fit within [maxWidth] pixels.
     * Wraps on word boundaries (spaces).
     */
    private fun wrapText(text: String, maxWidth: Int, font: net.minecraft.client.gui.Font): List<MutableComponent> {
        val words = text.split(" ")
        val lines = mutableListOf<MutableComponent>()
        var currentLine = StringBuilder()

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val candidateWidth = font.width(Component.literal(candidate).font(CobblemonResources.DEFAULT_LARGE))
            if (candidateWidth > maxWidth && currentLine.isNotEmpty()) {
                lines.add(Component.literal(currentLine.toString()))
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(candidate)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(Component.literal(currentLine.toString()))
        }
        return lines.ifEmpty { listOf(Component.literal("")) }
    }

    /**
     * Handle a mouse click while the tutorial is active.
     * Returns `true` if the click was consumed (i.e., the tutorial advanced or dismissed).
     */
    fun handleClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isActive) return false
        // Left click advances, right click goes back
        when (button) {
            0 -> advance()
            1 -> back()
        }
        return true
    }

    /**
     * Handle a key press while the tutorial is active.
     * Returns `true` if the key was consumed.
     */
    fun handleKeyPress(keyCode: Int): Boolean {
        if (!isActive) return false
        // Escape dismisses, any other key advances
        when (keyCode) {
            256 -> stop()   // ESCAPE
            else -> advance()
        }
        return true
    }
}
