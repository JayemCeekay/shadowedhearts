package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.client.gui.IrisWarningScreen;
import com.jayemceekay.shadowedhearts.client.util.IrisWarningUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 1001)
public class MixinMinecraft {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$onSetScreen(Screen screen, CallbackInfo ci) {
        if (ci.isCancelled()) return;

        Screen checkScreen = screen;
        if (checkScreen == null && Minecraft.getInstance().level == null) {
            checkScreen = new TitleScreen();
        }

        if (checkScreen instanceof TitleScreen && IrisWarningUtil.shouldShowWarning()) {
            Minecraft.getInstance().setScreen(new IrisWarningScreen(checkScreen));
            ci.cancel();
        }
    }
}
