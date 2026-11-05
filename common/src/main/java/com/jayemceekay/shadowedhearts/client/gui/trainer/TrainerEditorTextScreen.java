package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Optional UUID lookup; normal NPC discovery uses the loaded-entity browser. */
final class TrainerEditorTextScreen extends Screen {
    private final Screen parent;
    private final String help;
    private final Consumer<String> accepted;
    private String value;
    private int left, areaWidth, helpY, frameTop, frameHeight;
    TrainerEditorTextScreen(Screen parent, String title, String help, String value, Consumer<String> accepted) {
        super(TrainerEditorTheme.styled(title)); this.parent = parent; this.help = help; this.value = value; this.accepted = accepted;
    }
    @Override protected void init() {
        clearWidgets();
        areaWidth = Math.max(20, Math.min(336, width - 40));
        left = (width - areaWidth) / 2;
        int helpHeight = font.split(TrainerEditorTheme.styled(help), areaWidth).size() * 11;
        frameHeight = helpHeight + 87;
        frameTop = Math.max(8, (height - frameHeight) / 2);
        helpY = frameTop + 29;
        int inputY = helpY + helpHeight + 7;
        EditBox box = TrainerEditorTheme.textBox(font, left, inputY, areaWidth, TrainerEditorTheme.CONTROL_HEIGHT, title);
        box.setMaxLength(36); box.setValue(value); box.setResponder(v -> value = v);
        addRenderableWidget(box); setInitialFocus(box);
        int actionY = inputY + TrainerEditorTheme.CONTROL_HEIGHT + 10;
        addRenderableWidget(TrainerEditorTheme.button(tr("screen.shadowedhearts.trainer_editor.cancel"), left, actionY, 62, TrainerEditorTheme.CONTROL_HEIGHT, this::onClose));
        addRenderableWidget(TrainerEditorTheme.button(tr("screen.shadowedhearts.trainer_editor.load_npc"), left + areaWidth - 78, actionY, 78, TrainerEditorTheme.CONTROL_HEIGHT, () -> {
            minecraft.setScreen(parent); accepted.accept(value);
        }));
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TrainerEditorTheme.frame(graphics, left - 12, frameTop, areaWidth + 24, frameHeight);
    }
    @Override public void renderBlurredBackground(float partialTick) {}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, TrainerEditorTheme.styled(font.substrByWidth(title, areaWidth).getString()), left, frameTop + 10, TrainerEditorTheme.TEXT, true);
        int y = helpY;
        for (var line : font.split(TrainerEditorTheme.styled(help), areaWidth)) {
            graphics.drawString(font, line, left, y, TrainerEditorTheme.MUTED, true);
            y += 11;
        }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
