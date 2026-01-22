package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleOptionTile;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.SnagArmPacket;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.List;

/**
 * Inject a "Snag" option just before the Forfeit button in Cobblemon's BattleGeneralActionSelection.
 *
 * We redirect the specific addOption invocation for Forfeit, insert our Snag option first (same rank),
 * then call the original addOption with rank + 1 so Forfeit is placed after it.
 */
@Mixin(value = BattleGeneralActionSelection.class)
public abstract class MixinBattleGeneralActionSelection {

    @Shadow @Final
    private List<BattleOptionTile> tiles;

    // addOption(rank: Int, text: MutableComponent, texture: ResourceLocation, onClick: () -> Unit)
    @Shadow
    private void addOption(int rank, MutableComponent text, ResourceLocation texture, Function0<Unit> onClick) {}

    @Shadow
    public abstract void playDownSound(SoundManager soundManager);

    @org.spongepowered.asm.mixin.injection.Inject(method = "<init>(Lcom/cobblemon/mod/common/client/gui/battle/BattleGUI;Lcom/cobblemon/mod/common/client/battle/SingleActionRequest;)V", at = @At("TAIL"))
    private void shadowedhearts$addCallButton(BattleGUI battleGUI, com.cobblemon.mod.common.client.battle.SingleActionRequest request, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        MutableComponent callText = Component.literal("Call");
        ResourceLocation callIcon = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/battle_menu_bag.png");
        Function0<Unit> callClick = () -> {
            battleGUI.selectAction(request, new com.jayemceekay.shadowedhearts.cobblemon.battles.CallActionResponse());
            playDownSound(mc.getSoundManager());
            return Unit.INSTANCE;
        };

        float startY = mc.getWindow().getGuiScaledHeight() - BattleGUI.OPTION_VERTICAL_OFFSET;
        int x = (int) (BattleGUI.OPTION_ROOT_X);
        int y = (int) (startY + 2 * (BattleOptionTile.OPTION_HEIGHT + BattleGUI.OPTION_HORIZONTAL_SPACING));

        tiles.add(new BattleOptionTile(
                battleGUI,
                x,
                y,
                callIcon,
                callText,
                callClick
        ));
    }

    @Redirect(
            method = "<init>(Lcom/cobblemon/mod/common/client/gui/battle/BattleGUI;Lcom/cobblemon/mod/common/client/battle/SingleActionRequest;)V",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/battle/subscreen/BattleGeneralActionSelection;addOption(ILnet/minecraft/network/chat/MutableComponent;Lnet/minecraft/resources/ResourceLocation;Lkotlin/jvm/functions/Function0;)V"),
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=ui.forfeit"))
    )
    private void shadowedhearts$redirectForfeitAddOption(BattleGeneralActionSelection instance, int rank, MutableComponent text, ResourceLocation texture, Function0<Unit> onClick) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        // Only show the Snag option when it is actually usable per our rules
        if (player != null && com.jayemceekay.shadowedhearts.snag.SnagBattleUtil.canShowSnagButton(player)) {
            MutableComponent snagText = Component.literal("Snag");
            ResourceLocation snagIcon = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/battle_menu_switch.png");
            Function0<Unit> snagClick = () -> {
                ShadowedHeartsNetwork.sendToServer(
                        new SnagArmPacket(true)
                );
                playDownSound(mc.getSoundManager());
                return Unit.INSTANCE;
            };
            // Insert our Snag option at the current rank
            this.addOption(rank, snagText, snagIcon, snagClick);
            rank++;
        }

        // Place the Forfeit option after it
        this.addOption(rank, text, texture, onClick);
    }
}
