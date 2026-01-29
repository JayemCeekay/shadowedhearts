package com.jayemceekay.shadowedhearts.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class IrisRestartScreen extends Screen {
    private MultiLineLabel message = MultiLineLabel.EMPTY;

    public IrisRestartScreen() {
        super(Component.literal("Restart Required"));
    }

    @Override
    protected void init() {
        super.init();
        Component msgText = Component.literal("The iris configuration has been updated. Please restart your client for the changes to take effect.");
        this.message = MultiLineLabel.create(this.font, msgText, this.width - 50);

        int buttonWidth = 200;
        int startY = Math.max(this.height / 2 + 20, this.message.getLineCount() * 9 + 60);

        this.addRenderableWidget(Button.builder(Component.literal("Return to Title Screen"), button -> {
            this.minecraft.setScreen(new TitleScreen());
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        this.message.renderCentered(guiGraphics, this.width / 2, 80);
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
