package com.jayemceekay.shadowedhearts.client.gui;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.util.IrisWarningState;
import com.jayemceekay.shadowedhearts.client.util.IrisWarningUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public class IrisWarningScreen extends Screen {
    private final Screen parent;
    private MultiLineLabel message = MultiLineLabel.EMPTY;
    private Component errorMessage;

    public IrisWarningScreen(Screen parent) {
        super(Component.translatable("gui.shadowedhearts.iris_warning.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.message = MultiLineLabel.create(this.font, messageText(), this.width - 50);

        int buttonWidth = 200;
        int startY = Math.max(this.height / 2 + 20, this.message.getLineCount() * 9 + 60);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.iris_warning.configure"), button -> {
            try {
                IrisWarningUtil.enableUnknownShaders();
                this.minecraft.setScreen(new IrisRestartScreen());
            } catch (IOException exception) {
                showError("gui.shadowedhearts.iris_warning.update_failed", exception);
            }
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.iris_warning.okay"), button -> {
            onClose();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + 24, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.iris_warning.skip"), button -> {
            try {
                IrisWarningState.getInstance().setSkipWarning(true);
                onClose();
            } catch (IOException exception) {
                showError("gui.shadowedhearts.iris_warning.save_failed", exception);
            }
        }).bounds(this.width / 2 - buttonWidth / 2, startY + 48, buttonWidth, 20).build());
    }

    private void showError(String translationKey, IOException exception) {
        Shadowedhearts.LOGGER.error("Iris warning action failed ({})", translationKey, exception);
        errorMessage = Component.translatable(translationKey).withStyle(ChatFormatting.RED);
        rebuildWidgets();
    }

    private Component messageText() {
        var text = Component.translatable("gui.shadowedhearts.iris_warning.message",
                Component.literal("\"allowUnknownShaders=false\"").withStyle(ChatFormatting.BOLD),
                Component.literal("true").withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN));
        if (errorMessage != null) text.append("\n\n").append(errorMessage);
        return text;
    }

    @Override
    public Component getNarrationMessage() {
        return title.copy().append(". ").append(messageText());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        this.message.renderCentered(guiGraphics, this.width / 2, 60);
    }

    @Override
    public void onClose() {
        IrisWarningState.getInstance().dismissForSession();
        this.minecraft.setScreen(parent != null ? parent : new TitleScreen());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
