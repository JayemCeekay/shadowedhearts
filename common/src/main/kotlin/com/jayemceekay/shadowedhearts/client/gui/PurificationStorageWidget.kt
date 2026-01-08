package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.ModShaders
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters
import com.jayemceekay.shadowedhearts.client.purification.PurificationClientMetrics
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorage
import com.jayemceekay.shadowedhearts.storage.purification.PurificationMath
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Minimal storage widget for the Purification Chamber.
 * Similar intent to Cobblemon's StorageWidget, but with 5 slots (0 center, 1-4 outer).
 * Shows a single context button that toggles between "Add" and "Remove" depending on selection.
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
class PurificationStorageWidget(
    pX: Int,
    pY: Int,
    val gui: PurificationChamberGUI,
    val storage: ClientPurificationStorage,
    private val onAddRequested: (index: Int) -> Unit = {},
    private val onMoveToPCRequested: (index: Int) -> Unit = {}
) : SoundlessWidget(
    pX,
    pY,
    WIDTH,
    HEIGHT,
    Component.literal("PurificationStorageWidget")
) {

    companion object {
        const val WIDTH = 263
        const val HEIGHT = 155
        const val SCREEN_WIDTH = 174
        const val SCREEN_HEIGHT = 155
        const val RIGHT_PANEL_WIDTH = 82
        const val RIGHT_PANEL_HEIGHT = 176
        private val screenOverlayResource =
            cobblemonResource("textures/gui/pc/pc_screen_overlay.png")
        private val screenGridResource =
            cobblemonResource("textures/gui/pc/pc_screen_grid.png")
        private val partyPanelResource = ResourceLocation.fromNamespaceAndPath(
            Shadowedhearts.MOD_ID,
            "textures/gui/purification_pc/party_panel.png"
        )
        private val screenGlowResource =
            cobblemonResource("textures/gui/pc/pc_screen_glow.png")


        var screenLoaded = false

        // Slot visuals
        private const val SLOT_SIZE = 19

        // Platform texture borrowed from Cobblemon pokedex UI
        private val platformTexture =
            cobblemonResource("textures/gui/pokedex/platform_base.png")
        private const val PLATFORM_W = 56
        private const val PLATFORM_H = 15

        // Simple value class to expose the depth range for orbiting platforms
        data class DepthRange(val min: Float, val max: Float)

        /**
         * Computes the theoretical minimum and maximum camera-space depth (cam.z) for an orbiter that
         * moves on a circle of radius [orbitRadius] in the world XZ plane around the origin, given a camera
         * at [camPos] looking at [camTarget]. This matches the math used by the tiny 3D projection in this widget.
         *
         * Depth here corresponds to dot((world - camPos), forward), where forward = normalize(camTarget - camPos).
         * For points (x, 0, z) on the circle, cam.z varies between base ± orbitRadius * sqrt(fx^2 + fz^2), where
         * base = -dot(camPos, forward) and forward = (fx, fy, fz).
         */
        fun computeOrbDepthRange(
            camPosX: Float,
            camPosY: Float,
            camPosZ: Float,
            camTargetX: Float,
            camTargetY: Float,
            camTargetZ: Float,
            orbitRadius: Float
        ): DepthRange {
            // forward = normalize(camTarget - camPos)
            val dx = camTargetX - camPosX
            val dy = camTargetY - camPosY
            val dz = camTargetZ - camPosZ
            val len = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                .toFloat().let { if (it == 0f) 1f else it }
            val fx = dx / len
            val fy = dy / len
            val fz = dz / len

            // base = -dot(camPos, forward)
            val base = -(camPosX * fx + camPosY * fy + camPosZ * fz)

            // Variation term across the XZ circle
            val radial =
                orbitRadius * kotlin.math.sqrt((fx * fx + fz * fz).toDouble())
                    .toFloat()

            return DepthRange(min = base - radial, max = base + radial)
        }

        /**
         * Convenience: depth range using this widget's current camera/animation defaults.
         * Camera at (0, 25, 90), target (0, 0, 0), orbit radius 36.
         */
        fun defaultOrbDepthRange(): DepthRange = computeOrbDepthRange(
            camPosX = 0.0f,
            camPosY = 25.0f,
            camPosZ = 90.0f,
            camTargetX = 0.0f,
            camTargetY = 0.0f,
            camTargetZ = 0.0f,
            orbitRadius = 36.0f
        )
    }

    private val purificationChamberPurificationStorageSlots =
        arrayListOf<PurificationStorageSlot>()
    private var selectedIndex: Int? = null

    private var actionButton: PurificationActionButton? = null

    // State used for rendering the center Pokémon model (slot 0)
    private val centerModelState = FloatingState()

    // States used for rendering the four orbital Pokémon models (slots 1-4)
    // Each orbital gets its own state so updatePartialTicks is applied once per frame per Pokémon.
    private val orbitalModelStates = arrayOf(
        FloatingState(), // slot 1
        FloatingState(), // slot 2
        FloatingState(), // slot 3
        FloatingState()  // slot 4
    )

    init {
        setupSlots()
        setupActionButton()
    }

    private fun setupSlots() {
        this.purificationChamberPurificationStorageSlots.forEach(this::removeWidget)
        this.purificationChamberPurificationStorageSlots.clear()

        PurificationStorageSlot(
            x = x + 210,
            y = y - 10,
            this,
            storage,
            ClientPurificationStorage.PurificationPosition(0),
            onPress = {}).also { widget ->
            this.addWidget(widget)
            this.purificationChamberPurificationStorageSlots.add(widget)
        }

        // Render slots 1..4 in a 2x2 grid to the right of slot 0
        for (i in 1..4) {
            val index = i - 1
            val baseX =
                x + 160 + (SLOT_SIZE) // start one slot to the right of slot 0
            val baseY = y + 21

            PurificationStorageSlot(
                x = baseX + (SLOT_SIZE + 12),
                y = baseY + index * (SLOT_SIZE + 9),
                this,
                storage,
                ClientPurificationStorage.PurificationPosition(i),
                onPress = {}
            ).also { widget ->
                this.addWidget(widget)
                this.purificationChamberPurificationStorageSlots.add(widget)
            }
        }
    }

    private fun setupActionButton() {
        val btnX = x + 195 // align to right edge of widget width
        val btnY = y + 138 // a bit above bottom

        actionButton = PurificationActionButton(
            x = btnX,
            y = btnY,
            labelSupplier = {
                val idx = selectedIndex
                if (idx == null) Component.literal("Add")
                else {
                    val occupied = storage.get(
                        ClientPurificationStorage.PurificationPosition(idx)
                    ) != null
                    Component.literal(if (occupied) "Remove" else "Add")
                }
            },
            visibleSupplier = { selectedIndex != null }
        ) {
            val idx = selectedIndex
            if (idx != null) {
                val pos = ClientPurificationStorage.PurificationPosition(idx)
                val current = storage.get(pos)
                if (current == null) {
                    gui.playSound(CobblemonSounds.PC_CLICK)
                    onAddRequested(idx)
                } else {
                    gui.playSound(CobblemonSounds.PC_CLICK)
                    onMoveToPCRequested(idx)
                }
            }
        }
        // Initialize enabled state based on current selection (likely none initially)
        actionButton?.active = selectedIndex != null
    }

    fun getActionButton(): Button? = actionButton

    private fun updateActionLabel() {
        val btn = actionButton ?: return
        // Keep button enabled when a slot is selected; disabled otherwise
        btn.active = selectedIndex != null
    }

    override fun renderWidget(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        val matrices = guiGraphics.pose()


        // Render the purificationChamberBackground shader in a 174 x 155 quad
        run {
            val shader = ModShaders.PURIFICATION_CHAMBER_BACKGROUND
            if (shader != null) {
                // Bind shader and update generic uniforms we rely on
                RenderSystem.disableDepthTest()
                RenderSystem.enableBlend()
                RenderSystem.defaultBlendFunc()


                val window = Minecraft.getInstance().window
                // Set local quad size for aspect-correct math inside the shader
                shader.getUniform("ScreenSize")
                    ?.set(window.window.toFloat(), window.height.toFloat())

                val level = Minecraft.getInstance().level
                val dayTime = level?.dayTime ?: 0L
                val gameTimeDays = dayTime.toFloat() / 24000.0f
                shader.getUniform("GameTime")
                    ?.set(gui.ticksElapsed.toFloat() + gameTimeDays)

                // Apply and draw quad
                RenderSystem.setShader { shader }
                val pose = matrices.last().pose()
                val buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX
                )

                val x0 = x.toFloat()
                val y0 = y + 1.toFloat()
                val x1 = (x + SCREEN_WIDTH).toFloat()
                val y1 = (y + SCREEN_HEIGHT).toFloat()

                buffer.addVertex(pose, x0, y1, 1f).setUv(0f, 1f)
                buffer.addVertex(pose, x1, y1, 1f).setUv(1f, 1f)
                buffer.addVertex(pose, x1, y0, 1f).setUv(1f, 0f)
                buffer.addVertex(pose, x0, y0, 1f).setUv(0f, 0f)

                BufferUploader.drawWithShader(buffer.buildOrThrow())
                RenderSystem.disableBlend()
            }
        }


        // Background frame like PC screen
        blitk(
            matrixStack = matrices,
            texture = screenOverlayResource,
            x = x,
            y = y,
            width = SCREEN_WIDTH,
            height = SCREEN_HEIGHT + 1
        )

        blitk(
            matrixStack = matrices,
            texture = partyPanelResource,
            x = x + 182,
            y = y - 19,
            width = RIGHT_PANEL_WIDTH,
            height = RIGHT_PANEL_HEIGHT
        )

        drawScaledText(
            context = guiGraphics,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Component.translatable("shadowedhearts.gui.purification_chamber.tempo")
                .bold(),
            x = x + 192,
            y = y - 15.5,
            scale = 0.8f,
            centered = true,
            shadow = true
        )

        drawScaledText(
            context = guiGraphics,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Component.translatable("shadowedhearts.gui.purification_chamber.flow")
                .bold(),
            x = x + 253,
            y = y - 15.5,
            scale = 0.8f,
            centered = true,
            shadow = true
        )


        // --- Tempo/Flow vertical bars (8 x 137), positioned on either side of the purification storage slots ---
        run {
            // Dimensions per spec
            val barW = 8
            val barH = 137

            // Place bars centered under the labels (which already sit left/right of the slot column)
            val leftBarCenterX = x + 194
            val rightBarCenterX = x + 251
            val barTop =
                y - 3 // fits well within the right panel (which spans y-19 .. y+157)

            val leftBarX0 = leftBarCenterX - (barW / 2)
            val rightBarX0 = rightBarCenterX - (barW / 2)

            // Colors (ARGB): background, fill, and border
            val bgColor = 0x66000000     // semi-opaque dark background
            val fillColor = 0xFF6AD5FF.toInt()   // cyan-ish fill for visibility (tempo bar)
            val borderColor = 0x99FFFFFF.toInt() // soft white border
            val smallBarColor = 0xFFF0F0F0.toInt() // off-white base for small bars
            // Flow meter large segment palette (dark → light to blend into small bars)
            // Chosen by sampling the provided reference image: deep blue progressing to light cyan.
            val largeSegmentColors = intArrayOf(
                0xFF2334FF.toInt(), // darkest blue (segment 1)
                0xFF1F7FEF.toInt(), // medium blue (segment 2)
                0xFF29B9F8.toInt(), // bright sky blue (segment 3)
                0xFFA9E3FF.toInt()  // light cyan (segment 4, near small bars)
            )

            fun drawBar(x0: Int, y0: Int, w: Int, h: Int, percent: Float) {
                val x1 = x0 + w
                val y1 = y0 + h
                // Border
                guiGraphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, borderColor)
                // Background
                guiGraphics.fill(x0, y0, x1, y1, bgColor)
                // Filled amount (grow upwards)
                val clamped = percent.coerceIn(0f, 1f)
                val fillHeight = (h * clamped).toInt()
                val fy0 = y1 - fillHeight
                if (fillHeight > 0) {
                    guiGraphics.fill(x0 + 1, fy0 + 1, x1 - 1, y1 - 1, fillColor)
                }
            }

            // New segmented FLOW meter per spec:
            // - 4 larger bars occupy 60% of height and represent the current set's flow amount
            // - remaining 40% shows up to 9 smaller bars, each representing a set with full flow
            // - vertical brightness scroll from bottom to top
            fun drawFlowMeter(x0: Int, y0: Int, w: Int, h: Int, currentFlowPct: Float, fullSetsCount: Int, nonShadowSupportCount: Int) {
                // Border and background
                val x1 = x0 + w
                val y1 = y0 + h
                guiGraphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, borderColor)
                guiGraphics.fill(x0, y0, x1, y1, bgColor)

                val innerLeft = x0 + 1
                val innerRight = x1 - 1
                val innerTop = y0 + 1
                val innerBottom = y1 - 1
                val innerH = (innerBottom - innerTop).coerceAtLeast(1)
                // Gaps/padding configuration
                val segmentGap = 1          // 1f padding between large segments
                val smallBarGap = 1         // 1f padding between small bars
                val largeSmallGap = 1       // only 1 pixel gap between large and small regions

                // Helpers
                fun scaleColor(color: Int, brightness: Float): Int {
                    val a = (color ushr 24) and 0xFF
                    var r = (color ushr 16) and 0xFF
                    var g = (color ushr 8) and 0xFF
                    var b = color and 0xFF
                    val br = brightness.coerceIn(0f, 1.5f)
                    r = (r * br).toInt().coerceIn(0, 255)
                    g = (g * br).toInt().coerceIn(0, 255)
                    b = (b * br).toInt().coerceIn(0, 255)
                    return (a shl 24) or (r shl 16) or (g shl 8) or b
                }

                val time = gui.ticksElapsed.toFloat()

                // Layout: large zone occupies 60% of innerH from the bottom
                val largeZoneH = (innerH * 0.60f).toInt().coerceAtLeast(4)
                val smallZoneH = (innerH - largeZoneH).coerceAtLeast(3)

                // Large segments correspond to the number of populated non-shadow slots in the set
                val largeSegments = nonShadowSupportCount.coerceAtLeast(0)
                val largeSegH = ((largeZoneH / 4)).coerceAtLeast(1)

                // Fill large segments based on currentFlowPct, respecting 1px gaps between segments
                val segDrawableH = (largeSegH - segmentGap).coerceAtLeast(1)
                val effectiveDrawableH = largeZoneH * largeSegments
                val totalLargeFill = (effectiveDrawableH * currentFlowPct.coerceIn(0f, 1f)).toInt()

                val largeBottom = innerBottom
                var remainingFill = totalLargeFill
                var largeTipY = largeBottom
                for (i in 0 until largeSegments) {
                    val segBottom = largeBottom - (i * largeSegH)
                    val segTop = (segBottom - segDrawableH).coerceAtLeast(innerTop)
                    val avail = (segBottom - segTop).coerceAtLeast(0)
                    if (avail <= 0) continue
                    val filledInThisSeg = remainingFill.coerceIn(0, avail)
                    if (filledInThisSeg > 0) {
                        // brightness scroll upward: phase offset per pixel row
                        val centerY = segBottom - filledInThisSeg / 2f
                        val phase = (centerY - innerTop) / innerH.toFloat()
                        val brightness = 0.75f + 0.25f * kotlin.math.sin((time * 0.20f + phase * 6.28318f).toDouble()).toFloat()
                        // Pick base color for this large segment (bottom index 0 → darkest)
                        val baseCol = if (i < largeSegmentColors.size) largeSegmentColors[i] else largeSegmentColors.last()
                        val col = scaleColor(baseCol, brightness)
                        guiGraphics.fill(innerLeft, segBottom - filledInThisSeg, innerRight, segBottom, col)
                        largeTipY = segBottom - filledInThisSeg
                        remainingFill -= filledInThisSeg
                    }
                    if (remainingFill <= 0) break
                }

                // Small bars: up to 9 thin bars that start at the tip of the large bars and stack upward
                // NOTE: The maximum available space for the small-bar section is capped at 40% of the whole meter.
                //       This means the small bars can never occupy more than the top 40% region, regardless of where
                //       the large fill tip is. They still visually start from the tip, but are constrained by this cap.
                val smallBarsMax = 9
                val barsToDraw = fullSetsCount.coerceIn(0, smallBarsMax)
                if (barsToDraw > 0) {
                    // Clamp for the very top inside the border
                    val topClamp = innerTop

                    // Bars stack upward from just above the tip of the large fill, leaving 1px gap to the large region
                    val tipStartBottom = (largeTipY - largeSmallGap).coerceAtMost(innerBottom)

                    // Always anchor the small-bar stack at the tip of the large fill
                    val startBottom = tipStartBottom

                    // Limit the total height the small bars may occupy to the 40% small-zone cap
                    // without moving the anchor point. We do this by constraining the drawable height
                    // (distance from topClamp) to at most smallZoneH.
                    val available = kotlin.math.min((startBottom - topClamp).coerceAtLeast(0), smallZoneH)

                    // Partition the small-bar region into 9 slots. We distribute any remainder so that
                    // the sum of the 9 theoretical slot heights exactly equals the drawable space.
                    // This avoids leftover pixels accumulating as an extra gap at the very top when 9 bars are shown.
                    val totalSlotGaps = smallBarGap * (smallBarsMax - 1)
                    val drawableAllSlots = (available - totalSlotGaps).coerceAtLeast(0)
                    val baseH = if (smallBarsMax > 0) (drawableAllSlots / smallBarsMax) else 0
                    var remainder = drawableAllSlots - baseH * smallBarsMax

                    if (baseH > 0 || smallBarGap > 0) {
                        var cursorBottom = startBottom
                        for (i in 0 until barsToDraw) {
                            // Distribute remainder (+1px) to the first N slots to consume all drawable space.
                            val add = if (remainder > 0) 1 else 0
                            val thisH = baseH + add
                            val barBottom = cursorBottom
                            val barTop = (barBottom - thisH).coerceAtLeast(topClamp)

                            if (barBottom > barTop && thisH > 0) {
                                // Draw small bars with off-white base and apply brightness scrolling
                                val centerY = (barTop + barBottom) / 2f
                                val phase = (centerY - innerTop) / innerH.toFloat()
                                val brightness = 0.75f + 0.25f * kotlin.math.sin((time * 0.20f + phase * 6.28318f).toDouble()).toFloat()
                                val col = scaleColor(smallBarColor, brightness)
                                guiGraphics.fill(innerLeft, barTop, innerRight, barBottom, col)
                            }

                            // advance cursor to the next theoretical slot (slot height + fixed gap)
                            cursorBottom = (barTop - smallBarGap)
                            if (cursorBottom <= topClamp) break
                        }
                    }
                }
            }

            // Compute actual tempo/flow based on current set composition in storage
            val center =
                storage.get(ClientPurificationStorage.PurificationPosition(0))
            val supportsArray = arrayOf(
                storage.get(ClientPurificationStorage.PurificationPosition(1)),
                storage.get(ClientPurificationStorage.PurificationPosition(2)),
                storage.get(ClientPurificationStorage.PurificationPosition(3)),
                storage.get(ClientPurificationStorage.PurificationPosition(4))
            )
            // Compute global context for flow cross-set display adjustments
            var perfectSets = 0
            var anySetMissingMember = false
            for (setIdx in 0 until ClientPurificationStorage.TOTAL_SETS) {
                val s1g = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(1))
                val s2g = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(2))
                val s3g = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(3))
                val s4g = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(4))
                val ringList = listOfNotNull(s1g, s2g, s3g, s4g)
                if (ringList.size < 4) anySetMissingMember = true
                if (PurificationMath.isPerfectSet(ringList)) perfectSets++
            }

            val metrics =
                PurificationClientMetrics.compute(center, supportsArray, perfectSets, anySetMissingMember)
            val tempoPct = metrics.tempoPct
            val flowPct = metrics.flowPct

            // Count sets with full flow (~100%). Each set is the current page 0..8 in the client cache.
            var fullSets = 0
            for (setIdx in 0 until ClientPurificationStorage.TOTAL_SETS) {
                val c = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(0))
                val s1 = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(1))
                val s2 = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(2))
                val s3 = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(3))
                val s4 = storage.getAt(setIdx, ClientPurificationStorage.PurificationPosition(4))
                val m = PurificationClientMetrics.compute(c, arrayOf(s1, s2, s3, s4), perfectSets, anySetMissingMember)
                if (m.flowPct >= 0.999f) fullSets++
            }

            // Count populated non-shadow supports in the current set (slots 1..4)
            val nonShadowSupportCount = supportsArray.count { p -> p != null }

            drawBar(leftBarX0, barTop, barW, barH, tempoPct)
            drawFlowMeter(rightBarX0, barTop, barW, barH, flowPct, fullSets, nonShadowSupportCount)
        }

        blitk(
            matrixStack = matrices,
            texture = screenGlowResource,
            x = x - 17,
            y = y - 17,
            width = 208,
            height = 189,
            alpha = if (screenLoaded) 1F else ((gui.ticksElapsed).toFloat() / 10F).coerceIn(
                0F,
                1F
            )
        )

        // Render slots exactly once each (avoid multiple renders per frame which would speed up FloatingState animations)
        for (slot in purificationChamberPurificationStorageSlots) {
            val isSelected = slot.position.index == selectedIndex
            slot.render(guiGraphics, mouseX, mouseY, partialTick)
            val pokemon = slot.getPokemon()
            if (!isSelected && slot.isHovered(mouseX, mouseY) && pokemon != null && pokemon != gui.previewPokemon) {
                gui.setPreviewPokemon(pokemon)
            }
        }

        // Render platforms using a tiny virtual 3D scene (camera + 4 orbiters + perspective projection)
        run {
            // Time base for animation
            val level = Minecraft.getInstance().level
            val tickTime = (level?.gameTime ?: 0L).toFloat() + partialTick

            // Screen center inside the chamber frame (slightly biased down so it sits nicely)
            val screenCx = x + (SCREEN_WIDTH / 2f)
            val screenCy = y + (SCREEN_HEIGHT / 2f) + 18f

            data class Vec3(val x: Float, val y: Float, val z: Float)

            fun sub(a: Vec3, b: Vec3) = Vec3(a.x - b.x, a.y - b.y, a.z - b.z)
            fun dot(a: Vec3, b: Vec3) = a.x * b.x + a.y * b.y + a.z * b.z
            fun cross(a: Vec3, b: Vec3) = Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
            )

            fun normalize(v: Vec3): Vec3 {
                val lenSq = dot(v, v)
                if (lenSq <= 1e-6f) return Vec3(0f, 0f, 1f)
                val inv = 1f / kotlin.math.sqrt(lenSq)
                return Vec3(v.x * inv, v.y * inv, v.z * inv)
            }

            fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

            // Camera setup: above and in front, looking at origin
            val camPos = Vec3(0.0f, 15f, 50.0f)
            val camTarget = Vec3(0.0f, 5f, 0.0f)
            val camUp = Vec3(0.0f, 1.0f, 0.0f)

            // Build camera basis
            val forward = normalize(sub(camTarget, camPos))   // view direction
            val right = normalize(cross(forward, camUp))      // camera right
            val upCam = cross(right, forward)                  // corrected up

            // World -> camera space
            fun worldToCamera(p: Vec3): Vec3 {
                val rel = sub(p, camPos)
                return Vec3(dot(rel, right), dot(rel, upCam), dot(rel, forward))
            }

            data class OrbProj(
                val screenX: Float,
                val screenY: Float,
                val scale: Float,
                val depth: Float,
                val alpha: Float
            )

            // Camera space -> screen space
            fun projectToScreen(
                cam: Vec3,
                centerX: Float,
                centerY: Float
            ): OrbProj? {
                if (cam.z <= 0.1f) return null // behind camera or too close
                val focal = -120f
                val persp = focal / cam.z
                val sx = centerX + cam.x * persp
                val sy = centerY + cam.y * persp

                val nearRef = 1f   // closest expected depth
                val farRef = 100f  // farthest expected depth
                val t = ((cam.z - nearRef) / (farRef - nearRef))


                val minScale = 0.15f
                val maxScale = 1.0f
                val platformScale = lerp(maxScale, minScale, t)

                val depth = cam.z

                val alpha = lerp(t, 0.35f, 1.0f)

                return OrbProj(
                    screenX = sx,
                    screenY = sy,
                    scale = platformScale,
                    depth = depth,
                    alpha = alpha
                )
            }

            // Animation parameters
            val speed = -0.06f
            val theta = tickTime * speed
            val radius = 22.5f

            // Determine which purification slots are populated (1..4 only), keep deterministic order by slot index
            val occupiedSlots: List<Int> = (1..4)
                .filter { idx ->
                    storage.get(
                        ClientPurificationStorage.PurificationPosition(
                            idx
                        )
                    ) != null
                }
                .sorted()

            // Build evenly spaced orbit positions for the occupied slots, mapped to their slot index
            val projectedOccupied: List<Pair<Int, OrbProj>> =
                if (occupiedSlots.isEmpty()) emptyList() else {
                    val count = occupiedSlots.size
                    occupiedSlots.mapIndexedNotNull { k, slotIdx ->
                        val ang = theta + (2.0f * Math.PI.toFloat() / count) * k
                        val world = Vec3(
                            x = kotlin.math.cos(ang) * radius,
                            y = 0.0f,
                            z = kotlin.math.sin(ang) * radius
                        )
                        val cam = worldToCamera(world)
                        projectToScreen(
                            cam,
                            screenCx,
                            screenCy
                        )?.let { slotIdx to it }
                    }
                }

            projectedOccupied.sortedByDescending { it.second.depth }
                .forEach { (_, orb) ->
                    // Debug: print min/max possible depth for each orbiter using the widget defaults
                    val px = (orb.screenX - (PLATFORM_W * orb.scale) / 2f)
                    val py = (orb.screenY - (PLATFORM_H * orb.scale) / 2f)
                    blitk(
                        matrixStack = matrices,
                        texture = platformTexture,
                        x = px,
                        y = py,
                        width = PLATFORM_W * orb.scale,
                        height = PLATFORM_H * orb.scale,
                        alpha = 0.8f,
                        scale = 1.0f
                    )
                }

            // Draw the center platform at world origin
            val centerCam = worldToCamera(Vec3(0.0f, 0.0f, 0.0f))
            val centerProj = projectToScreen(centerCam, screenCx, screenCy)

            if (centerProj != null) {
                val baseScale = 1.0f
                val baseX =
                    (centerProj.screenX - (PLATFORM_W * baseScale) / 2f)
                val baseY =
                    (centerProj.screenY - (PLATFORM_H * baseScale) / 2f)
                blitk(
                    matrixStack = matrices,
                    texture = platformTexture,
                    x = baseX,
                    y = baseY,
                    width = PLATFORM_W * baseScale,
                    height = PLATFORM_H * baseScale,
                    alpha = 1.0f,
                    scale = 1.0f
                )

                // If a Pokémon is in purification slot 0, render it on top of the center platform.
                val centerPokemon: Pokemon? =
                    storage.get(ClientPurificationStorage.PurificationPosition(0))
                if (centerPokemon != null) {
                    matrices.pushPose()
                    // Translate to the projected center of the platform; lift slightly so feet sit on the pad
                    matrices.translate(
                        centerProj.screenX.toDouble(),
                        centerProj.screenY.toDouble() - 53.0,
                        0.0
                    )
                    // Scale the model relative to projection; tuned empirically for visibility
                    val modelScale = 1.5f * baseScale.coerceIn(0.7f, 1.0f)
                    matrices.scale(modelScale, modelScale, modelScale)

                    // Apply requested Y rotation of 325 degrees (with a slight X tilt like other GUIs)
                    val rotation = Quaternionf().fromEulerXYZDegrees(
                        Vector3f(
                            13f,
                            325f,
                            0f
                        )
                    )
                    matrices.pushPose()
                    drawProfilePokemon(
                        renderablePokemon = centerPokemon.asRenderablePokemon(),
                        matrixStack = matrices,
                        rotation = rotation,
                        state = centerModelState,
                        partialTicks = partialTick
                    )
                    matrices.popPose()
                    if (PokemonAspectUtil.hasShadowAspect(centerPokemon)) {
                        AuraEmitters.renderInPurificationGUI(
                            guiGraphics,
                            matrices,
                            Minecraft.getInstance().renderBuffers().bufferSource(),
                            PokemonAspectUtil.getHeartGaugeValue(centerPokemon) / 100.0f,
                            partialTick,
                            centerPokemon.asRenderablePokemon(),
                            centerModelState,
                            x.toFloat(),
                            y.toFloat(),
                            SCREEN_WIDTH.toFloat(),
                            SCREEN_HEIGHT.toFloat()
                        )
                    }

                    matrices.popPose()




            // Draw Pokémon on top of their respective orbitals (only populated ones).
            // projectedOccupied pairs: (slotIndex 1..4, projection)
            projectedOccupied.sortedByDescending { it.second.depth }
                .forEach { (slotIdx, orb) ->
                    val pokemon: Pokemon? =
                        storage.get(
                            ClientPurificationStorage.PurificationPosition(
                                slotIdx
                            )
                        )
                    if (pokemon != null) {
                        matrices.pushPose()

                        var zTranslation = 0.0
                        zTranslation = if (centerProj.screenY > orb.screenY) {
                            -350.0
                        } else {
                            350.0
                        }
                        // Place the Pokémon roughly centered over the platform and lifted a bit.
                        val lift =
                            36.0 * orb.scale // scale-aware lift so feet meet the pad visually
                        matrices.translate(
                            orb.screenX.toDouble(),
                            orb.screenY.toDouble() - lift,
                            zTranslation
                        )

                        // Scale model relative to the projected platform scale
                        val modelScale = (orb.scale)
                        matrices.scale(modelScale, modelScale, modelScale)

                        // Match the center’s display rotation
                        val rotation = Quaternionf().fromEulerXYZDegrees(
                            Vector3f(
                                13f,
                                325f,
                                0f
                            )
                        )

                        // Pick a distinct PosableState per orbital to avoid multiple partialTick updates on the same state per frame
                        val orbitalState = orbitalModelStates.getOrNull(slotIdx - 1) ?: orbitalModelStates.first()

                        drawProfilePokemon(
                            renderablePokemon = pokemon.asRenderablePokemon(),
                            matrixStack = matrices,
                            rotation = rotation,
                            state = orbitalState,
                            partialTicks = partialTick
                        )

                        matrices.popPose()
                    }
                }

                    // Tooltip on hover over the center Pokémon/platform (slot 0)
                    run {
                        val hoverX0 = baseX
                        val hoverY0 = baseY - 60f // extend above the platform to include the model area
                        val hoverX1 = baseX + (PLATFORM_W * baseScale)
                        val hoverY1 = baseY + (PLATFORM_H * baseScale)

                        val hovered =
                            mouseX.toFloat() >= hoverX0 && mouseX.toFloat() <= hoverX1 &&
                                    mouseY.toFloat() >= hoverY0 && mouseY.toFloat() <= hoverY1

                        if (hovered) {
                            val value = kotlin.math.max(0, PokemonAspectUtil.getHeartGaugeValue(centerPokemon))
                            val message = when {
                                value >= 100 -> "The door to its heart is tightly shut."
                                value >= 80 -> "The door to its heart is starting to open."
                                value >= 60 -> "The door to its heart is opening up."
                                value >= 40 -> "The door to its heart is opening wider."
                                value >= 20 -> "The door to its heart is nearly open."
                                value >= 1 -> "The door to its heart is almost fully open."
                                else -> "The door to its heart is about to open. Undo the final lock!"
                            }
                            com.cobblemon.mod.common.client.gui.pokedex.renderTooltip(
                                guiGraphics,
                                Component.literal(message),
                                mouseX,
                                mouseY,
                                partialTick
                            )
                        }
                    }
                }
            }
        }


    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int
    ): Boolean {
        for (slot in purificationChamberPurificationStorageSlots) {
            // Use the actual slot widget size so empty slots are selectable as well
            if (
                mouseX >= slot.x && mouseX < slot.x + PurificationStorageSlot.SIZE &&
                mouseY >= slot.y && mouseY < slot.y + PurificationStorageSlot.SIZE
            ) {
                selectedIndex = slot.position.index
                gui.playSound(CobblemonSounds.PC_CLICK)
                updateActionLabel()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }
}
