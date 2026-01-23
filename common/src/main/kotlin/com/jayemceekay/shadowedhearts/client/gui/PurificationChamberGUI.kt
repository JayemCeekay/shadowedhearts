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
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import com.jayemceekay.shadowedhearts.ShadowGate
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.gui.summary.widgets.screens.stats.features.HeartGaugeFeatureRenderer
import com.jayemceekay.shadowedhearts.client.purification.PurificationClientMetrics
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorage
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorage.PurificationPosition
import com.jayemceekay.shadowedhearts.network.purification.MovePCToPurificationPacket
import com.jayemceekay.shadowedhearts.network.purification.MovePurificationToPCPacket
import com.jayemceekay.shadowedhearts.network.purification.PurifyPokemonPacket
import com.jayemceekay.shadowedhearts.network.purification.UnlinkPlayerFromPurificationChamberPacket
import com.jayemceekay.shadowedhearts.storage.purification.PurificationMath
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import kotlin.math.max

/**
 * Purification Chamber GUI (minimal scaffolding).
 *
 * This class intentionally mirrors the structure of Cobblemon's PCGUI but is
 * backed by our ClientPurificationStorage rather than Cobblemon's ClientStorage.
 * It can be expanded with widgets/animations similarly to PCGUI as features grow.
 */
