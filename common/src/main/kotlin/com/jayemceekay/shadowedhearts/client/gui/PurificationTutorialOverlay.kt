package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.client.CobblemonResources
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderTutorialLayout
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderHudRect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import kotlin.math.ceil
import kotlin.math.roundToInt

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

        /** Use the same font, wrap width and counter spacing as the renderer. */
        @JvmStatic fun measureHeight(font: Font, text: Component, width: Int, stepCount: Int): Int {
            val available = (width - 6).coerceAtLeast(1)
            val bodyLines = font.split(text.copy().font(CobblemonResources.DEFAULT_LARGE), available).size
            val counterLines = if (stepCount <= 1) 0 else (1..stepCount).maxOf { step ->
                font.split(Component.translatable("gui.shadowedhearts.tutorial.counter", step, stepCount)
                    .font(CobblemonResources.DEFAULT_LARGE), available).size
            }
            return bodyLines * 10 + 1 + if (counterLines == 0) 0 else counterLines * 10 + 2
        }
    }

    /** The ordered list of tutorial steps. Populate before calling [start]. */
    var steps: List<TutorialStep> = emptyList()

    /** Optional inline controls for screens that do not already draw a navigation footer. */
    var navigationHint: Component? = null

    /** Index of the currently displayed step, or -1 when inactive. */
    var currentIndex: Int = -1
        private set

    /** Whether the tutorial overlay is currently being shown. */
    val isActive: Boolean get() = currentIndex in steps.indices

    fun narrate(output: NarrationElementOutput, navigation: Component) {
        val step = steps.getOrNull(currentIndex) ?: return
        output.add(NarratedElementType.TITLE, step.text)
        output.add(NarratedElementType.POSITION,
            Component.translatable("gui.shadowedhearts.tutorial.step", currentIndex + 1, steps.size))
        output.add(NarratedElementType.USAGE, navigation)
    }

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
        renderStep(context, step, partialTick)
    }

    /** Use a measured placement without changing other tutorials' coordinate systems. */
    fun renderPlaced(context: GuiGraphics, partialTick: Float, placement: AuraReaderTutorialLayout.Placement) {
        if (!isActive) return
        renderStep(context, steps[currentIndex], partialTick, placement = placement)
    }

    /** Render at screen resolution while keeping arrows attached to the fitted panel. */
    fun renderFitted(context: GuiGraphics, partialTick: Float, fit: GuiViewportFit, viewportWidth: Int, viewportHeight: Int) {
        if (!isActive) return
        val step = steps.getOrNull(currentIndex) ?: return
        val mappedStep = step.copy(
            posX = fit.toScreenX(step.posX.toDouble()).roundToInt(),
            posY = fit.toScreenY((step.posY + step.offsetY).toDouble()).roundToInt(),
            offsetY = 0,
            arrowTargetX = fit.toScreenX(step.arrowTargetX.toDouble()).roundToInt(),
            arrowTargetY = fit.toScreenY(step.arrowTargetY.toDouble()).roundToInt(),
            highlightX = step.highlightX?.let { fit.toScreenX(it.toDouble()).roundToInt() },
            highlightY = fit.toScreenY(step.highlightY.toDouble()).roundToInt(),
            highlightWidth = (step.highlightWidth * fit.scale()).roundToInt(),
            highlightHeight = (step.highlightHeight * fit.scale()).roundToInt()
        )
        renderStep(context, mappedStep, partialTick, viewportWidth, viewportHeight)
    }

    private fun renderStep(context: GuiGraphics, step: TutorialStep, partialTick: Float,
                           viewportWidth: Int? = null, viewportHeight: Int? = null,
                           placement: AuraReaderTutorialLayout.Placement? = null) {

        val poseStack = context.pose()
        val font = Minecraft.getInstance().font
        val lineHeight = 10
        val padding = 6

        val displayedText = step.text.copy().also { text -> navigationHint?.let { text.append("\n\n").append(it) } }
        val requestedWidth = placement?.contentWidth() ?: step.width ?: (font.width(displayedText.copy().font(CobblemonResources.DEFAULT_LARGE)) + padding)
        val tooltipWidth = if (viewportWidth == null) requestedWidth else
            requestedWidth.coerceIn(padding + 1, (viewportWidth - 8).coerceAtLeast(padding + 1))
        val wrappedLines = font.split(displayedText.font(CobblemonResources.DEFAULT_LARGE), (tooltipWidth - padding).coerceAtLeast(1))
        val contentHeight = wrappedLines.size * lineHeight + 1
        val indicatorLines = if (steps.size > 1) font.split(
            Component.translatable("gui.shadowedhearts.tutorial.counter", currentIndex + 1, steps.size)
                .font(CobblemonResources.DEFAULT_LARGE), (tooltipWidth - padding).coerceAtLeast(1)) else emptyList()
        val indicatorHeight = if (indicatorLines.isEmpty()) 0 else indicatorLines.size * lineHeight + 2
        val tooltipHeight = maxOf(placement?.let { it.contentHeight() - indicatorHeight } ?: step.height ?: contentHeight, contentHeight)
        val popupScale = placement?.scale() ?: if (viewportHeight == null || viewportWidth == null) 1.0 else minOf(
            1.0,
            (viewportWidth - 8).coerceAtLeast(1).toDouble() / tooltipWidth,
            (viewportHeight - 8).coerceAtLeast(1).toDouble() / (tooltipHeight + indicatorHeight)
        )
        val displayWidth = ceil(tooltipWidth * popupScale).toInt()
        val totalHeight = ceil((tooltipHeight + indicatorHeight) * popupScale).toInt()
        val preferredLeft = step.posX - displayWidth / 2
        val preferredTop = step.posY + step.offsetY
        val leftX = placement?.bounds()?.left() ?: if (viewportWidth == null) preferredLeft else preferredLeft.coerceIn(3, maxOf(3, viewportWidth - displayWidth - 3))
        val tooltipTop = placement?.bounds()?.top() ?: if (viewportHeight == null) preferredTop else preferredTop.coerceIn(3, maxOf(3, viewportHeight - totalHeight - 3))

        poseStack.pushPose()
        try {
            poseStack.translate(0.0, 0.0, 1000.0)
            poseStack.pushPose()
            try {
                poseStack.translate(leftX.toDouble(), tooltipTop.toDouble(), 0.0)
                poseStack.scale(popupScale.toFloat(), popupScale.toFloat(), 1F)

                // Apply the same blur backdrop as the Pokédex tooltip
                Minecraft.getInstance().let {
                    context.flush()
                    it.mainRenderTarget.bindWrite(false)
                    try {
                        GuiPoseScissor.enable(context, 0, 1, tooltipWidth, tooltipHeight - 1)
                        try {
                            it.gameRenderer.processBlurEffect(partialTick)
                        } finally {
                            context.disableScissor()
                        }
                    } finally {
                        it.mainRenderTarget.bindWrite(true)
                    }
                }

                // Dynamically drawn tooltip box — edges (#84FEFF) and background (#4F9297)
                // Background fill
                context.fill(-1, -1, tooltipWidth + 1, tooltipHeight + 1, BG_COLOR)
                // Left edge (1px)
                context.fill(-1, 0, 0, tooltipHeight, EDGE_COLOR)
                // Right edge (1px)
                context.fill(tooltipWidth, 0, tooltipWidth + 1, tooltipHeight, EDGE_COLOR)
                // Top edge (1px)
                context.fill(0, -1, tooltipWidth, 0, EDGE_COLOR)
                // Bottom edge (1px)
                context.fill(0, tooltipHeight, tooltipWidth, tooltipHeight + 1, EDGE_COLOR)

                // Draw each wrapped line, centered
                for ((i, line) in wrappedLines.withIndex()) {
                    context.drawString(font, line, (tooltipWidth - font.width(line)) / 2, 1 + i * lineHeight, -1, true)
                }

                // Step indicator (e.g., "1 / 5") rendered just below the tooltip
                for ((index, line) in indicatorLines.withIndex()) {
                    context.drawString(font, line, (tooltipWidth - font.width(line)) / 2,
                        tooltipHeight + 2 + index * lineHeight, -1, true)
                }
                context.flush()
            } finally {
                poseStack.popPose()
            }

            // Clamping may put the popup over its original cue. Never draw a cue through
            // readable text, and keep its border inside the viewport after panel fitting.
            val screenWidth = viewportWidth ?: context.guiWidth()
            val screenHeight = viewportHeight ?: context.guiHeight()
            val popupBounds = AuraReaderHudRect(leftX - 1, tooltipTop - 1,
                leftX + displayWidth + 1, tooltipTop + totalHeight + 1)
            val target = step.highlightX?.let { x ->
                AuraReaderHudRect(x, step.highlightY, x + step.highlightWidth,
                    step.highlightY + step.highlightHeight)
            }
            val cue = TutorialCueLayout.resolve(screenWidth, screenHeight, popupBounds, target,
                step.arrowTargetX, step.arrowTargetY)
            if (step.arrowSide != null && cue.side() != null) {
                // Pick the exit side after clamping, rather than using a side that now
                // points back through the popup or its wrapped step counter.
                drawManhattanArrow(context, ArrowSide.valueOf(cue.side().name), leftX, tooltipTop,
                    displayWidth, totalHeight, cue.targetX(), cue.targetY())
            }
            cue.highlight()?.let { visible ->
                drawHighlightBox(context, visible.left(), visible.top(), visible.width(), visible.height())
            }

        } finally {
            poseStack.popPose()
        }
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
