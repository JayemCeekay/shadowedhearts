package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleOptionTile;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.jayemceekay.shadowedhearts.client.gui.CallButtonOptionTile;
import com.jayemceekay.shadowedhearts.client.snag.ClientSnagState;
import com.jayemceekay.shadowedhearts.common.snag.SnagBattleUtil;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.snag.SnagArmPacket;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = BattleGeneralActionSelection.class, remap = false)
public abstract class MixinBattleGeneralActionSelection {

    @Shadow @Final
    private List<BattleOptionTile> tiles;

    @Shadow
    private com.cobblemon.mod.common.client.battle.SingleActionRequest lastAnwseredRequest;

    public CallButtonOptionTile callButtonTile;

    @Unique
    private BattleOptionTile shadowedhearts$snagButtonTile;

    @Shadow
    public abstract void playDownSound(SoundManager soundManager);

    @org.spongepowered.asm.mixin.injection.Inject(method = "<init>(Lcom/cobblemon/mod/common/client/gui/battle/BattleGUI;Lcom/cobblemon/mod/common/client/battle/SingleActionRequest;)V", at = @At("TAIL"))
    private void shadowedhearts$addCallButton(BattleGUI battleGUI, com.cobblemon.mod.common.client.battle.SingleActionRequest request, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        MutableComponent callText = Component.translatable("battle.shadowedhearts.call");
        ResourceLocation callIcon = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/call_button_long.png");
        Function0<Unit> callClick = () -> {
            battleGUI.selectAction(request, new com.jayemceekay.shadowedhearts.cobblemon.battles.CallActionResponse());
            playDownSound(mc.getSoundManager());
            return Unit.INSTANCE;
        };

        // Place the Call button below the 4 option tiles, where the back button sits.
        // If the back button is visible (lastAnwseredRequest != null), put Call to the right of it.
        // BattleBackButton: x = OPTION_ROOT_X - 3, y = guiScaledHeight - 22, effective width = 58 * 0.5 = 29
        int backButtonX = BattleGUI.OPTION_ROOT_X;
        int callY = mc.getWindow().getGuiScaledHeight() - 22;
        int callX = (lastAnwseredRequest != null)
                ? backButtonX + 29 + 4   // right of the back button with a small gap
                : backButtonX;            // same position as the back button would be

        callButtonTile = new CallButtonOptionTile(battleGUI, callX, callY, callIcon, callText, callClick);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void shadowedhearts$updateSnagButton(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (shadowedhearts$snagButtonTile == null) return;
        MutableComponent label = shadowedhearts$snagButtonTile.getText();
        label.getSiblings().clear();
        label.append(Component.translatable(ClientSnagState.getRechargeTurnsRemaining() > 1
                ? "ui.shadowedhearts.snag.recharging" : "ui.shadowedhearts.snag"));
        label.setStyle(ClientSnagState.isRecharging() ? Style.EMPTY.withColor(ChatFormatting.GRAY) : Style.EMPTY);
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    public void shadowedhearts$renderCallButton(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (callButtonTile != null) {
            callButtonTile.render(context, mouseX, mouseY, delta);
        }
        if (shadowedhearts$snagButtonTile != null && ClientSnagState.isRecharging()) {
            int turns = ClientSnagState.getRechargeTurnsRemaining();
            Component readiness = turns == 1
                    ? Component.translatable("ui.shadowedhearts.snag.ready_next_turn")
                    : Component.translatable("ui.shadowedhearts.snag.ready_in_turns", turns);
            var mc = Minecraft.getInstance();
            int alpha = (int) (CobblemonClient.INSTANCE.getBattleOverlay().getOpacityRatio() * 255);
            if (alpha < 26) return;
            context.drawString(mc.font, readiness, shadowedhearts$snagButtonTile.getX() + 6,
                    shadowedhearts$snagButtonTile.getY() + 17, (alpha << 24) | 0xA0A0A0, false);
            if (shadowedhearts$snagButtonTile.isHovered(mouseX, mouseY)) {
                Component detail = turns == 1
                        ? Component.translatable("ui.shadowedhearts.snag.unavailable_next_turn")
                        : Component.translatable("ui.shadowedhearts.snag.recharging_in_turns", turns);
                context.renderTooltip(mc.font, detail, mouseX, mouseY);
            }
        }
    }

    @Inject(
            method = "mousePrimaryClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;next()Ljava/lang/Object;"
            ),
            remap = false,
            cancellable = true)
    private void shadowedhearts$onMousePrimaryClicked(
            double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir
    ) {
        if (callButtonTile != null && callButtonTile.isHovered(mouseX, mouseY)) {
            callButtonTile.mousePrimaryClicked(mouseX, mouseY);
            cir.setReturnValue(true);
    }
}

    @WrapMethod(method = "addOption")
    private void shadowedhearts$addSnagOption(int rank, MutableComponent text, ResourceLocation texture, Function0<Unit> onClick, Operation<Void> original) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player != null && SnagBattleUtil.canShowSnagButton(player) && text.getContents() instanceof TranslatableContents label) {
            boolean forfeit = label.getKey().equals("cobblemon.battle.ui.forfeit");
            // ABA offers Catch instead of Forfeit when an NPC side contains an open WILD slot.
            var battle = CobblemonClient.INSTANCE.getBattle();
            boolean trainerCatch = label.getKey().equals("cobblemon.battle.ui.capture") && battle != null
                    && battle.getSide2().getActors().stream().anyMatch(actor -> actor.getType() == ActorType.NPC);
            if (!forfeit && !trainerCatch) {
                original.call(rank, text, texture, onClick);
                return;
            }
            MutableComponent snagText = Component.empty().append(Component.translatable("ui.shadowedhearts.snag"));
            ResourceLocation snagIcon = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/battle_menu_switch.png");
            Function0<Unit> snagClick = () -> {
                if (!ClientSnagState.isEligible() || ClientSnagState.isRecharging()) return Unit.INSTANCE;
                ShadowedHeartsNetwork.sendToServer(
                        new SnagArmPacket(true)
                );
                playDownSound(mc.getSoundManager());
                return Unit.INSTANCE;
            };
            // Insert our Snag option at the current rank
            original.call(rank, snagText, snagIcon, snagClick);
            shadowedhearts$snagButtonTile = tiles.get(tiles.size() - 1);
            if (trainerCatch) return;
            rank++;
        }

        // Place the Forfeit option after it
        original.call(rank, text, texture, onClick);
    }
}
