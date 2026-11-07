package com.jayemceekay.shadowedhearts.client.gui;

import com.jayemceekay.shadowedhearts.client.gui.config.NativeSettingsBrowser;
import com.jayemceekay.shadowedhearts.client.gui.config.NativeTopicSettingsScreen;
import com.jayemceekay.shadowedhearts.client.gui.config.YaclSettingsScreen;
import com.jayemceekay.shadowedhearts.client.gui.preset.RulesPresetClient;
import com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorClient;
import com.jayemceekay.shadowedhearts.config.ClientConfig;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ServerConfig;
import com.jayemceekay.shadowedhearts.integration.yacl.YaclCompatibility;
import dev.architectury.platform.Platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.BooleanSupplier;

/** Shared entry point for settings, rules presets, and trainer editing. */
public final class ShadowedHeartsConfigScreen extends Screen {
    private final Screen parent;
    private int hintY;

    public ShadowedHeartsConfigScreen(Screen parent) {
        super(Component.translatable("gui.shadowedhearts.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(260, width - 32);
        int x = (width - buttonWidth) / 2;
        int y = Math.max(38, height / 2 - 60);
        addSettingsButton("client_preferences", ClientConfig.SPEC, ClientConfig.SPEC::isLoaded, x, y, buttonWidth);
        y += 26;
        int halfWidth = (buttonWidth - 4) / 2;
        IntegratedServer world = minecraft.getSingleplayerServer();
        addSettingsButton("gameplay_settings", ModConfig.SPEC, () -> canEdit(world, ModConfig.SPEC), x, y, halfWidth);
        addSettingsButton("world_settings", ServerConfig.SPEC, () -> canEdit(world, ServerConfig.SPEC),
                x + halfWidth + 4, y, buttonWidth - halfWidth - 4);
        y += 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.config.rules_presets"),
                button -> RulesPresetClient.open(this)).bounds(x, y, buttonWidth, 20).build());
        Button trainers = addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.config.trainers"),
                button -> TrainerEditorClient.open(this, "")).bounds(x, y + 26, buttonWidth, 20).build());
        trainers.active = minecraft != null && minecraft.level != null && minecraft.getConnection() != null;
        hintY = y + 54;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, height - 30, buttonWidth, 20).build());
    }

    private void addSettingsButton(String key, ModConfigSpec spec, BooleanSupplier canEdit, int x, int y, int width) {
        Component label = Component.translatable("gui.shadowedhearts.config." + key);
        Button button = addRenderableWidget(Button.builder(label, ignored -> {
            if (!canEdit.getAsBoolean()) return;
            boolean hasYacl = Platform.getOptionalMod(YaclCompatibility.MOD_ID)
                    .map(mod -> YaclCompatibility.supportsEditor(mod.getVersion())).orElse(false);
            Screen editor = hasYacl ? YaclSettingsScreen.create(this, spec, label, canEdit)
                    : new NativeSettingsBrowser(this, spec, label, canEdit, NativeTopicSettingsScreen::create);
            minecraft.setScreen(editor);
        }).bounds(x, y, width, 20).build());
        button.active = canEdit.getAsBoolean();
        if (!button.active && spec != ClientConfig.SPEC) {
            button.setTooltip(Tooltip.create(Component.translatable("gui.shadowedhearts.config.private_world_hint")));
        }
    }

    private boolean canEdit(IntegratedServer world, ModConfigSpec spec) {
        return world != null && minecraft.getSingleplayerServer() == world && minecraft.level != null
                && !world.isPublished() && spec.isLoaded();
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
        for (int i = 0; i < Math.min(2, hintLines.size()); i++)
            graphics.drawCenteredString(font, hintLines.get(i), width / 2, hintY + i * 10, 0xBDB5C8);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
