package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.ExitButton
import com.cobblemon.mod.common.client.gui.pc.IconButton
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.SummaryButton
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.content.blocks.entity.UmbraInfuserBlockEntity
import com.jayemceekay.shadowedhearts.content.infusion.InfusionProgress
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserLayout as Layout
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserMenu
import com.jayemceekay.shadowedhearts.network.infusion.UmbraInfuserActionPacket
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** PC-style presentation over the existing server-authoritative infuser menu. */
class UmbraInfuserScreen(menu: UmbraInfuserMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<UmbraInfuserMenu>(menu, inventory, title), RecipeViewerScreen {
    companion object {
        private const val TEXT = 0xFFFFFF
        private const val MUTED = 0xC4C4C4
        private const val DRAWER_MILLIS = 240F
        private const val ENERGY_METER_WIDTH = 186
        private const val ENERGY_METER_HEIGHT = 16
        // Bottom gray panel in infuser_base.png: (77, 184), 190 x 20.
        private const val ENERGY_METER_X = 77 + (190 - ENERGY_METER_WIDTH) / 2
        private const val ENERGY_METER_Y = 184 + (20 - ENERGY_METER_HEIGHT) / 2
        private const val REQUIREMENTS_X = 272
        private const val REQUIREMENTS_Y = 25
        private const val REQUIREMENTS_WIDTH = 70
        private const val REQUIREMENTS_HEIGHT = 46
        private const val TANK_COUNT_Y = 76
        private const val TABS_Y = 86
        private const val INVENTORY_BUTTON_Y = 162
        private val PC_BUTTON = cobblemonResource("textures/gui/pc/pc_release_button.png")
        private val DRAWER_BACK_BUTTON = cobblemonResource("textures/gui/common/back_button.png")
        private val DRAWER_BACK_ICON = cobblemonResource("textures/gui/common/back_button_icon.png")
        private fun texture(name: String) = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts", "textures/gui/umbra_infuser/$name.png")
    }

    private enum class Tab { SUPPLY, TANKS }
    private var selectedTab = Tab.SUPPLY
    private val details = UmbraInfuserPokemonPanel()
    private val partyButtons = mutableListOf<PartyButton>()
    private lateinit var startButton: Button
    private lateinit var retrieveButton: Button
    private lateinit var inventoryButton: Button
    private lateinit var closeDrawerButton: Button
    private lateinit var itemViewButton: SummaryButton
    private lateinit var infoPanelButton: IconButton
    private var inspectedPokemon: UUID? = null
    private val chamberBackground = UmbraInfuserBackground()
    private val chamberPreview = UmbraInfuserChamberPreview()
    private var chamberRevision = -1
    private var drawerOpen = false
    private var drawerAmount = 0F
    private var lastFrameTime = 0L
    private var frameMouseX = 0
    private var frameMouseY = 0
    private var overflowTooltip: Component? = null
    private val drawerCoveringChamber get() = drawerOpen || drawerAmount > 0F
    private val drawerInteractive get() = drawerOpen && drawerAmount >= 1F
    private val viewportFit get() = GuiViewportFit.fit(width, height, imageWidth, imageHeight)

    override fun recipeViewerBounds(): ScreenRectangle =
        RecipeViewerScreen.bounds(viewportFit, leftPos, topPos, imageWidth, imageHeight)

    override fun recipeViewerStackAt(screenX: Double, screenY: Double): RecipeViewerScreen.HoveredStack? {
        val fit = viewportFit
        val x = fit.toContentX(screenX) - leftPos
        val y = fit.toContentY(screenY) - topPos
        val slot = menu.slots.firstOrNull {
            it.isActive && it.hasItem() && x >= it.x && x < it.x + 16 && y >= it.y && y < it.y + 16
        } ?: return null
        return RecipeViewerScreen.HoveredStack(slot.item.copy(),
            RecipeViewerScreen.bounds(fit, leftPos + slot.x, topPos + slot.y, 16, 16))
    }

    init {
        imageWidth = Layout.WIDTH
        imageHeight = Layout.HEIGHT
    }

    override fun init() {
        super.init()
        details.reset()
        chamberBackground.reset()
        chamberPreview.reset()
        chamberRevision = -1
        lastFrameTime = Util.getMillis()
        partyButtons.clear()
        repeat(6) { index ->
            val position = Layout.partyPosition(index, drawerCoveringChamber)
            partyButtons += addRenderableWidget(PartyButton(
                leftPos + position.x, topPos + position.y, index
            ))
        }
        startButton = addRenderableWidget(PcButton(Layout.CENTER_X - 62, 164, label("start")) {
            sendAction(UmbraInfuserActionPacket.Action.START, menu.pokemon)
        })
        retrieveButton = addRenderableWidget(PcButton(Layout.CENTER_X + 4, 164, label("retrieve")) {
            sendAction(UmbraInfuserActionPacket.Action.RETRIEVE, menu.pokemon)
        })
        addRenderableWidget(TabButton(274, Tab.SUPPLY, label("tab_supply")))
        addRenderableWidget(TabButton(308, Tab.TANKS, label("tab_tanks")))
        itemViewButton = addRenderableWidget(SummaryButton(
            buttonX = leftPos + 67F,
            buttonY = topPos + 107F,
            buttonWidth = 12,
            buttonHeight = 12,
            scale = 0.5F,
            resource = Summary.iconCosmeticItemResource,
            activeResource = Summary.iconHeldItemResource,
            clickAction = {
                details.toggleItemView()
                updateWidgets()
            }
        ).apply {
            // SummaryButton scales its sprite separately from its inherited hitbox.
            width = 6
            height = 6
        })
        infoPanelButton = addRenderableWidget(IconButton(
            pX = leftPos + 1,
            pY = topPos + 157,
            buttonWidth = 10,
            buttonHeight = 16,
            resource = cobblemonResource("textures/gui/pc/info_arrow.png"),
            label = ""
        ) {
            details.cycleInfoPanel()
            updateWidgets()
        })
        inventoryButton = addRenderableWidget(PcButton(278, INVENTORY_BUTTON_Y, label("inventory")) { toggleDrawer() })
        addRenderableWidget(ExitButton(leftPos + 320, topPos + 186) {
            minecraft?.soundManager?.play(SimpleSoundInstance.forUI(CobblemonSounds.PC_OFF, 1F))
            onClose()
        }.apply { message = Component.translatable("gui.shadowedhearts.exit") })
        // Constructor arguments run in the screen's scope, so avoid Button's protected DEFAULT_NARRATION.
        closeDrawerButton = addRenderableWidget(object : Button(
            leftPos + Layout.DRAWER_X + Layout.DRAWER_WIDTH - 31, topPos + Layout.DRAWER_Y + 2,
            26, 13, label("close_inventory"),
            OnPress { toggleDrawer() }, Button.CreateNarration { it.get() }
        ) {
            override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
                graphics.flush()
                graphics.pose().pushPose()
                try {
                    graphics.pose().translate(0F, 0F, 20F)
                    // Cobblemon's native back-button art, with focus feedback and a matching hitbox.
                    RenderSystem.enableBlend()
                    RenderSystem.defaultBlendFunc()
                    graphics.blit(DRAWER_BACK_BUTTON, x, y, 0F, if (isHoveredOrFocused) 13F else 0F,
                        width, height, 26, 26)
                    graphics.pose().translate((x + 7).toFloat(), (y + 4).toFloat(), 0F)
                    graphics.pose().scale(0.5F, 0.5F, 1F)
                    graphics.blit(DRAWER_BACK_ICON, 0, 0, 0F, 0F, 21, 11, 21, 11)
                    graphics.flush()
                } finally {
                    graphics.pose().popPose()
                }
            }
        })
        updateWidgets()
    }

    override fun removed() {
        chamberPreview.reset()
        chamberBackground.reset()
        super.removed()
    }

    private fun toggleDrawer() {
        drawerOpen = !drawerOpen
        if (drawerOpen) selectedTab = Tab.SUPPLY
        updateWidgets()
    }

    private fun sendAction(action: UmbraInfuserActionPacket.Action, pokemon: Pokemon?) {
        UmbraInfuserActionPacket(menu.containerId, action, pokemon?.uuid).sendToServer()
    }

    override fun containerTick() {
        super.containerTick()
        updateWidgets()
    }

    private fun canInsert(pokemon: Pokemon) = minecraft?.player?.let { player ->
        menu.canManage(player) && pokemon.uuid in menu.eligiblePartyPokemon &&
            menu.pokemon == null && !menu.getSlot(Layout.INPUT_SLOT).hasItem() &&
            !menu.getSlot(Layout.OUTPUT_SLOT).hasItem()
    } == true

    private fun updateWidgets() {
        val authorized = minecraft?.player?.let { menu.canManage(it) } == true
        menu.setClientSlotVisibility(selectedTab == Tab.SUPPLY, drawerInteractive)
        if (hoveredSlot?.isActive == false) hoveredSlot = null
        partyButtons.forEachIndexed { index, button ->
            val pokemon = button.pokemon()
            val position = Layout.partyPosition(index, drawerCoveringChamber)
            button.x = leftPos + position.x
            button.y = topPos + position.y
            button.visible = true
            // Occupied machines can still inspect party members without moving them.
            button.active = pokemon != null
            button.message = pokemon?.let {
                if (canInsert(it)) label("insert_named", it.getDisplayName())
                else label("pokemon_named", it.getDisplayName(), it.level)
            } ?: label("party_empty")
        }
        startButton.visible = !drawerCoveringChamber
        startButton.active = authorized && menu.pokemon != null &&
            !ShadowAspectUtil.hasShadowAspect(menu.pokemon!!) && menu.progress == 0 &&
            menu.statusKey == "message.shadowedhearts.infuser.select"
        retrieveButton.visible = !drawerCoveringChamber
        retrieveButton.active = authorized && menu.pokemon != null
        retrieveButton.message = label(if (menu.progress > 0) "cancel_retrieve" else "retrieve")
        inventoryButton.message = label(if (drawerOpen) "close_bag" else "inventory")
        closeDrawerButton.visible = drawerInteractive
        itemViewButton.buttonActive = details.showCosmeticItem
        itemViewButton.message = lang(if (details.showCosmeticItem) "held_item" else "cosmetic_item")
        infoPanelButton.message = lang(when (details.currentStatIndex) {
            0 -> "ui.stats.ivs"
            1 -> "ui.stats.evs"
            else -> "ui.info"
        })
        if (chamberRevision != menu.pokemonRevision) {
            chamberRevision = menu.pokemonRevision
            if (inspectedPokemon == null && partyButtons.any { it.isFocused }) setFocused(null)
        }
    }

    private fun previewPokemon(): Pokemon? {
        partyButtons.firstOrNull { it.isMouseOver(frameMouseX.toDouble(), frameMouseY.toDouble()) }
            ?.pokemon()?.let { return it }
        partyButtons.firstOrNull { it.isFocused }?.pokemon()?.let { return it }
        inspectedPokemon?.let { id ->
            partyButtons.firstOrNull { it.pokemon()?.uuid == id }?.pokemon()?.let { return it }
            inspectedPokemon = null
        }
        return menu.pokemon
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val fit = viewportFit
        val contentMouseX = fit.mouseX(mouseX)
        val contentMouseY = fit.mouseY(mouseY)
        frameMouseX = contentMouseX
        frameMouseY = contentMouseY
        overflowTooltip = null
        val now = Util.getMillis()
        val step = ((now - lastFrameTime).coerceIn(0L, 100L) / DRAWER_MILLIS)
        lastFrameTime = now
        drawerAmount = (drawerAmount + if (drawerOpen) step else -step).coerceIn(0F, 1F)
        updateWidgets()
        // The dim backdrop fills the real viewport. Everything in the container remains in
        // authored coordinates, including native slots, dragged stacks and touchscreen snapback.
        super.renderTransparentBackground(graphics)
        graphics.flush()
        graphics.pose().pushPose()
        try {
            graphics.pose().translate(fit.offsetX(), fit.offsetY(), 0.0)
            graphics.pose().scale(fit.scale().toFloat(), fit.scale().toFloat(), 1F)
            super.render(graphics, contentMouseX, contentMouseY, partialTick)
        } finally {
            try { graphics.flush() } finally { graphics.pose().popPose() }
        }
        // Tooltip layout uses the real window bounds, after logical hover selection above.
        hoveredSlot?.takeIf { menu.carried.isEmpty && it.isActive && it.hasItem() }?.let {
            UmbraInfuserTooltip.renderItem(graphics, it.item, mouseX, mouseY)
            return
        }
        if (details.renderTooltip(graphics, mouseX, mouseY, partialTick)) return
        (overflowTooltip ?: customTooltip(contentMouseX, contentMouseY))?.let {
            UmbraInfuserTooltip.render(graphics, it, mouseX, mouseY, partialTick)
        }
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Screen.render invokes this inside the fitted pose; the full backdrop was drawn above.
        renderBg(graphics, partialTick, mouseX, mouseY)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        graphics.flush()
        val previewPokemon = previewPokemon()
        details.renderBackground(graphics, leftPos, topPos, previewPokemon, mouseX, mouseY, partialTick)
        graphics.flush()
        sprite(graphics, "infuser_base", 0, 0, 349, 205)
        UmbraInfuserDecorations.renderFrame(graphics, leftPos, topPos)
        if (previewPokemon == null) UmbraInfuserDecorations.renderEmptyTypeSpacer(graphics, leftPos, topPos)
        details.render(graphics, leftPos, topPos, previewPokemon, mouseX, mouseY, partialTick)
        val heading = label("title").bold().withStyle { it.withFont(CobblemonResources.DEFAULT_LARGE) }
        val headingScale = minOf(1F, 120F / font.width(heading).coerceAtLeast(1))
        smallText(graphics, heading, Layout.CENTER_X - 60, 15, 120, headingScale, true, large = true)
        // The drawer replaces this scene, including its text and models. Drawing hidden
        // text first is insufficient: font depth/batching can put it over a later blit.
        if (!drawerCoveringChamber) renderChamber(graphics, partialTick)
        else {
            chamberPreview.deactivateAura()
            smallText(graphics, label("party"), 85, 31, 174, centered = true)
        }
        renderRightPanel(graphics)
        if (drawerAmount > 0F) renderDrawer(graphics)
        renderEnergy(graphics)
    }

    private fun renderChamberBackground(graphics: GuiGraphics) {
        val workpiece = menu.pokemon
        val occupied = workpiece != null || menu.getSlot(Layout.INPUT_SLOT).hasItem() ||
            menu.getSlot(Layout.OUTPUT_SLOT).hasItem()
        val complete = workpiece?.let(ShadowAspectUtil::hasShadowAspect) == true ||
            (menu.statusKey == "message.shadowedhearts.infuser.ready" && menu.getSlot(Layout.OUTPUT_SLOT).hasItem())
        sprite(graphics, "chamber_frame", Layout.CHAMBER_X, Layout.CHAMBER_Y, Layout.CHAMBER_WIDTH, Layout.CHAMBER_HEIGHT)
        // Cover the old name strip along with the rest of the interior; retain the 1px frame.
        chamberBackground.render(
            graphics, leftPos + Layout.CHAMBER_X + 1, topPos + Layout.CHAMBER_Y + 1,
            Layout.CHAMBER_WIDTH - 2, Layout.CHAMBER_HEIGHT - 2,
            energy = menu.charge.toFloat() / menu.capacity.coerceAtLeast(1),
            progress = progressFraction(),
            active = menu.statusKey == "message.shadowedhearts.infuser.processing",
            occupied = occupied,
            complete = complete
        )
    }

    private fun renderChamber(graphics: GuiGraphics, partialTick: Float) {
        renderChamberBackground(graphics)
        val workpiece = menu.pokemon
        val stack = menu.getSlot(Layout.INPUT_SLOT).item.let {
            if (it.isEmpty) menu.getSlot(Layout.OUTPUT_SLOT).item else it
        }
        val chamberName = workpiece?.getDisplayName() ?: stack.takeUnless { it.isEmpty }?.hoverName
        chamberName?.let {
            smallText(graphics, it, Layout.CENTER_X - 51, 32, 102, centered = true)
        }
        chamberPreview.render(graphics, leftPos, topPos, workpiece, partialTick)
        if (workpiece == null && !stack.isEmpty) {
            graphics.pose().pushPose()
            try {
                graphics.pose().translate((leftPos + Layout.CENTER_X - 16).toDouble(), (topPos + 77).toDouble(), 0.0)
                graphics.pose().scale(2F, 2F, 1F)
                graphics.renderItem(stack, 0, 0)
            } finally {
                graphics.pose().popPose()
            }
        }
        fill(graphics, Layout.CENTER_X - 47, 149, 94, 2, 0xFF343434.toInt())
        fill(graphics, Layout.CENTER_X - 47, 149, (94 * progressFraction()).roundToInt(), 2, 0xFF8FA89B.toInt())
        smallText(graphics, status(), Layout.CENTER_X - 50, 154, 100, centered = true)
    }

    private fun renderRightPanel(graphics: GuiGraphics) {
        panelSprite(graphics, "requirements_panel", REQUIREMENTS_X, REQUIREMENTS_Y,
            REQUIREMENTS_WIDTH, REQUIREMENTS_HEIGHT, 66, 40)
        val textX = REQUIREMENTS_X + 4
        val textWidth = REQUIREMENTS_WIDTH - 8
        smallText(graphics, label("requirements").bold(), textX, 29, textWidth, centered = true)
        smallText(graphics, label("cost_short", menu.requiredCharge), textX, 38, textWidth)
        val seconds = ceil((menu.duration - menu.progress).coerceAtLeast(0) / 20.0).toInt()
        smallText(graphics, label(if (menu.progress > 0) "time_left" else "time_short", seconds), textX, 46, textWidth)
        val readiness = when {
            !menu.stateReceived -> label("waiting")
            menu.requiredCharge > menu.capacity -> label("connect_tank")
            menu.requiredCharge > menu.charge -> label("add_fuel")
            menu.statusKey.endsWith(".select") -> label("ready")
            else -> status()
        }
        wrappedStatus(graphics, readiness, textX, 55, textWidth,
            color = if (menu.requiredCharge > menu.charge) 0xF0C182 else MUTED)
        smallText(graphics, linkedTanks(), 274, TANK_COUNT_Y, 66, centered = true)
        if (selectedTab == Tab.SUPPLY) {
            machineSlot(graphics, Layout.FUEL_SLOT)
            smallText(graphics, label("fuel"), 301, Layout.FUEL_Y, 38)
            val fuelYield = UmbraInfuserBlockEntity.fuelValue(minecraft?.level, menu.getSlot(Layout.FUEL_SLOT).item)
            smallText(graphics, if (fuelYield > 0) label("fuel_yield", fuelYield) else label("add_fuel"), 301, Layout.FUEL_Y + 9, 38)
            if (menu.getSlot(Layout.INPUT_SLOT).hasItem()) {
                smallText(graphics, label("queue_count", menu.getSlot(Layout.INPUT_SLOT).item.count), 278, 126, 58, centered = true)
            }
            smallText(graphics, label("input"), 274, 132, 26, centered = true)
            smallText(graphics, label("output"), 315, 132, 26, centered = true)
            machineSlot(graphics, Layout.INPUT_SLOT)
            machineSlot(graphics, Layout.OUTPUT_SLOT)
            smallText(graphics, Component.literal("→"), 303, 146, 13, 1F, true)
        } else {
            panelSprite(graphics, "storage_panel", 274, 104, 66, 56, 66, 60)
            smallText(graphics, label("capacity").bold(), 278, 109, 58, centered = true)
            smallText(graphics, label("internal_capacity", internalCapacity()), 278, 120, 58)
            smallText(graphics, label("tank_capacity", tankCapacity()), 278, 130, 58)
            smallText(graphics, label("total_capacity", menu.capacity), 278, 141, 58)
            smallText(graphics, linkedTanks(), 278, 151, 58)
        }
    }

    private fun renderDrawer(graphics: GuiGraphics) {
        val eased = 1F - (1F - drawerAmount) * (1F - drawerAmount) * (1F - drawerAmount)
        val offset = ((1F - eased) * Layout.DRAWER_HEIGHT).roundToInt()
        graphics.flush()
        graphics.pose().pushPose()
        GuiPoseScissor.enable(graphics, leftPos + 85, topPos + 28, leftPos + 259, topPos + 182)
        try {
            // Above scene textures and below vanilla's slots (+100); do not dim the party row.
            graphics.pose().translate(0F, 0F, 10F)
            sprite(graphics, "inventory_drawer", Layout.DRAWER_X, Layout.DRAWER_Y + offset,
                Layout.DRAWER_WIDTH, Layout.DRAWER_HEIGHT)
            // Match the visible right/bottom edge; the original dark edges disappear against the chamber.
            fill(graphics, Layout.DRAWER_X, Layout.DRAWER_Y + offset,
                Layout.DRAWER_WIDTH, 1, 0xFF929292.toInt())
            fill(graphics, Layout.DRAWER_X, Layout.DRAWER_Y + 1 + offset,
                1, Layout.DRAWER_HEIGHT - 1, 0xFF929292.toInt())
            for (index in Layout.PLAYER_SLOT_START until Layout.SLOT_COUNT) {
                val slot = menu.getSlot(index)
                sprite(graphics, "inventory_slot", slot.x - 1, slot.y - 1 + offset, 18, 18,
                    if (drawerInteractive && inside(frameMouseX, frameMouseY, slot.x - 1, slot.y - 1, 18, 18)) 18 else 0, 36)
                // The real slots render at fixed menu positions only once fully open.
                if (!drawerInteractive && slot.hasItem()) {
                    graphics.renderItem(slot.item, leftPos + slot.x, topPos + slot.y + offset)
                    graphics.renderItemDecorations(font, slot.item, leftPos + slot.x, topPos + slot.y + offset)
                }
            }
            smallText(graphics, playerInventoryTitle.copy().bold(), Layout.DRAWER_X + 32,
                Layout.DRAWER_Y + 4 + offset, Layout.DRAWER_WIDTH - 64,
                scale = 0.75F, centered = true, large = true)
            graphics.flush()
        } finally {
            graphics.disableScissor()
            graphics.pose().popPose()
        }
    }

    private fun renderEnergy(graphics: GuiGraphics) {
        val fillWidth = ENERGY_METER_WIDTH - 4
        val fillHeight = ENERGY_METER_HEIGHT - 4
        fill(graphics, ENERGY_METER_X + 2, ENERGY_METER_Y + 2, fillWidth, fillHeight, 0xFF333333.toInt())
        fill(graphics, ENERGY_METER_X + 2, ENERGY_METER_Y + 2,
            (fillWidth.toFloat() * menu.charge / menu.capacity.coerceAtLeast(1)).roundToInt().coerceIn(0, fillWidth),
            fillHeight, 0xFF8A35D1.toInt())
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        graphics.blit(texture("umbra_energy_meter"), leftPos + ENERGY_METER_X, topPos + ENERGY_METER_Y,
            ENERGY_METER_WIDTH, ENERGY_METER_HEIGHT, 0F, 0F, 220, 20, 220, 20)
        smallText(graphics, label("energy", menu.charge, menu.capacity), ENERGY_METER_X + 4,
            ENERGY_METER_Y + (ENERGY_METER_HEIGHT - font.lineHeight * 0.5F) / 2F,
            ENERGY_METER_WIDTH - 8, centered = true)
    }

    private fun machineSlot(graphics: GuiGraphics, index: Int) {
        val slot = menu.getSlot(index)
        val disabled = index == Layout.INPUT_SLOT && menu.pokemon != null
        val state = if (disabled) 3 else if (inside(frameMouseX, frameMouseY, slot.x - 2, slot.y - 2, 20, 20)) 1 else 0
        sprite(graphics, "machine_slot", slot.x - 2, slot.y - 2, 20, 20, state * 20, 80)
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) = Unit

    // Vanilla passes null for outside clicks despite its package-level non-null annotation.
    @Suppress("WRONG_NULLABILITY_FOR_JAVA_OVERRIDE", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    override fun slotClicked(slot: Slot?, slotId: Int, button: Int, clickType: ClickType) {
        if ((slot ?: menu.slots.getOrNull(slotId))?.isActive == false) return
        super.slotClicked(slot, slotId, button, clickType)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val fit = viewportFit
        return contentMouseClicked(fit.toContentX(mouseX), fit.toContentY(mouseY), button)
    }

    private fun contentMouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && itemViewButton.active && itemViewButton.visible &&
            itemViewButton.isButtonHovered(mouseX, mouseY)) {
            // Cobblemon's SummaryButton fires its action but returns false. Consume it
            // here so the same click cannot fall through into container item handling.
            itemViewButton.mouseClicked(mouseX, mouseY, button)
            setFocused(itemViewButton)
            return true
        }
        if (!drawerCoveringChamber && button == 0 && inside(mouseX.toInt(), mouseY.toInt(),
                Layout.CHAMBER_X, Layout.CHAMBER_Y, Layout.CHAMBER_WIDTH, Layout.CHAMBER_HEIGHT)) {
            inspectedPokemon = null
            setFocused(null)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val fit = viewportFit
        return super.mouseReleased(fit.toContentX(mouseX), fit.toContentY(mouseY), button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val fit = viewportFit
        return super.mouseDragged(fit.toContentX(mouseX), fit.toContentY(mouseY), button,
            fit.dragDelta(dragX), fit.dragDelta(dragY))
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        val fit = viewportFit
        super.mouseMoved(fit.toContentX(mouseX), fit.toContentY(mouseY))
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val fit = viewportFit
        return super.mouseScrolled(fit.toContentX(mouseX), fit.toContentY(mouseY), scrollX, scrollY)
    }

    override fun getRectangle(): ScreenRectangle {
        val fit = viewportFit
        val left = floor(fit.toContentX(0.0)).toInt()
        val top = floor(fit.toContentY(0.0)).toInt()
        val right = ceil(fit.toContentX(width.toDouble())).toInt()
        val bottom = ceil(fit.toContentY(height.toDouble())).toInt()
        return ScreenRectangle(left, top, right - left, bottom - top)
    }

    private fun customTooltip(mouseX: Int, mouseY: Int): Component? {
        if (closeDrawerButton.visible && closeDrawerButton.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
            return closeDrawerButton.message
        }
        if (itemViewButton.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) return itemViewButton.message
        if (infoPanelButton.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) return infoPanelButton.message
        partyButtons.firstOrNull { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }?.let { button ->
            val pokemon = button.pokemon() ?: return label("party_empty")
            return when {
                ShadowAspectUtil.hasShadowAspect(pokemon) -> label("already_shadow", pokemon.getDisplayName())
                pokemon.uuid !in menu.eligiblePartyPokemon -> Component.translatable("message.shadowedhearts.infuser.pokemon_invalid")
                canInsert(pokemon) -> label("insert_named", pokemon.getDisplayName())
                else -> label("pokemon_named", pokemon.getDisplayName(), pokemon.level)
            }
        }
        if (!drawerCoveringChamber) {
            if (inside(mouseX, mouseY, Layout.CHAMBER_X, Layout.CHAMBER_Y, Layout.CHAMBER_WIDTH, Layout.CHAMBER_HEIGHT) ||
                inside(mouseX, mouseY, Layout.CENTER_X - 51, 32, 102, 8)) return menu.pokemon?.let {
                label("pokemon_named", it.getDisplayName(), it.level)
            } ?: label("item_tooltip")
            if (inside(mouseX, mouseY, Layout.CENTER_X - 50, 148, 100, 15)) return status().copy()
                .append("\n").append(label("progress_tooltip", (100 * progressFraction()).roundToInt()))
        }
        if (inside(mouseX, mouseY, ENERGY_METER_X, ENERGY_METER_Y, ENERGY_METER_WIDTH, ENERGY_METER_HEIGHT)) return label("energy_tooltip", menu.charge,
            menu.capacity, menu.expansionCount, menu.requiredCharge)
        if (inside(mouseX, mouseY, REQUIREMENTS_X, REQUIREMENTS_Y, REQUIREMENTS_WIDTH, REQUIREMENTS_HEIGHT)) return status()
        if (inside(mouseX, mouseY, 274, TANK_COUNT_Y - 2, 66, 9) || selectedTab == Tab.TANKS && inside(mouseX, mouseY, 274, 104, 66, 56)) {
            return label("network_tooltip", internalCapacity(), menu.expansionCount, tankCapacity(), menu.capacity)
        }
        if (selectedTab == Tab.SUPPLY) {
            if (inside(mouseX, mouseY, Layout.FUEL_X - 2, Layout.FUEL_Y - 2, 63, 20)) return label("fuel_tooltip")
            if (inside(mouseX, mouseY, 277, 140, 20, 20)) return label(if (menu.pokemon == null) "item_tooltip" else "input_locked")
            if (inside(mouseX, mouseY, 318, 140, 20, 20)) return label("output_tooltip")
        }
        return null
    }

    private fun status(): Component = when {
        !menu.stateReceived -> label("waiting")
        minecraft?.player?.let { !menu.canManage(it) } == true -> label("owned")
        else -> Component.translatable(menu.statusKey)
    }
    private fun linkedTanks() = if (menu.expansionCount == 0) label("no_tanks") else label("tanks_linked", menu.expansionCount)
    private fun internalCapacity() = (menu.capacity / (menu.expansionCount + 1)).coerceAtLeast(1)
    private fun tankCapacity() = (menu.capacity - internalCapacity()).coerceAtLeast(0)
    private fun progressFraction() = if (menu.duration > 0) (menu.progress.toFloat() / menu.duration).coerceIn(0F, 1F) else 0F

    private fun sprite(graphics: GuiGraphics, name: String, x: Int, y: Int, w: Int, h: Int, v: Int = 0, textureHeight: Int = h) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        graphics.blit(texture(name), leftPos + x, topPos + y, 0F, v.toFloat(), w, h, w, textureHeight)
    }

    private fun fill(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, color: Int) =
        graphics.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, color)

    /** Resize panel interiors while retaining the authored one-pixel bevel. */
    private fun panelSprite(graphics: GuiGraphics, name: String, x: Int, y: Int,
                            width: Int, height: Int, sourceWidth: Int, sourceHeight: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        val sourceX = intArrayOf(0, 1, sourceWidth - 1, sourceWidth)
        val sourceY = intArrayOf(0, 1, sourceHeight - 1, sourceHeight)
        val destX = intArrayOf(0, 1, width - 1, width)
        val destY = intArrayOf(0, 1, height - 1, height)
        for (row in 0..2) for (column in 0..2) {
            graphics.blit(texture(name), leftPos + x + destX[column], topPos + y + destY[row],
                destX[column + 1] - destX[column], destY[row + 1] - destY[row],
                sourceX[column].toFloat(), sourceY[row].toFloat(),
                sourceX[column + 1] - sourceX[column], sourceY[row + 1] - sourceY[row], sourceWidth, sourceHeight)
        }
    }

    /** Keep compact status text readable; the complete explanation remains available on hover. */
    private fun wrappedStatus(graphics: GuiGraphics, text: Component, x: Int, y: Int, width: Int, color: Int) {
        val suffix = Component.literal("...")
        val plan = UmbraInfuserTextLayout.status(width, font.split(text, width * 2).size, font.width(suffix))
        val scale = UmbraInfuserTextLayout.STATUS_SCALE.toFloat()
        val lines = font.split(text, plan.wrapWidth()).take(UmbraInfuserTextLayout.STATUS_LINES)
        lines.forEachIndexed { index, line ->
            val shown = if (plan.ellipsis() && index == lines.lastIndex)
                FormattedCharSequence.composite(line, suffix.visualOrderText) else line
            drawScaledText(graphics, shown, leftPos + x, topPos + y + index * UmbraInfuserTextLayout.STATUS_LINE_SPACING,
                scaleX = scale, scaleY = scale, colour = color, shadow = true)
        }
        if (plan.ellipsis() && inside(frameMouseX, frameMouseY, x, y, width,
                UmbraInfuserTextLayout.STATUS_LINES * UmbraInfuserTextLayout.STATUS_LINE_SPACING)) {
            overflowTooltip = text.copy()
        }
    }

    private fun smallText(graphics: GuiGraphics, text: Component, x: Int, y: Number, w: Int,
                          scale: Float = 0.5F, centered: Boolean = false, color: Int = TEXT, large: Boolean = false) {
        val styled = text.copy()
        if (large) styled.withStyle(styled.style.withFont(CobblemonResources.DEFAULT_LARGE))
        val limit = (w / scale).toInt()
        val clipped = font.width(styled) > limit
        val shown = BoundedGuiText.fit(styled, limit, font)
        drawScaledText(context = graphics, text = shown, font = if (large) CobblemonResources.DEFAULT_LARGE else null,
            x = leftPos + x + if (centered) w / 2.0 else 0.0, y = topPos + y.toDouble(), scale = scale,
            centered = centered, shadow = true, colour = color)
        if (clipped && inside(frameMouseX, frameMouseY, x, floor(y.toDouble()).toInt(), w,
                maxOf(6, ceil(font.lineHeight * scale).toInt()))) overflowTooltip = styled
    }

    private fun inside(mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) =
        mouseX >= leftPos + x && mouseX < leftPos + x + w && mouseY >= topPos + y && mouseY < topPos + y + h
    override fun updateNarratedWidget(output: NarrationElementOutput) {
        if (!narrateSummaryButton(this, output)) super.updateNarratedWidget(output)
    }

    private fun label(key: String, vararg values: Any) = Component.translatable("gui.shadowedhearts.umbra_infuser.$key", *values)

    /** Preserve the native button's bevels when the two panel tabs need narrower buttons. */
    private fun renderPcButton(graphics: GuiGraphics, button: Button, selected: Boolean = false) {
        val x = button.x
        val y = button.y
        val width = button.width
        val v = if (button.active && button.isHoveredOrFocused) 16F else 0F
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        graphics.blit(PC_BUTTON, x, y, 0F, v, 4, 16, 58, 32)
        graphics.blit(PC_BUTTON, x + 4, y, width - 8, 16, 4F, v, 50, 16, 58, 32)
        graphics.blit(PC_BUTTON, x + width - 4, y, 54F, v, 4, 16, 58, 32)
        val label = button.message.copy().bold().withStyle { it.withFont(CobblemonResources.DEFAULT_LARGE) }
        val labelWidth = width - 6
        val textScale = minOf(if (width < 58) 0.75F else 1F, labelWidth.toFloat() / font.width(label).coerceAtLeast(1))
        smallText(graphics, label, x - leftPos + 3, y - topPos + 3.5, labelWidth,
            textScale, true, if (button.active) TEXT else 0x8C8C8C, true)
        if (selected) graphics.fill(x + 6, y + 12, x + width - 6, y + 13, 0xFFDCDCDC.toInt())
    }

    private inner class PcButton(localX: Int, localY: Int, text: Component, onPress: OnPress) :
        Button(leftPos + localX, topPos + localY, 58, 16, text, onPress, DEFAULT_NARRATION) {
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            renderPcButton(graphics, this)
        }
    }

    private inner class TabButton(localX: Int, private val tab: Tab, text: Component) :
        Button(leftPos + localX, topPos + TABS_Y, 32, 16, text, OnPress {
            selectedTab = tab
            updateWidgets()
        }, DEFAULT_NARRATION) {
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            renderPcButton(graphics, this, selectedTab == tab)
        }
    }

    private inner class PartyButton(x: Int, y: Int, private val index: Int) :
        Button(x, y, Layout.PARTY_SLOT_SIZE, Layout.PARTY_SLOT_SIZE, label("party_empty"), OnPress {}, DEFAULT_NARRATION) {
        private val preview = UmbraInfuserPartyPreview()
        fun pokemon() = CobblemonClient.storage.party.get(index)

        override fun onPress() {
            val pokemon = pokemon() ?: return
            if (canInsert(pokemon)) {
                inspectedPokemon = null
                sendAction(UmbraInfuserActionPacket.Action.INSERT, pokemon)
            } else inspectedPokemon = pokemon.uuid
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val pokemon = pokemon()
            sprite(graphics, "party_slot", x - leftPos, y - topPos, width, height)
            smallText(graphics, Component.literal((index + 1).toString()), x - leftPos, y - topPos - 8, width, centered = true)
            preview.render(graphics, x, y, pokemon, partialTick)

            // Match the purification PC's outlines without requiring extra texture frames.
            val outlineColor = when {
                pokemon == null -> 0
                inspectedPokemon == pokemon.uuid -> 0xFFFFFFFF.toInt()
                isHoveredOrFocused -> 0x88FFFFFF.toInt()
                else -> 0
            }
            if (outlineColor != 0) {
                graphics.pose().pushPose()
                try {
                    graphics.pose().translate(0F, 0F, 100F)
                    graphics.fill(x - 1, y - 1, x + width + 1, y, outlineColor)
                    graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, outlineColor)
                    graphics.fill(x - 1, y, x, y + height, outlineColor)
                    graphics.fill(x + width, y, x + width + 1, y + height, outlineColor)
                } finally {
                    graphics.pose().popPose()
                }
            }
        }
    }
}
