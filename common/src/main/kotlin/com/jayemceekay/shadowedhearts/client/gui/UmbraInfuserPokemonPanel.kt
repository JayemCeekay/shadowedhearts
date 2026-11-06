package com.jayemceekay.shadowedhearts.client.gui

import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.italicise
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.common.reformatNatureTextIfMinted
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.lwjgl.opengl.GL11

/** Read-only PC-style details for the hovered party member or the chamber occupant. */
class UmbraInfuserPokemonPanel {
    companion object {
        const val STAT_INFO = 0
        const val STAT_IV = 1
        const val STAT_EV = 2
        private val PORTRAIT = cobblemonResource("textures/gui/pc/portrait_background.png")
        private val INFO = cobblemonResource("textures/gui/pc/info_box.png")
        private val INFO_STATS = cobblemonResource("textures/gui/pc/info_box_stats.png")
        private val TYPE_SINGLE = cobblemonResource("textures/gui/pc/type_spacer_single.png")
        private val TYPE_DOUBLE = cobblemonResource("textures/gui/pc/type_spacer_double.png")
        private val MARKINGS = List(6) { cobblemonResource("textures/gui/summary/icon_marking_$it.png") }
        private val STATS = arrayOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)
        private val STAT_LABELS = arrayOf(
            lang("ui.stats.hp"), lang("ui.stats.atk"), lang("ui.stats.def"),
            lang("ui.stats.sp_atk"), lang("ui.stats.sp_def"), lang("ui.stats.speed")
        )
    }

    var showCosmeticItem = false
        private set
    var currentStatIndex = STAT_INFO
        private set

    private var model: ModelWidget? = null
    private var modelPokemon: Pokemon? = null
    private var modelAspects: Set<String> = emptySet()
    private var modelHeldItem = ItemStack.EMPTY
    private var tooltipText: Component? = null
    private var tooltipItem: ItemStack? = null
    private var tooltipStyle: Style? = null

    fun toggleItemView() {
        showCosmeticItem = !showCosmeticItem
    }

    fun cycleInfoPanel() {
        currentStatIndex = (currentStatIndex + 1) % 3
    }

    /** Rebuild render state after resizing without losing the player's selected detail modes. */
    fun reset() {
        model = null
        modelPokemon = null
        modelAspects = emptySet()
        modelHeldItem = ItemStack.EMPTY
        tooltipText = null
        tooltipItem = null
        tooltipStyle = null
    }

    /** Draw before the infuser base so its rounded portrait trim masks the background and model. */
    fun renderBackground(
        graphics: GuiGraphics,
        originX: Int,
        originY: Int,
        pokemon: Pokemon?,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        val x = originX
        val y = originY
        blitk(graphics.pose(), PORTRAIT, x + 6, y + 27, width = 66, height = 66)
        if (pokemon == null) {
            model = null
            modelPokemon = null
            return
        }

        val heldItem = pokemon.heldItem()
        if (modelPokemon !== pokemon || model == null || model?.pokemon?.species != pokemon.species ||
            modelAspects != pokemon.aspects ||
            !ItemStack.isSameItemSameComponents(modelHeldItem, heldItem)) {
            modelPokemon = pokemon
            modelAspects = pokemon.aspects.toSet()
            modelHeldItem = heldItem.copy()
            model = ModelWidget(
                pX = x + 6, pY = y + 27, pWidth = 66, pHeight = 66,
                pokemon = pokemon.asRenderablePokemon(), baseScale = 2F,
                rotationY = 325F, offsetY = -10.0
            )
        }
        model?.let {
            it.x = x + 6
            it.y = y + 27
            // ModelWidget clips its Pokemon internally, but its held-item renderer can leave
            // buffered vertices behind. Keep an outer clip until those vertices are flushed.
            graphics.flush()
            val depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
            val depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
            val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
            GuiPoseScissor.enable(graphics, x + 6, y + 27, x + 72, y + 93)
            try {
                it.render(graphics, mouseX, mouseY, partialTick)
                graphics.flush()
            } finally {
                // The base's rounded trim and shiny icon must cover the portrait even
                // where the 3D model wrote nearer depth than an ordinary GUI texture.
                RenderSystem.depthMask(true)
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
                graphics.disableScissor()
                RenderSystem.depthFunc(depthFunction)
                RenderSystem.depthMask(depthWrite)
                if (depthEnabled) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            }
        }
    }

    /** Identity, markings and information use the purification PC's native coordinates and font sizes. */
    fun render(
        graphics: GuiGraphics,
        originX: Int,
        originY: Int,
        pokemon: Pokemon?,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        tooltipText = null
        tooltipItem = null
        tooltipStyle = null
        val x = originX
        val y = originY
        blitk(graphics.pose(), if (currentStatIndex == STAT_INFO) INFO else INFO_STATS, x + 9, y + 128, width = 63, height = 69)
        // Complete the texture pass before the font's slightly raised glyph vertices are submitted.
        graphics.flush()
        when (currentStatIndex) {
            STAT_INFO -> {
                text(graphics, lang("ui.info.nature").bold(), x + 10, y + 129.5, 60, mouseX, mouseY, centered = true, shadow = false)
                text(graphics, lang("ui.info.ability").bold(), x + 10, y + 146.5, 60, mouseX, mouseY, centered = true, shadow = false)
                text(graphics, lang("ui.moves").bold(), x + 10, y + 163.5, 60, mouseX, mouseY, centered = true, shadow = false)
            }
            STAT_IV -> text(graphics, lang("ui.stats.ivs").bold(), x + 10, y + 129.5, 60, mouseX, mouseY, centered = true, shadow = false)
            STAT_EV -> text(graphics, lang("ui.stats.evs").bold(), x + 10, y + 129.5, 60, mouseX, mouseY, centered = true, shadow = false)
        }

        if (pokemon == null) return

        repeat(6) { index ->
            // The infuser displays markings but never edits or sends marking packets.
            blitk(
                graphics.pose(), MARKINGS[index],
                (x + 29 + index * 7) / 0.5F, (y + 96.5) / 0.5F,
                width = 12, height = 12, textureWidth = 24, textureHeight = 36,
                vOffset = (pokemon.markings.getOrNull(index) ?: 0).coerceIn(0, 2) * 12,
                scale = 0.5F
            )
        }

        text(
            graphics, lang("ui.lv").bold(), x + 6, y + 1.5, 13, mouseX, mouseY,
            scale = 1F, font = CobblemonResources.DEFAULT_LARGE
        )
        text(
            graphics, Component.literal(pokemon.level.toString()).bold(), x + 19, y + 1.5, 15, mouseX, mouseY,
            scale = 1F, font = CobblemonResources.DEFAULT_LARGE
        )
        val statusName = if (pokemon.isFainted()) "fnt" else pokemon.status?.status?.showdownName
        if (statusName != null) {
            blitk(
                graphics.pose(), cobblemonResource("textures/gui/battle/battle_status_$statusName.png"),
                x + 34, y + 1, width = 39, height = 7, uOffset = 35, textureWidth = 74
            )
            blitk(graphics.pose(), cobblemonResource("textures/gui/summary/status_trim.png"), x + 34, y + 2, width = 3, height = 6)
            text(
                graphics, lang("ui.status.$statusName").bold(), x + 39, y, 33, mouseX, mouseY,
                scale = 1F, font = CobblemonResources.DEFAULT_LARGE, shadow = false
            )
        }
        renderItem(graphics, pokemon.caughtBall.stack(), x + 3.5, y + 12, 0.5F)
        text(
            graphics, pokemon.getDisplayName().bold(), x + 12, y + 11.5,
            if (pokemon.gender == Gender.GENDERLESS) 60 else 54, mouseX, mouseY,
            scale = 1F, font = CobblemonResources.DEFAULT_LARGE
        )
        if (pokemon.gender != Gender.GENDERLESS) {
            val male = pokemon.gender == Gender.MALE
            text(
                graphics, Component.literal(if (male) "♂" else "♀").bold(),
                x + 69, y + 11.5, 6, mouseX, mouseY, scale = 1F,
                font = CobblemonResources.DEFAULT_LARGE,
                color = if (male) 0xFF32CBFF.toInt() else 0xFFFC5454.toInt()
            )
        }
        if (pokemon.shiny) {
            blitk(
                graphics.pose(), Summary.iconShinyResource,
                (x + 62.5) / 0.5F, (y + 28.5) / 0.5F,
                width = 16, height = 16, scale = 0.5F
            )
        }

        val displayedItem = if (showCosmeticItem) pokemon.cosmeticItem else pokemon.heldItem()
        if (!displayedItem.isEmpty) {
            renderItem(graphics, displayedItem, x + 3, y + 98, 1F)
            graphics.renderItemDecorations(Minecraft.getInstance().font, displayedItem, x + 3, y + 98)
            if (mouseX in x + 3 until x + 19 && mouseY in y + 98 until y + 114) {
                tooltipItem = displayedItem
            }
        }
        text(graphics, lang(if (showCosmeticItem) "cosmetic_item" else "held_item"), x + 24, y + 108.5, 42, mouseX, mouseY, shadow = false)

        blitk(
            graphics.pose(), if (pokemon.secondaryType == null) TYPE_SINGLE else TYPE_DOUBLE,
            (x + 9) / 0.5F, (y + 118.5) / 0.5F,
            width = 126, height = 12, scale = 0.5F
        )
        TypeIcon(
            x = x + 40.5, y = y + 117,
            type = pokemon.primaryType, secondaryType = pokemon.secondaryType,
            doubleCenteredOffset = 5F, secondaryOffset = 10F,
            small = true, centeredX = true
        ).render(graphics)

        if (currentStatIndex != STAT_INFO) {
            renderStats(graphics, x, y, pokemon, mouseX, mouseY)
            return
        }

        // Use the same disclosure rules as the purification PC, including after infusion finishes.
        val nature = if (ShadowUnlockPolicy.isNatureHiddenByGauge(pokemon)) {
            Component.literal("????")
        } else reformatNatureTextIfMinted(pokemon)
        text(graphics, nature, x + 10, y + 137, 60, mouseX, mouseY, centered = true)
        text(graphics, pokemon.ability.displayName.asTranslated(), x + 10, y + 154, 60, mouseX, mouseY, centered = true)

        val allowed = ShadowUnlockPolicy.getAllowedVisibleNonShadowMoves(pokemon)
        var nonShadowIndex = 0
        val moves = pokemon.moveSet.getMoves().take(4).map { move ->
            if (ShadowMoveUtil.isShadowMove(move)) move.displayName
            else if (nonShadowIndex++ < allowed) move.displayName
            else Component.literal("????")
        }
        repeat(4) { index ->
            text(
                graphics, moves.getOrNull(index) ?: Component.literal("—"),
                x + 10, y + 170.5 + index * 7, 60, mouseX, mouseY, centered = true
            )
        }
    }

    private fun renderStats(graphics: GuiGraphics, x: Int, y: Int, pokemon: Pokemon, mouseX: Int, mouseY: Int) {
        val hidden = if (currentStatIndex == STAT_IV) {
            ShadowUnlockPolicy.isIVHiddenByGauge(pokemon)
        } else ShadowUnlockPolicy.isEVHiddenByGauge(pokemon)
        for (index in STATS.indices) {
            val rowY = y + 139 + 10 * index
            text(graphics, STAT_LABELS[index], x + 13, rowY, 44, mouseX, mouseY)
            val value = when {
                hidden -> Component.literal("??")
                currentStatIndex == STAT_IV -> {
                    val iv = pokemon.ivs.get(STATS[index])
                    val effectiveIv = pokemon.ivs.getEffectiveBattleIV(STATS[index])
                    if (iv != effectiveIv) Component.literal(effectiveIv.toString()).italicise()
                    else Component.literal(iv.toString())
                }
                else -> Component.literal(pokemon.evs.get(STATS[index]).toString())
            }
            text(graphics, value, x + 59, rowY, 12, mouseX, mouseY, centered = true)
        }
    }

    /** Draw after the screen and container slots so long-name and held-item tooltips stay above them. */
    fun renderTooltip(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Boolean {
        tooltipItem?.let {
            UmbraInfuserTooltip.renderItem(graphics, it, mouseX, mouseY)
            return true
        }
        tooltipText?.let {
            UmbraInfuserTooltip.render(graphics, it, mouseX, mouseY, partialTick)
            return true
        }
        tooltipStyle?.let {
            return UmbraInfuserTooltip.renderHover(graphics, it, mouseX, mouseY, partialTick)
        }
        return false
    }

    private fun renderItem(graphics: GuiGraphics, item: ItemStack, x: Number, y: Number, scale: Float) {
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(x.toDouble(), y.toDouble(), 0.0)
        pose.scale(scale, scale, 1F)
        graphics.renderItem(item, 0, 0)
        pose.popPose()
    }

    private fun text(
        graphics: GuiGraphics,
        value: Component,
        x: Int,
        y: Number,
        width: Int,
        mouseX: Int,
        mouseY: Int,
        centered: Boolean = false,
        scale: Float = 0.5F,
        font: ResourceLocation? = null,
        color: Int = -1,
        shadow: Boolean = true
    ) {
        val renderer = Minecraft.getInstance().font
        val full = value.copy()
        if (font != null) full.withStyle(full.style.withFont(font))
        val limit = (width / scale).toInt()
        val clipped = renderer.width(full) > limit
        val shown = BoundedGuiText.fit(full, limit, renderer)
        drawScaledText(
            context = graphics, font = font, text = shown,
            x = if (centered) x + width / 2.0 else x, y = y,
            scale = scale, centered = centered, shadow = shadow, colour = color
        )
        if (mouseX >= x && mouseX < x + width && mouseY >= y.toDouble() && mouseY < y.toDouble() + 8) {
            if (clipped) tooltipText = full
            else if (full.style.hoverEvent != null) tooltipStyle = full.style
        }
    }
}
