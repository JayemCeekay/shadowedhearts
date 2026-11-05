package com.jayemceekay.shadowedhearts.client.gui

import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.ModKeybinds
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderClientState
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderHud
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderHudRect
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderTutorialLayout
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import dev.architectury.platform.Platform
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.util.Properties

/** Once per client installation, with help available again from the Reader binding. */
object AuraReaderTutorial {
    private const val VERSION = "1"
    private val preferences = Properties()
    private var loaded = false
    private val path get() = Platform.getConfigFolder().resolve("shadowedhearts-aura-reader-tutorial.properties")

    private fun load() {
        if (loaded) return
        loaded = true
        try {
            if (Files.isRegularFile(path)) Files.newInputStream(path).use(preferences::load)
        } catch (exception: Exception) {
            Shadowedhearts.LOGGER.warn("Unable to read Aura Reader tutorial preference", exception)
        }
    }

    @JvmStatic fun tick(client: Minecraft) {
        if (client.screen != null || client.player == null || client.level == null) return
        if (AuraReaderClientState.isInBattle()) return
        if (!SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(client.player)) return
        load()
        if (preferences.getProperty("seenVersion") != VERSION) open(client)
    }

    @JvmStatic fun open(client: Minecraft) {
        if (client.screen == null) {
            AuraReaderClientState.setTutorialPreview(true)
            client.setScreen(AuraReaderTutorialScreen())
        }
    }

    fun acknowledge() {
        load()
        preferences.setProperty("seenVersion", VERSION)
        try {
            Files.createDirectories(path.parent)
            Files.newOutputStream(path).use { preferences.store(it, "Aura Reader tutorial preference") }
        } catch (exception: Exception) {
            Shadowedhearts.LOGGER.warn("Unable to save Aura Reader tutorial preference", exception)
        }
    }
}

private class AuraReaderTutorialScreen : Screen(Component.translatable("gui.shadowedhearts.aura_reader.tutorial.title")) {
    private val overlay = PurificationTutorialOverlay()
    private lateinit var placement: AuraReaderTutorialLayout.Placement
    private var navigation: List<FormattedCharSequence> = emptyList()
    private var navigationTop = 0

    override fun updateNarrationState(output: NarrationElementOutput) {
        if (overlay.isActive) overlay.narrate(output, Component.translatable("gui.shadowedhearts.aura_reader.tutorial.navigation"))
        else super.updateNarrationState(output)
    }

    override fun init() {
        val step = overlay.currentIndex.coerceAtLeast(0)
        fun text(key: String, vararg args: Any) = Component.translatable("gui.shadowedhearts.aura_reader.tutorial.$key", *args)
        fun binding(key: net.minecraft.client.KeyMapping?) = key?.translatedKeyMessage ?: Component.literal("?")
        val geometry = AuraReaderHud.getTutorialGeometry(width, height)
        val viewport = AuraReaderHudRect(0, 0, width, height)
        val reticle = geometry.reticle().clamp(viewport)
        val rail = geometry.compassRail()
        val compass = AuraReaderHudRect(rail.left(), (rail.top() - 16).coerceAtLeast(0),
            rail.right(), (rail.bottom() + 16).coerceAtMost(height))
        val pages = listOf(
            text("welcome", binding(ModKeybinds.AURA_SCANNER)) to null,
            text("pulse", binding(ModKeybinds.AURA_PULSE)) to reticle,
            text("selection", binding(ModKeybinds.AURA_PREV_SIGNAL),
                binding(ModKeybinds.AURA_NEXT_SIGNAL)) to compass,
            text("analysis") to reticle,
            text("pin", binding(ModKeybinds.AURA_LOCK)) to compass,
            text("wild_lock", binding(ModKeybinds.AURA_LOCK)) to reticle,
            text("sites") to geometry.signalPopover(),
            text("battle") to null,
            text("finish", binding(ModKeybinds.AURA_READER_HELP)) to null
        )
        navigation = font.split(text("navigation"), (width - 16).coerceAtLeast(1))
        val footerHeight = navigation.size * font.lineHeight + 12
        navigationTop = height - footerHeight + 4
        // Measure with the renderer's actual font, padding, line spacing and counter.
        // Reserve the longest page so advancing never makes the textbox jump around.
        placement = AuraReaderTutorialLayout.resolve(width, height, footerHeight, reticle,
            listOf(compass, geometry.signalPopover())) { textWidth ->
            pages.maxOf { (body, _) ->
                PurificationTutorialOverlay.measureHeight(font, body, textWidth, pages.size)
            }
        }
        val box = placement.bounds()
        overlay.steps = pages.map { (body, target) ->
            // Use the visible HUD bounds, including the configured reticle scale.
            // Omit a cue when a cramped viewport cannot keep the box off its target.
            val visibleTarget = target?.takeUnless { it.intersects(box) }
            val side = when {
                visibleTarget == null -> null
                box.left() >= visibleTarget.right() -> ArrowSide.LEFT
                box.right() <= visibleTarget.left() -> ArrowSide.RIGHT
                box.top() >= visibleTarget.bottom() -> ArrowSide.TOP
                else -> ArrowSide.BOTTOM
            }
            TutorialStep(body, (box.left() + box.right()) / 2, box.top(),
                arrowSide = side,
                arrowTargetX = when (side) {
                    ArrowSide.LEFT -> visibleTarget!!.right()
                    ArrowSide.RIGHT -> visibleTarget!!.left()
                    else -> visibleTarget?.let { (it.left() + it.right()) / 2 } ?: 0
                },
                arrowTargetY = when (side) {
                    ArrowSide.TOP -> visibleTarget!!.bottom()
                    ArrowSide.BOTTOM -> visibleTarget!!.top()
                    else -> visibleTarget?.let { (it.top() + it.bottom()) / 2 } ?: 0
                },
                highlightX = visibleTarget?.left(), highlightY = visibleTarget?.top() ?: 0,
                highlightWidth = visibleTarget?.width() ?: 0, highlightHeight = visibleTarget?.height() ?: 0)
        }
        overlay.start()
        repeat(step.coerceAtMost(overlay.steps.lastIndex)) { overlay.advance() }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        overlay.renderPlaced(graphics, delta, placement)
        for ((index, line) in navigation.withIndex()) {
            val x = (width - font.width(line)) / 2
            val y = navigationTop + index * font.lineHeight
            graphics.fill(x - 3, y - 1, x + font.width(line) + 3, y + font.lineHeight, 0xA0000000.toInt())
            graphics.drawString(font, line, x, y, 0xDDB8FF)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) overlay.advance() else if (button == 1) overlay.back() else return true
        if (!overlay.isActive) onClose()
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE -> onClose()
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_BACKSPACE -> overlay.back()
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> {
                overlay.advance()
                if (!overlay.isActive) onClose()
            }
        }
        return true
    }

    override fun onClose() {
        AuraReaderTutorial.acknowledge()
        super.onClose()
    }

    override fun removed() {
        AuraReaderClientState.setTutorialPreview(false)
        super.removed()
    }

    override fun isPauseScreen() = false
}
