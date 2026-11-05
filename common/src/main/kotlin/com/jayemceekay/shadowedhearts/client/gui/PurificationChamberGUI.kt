package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.storage.pc.PCPosition
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.italicise
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.ExitButton
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.pc.IconButton
import com.cobblemon.mod.common.client.gui.pc.NavigationButton
import com.cobblemon.mod.common.client.gui.pc.PCGUI
import com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.Summary.Companion.iconCosmeticItemResource
import com.cobblemon.mod.common.client.gui.summary.Summary.Companion.iconHeldItemResource
import com.cobblemon.mod.common.client.gui.summary.SummaryButton
import com.cobblemon.mod.common.client.gui.summary.widgets.MarkingsWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.common.reformatNatureTextIfMinted
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.gui.summary.widgets.screens.stats.features.HeartGaugeFeatureRenderer
import com.jayemceekay.shadowedhearts.client.purification.PurificationClientMetrics
import com.jayemceekay.shadowedhearts.client.purification.PurificationChamberClientSession
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorage
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorage.PurificationPosition
import com.jayemceekay.shadowedhearts.common.purification.PurificationMath
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.network.purification.MovePCToPurificationPacket
import com.jayemceekay.shadowedhearts.network.purification.MovePurificationToPCPacket
import com.jayemceekay.shadowedhearts.network.purification.PurifyPokemonPacket
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Purification Chamber GUI (minimal scaffolding).
 *
 * This class intentionally mirrors the structure of Cobblemon's PCGUI but is
 * backed by our ClientPurificationStorage rather than Cobblemon's ClientStorage.
 * It can be expanded with widgets/animations similarly to PCGUI as features grow.
 */
