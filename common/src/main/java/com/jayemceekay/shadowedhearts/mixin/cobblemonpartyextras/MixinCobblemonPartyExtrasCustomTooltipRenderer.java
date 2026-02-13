package com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategory;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSwapScreen;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.jayemceekay.shadowedhearts.mixin.MovesWidgetAccessor;
import com.jayemceekay.shadowedhearts.mixin.SummaryAccessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.List;

@Pseudo
@Mixin(targets = "party.extras.cobblemon.client.CustomTooltipRenderer")
public abstract class MixinCobblemonPartyExtrasCustomTooltipRenderer {

    @WrapMethod(method = "renderMoveTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;III)V")
    private static void shadowedhearts$blockRenderMoveTooltip(
            GuiGraphics graphics,
            List<Component> lines,
            int mouseX,
            int mouseY,
            int typeHue,
            Operation<Void> original
    ) {
        if (shadowedhearts$shouldMaskTooltip(mouseX, mouseY)) {
            return;
        }
        original.call(graphics, lines, mouseX, mouseY, typeHue);
    }

    @WrapMethod(method = "renderMoveTooltipWithIcons")
    private static void shadowedhearts$blockRenderMoveTooltipWithIcons(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY, int typeHue, ElementalType type, DamageCategory category, Operation<Void> original) {
        if (shadowedhearts$shouldMaskTooltip(mouseX, mouseY)) {
            return;
        }
        original.call(graphics, lines, mouseX, mouseY, typeHue, type, category);
    }


    private static boolean shadowedhearts$shouldMaskTooltip(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;
        if (currentScreen instanceof Summary summary) {
            Pokemon pokemon = summary.getSelectedPokemon$common();
            if (pokemon == null || !ShadowGate.isShadowLockedClient(pokemon)) {
                return false;
            }

            // Check main screen (Moves tab)
            AbstractWidget main = ((SummaryAccessor) (Object) summary).getMainScreen();
            if (main instanceof MovesWidget movesWidget) {
                List<MoveSlotWidget> slots = ((MovesWidgetAccessor) (Object) movesWidget).getMoves();
                for (MoveSlotWidget slot : slots) {
                    if (slot.isHovered()) {
                        Move move = slot.getMove();
                        return shadowedhearts$isMoveMasked(move, pokemon);
                    }
                }
            }

            // Check side screen (Move Swap screen)
            GuiEventListener side = summary.getSideScreen();
            if (side instanceof MoveSwapScreen swapScreen) {
                for (MoveSwapScreen.MoveSlot entry : swapScreen.children()) {
                    if (entry.isMouseOver(mouseX, mouseY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private static boolean shadowedhearts$isMoveMasked(Move m, Pokemon pokemon) {
        if (m == null) return false;
        if (ShadowGate.isShadowMoveId(m.getTemplate().getName())) return false;

        int nonShadowIndex = 0;
        int allowed = ShadowAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        for (Move mv : pokemon.getMoveSet().getMovesWithNulls()) {
            if (mv == null || ShadowGate.isShadowMoveId(mv.getName())) continue;
            if (mv == m) return nonShadowIndex >= allowed;
            nonShadowIndex++;
        }
        return false;
    }


    private static boolean shadowedhearts$isTemplateMasked(MoveTemplate t, Pokemon pokemon) {
        if (t == null) return false;
        if (ShadowGate.isShadowMoveId(t.getName())) return false;

        int nonShadowIndex = 0;
        int allowed = ShadowAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        for (Move mv : pokemon.getMoveSet().getMovesWithNulls()) {
            if (mv == null || ShadowGate.isShadowMoveId(mv.getName())) continue;
            if (mv.getTemplate() == t) return nonShadowIndex >= allowed;
            nonShadowIndex++;
        }
        return false;
    }
}