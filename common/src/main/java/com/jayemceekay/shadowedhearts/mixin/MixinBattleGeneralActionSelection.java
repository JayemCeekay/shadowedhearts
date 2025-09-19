package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import dev.architectury.networking.NetworkManager;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Inject a "Snag" option just before the Forfeit button in Cobblemon's BattleGeneralActionSelection.
 *
 * We redirect the specific addOption invocation for Forfeit, insert our Snag option first (same rank),
 * then call the original addOption with rank + 1 so Forfeit is placed after it.
 */
@Mixin(value = BattleGeneralActionSelection.class, remap = false)
public abstract class MixinBattleGeneralActionSelection {

    // addOption(rank: Int, text: MutableComponent, texture: ResourceLocation, onClick: () -> Unit)
    @Shadow
    private void addOption(int rank, MutableComponent text, ResourceLocation texture, Function0<Unit> onClick) {}

    @Shadow
    public abstract void playDownSound(SoundManager soundManager);

    @Redirect(
            method = "<init>(Lcom/cobblemon/mod/common/client/gui/battle/BattleGUI;Lcom/cobblemon/mod/common/client/battle/SingleActionRequest;)V",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/battle/subscreen/BattleGeneralActionSelection;addOption(ILnet/minecraft/network/chat/MutableComponent;Lnet/minecraft/resources/ResourceLocation;Lkotlin/jvm/functions/Function0;)V"),
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=ui.forfeit"))
    )
    private void shadowedhearts$redirectForfeitAddOption(BattleGeneralActionSelection instance, int rank, MutableComponent text, ResourceLocation texture, Function0<Unit> onClick) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player != null && com.jayemceekay.shadowedhearts.snag.SnagCaps.hasMachineInOffhand(player)) {
            MutableComponent snagText = Component.literal("Snag");
            ResourceLocation snagIcon = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/battle_menu_switch.png");
            Function0<Unit> snagClick = () -> {
              NetworkManager.sendToServer(
                        new com.jayemceekay.shadowedhearts.network.payload.SnagArmC2S(true)
                );
                playDownSound(mc.getSoundManager());
                return Unit.INSTANCE;
            };
            // Insert our Snag option at the current rank
            this.addOption(rank, snagText, snagIcon, snagClick);
            // Place the Forfeit option after it
            this.addOption(rank + 1, text, texture, onClick);
        } else {
            // Default behavior: just add Forfeit
            this.addOption(rank, text, texture, onClick);
        }
    }
}