class PurificationChamberGUI(
    val purificationStorage: ClientPurificationStorage
) : Screen(Component.translatable("gui.shadowedhearts.purification_chamber.title")),
    CobblemonRenderable {

    companion object {
        const val BASE_WIDTH = 349
        const val BASE_HEIGHT = 205
        const val INFO_BOX_WIDTH = 63
        const val INFO_BOX_HEIGHT = 69
        const val RIGHT_PANEL_WIDTH = 82
        const val RIGHT_PANEL_HEIGHT = 169
        const val TYPE_SPACER_WIDTH = 126
        const val TYPE_SPACER_HEIGHT = 12
        const val PC_SPACER_WIDTH = 342
        const val PC_SPACER_HEIGHT = 14
        const val PORTRAIT_SIZE = 66
        const val SCALE = 0.5F

        const val STAT_INFO = 0
        const val STAT_IV = 1
        const val STAT_EV = 2

        private val statLabels = arrayOf(
            lang("ui.stats.hp"),
            lang("ui.stats.atk"),
            lang("ui.stats.def"),
            lang("ui.stats.sp_atk"),
            lang("ui.stats.sp_def"),
            lang("ui.stats.speed")
        )

        private val stats = arrayOf(
            Stats.HP,
            Stats.ATTACK,
            Stats.DEFENCE,
            Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE,
            Stats.SPEED
        )

        private val baseResource =
            cobblemonResource("textures/gui/pc/pc_base.png")
        private val baseBottomEdgeResource =
            ResourceLocation.fromNamespaceAndPath(
                Shadowedhearts.MOD_ID,
                "textures/gui/purification_pc/base_bottom_edge.png"
            )
        private val portraitBackgroundResource =
            cobblemonResource("textures/gui/pc/portrait_background.png")
        private val infoBoxResource =
            cobblemonResource("textures/gui/pc/info_box.png")
        private val infoBoxStatResource =
            cobblemonResource("textures/gui/pc/info_box_stats.png")

        private val buttonInfoArrow =
            cobblemonResource("textures/gui/pc/info_arrow.png")

        private val topSpacerResource =
            cobblemonResource("textures/gui/pc/pc_spacer_top.png")
        private val bottomSpacerResource =
            cobblemonResource("textures/gui/pc/pc_spacer_bottom.png")
        private val rightSpacerResource =
            cobblemonResource("textures/gui/pc/pc_spacer_right.png")
        private val typeSpacerResource =
            cobblemonResource("textures/gui/pc/type_spacer.png")
        private val typeSpacerSingleResource =
            cobblemonResource("textures/gui/pc/type_spacer_single.png")
        private val typeSpacerDoubleResource =
            cobblemonResource("textures/gui/pc/type_spacer_double.png")
    }

    private lateinit var storageWidget: PurificationStorageWidget
    private lateinit var markingsWidget: MarkingsWidget
    private var modelWidget: ModelWidget? = null
    internal var previewPokemon: Pokemon? = null
    private var heartGaugeRenderer: HeartGaugeFeatureRenderer? = null
    private var purifyButton: PurificationActionButton? = null

    private var showCosmeticItem = false
    private var currentStatIndex = 0
    private lateinit var setNameWidget: SetNameWidget

    /** Tutorial overlay that renders step-by-step popups over the GUI. */
    private val tutorialOverlay = PurificationTutorialOverlay().also {
        it.navigationHint = Component.translatable("gui.shadowedhearts.purification_chamber.tutorial.navigation")
    }
    private val viewportFit get() = PurificationScreenLayout.fit(width, height)

    private var deferredTooltip: ((GuiGraphics, Int, Int, Float) -> Unit)? = null

    var ticksElapsed = 0

    // Animated select pointer offset (mirror PCGUI behavior)
    var selectPointerOffsetY = 0
    var selectPointerOffsetIncrement = false

    // Region: simple utilities adapted from PCGUI style
    fun playSound(
        soundEvent: SoundEvent,
        volume: Float = 1F,
        pitch: Float = 1F
    ) {
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(
                soundEvent,
                volume,
                pitch
            )
        )
    }

    fun playClick() = playSound(CobblemonSounds.PC_CLICK)

    // Region: Storage helpers specific to PurificationChamber layout
    /**
     * 0 = center; 1..4 = outer positions (clockwise), see ClientPurificationStorage.PurificationPosition
     */
    fun setPokemonAt(index: Int, pokemon: Pokemon?) {
        purificationStorage.set(
            ClientPurificationStorage.PurificationPosition(
                index
            ), pokemon
        )
    }

    fun getPokemonAt(index: Int): Pokemon? = purificationStorage.get(
        ClientPurificationStorage.PurificationPosition(index)
    )

    private fun currentSet() = purificationStorage.currentSetIndex
    private fun nextSet() {
        purificationStorage.nextSet(); playClick()
    }

    private fun prevSet() {
        purificationStorage.prevSet(); playClick()
    }

    override fun init() {
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2
        this.storageWidget = PurificationStorageWidget(
            pX = x + 85,
            pY = y + 27,
            gui = this,
            storage = purificationStorage,
            onAddRequested = { targetIndex ->
                // Open the player's PCGUI and intercept the first click to select a Pokémon.
                val pc = CobblemonClient.storage.pcStores.values.firstOrNull()
                    ?: return@PurificationStorageWidget
                val party = CobblemonClient.storage.party

                val configuration = PCGUIConfiguration(
                    // When a slot in the PC/party is clicked inside the PCGUI, this override is invoked.
                    selectOverride = { pcGui, position, pokemon ->
                        // We only accept selecting a Pokemon from the PC boxes for now.
                        if (position is PCPosition && pokemon != null) {
                            // Send to server for authoritative move
                            MovePCToPurificationPacket(
                                pokemonID = pokemon.uuid,
                                pcPosition = position,
                                purificationStoreID = purificationStorage.uuid,
                                targetIndex = targetIndex,
                                setIndex = currentSet()
                            ).sendToServer()
                            // The server updates both stores after authorization. A stale
                            // session or rejected transfer must not move client-only Pokemon.

                            // Close PC and return to the Purification Chamber screen.
                            PurificationChamberClientSession.returnTo(this@PurificationChamberGUI)
                            // Optional sound feedback
                            playClick()
                        }
                    },
                    // Do not show the party by default for this selection flow; PC only is clearer, but keep as true if desired.
                    showParty = true
                )

                PurificationChamberClientSession.select(
                    this@PurificationChamberGUI,
                    PCGUI(
                        pc = pc,
                        party = party,
                        configuration = configuration
                    )
                )
            },
            onMoveToPCRequested = { fromIndex ->
                // Open PC to choose a destination position, treating the selected chamber mon as grabbed
                val pc = CobblemonClient.storage.pcStores.values.firstOrNull()
                    ?: return@PurificationStorageWidget
                val party = CobblemonClient.storage.party

                getPokemonAt(fromIndex) ?: return@PurificationStorageWidget

                val configuration = PCGUIConfiguration(
                    selectOverride = { _, position, _ ->
                        if (position is PCPosition) {
                            // Send to server to move/swap
                            MovePurificationToPCPacket(
                                purificationStoreID = purificationStorage.uuid,
                                fromIndex = fromIndex,
                                setIndex = currentSet(),
                                pcPosition = position
                            ).sendToServer()

                            // Wait for authoritative store updates, including swap validation.

                            PurificationChamberClientSession.returnTo(this@PurificationChamberGUI)
                            playClick()
                        }
                    },
                    showParty = true
                )

                PurificationChamberClientSession.select(
                    this@PurificationChamberGUI,
                    PCGUI(
                        pc = pc,
                        party = party,
                        configuration = configuration
                    )
                )
            }
        )
        this.addRenderableWidget(storageWidget)

        // Add the set name widget (centered near the top bar similar to PC box name)
        this.setNameWidget = SetNameWidget(
            pX = x + 126, // initial x; SetNameWidget centers itself within its width
            pY = y + 12
        ) { currentSet() }

        this.addRenderableWidget(setNameWidget)

        this.purifyButton = PurificationActionButton(
            x = x + (BASE_WIDTH / 2) - 27, // WIDTH is 54, so -27 centers it
            y = y + BASE_HEIGHT - 21,
            labelSupplier = { Component.translatable("gui.shadowedhearts.purification_chamber.purify") },
            visibleSupplier = {
                val centerPokemon =
                    purificationStorage.get(PurificationPosition(0))
                centerPokemon != null && ShadowAspectUtil.hasShadowAspect(
                    centerPokemon
                ) && ShadowAspectUtil.getHeartGauge(centerPokemon) == 0F
            }
        ) {
            PurifyPokemonPacket(
                purificationStorage.uuid,
                currentSet()
            ).sendToServer()
            /*val centerPokemon = purificationStorage.get(PurificationPosition(0))
            if (centerPokemon != null) {
                PokemonAspectUtil.setShadowAspect(centerPokemon, false)
            }*/
        }
        this.addRenderableWidget(purifyButton!!)

        storageWidget.getActionButton()?.let { this.addRenderableWidget(it) }

        this.addRenderableWidget(
            ExitButton(pX = x + 320, pY = y + 186) {
                playSound(CobblemonSounds.PC_OFF)
                Minecraft.getInstance().setScreen(null)

            }.apply { message = Component.translatable("gui.shadowedhearts.exit") }
        )

        // Tutorial "?" button — opens the step-by-step tutorial overlay
        this.addRenderableWidget(
            PurificationActionButton(
                x = x + 14,
                y = y + 203,
                labelSupplier = { Component.translatable("gui.shadowedhearts.purification_chamber.tutorial") },
                visibleSupplier = { !tutorialOverlay.isActive }
            ) {
                playClick()
                startTutorial()
            }
        )

        this.addRenderableWidget(
            NavigationButton(
                pX = x + 220,
                pY = y + 16,
                forward = true
            ) { nextSet() }.apply {
                message = Component.translatable("gui.shadowedhearts.purification_chamber.next_set")
            }
        )

        this.addRenderableWidget(
            NavigationButton(
                pX = x + 117,
                pY = y + 16,
                forward = false
            ) { prevSet() }.apply {
                message = Component.translatable("gui.shadowedhearts.purification_chamber.previous_set")
            }
        )

        this.markingsWidget = MarkingsWidget(x + 29, y + 96.5, null, false)
        this.addRenderableWidget(markingsWidget)

        // Held/Cosmetic Item Button
        addRenderableWidget(
            SummaryButton(
                buttonX = x + 67F,
                buttonY = y + 107F,
                buttonWidth = 12,
                buttonHeight = 12,
                scale = 0.5F,
                resource = iconCosmeticItemResource,
                activeResource = iconHeldItemResource,
                clickAction = {
                    showCosmeticItem = !showCosmeticItem
                    (it as SummaryButton).buttonActive = showCosmeticItem
                    it.message = lang(if (showCosmeticItem) "held_item" else "cosmetic_item")
                }
            ).apply { message = lang("cosmetic_item") }
        )

        addRenderableWidget(
            IconButton(
                pX = x + 1,
                pY = y + 157,
                buttonWidth = 10,
                buttonHeight = 16,
                resource = buttonInfoArrow,
                label = ""
            ) {
                currentStatIndex = (currentStatIndex + 1) % 3
                it.message = lang(when (currentStatIndex) {
                    0 -> "ui.stats.ivs"
                    1 -> "ui.stats.evs"
                    else -> "ui.info"
                })
            }.apply { message = lang("ui.stats.ivs") }
        )

        this.setPreviewPokemon(null)

        updateTutorialSteps()
        refreshActionButtons()
        super.init()
    }

    private fun refreshActionButtons() {
        children().filterIsInstance<PurificationActionButton>().forEach { it.refreshState() }
        val focusedAction = focused as? PurificationActionButton
        if (focusedAction != null && !focusedAction.isActive) setFocused(null)
    }

    override fun updateNarrationState(output: NarrationElementOutput) {
        if (tutorialOverlay.isActive) tutorialOverlay.narrate(output,
            Component.translatable("gui.shadowedhearts.purification_chamber.tutorial.navigation"))
        else super.updateNarrationState(output)
    }

    override fun updateNarratedWidget(output: NarrationElementOutput) {
        if (!narrateSummaryButton(this, output)) super.updateNarratedWidget(output)
    }

    override fun render(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        val fit = viewportFit
        deferredTooltip = null
        val pose = context.pose()
        pose.pushPose()
        try {
            pose.translate(fit.offsetX(), fit.offsetY(), 0.0)
            pose.scale(fit.scale().toFloat(), fit.scale().toFloat(), 1F)
            renderContent(context, fit.mouseX(mouseX), fit.mouseY(mouseY), partialTick)
        } finally {
            try {
                context.flush()
            } finally {
                pose.popPose()
            }
        }
        if (tutorialOverlay.isActive) {
            tutorialOverlay.renderFitted(context, partialTick, fit, width, height)
        } else {
            deferredTooltip?.invoke(context, mouseX, mouseY, partialTick)
        }
    }

    internal fun deferTooltip(text: Component) {
        deferredTooltip = { graphics, mouseX, mouseY, partialTick ->
            UmbraInfuserTooltip.render(graphics, text, mouseX, mouseY, partialTick)
        }
    }

    /** Keep fixed PC fields inside their frame; the complete text stays available on hover. */
    internal fun drawBoundedText(
        graphics: GuiGraphics, text: Component, left: Number, top: Number, fieldWidth: Int,
        mouseX: Int, mouseY: Int, scale: Float = SCALE, centered: Boolean = false,
        fontResource: ResourceLocation? = null, shadow: Boolean = true
    ) {
        val leftX = left.toDouble()
        val font = Minecraft.getInstance().font
        val full = text.copy()
        if (fontResource != null) full.withStyle(full.style.withFont(fontResource))
        val limit = (fieldWidth / scale).toInt()
        val clipped = font.width(full) > limit
        val shown = BoundedGuiText.fit(full, limit, font)
        drawScaledText(context = graphics, font = fontResource, text = shown,
            x = if (centered) leftX + fieldWidth / 2.0 else leftX, y = top,
            scale = scale, centered = centered, shadow = shadow)
        if (mouseX >= leftX && mouseX < leftX + fieldWidth && mouseY >= top.toDouble()
            && mouseY < top.toDouble() + font.lineHeight * scale) {
            if (clipped) deferTooltip(full)
            else if (full.style.hoverEvent != null) deferredTooltip = { context, screenX, screenY, delta ->
                UmbraInfuserTooltip.renderHover(context, full.style, screenX, screenY, delta)
                Unit
            }
        }
    }

    private fun renderContent(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        refreshActionButtons()
        val matrices = context.pose()
        renderBackground(context, mouseX, mouseY, partialTick)
        // Minimal placeholder background similar sizing to PCGUI
        val x = (this.width - BASE_WIDTH) / 2
        val y = (this.height - BASE_HEIGHT) / 2

        blitk(
            matrixStack = matrices,
            texture = portraitBackgroundResource,
            x = x + 6,
            y = y + 27,
            width = PORTRAIT_SIZE,
            height = PORTRAIT_SIZE
        )


        modelWidget?.render(context, mouseX, mouseY, partialTick)


        blitk(
            matrixStack = matrices,
            texture = baseResource,
            x = x, y = y,
            width = BASE_WIDTH,
            height = BASE_HEIGHT
        )

        blitk(
            matrixStack = matrices,
            texture = if (currentStatIndex > 0) infoBoxStatResource else infoBoxResource,
            x = x + 9,
            y = y + 128,
            width = INFO_BOX_WIDTH,
            height = INFO_BOX_HEIGHT
        )

        val labelX = x + 9 + (INFO_BOX_WIDTH / 2)
        // Render Info Labels
        when (currentStatIndex) {
            STAT_INFO -> {
                drawScaledText(
                    context = context,
                    text = lang("ui.info.nature").bold(),
                    x = labelX,
                    y = y + 129.5,
                    centered = true,
                    scale = SCALE
                )

                drawScaledText(
                    context = context,
                    text = lang("ui.info.ability").bold(),
                    x = labelX,
                    y = y + 146.5,
                    centered = true,
                    scale = SCALE
                )

                drawScaledText(
                    context = context,
                    text = lang("ui.moves").bold(),
                    x = labelX,
                    y = y + 163.5,
                    centered = true,
                    scale = SCALE
                )
            }

            STAT_IV -> {
                drawScaledText(
                    context = context,
                    text = lang("ui.stats.ivs").bold(),
                    x = labelX,
                    y = y + 129.5,
                    centered = true,
                    scale = SCALE
                )
            }

            STAT_EV -> {
                drawScaledText(
                    context = context,
                    text = lang("ui.stats.evs").bold(),
                    x = labelX,
                    y = y + 129.5,
                    centered = true,
                    scale = SCALE
                )
            }
        }

        val pokemon = previewPokemon
        if (pokemon != null) {
            // Status
            val status = pokemon.status?.status
            if (pokemon.isFainted() || status != null) {
                val statusName =
                    if (pokemon.isFainted()) "fnt" else status?.showdownName
                blitk(
                    matrixStack = matrices,
                    texture = cobblemonResource("textures/gui/battle/battle_status_$statusName.png"),
                    x = x + 34,
                    y = y + 1,
                    height = 7,
                    width = 39,
                    uOffset = 35,
                    textureWidth = 74
                )

                blitk(
                    matrixStack = matrices,
                    texture = cobblemonResource("textures/gui/summary/status_trim.png"),
                    x = x + 34,
                    y = y + 2,
                    height = 6,
                    width = 3
                )

                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = lang("ui.status.$statusName").bold(),
                    x = x + 39,
                    y = y
                )
            }

            // Level
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = lang("ui.lv").bold(),
                x = x + 6,
                y = y + 1.5,
                shadow = true
            )

            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = (pokemon.level.toString()).text().bold(),
                x = x + 19,
                y = y + 1.5,
                shadow = true
            )

            // Poké Ball
            val ballResource =
                cobblemonResource("textures/item/poke_balls/" + pokemon.caughtBall.name.path + ".png")
            blitk(
                matrixStack = matrices,
                texture = ballResource,
                x = (x + 3.5) / SCALE,
                y = (y + 12) / SCALE,
                width = 16,
                height = 16,
                scale = SCALE
            )

            drawBoundedText(context, pokemon.getDisplayName().bold(), x + 12, y + 11.5,
                if (pokemon.gender == Gender.GENDERLESS) 61 else 54, mouseX, mouseY,
                scale = 1F, fontResource = CobblemonResources.DEFAULT_LARGE)

            if (pokemon.gender != Gender.GENDERLESS) {
                val isMale = pokemon.gender == Gender.MALE
                val textSymbol =
                    if (isMale) "♂".text().bold() else "♀".text().bold()
                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = textSymbol,
                    x = x + 69, // 64 when tag icon is implemented
                    y = y + 11.5,
                    colour = if (isMale) 0x32CBFF else 0xFC5454,
                    shadow = true
                )
            }

            // Held/Cosmetic Item
            val displayedItem =
                if (showCosmeticItem) pokemon.cosmeticItem else pokemon.heldItem()
            val itemX = x + 3
            val itemY = y + 98
            if (!displayedItem.isEmpty) {
                context.renderItem(displayedItem, itemX, itemY)
                context.renderItemDecorations(
                    Minecraft.getInstance().font,
                    displayedItem,
                    itemX,
                    itemY
                )
            }

            drawScaledText(
                context = context,
                text = lang("${if (showCosmeticItem) "cosmetic" else "held"}_item"),
                x = x + 24,
                y = y + 108.5,
                scale = SCALE
            )

            // Shiny Icon
            if (pokemon.shiny) {
                blitk(
                    matrixStack = matrices,
                    texture = Summary.iconShinyResource,
                    x = (x + 62.5) / SCALE,
                    y = (y + 28.5) / SCALE,
                    width = 16,
                    height = 16,
                    scale = SCALE
                )
            }

            blitk(
                matrixStack = matrices,
                texture = if (pokemon.secondaryType != null) typeSpacerDoubleResource else typeSpacerSingleResource,
                x = (x + 9) / SCALE,
                y = (y + 118.5) / SCALE,
                width = TYPE_SPACER_WIDTH,
                height = TYPE_SPACER_HEIGHT,
                scale = SCALE
            )

            TypeIcon(
                x = x + 40.5,
                y = y + 117,
                type = pokemon.primaryType,
                secondaryType = pokemon.secondaryType,
                doubleCenteredOffset = 5F,
                secondaryOffset = 10F,
                small = true,
                centeredX = true
            ).render(context)

            when (currentStatIndex) {
                STAT_INFO -> {
                    // Nature (mask when gauge hides nature)
                    val natureText =
                        if (ShadowAspectUtil.isNatureHiddenByGauge(
                                pokemon
                            )
                        ) {
                            "????".text()
                        } else reformatNatureTextIfMinted(pokemon)
                    drawBoundedText(context, natureText, x + 10, y + 137, 60,
                        mouseX, mouseY, centered = true)

                    // Ability
                    drawBoundedText(context, pokemon.ability.displayName.asTranslated(), x + 10, y + 154,
                        60, mouseX, mouseY, centered = true)

                    // Moves (mask non-Shadow moves progressively by heart gauge)
                    val moves = pokemon.moveSet.getMoves().take(4)
                    val allowed =
                        ShadowAspectUtil.getAllowedVisibleNonShadowMoves(
                            pokemon
                        )
                    var nonShadowIndex = 0
                    val moveList = moves.map { mv ->
                        if (mv.type == Shadowedhearts.SH_SHADOW_TYPE) {
                            mv.displayName
                        } else {
                            val masked = nonShadowIndex >= allowed
                            nonShadowIndex++
                            if (masked) "????".text().bold() else mv.displayName
                        }
                    }.plus(List(4 - moves.size) { "—".text() })

                    for (i in moveList.indices) {
                        drawBoundedText(context, moveList[i], x + 10, y + 170.5 + (7 * i),
                            60, mouseX, mouseY, centered = true)
                    }
                }

                STAT_IV -> {
                    for (i in statLabels.indices) {
                        drawScaledText(
                            context = context,
                            text = statLabels[i],
                            x = x + 13,
                            y = y + 139 + (10 * i),
                            shadow = true,
                            scale = SCALE
                        )

                        val iv = pokemon.ivs.get(stats[i])
                        val effectiveIv =
                            pokemon.ivs.getEffectiveBattleIV(stats[i])
                        drawScaledText(
                            context = context,
                            text = if (ShadowAspectUtil.isIVHiddenByGauge(
                                    pokemon
                                )
                            ) {
                                "??".text()
                            } else if (iv != effectiveIv) effectiveIv.toString()
                                .text().italicise() else iv.toString().text(),
                            x = x + 65,
                            y = y + 139 + (10 * i),
                            centered = true,
                            shadow = true,
                            scale = SCALE
                        )
                    }
                }

                STAT_EV -> {
                    for (i in statLabels.indices) {
                        drawScaledText(
                            context = context,
                            text = statLabels[i],
                            x = x + 13,
                            y = y + 139 + (10 * i),
                            shadow = true,
                            scale = SCALE
                        )

                        drawScaledText(
                            context = context,
                            text = (if (ShadowAspectUtil.isEVHiddenByGauge(
                                    pokemon
                                )
                            ) "??" else pokemon.evs.get(stats[i])
                                .toString()).text(),
                            x = x + 65,
                            y = y + 139 + (10 * i),
                            centered = true,
                            shadow = true,
                            scale = SCALE
                        )
                    }
                }
            }


        } else {
            blitk(
                matrixStack = matrices,
                texture = typeSpacerResource,
                x = (x + 7) / SCALE,
                y = (y + 118.5) / SCALE,
                width = TYPE_SPACER_WIDTH,
                height = TYPE_SPACER_HEIGHT,
                scale = SCALE
            )
        }


        blitk(
            matrixStack = matrices,
            texture = topSpacerResource,
            x = (x + 86.5) / SCALE,
            y = (y + 13) / SCALE,
            width = PC_SPACER_WIDTH,
            height = PC_SPACER_HEIGHT,
            scale = SCALE
        )

        run {
            val centerPokemon =
                purificationStorage.get(PurificationPosition(0))
            if (centerPokemon != null) {
                // Render heart gauge at bottom middle of the GUI if available
                heartGaugeRenderer = HeartGaugeFeatureRenderer(centerPokemon)

                if (ShadowAspectUtil.getHeartGauge(centerPokemon) != 0F) {
                    heartGaugeRenderer?.let { renderer ->
                        val bottomY =
                            y + BASE_HEIGHT - 25 // leave a small margin above bottom
                        val barWidth =
                            120 // matches BarSummarySpeciesFeatureRenderer underlay width
                        val centerX = x + (BASE_WIDTH / 2)
                        val renderX = centerX - (barWidth / 2)
                        renderer.render(
                            context,
                            renderX.toFloat(),
                            bottomY.toFloat(),
                            centerPokemon
                        )
                    }
                }
            }
        }

        blitk(
            matrixStack = matrices,
            texture = baseBottomEdgeResource,
            x = (x - 2) / SCALE, y = (y + BASE_HEIGHT - 2) / SCALE,
            width = BASE_WIDTH / SCALE,
            height = 2 / SCALE,
            scale = SCALE
        )

        blitk(
            matrixStack = matrices,
            texture = rightSpacerResource,
            x = (x + 275.5) / SCALE,
            y = (y + 184) / SCALE,
            width = 64,
            height = 24,
            scale = SCALE
        )

        super.render(context, mouseX, mouseY, partialTick)
        setNameWidget.overflowTooltip?.let(::deferTooltip)
        children().filterIsInstance<PurificationActionButton>().filter { it.visible }.forEach { button ->
            button.overflowTooltip?.let(::deferTooltip)
        }

        val centerPokemon = purificationStorage.get(PurificationPosition(0))
        if (centerPokemon != null && !ShadowAspectUtil.hasShadowAspect(
                centerPokemon
            )
        ) {
            drawBoundedText(context, Component.translatable("gui.shadowedhearts.purification_chamber.purified"),
                x + 87, y + BASE_HEIGHT - 18, 174, mouseX, mouseY, scale = 0.6F, centered = true)
        }

        if (pokemon != null) {


            val displayedItem =
                if (showCosmeticItem) pokemon.cosmeticItem else pokemon.heldItem()
            val itemX = x + 3
            val itemY = y + 98
            if (!displayedItem.isEmpty) {
                val itemHovered =
                    mouseX.toFloat() in (itemX.toFloat()..(itemX.toFloat() + 16)) && mouseY.toFloat() in (itemY.toFloat()..(itemY.toFloat() + 16))
                if (itemHovered) deferredTooltip = { graphics, screenX, screenY, _ ->
                    UmbraInfuserTooltip.renderItem(graphics, displayedItem, screenX, screenY)
                }
            }
        }

        // Heart Gauge tooltip (bottom center bar). Shows context-sensitive text based on current gauge level.
        run {
            val centerPokemon = purificationStorage.get(PurificationPosition(0))
            if (centerPokemon != null) {
                val barWidth = 120
                val barHeight =
                    25 // reasonable hover height for the rendered bar
                val bottomY = y + BASE_HEIGHT - 25
                val centerX = x + (BASE_WIDTH / 2)
                val renderX = centerX - (barWidth / 2)

                val hovered =
                    mouseX in renderX..(renderX + barWidth) && mouseY in bottomY..(bottomY + barHeight)
                if (hovered) {
                    val message =
                        if (ShadowAspectUtil.hasShadowAspect(centerPokemon)) {
                            val value = max(
                                0,
                                ShadowAspectUtil.getHeartGaugeValue(
                                    centerPokemon
                                )
                            )
                            when {
                                value >= 100 -> Component.translatable("gui.shadowedhearts.purification_chamber.heart.closed")
                                value >= 80 -> Component.translatable("gui.shadowedhearts.purification_chamber.heart.starting")
                                value >= 60 -> Component.translatable("gui.shadowedhearts.purification_chamber.heart.opening")
                                value >= 40 -> Component.translatable("gui.shadowedhearts.purification_chamber.heart.wider")
                                value >= 20 -> Component.translatable("gui.shadowedhearts.purification_chamber.heart.nearly")
                                value >= 1 -> Component.translatable("gui.shadowedhearts.purification_chamber.heart.almost")
                                else -> Component.translatable("gui.shadowedhearts.purification_chamber.heart.ready")
                            }
                        } else {
                            Component.translatable("gui.shadowedhearts.purification_chamber.purified")
                        }
                    deferTooltip(message)
                }
            }
        }
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int
    ): Boolean {
        refreshActionButtons()
        try {
            // Let the tutorial overlay consume clicks when active
            if (tutorialOverlay.handleClick(mouseX, mouseY, button)) return true
            val fit = viewportFit
            return super.mouseClicked(fit.toContentX(mouseX), fit.toContentY(mouseY), button)
        } finally {
            // Selection or tutorial state can change during the click itself.
            refreshActionButtons()
        }
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val fit = viewportFit
        return super.mouseReleased(fit.toContentX(mouseX), fit.toContentY(mouseY), button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val fit = viewportFit
        return super.mouseDragged(fit.toContentX(mouseX), fit.toContentY(mouseY), button, fit.dragDelta(dragX), fit.dragDelta(dragY))
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val fit = viewportFit
        return super.mouseScrolled(fit.toContentX(mouseX), fit.toContentY(mouseY), horizontal, vertical)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        val fit = viewportFit
        super.mouseMoved(fit.toContentX(mouseX), fit.toContentY(mouseY))
    }

    override fun getRectangle(): ScreenRectangle {
        val fit = viewportFit
        val left = floor(fit.toContentX(0.0)).toInt()
        val top = floor(fit.toContentY(0.0)).toInt()
        val right = ceil(fit.toContentX(width.toDouble())).toInt()
        val bottom = ceil(fit.toContentY(height.toDouble())).toInt()
        return ScreenRectangle(left, top, right - left, bottom - top)
    }

    override fun keyPressed(
        keyCode: Int,
        scanCode: Int,
        modifiers: Int
    ): Boolean {
        refreshActionButtons()
        try {
            // Let the tutorial overlay consume key presses when active
            if (tutorialOverlay.handleKeyPress(keyCode)) return true
            when (keyCode) {
                InputConstants.KEY_ESCAPE -> {
                    playSound(CobblemonSounds.PC_OFF)
                    onClose()
                    return true
                }

                InputConstants.KEY_RIGHT -> {
                    playSound(CobblemonSounds.PC_CLICK)
                    nextSet(); return true
                }

                InputConstants.KEY_LEFT -> {
                    playSound(CobblemonSounds.PC_CLICK)
                    prevSet(); return true
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers)
        } finally {
            refreshActionButtons()
        }
    }

    override fun tick() {
        refreshActionButtons()
        ticksElapsed++

        val center = getPokemonAt(0)
        val supports = arrayOf(
            getPokemonAt(1),
            getPokemonAt(2),
            getPokemonAt(3),
            getPokemonAt(4)
        )

        val currentSetEmpty = center == null && supports.all { it == null }

        if (currentSetEmpty) {
            /* val soundEvent = ModSounds.PURIFICATION_CHAMBER.get()
             if (loopSound == null || loopSound!!.isStopped || loopSound!!.event != soundEvent) {
                 loopSound?.fadeOutAndStop()
                 loopSound = PurificationChamberLoopSoundInstance(soundEvent)
                 Minecraft.getInstance().soundManager.play(loopSound!!)
             }*/
        } else {
            // Compute tempo/flow
            var perfectSets = 0
            var anySetMissingMember = false
            for (setIdx in 0 until ClientPurificationStorage.TOTAL_SETS) {
                val s1g =
                    purificationStorage.getAt(setIdx, PurificationPosition(1))
                val s2g =
                    purificationStorage.getAt(setIdx, PurificationPosition(2))
                val s3g =
                    purificationStorage.getAt(setIdx, PurificationPosition(3))
                val s4g =
                    purificationStorage.getAt(setIdx, PurificationPosition(4))
                val ringList = listOfNotNull(s1g, s2g, s3g, s4g)
                if (ringList.size < 4) anySetMissingMember = true
                if (PurificationMath.isPerfectSet(ringList)) perfectSets++
            }

            val metrics = PurificationClientMetrics.compute(
                center,
                supports,
                perfectSets,
                anySetMissingMember
            )
            val tempoPct = metrics.tempoPct

            /*val soundEvent = when {
                tempoPct >= 1.0f -> ModSounds.PURIFICATION_CHAMBER_PERFECT.get()
                tempoPct >= 0.70f -> ModSounds.PURIFICATION_CHAMBER_VERY_GOOD.get()
                tempoPct >= 0.5f -> ModSounds.PURIFICATION_CHAMBER_GOOD.get()
                tempoPct > 0.0f -> ModSounds.PURIFICATION_CHAMBER_BAD.get()
                else -> ModSounds.PURIFICATION_CHAMBER.get()
            }

            if (loopSound == null || loopSound!!.isStopped || loopSound!!.event != soundEvent) {
                loopSound?.fadeOutAndStop()
                loopSound = PurificationChamberLoopSoundInstance(soundEvent)
                //Minecraft.getInstance().soundManager.play(loopSound!!)
            }*/
        }

        // Animate select pointer just like PCGUI
        val delayFactor = 3
        if (ticksElapsed % (2 * delayFactor) == 0) selectPointerOffsetIncrement =
            !selectPointerOffsetIncrement
        if (ticksElapsed % delayFactor == 0) selectPointerOffsetY += if (selectPointerOffsetIncrement) 1 else -1
        super.tick()
    }

    private fun saveMarkings(isParty: Boolean = false) {
        if (::markingsWidget.isInitialized) markingsWidget.saveMarkingsToPokemon(
            isParty
        )
    }

    fun setPreviewPokemon(pokemon: Pokemon?, isParty: Boolean = false) {
        if (pokemon != null) {
            saveMarkings(isParty)
            previewPokemon = pokemon
            // Prepare heart gauge renderer for the selected Pokémon
            heartGaugeRenderer = HeartGaugeFeatureRenderer(pokemon)

            val x = (width - BASE_WIDTH) / 2
            val y = (height - BASE_HEIGHT) / 2
            modelWidget = ModelWidget(
                pX = x + 6,
                pY = y + 27,
                pWidth = PORTRAIT_SIZE,
                pHeight = PORTRAIT_SIZE,
                pokemon = pokemon.asRenderablePokemon(),
                baseScale = 2F,
                rotationY = 325F,
                offsetY = -10.0
            )
            markingsWidget.setActivePokemon(previewPokemon)
        } else {
            previewPokemon = null
            modelWidget = null
            markingsWidget.setActivePokemon(null)
            heartGaugeRenderer = null
        }
    }

    override fun renderMenuBackground(
        guiGraphics: GuiGraphics,
        i: Int,
        j: Int,
        k: Int,
        l: Int
    ) {
    }

    override fun renderMenuBackground(guiGraphics: GuiGraphics) {}

    override fun renderBlurredBackground(f: Float) {}

    override fun renderTransparentBackground(guiGraphics: GuiGraphics) {}

    override fun renderBackground(
        guiGraphics: GuiGraphics,
        i: Int,
        j: Int,
        f: Float
    ) {
    }

    /**
     * Builds the tutorial steps and starts the overlay.
     *
     * Each [TutorialStep] defines a tooltip-style popup at (posX, posY) with the
     * given text. Steps are shown sequentially; left-click or any key advances,
     * right-click goes back, and Escape dismisses.
     */
    private fun startTutorial() {
        updateTutorialSteps()
        tutorialOverlay.start()
    }

    private fun updateTutorialSteps() {
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2
        val popupWidth = PurificationScreenLayout.tutorialWidth(width)

        tutorialOverlay.steps = listOf(
            // Step 1 — Placeholder: introduce the chamber
            TutorialStep(
                text = Component.translatable("gui.shadowedhearts.purification_chamber.tutorial.welcome"),
                posX = x + BASE_WIDTH / 2,
                posY = y + 40,
                0,
                width = popupWidth
            ),
            // Step 2 — Placeholder: explain the center slot
            TutorialStep(
                text = Component.translatable("gui.shadowedhearts.purification_chamber.tutorial.center"),
                posX = x + BASE_WIDTH / 2,
                posY = y + 40,
                width = popupWidth,
                arrowSide = ArrowSide.RIGHT,
                arrowTargetX = x + BASE_WIDTH / 2 + 133,
                arrowTargetY = y + 42,
                highlightX = x + BASE_WIDTH / 2 + 121,
                highlightY = y + 17,
                highlightWidth = 25,
                highlightHeight = 25
            ),
            // Step 3 — Placeholder: explain support slots
            TutorialStep(
                text = Component.translatable("gui.shadowedhearts.purification_chamber.tutorial.supports"),
                posX = x + BASE_WIDTH / 2,
                posY = y + 40,
                width = popupWidth,
                arrowSide = ArrowSide.RIGHT,
                arrowTargetX = x + BASE_WIDTH / 2 + 120,
                arrowTargetY = y + 82,
                highlightX = x + BASE_WIDTH / 2 + 121,
                highlightY = y + 48,
                highlightWidth = 25,
                highlightHeight = 109
            ),
            // Step 4 — Placeholder: explain tempo & flow
            TutorialStep(
                text = Component.translatable("gui.shadowedhearts.purification_chamber.tutorial.flow"),
                posX = x + BASE_WIDTH / 2,
                posY = y + 40,
                width = popupWidth,
            ),
            // Step 5 — Placeholder: explain purify button
            TutorialStep(
                text = Component.translatable("gui.shadowedhearts.purification_chamber.tutorial.walking"),
                posX = x + BASE_WIDTH / 2,
                posY = y + 40,
                width = popupWidth,
            )
        )
    }

    override fun isPauseScreen() = false

    override fun removed() {
        //loopSound?.fadeOutAndStop()
        //loopSound = null
        super.removed()
    }
}
