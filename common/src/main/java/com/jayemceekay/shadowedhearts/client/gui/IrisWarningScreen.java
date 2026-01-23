package com.jayemceekay.shadowedhearts.client.gui;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class IrisWarningScreen extends Screen {
    private final Screen parent;
    private MultiLineLabel message = MultiLineLabel.EMPTY;

    public IrisWarningScreen(Screen parent) {
        super(Component.literal("Cobblemon: Shadowed Hearts Iris Shader Warning"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        Component msgText = Component.literal("Iris Shaders is installed. Only by changing ").append(Component.literal("\"allowUnknownShaders=false\"").withStyle(ChatFormatting.BOLD)).append(" to ").append(Component.literal("true").withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN)).append(" in iris.properties will allow shadow pokemon auras to be seen when Iris shaders are enabled.");
        this.message = MultiLineLabel.create(this.font, msgText, this.width - 50);

        int buttonWidth = 200;
        int startY = Math.max(this.height / 2 + 20, this.message.getLineCount() * 9 + 60);

        this.addRenderableWidget(Button.builder(Component.literal("Do this for me"), button -> {
            doThisForMe();
            this.minecraft.setScreen(new TitleScreen());
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Okay"), button -> {
            this.minecraft.setScreen(new TitleScreen());
        }).bounds(this.width / 2 - buttonWidth / 2, startY + 24, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Don't ask me again"), button -> {
            ShadowedHeartsConfigs.getInstance().getClientConfig().setSkipIrisWarning(true);
            ShadowedHeartsConfigs.getInstance().getShadowConfig().setSkipIrisWarning(true);
            this.minecraft.setScreen(new TitleScreen());
        }).bounds(this.width / 2 - buttonWidth / 2, startY + 48, buttonWidth, 20).build());
    }

    private void doThisForMe() {
        Path configDir = Platform.getConfigFolder();
        Path irisConfig = configDir.resolve("iris.properties");
        if (Files.exists(irisConfig)) {
            try {
                List<String> lines = Files.readAllLines(irisConfig);
                List<String> updatedLines = lines.stream()
                        .map(line -> line.replace("allowUnknownShaders=false", "allowUnknownShaders=true"))
                        .collect(Collectors.toList());
                Files.write(irisConfig, updatedLines);
            } catch (IOException e) {
                Shadowedhearts.LOGGER.error("Failed to update iris.properties", e);
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        this.message.renderCentered(guiGraphics, this.width / 2, 60);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(new TitleScreen());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
