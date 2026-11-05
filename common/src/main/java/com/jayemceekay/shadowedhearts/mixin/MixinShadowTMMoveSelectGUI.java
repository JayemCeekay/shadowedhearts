package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.interact.moveselect.MoveSelectConfiguration;
import com.cobblemon.mod.common.client.gui.interact.moveselect.MoveSelectGUI;
import com.cobblemon.mod.common.client.gui.tmmachine.TMMachineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Shadow TM slot chooser is a temporary child of the still-open TM machine menu. */
@Mixin(value = MoveSelectGUI.class, remap = false)
public abstract class MixinShadowTMMoveSelectGUI {
    @Unique
    private TMMachineScreen shadowedhearts$tmParent;

    @Inject(method = "<init>(Lcom/cobblemon/mod/common/client/gui/interact/moveselect/MoveSelectConfiguration;)V",
            at = @At("RETURN"))
    private void shadowedhearts$rememberTMMachine(MoveSelectConfiguration config, CallbackInfo ci) {
        if (!(config.getTitle().getContents() instanceof TranslatableContents text)
                || !text.getKey().equals("gui.shadowedhearts.shadow_tm.title")) return;
        var current = Minecraft.getInstance().screen;
        if (current instanceof TMMachineScreen parent) {
            shadowedhearts$tmParent = parent;
        } else if (current instanceof MoveSelectGUI) {
            // Rapid repeated requests replace the callback; keep its original machine as the parent.
            shadowedhearts$tmParent = ((MixinShadowTMMoveSelectGUI) (Object) current).shadowedhearts$tmParent;
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$returnToTMMachine(CallbackInfo ci) {
        TMMachineScreen parent = shadowedhearts$tmParent;
        Minecraft minecraft = Minecraft.getInstance();
        if (parent == null || minecraft.player == null || !minecraft.player.isAlive()
                || minecraft.player.containerMenu != parent.getMenu()) return;

        MoveSelectGUI chooser = (MoveSelectGUI) (Object) this;
        if (!chooser.getClosed()) {
            chooser.setClosed(true);
            chooser.getConfig().getOnCancel().invoke(chooser);
        }
        shadowedhearts$tmParent = null;
        minecraft.setScreen(parent);
        ci.cancel();
    }
}