class PurificationChamberGUI(
    val purificationStorage: ClientPurificationStorage
) : Screen(Component.translatable("shadowedhearts.ui.purification_chamber.title")),
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
        // Ensure Shadow aspects exist before storing/using in UI
        if (pokemon != null) {
            try {
                PokemonAspectUtil.ensureRequiredShadowAspects(pokemon)
            } catch (_: Exception) {
            }
        }
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
        // Validate any existing Pokémon in the current set upon opening the chamber UI
        try {
            for (i in 0..4) {
                val p = getPokemonAt(i)
                if (p != null) PokemonAspectUtil.ensureRequiredShadowAspects(p)
            }
        } catch (_: Exception) {
        }
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
                            // Place into the selected purification slot locally for MVP.
                            setPokemonAt(targetIndex, pokemon)

                            // Remove it from the PC client view to simulate a move.
                            pc.set(position, null)

                            // Close PC and return to the Purification Chamber screen.
                            Minecraft.getInstance()
                                .setScreen(this@PurificationChamberGUI)
                            // Optional sound feedback
                            playClick()
                        }
                    },
                    // Do not show the party by default for this selection flow; PC only is clearer, but keep as true if desired.
                    showParty = true
                )

                Minecraft.getInstance().setScreen(
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

                val fromMon =
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

                            // Optimistic client update
                            val dest = pc.get(position)
                            if (dest == null) {
                                // Move
                                pc.set(position, fromMon)
                                setPokemonAt(fromIndex, null)
                            } else {
                                // Swap if compatible; UI doesn't enforce compatibility, server will reject if invalid.
                                pc.set(position, fromMon)
                                setPokemonAt(fromIndex, dest)
                            }

                            Minecraft.getInstance()
                                .setScreen(this@PurificationChamberGUI)
                            playClick()
                        }
                    },
                    showParty = true
                )

                Minecraft.getInstance().setScreen(
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
            labelSupplier = { Component.literal("Purify") },
            visibleSupplier = {
                val centerPokemon = purificationStorage.get(PurificationPosition(0))
                centerPokemon != null && PokemonAspectUtil.hasShadowAspect(centerPokemon) && PokemonAspectUtil.getHeartGauge(centerPokemon) == 0F
            }
        ) {
            PurifyPokemonPacket(purificationStorage.uuid, currentSet()).sendToServer()
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
                // Inform server to unlink this player from the purification chamber
                UnlinkPlayerFromPurificationChamberPacket().sendToServer()
                Minecraft.getInstance().setScreen(null)
                
            }
        )

        this.addRenderableWidget(
            NavigationButton(
                pX = x + 220,
                pY = y + 16,
                forward = true
            ) { nextSet() }
        )

        this.addRenderableWidget(
            NavigationButton(
                pX = x + 117,
                pY = y + 16,
                forward = false
            ) { prevSet() }
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
                }
            )
        )

        addRenderableWidget(
            IconButton(
                pX = x + 1,
                pY = y + 157,
                buttonWidth = 10,
                buttonHeight = 16,
                resource = buttonInfoArrow,
                label = "switch"
            ) {
                currentStatIndex = (currentStatIndex + 1) % 3
            }
        )

        this.setPreviewPokemon(null)

        super.init()
    }

    override fun render(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
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

            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = pokemon.getDisplayName().bold(),
                x = x + 12,
                y = y + 11.5,
                shadow = true
            )

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
                        if (com.jayemceekay.shadowedhearts.PokemonAspectUtil.isNatureHiddenByGauge(
                                pokemon
                            )
                        ) {
                            "????".text()
                        } else reformatNatureTextIfMinted(pokemon)
                    drawScaledText(
                        context = context,
                        text = natureText,
                        x = labelX,
                        y = y + 137,
                        centered = true,
                        shadow = true,
                        scale = SCALE,
                        pMouseX = mouseX,
                        pMouseY = mouseY
                    )

                    // Ability
                    drawScaledText(
                        context = context,
                        text = pokemon.ability.displayName.asTranslated(),
                        x = labelX,
                        y = y + 154,
                        centered = true,
                        shadow = true,
                        scale = SCALE
                    )

                    // Moves (mask non-Shadow moves progressively by heart gauge)
                    val moves = pokemon.moveSet.getMoves().take(4)
                    val allowed =
                        PokemonAspectUtil.getAllowedVisibleNonShadowMoves(
                            pokemon
                        )
                    var nonShadowIndex = 0
                    val moveList = moves.map { mv ->
                        val id = mv.name
                        if (ShadowGate.isShadowMoveId(
                                id
                            )
                        ) {
                            mv.displayName
                        } else {
                            val masked = nonShadowIndex >= allowed
                            nonShadowIndex++
                            if (masked) "????".text().bold() else mv.displayName
                        }
                    }.plus(List(4 - moves.size) { "—".text() })

                    for (i in moveList.indices) {
                        drawScaledText(
                            context = context,
                            text = moveList[i],
                            x = labelX,
                            y = y + 170.5 + (7 * i),
                            centered = true,
                            shadow = true,
                            scale = SCALE
                        )
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
                            text = if (com.jayemceekay.shadowedhearts.PokemonAspectUtil.isIVHiddenByGauge(
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
                            text = (if (com.jayemceekay.shadowedhearts.PokemonAspectUtil.isEVHiddenByGauge(
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

                if (PokemonAspectUtil.getHeartGauge(centerPokemon) != 0F) {
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

        val centerPokemon = purificationStorage.get(PurificationPosition(0))
        if (centerPokemon != null && !PokemonAspectUtil.hasShadowAspect(centerPokemon)) {
            drawScaledText(
                context = context,
                text = Component.literal("This Pokemon has been purified!"),
                x = x + (BASE_WIDTH / 2),
                y = y + BASE_HEIGHT - 18,
                centered = true,
                shadow = true,
                scale = 0.6F
            )
        }

        if (pokemon != null) {


            val displayedItem =
                if (showCosmeticItem) pokemon.cosmeticItem else pokemon.heldItem()
            val itemX = x + 3
            val itemY = y + 98
            if (!displayedItem.isEmpty) {
                val itemHovered =
                    mouseX.toFloat() in (itemX.toFloat()..(itemX.toFloat() + 16)) && mouseY.toFloat() in (itemY.toFloat()..(itemY.toFloat() + 16))
                if (itemHovered) context.renderTooltip(
                    Minecraft.getInstance().font,
                    displayedItem,
                    mouseX,
                    mouseY
                )
            }
        }

        // Heart Gauge tooltip (bottom center bar). Shows context-sensitive text based on current gauge level.
        run {
            val centerPokemon = purificationStorage.get(PurificationPosition(0))
            if (centerPokemon != null) {
                val barWidth = 120
                val barHeight = 25 // reasonable hover height for the rendered bar
                val bottomY = y + BASE_HEIGHT - 25
                val centerX = x + (BASE_WIDTH / 2)
                val renderX = centerX - (barWidth / 2)

                val hovered = mouseX in renderX..(renderX + barWidth) && mouseY in bottomY..(bottomY + barHeight)
                if (hovered) {
                    val message = if (PokemonAspectUtil.hasShadowAspect(centerPokemon)) {
                        val value = max(0, PokemonAspectUtil.getHeartGaugeValue(centerPokemon))
                        when {
                            value >= 100 -> "The door to its heart is tightly shut."
                            value >= 80 -> "The door to its heart is starting to open."
                            value >= 60 -> "The door to its heart is opening up."
                            value >= 40 -> "The door to its heart is opening wider."
                            value >= 20 -> "The door to its heart is nearly open."
                            value >= 1 -> "The door to its heart is almost fully open."
                            else -> "The door to its heart is about to open. Undo the final lock!"
                        }
                    } else {
                        "This Pokemon has been purified!"
                    }
                    com.cobblemon.mod.common.client.gui.pokedex.renderTooltip(
                        context,
                        Component.literal(message),
                        mouseX,
                        mouseY,
                        partialTick
                    )
                }
            }
        }
    }

    override fun keyPressed(
        keyCode: Int,
        scanCode: Int,
        modifiers: Int
    ): Boolean {
        when (keyCode) {
            InputConstants.KEY_ESCAPE -> {
                playSound(CobblemonSounds.PC_OFF)
                UnlinkPlayerFromPurificationChamberPacket().sendToServer()
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
    }

    override fun tick() {
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
                val s1g = purificationStorage.getAt(setIdx, PurificationPosition(1))
                val s2g = purificationStorage.getAt(setIdx, PurificationPosition(2))
                val s3g = purificationStorage.getAt(setIdx, PurificationPosition(3))
                val s4g = purificationStorage.getAt(setIdx, PurificationPosition(4))
                val ringList = listOfNotNull(s1g, s2g, s3g, s4g)
                if (ringList.size < 4) anySetMissingMember = true
                if (PurificationMath.isPerfectSet(ringList)) perfectSets++
            }

            val metrics = PurificationClientMetrics.compute(center, supports, perfectSets, anySetMissingMember)
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

    override fun isPauseScreen() = false

    override fun removed() {
        //loopSound?.fadeOutAndStop()
        //loopSound = null
        super.removed()
    }
}
