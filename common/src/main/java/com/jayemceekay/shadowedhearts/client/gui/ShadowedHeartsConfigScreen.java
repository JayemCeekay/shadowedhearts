package com.jayemceekay.shadowedhearts.client.gui;

import com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorClient;
import com.jayemceekay.shadowedhearts.client.gui.preset.RulesPresetClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/** Shared entry point; the native loader configuration screen remains available in full. */
public final class ShadowedHeartsConfigScreen extends Screen {
    private final Screen parent;
    private final Function<Screen, Screen> generalConfig;

    public ShadowedHeartsConfigScreen(Screen parent, Function<Screen, Screen> generalConfig) {
        super(Component.translatable("gui.shadowedhearts.config.title"));
        this.parent = parent;
        this.generalConfig = generalConfig;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(260, width - 32);
        int x = (width - buttonWidth) / 2;
        int y = Math.max(45, height / 2 - 45);
        addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.config.rules_presets"),
                button -> RulesPresetClient.open(this)).bounds(x, y, buttonWidth, 20).build());
        Button trainers = addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.config.trainers"),
                button -> TrainerEditorClient.open(this, "")).bounds(x, y + 26, buttonWidth, 20).build());
        trainers.active = minecraft != null && minecraft.level != null && minecraft.getConnection() != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.config.all"),
                button -> minecraft.setScreen(generalConfig.apply(this))).bounds(x, y + 52, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, height - 30, buttonWidth, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xF21A1623);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xE5D5F5);
        Component hint = minecraft != null && minecraft.level != null
                ? Component.translatable("gui.shadowedhearts.config.presets_permission_hint")
                : Component.translatable("gui.shadowedhearts.config.presets_world_hint");
        var hintLines = font.split(hint, Math.max(1, width - 32));
        int hintY = Math.max(45, height / 2 - 45) + 80;
        for (int i = 0; i < Math.min(2, hintLines.size()); i++)
            graphics.drawCenteredString(font, hintLines.get(i), width / 2, hintY + i * 10, 0xBDB5C8);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
