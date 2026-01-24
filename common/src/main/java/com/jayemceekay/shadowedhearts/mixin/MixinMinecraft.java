package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.client.gui.IrisWarningScreen;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    private static boolean shadowedhearts$checkedIris = false;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof TitleScreen && !shadowedhearts$checkedIris) {
            shadowedhearts$checkedIris = true;
            if (ShadowedHeartsConfigs.getInstance().getClientConfig().skipIrisWarning()) {
                return;
            }

            if (Platform.isModLoaded("iris")) {
                Path configDir = Platform.getConfigFolder();
                Path irisConfig = configDir.resolve("iris.properties");
                if (Files.exists(irisConfig)) {
                    try {
                        List<String> lines = Files.readAllLines(irisConfig);
                        boolean allowUnknownShaders = false;
                        for (String line : lines) {
                            if (line.trim().equals("allowUnknownShaders=true")) {
                                allowUnknownShaders = true;
                                break;
                            }
                        }

                        if (!allowUnknownShaders) {
                            Minecraft.getInstance().setScreen(new IrisWarningScreen(screen));
                            ci.cancel();
                        }
                    } catch (IOException ignored) {
                    }
                } else {
                    // iris.properties doesn't exist, but iris is installed.
                    // Iris usually creates it on first run. If it's missing, we might want to warn anyway or skip.
                    // The requirement says "whenever iris.properties is in the config folder/ iris is installed"
                    Minecraft.getInstance().setScreen(new IrisWarningScreen(screen));
                    ci.cancel();
                }
            }
        }
    }
}
