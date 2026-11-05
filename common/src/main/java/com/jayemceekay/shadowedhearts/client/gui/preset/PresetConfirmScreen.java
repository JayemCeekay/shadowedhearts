package com.jayemceekay.shadowedhearts.client.gui.preset;

import com.jayemceekay.shadowedhearts.client.gui.BoundedGuiText;
import com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Confirmation dialog sharing the preset workspace's Cobblemon controls and typography. */
class PresetConfirmScreen extends Screen {
    private static final int PADDING = 8;
    private static final int GAP = 6;
    private final Consumer<Boolean> callback;
    private final Component message, yesLabel, noLabel;
    private int frameX, frameY, frameWidth, frameHeight;
    private boolean resolved;

    PresetConfirmScreen(Consumer<Boolean> callback, Component title, Component message) {
        this(callback, title, message, Component.translatable("gui.yes"), Component.translatable("gui.no"));
    }

    PresetConfirmScreen(Consumer<Boolean> callback, Component title, Component message,
                        Component yesLabel, Component noLabel) {
        super(title);
        this.callback = Objects.requireNonNull(callback);
        this.message = Objects.requireNonNull(message);
        this.yesLabel = Objects.requireNonNull(yesLabel);
        this.noLabel = Objects.requireNonNull(noLabel);
    }

    @Override protected void init() {
        clearWidgets();
        frameWidth = Math.max(1, Math.min(400, width - 16));
        frameHeight = Math.max(1, Math.min(200, height - 16));
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;
        int contentX = frameX + PADDING;
        int contentWidth = Math.max(1, frameWidth - PADDING * 2);
        int buttonsY = frameY + frameHeight - PADDING - TrainerEditorTheme.CONTROL_HEIGHT;
        int reviewY = frameY + 28;
        RulesPresetReviewPane review = addRenderableWidget(new RulesPresetReviewPane(font, contentX,
                reviewY, contentWidth, Math.max(1, buttonsY - GAP - reviewY)));
        review.setMessage(title);
        review.setContent(List.of(message), true);
        int yesWidth = Math.max(1, (contentWidth - GAP) / 2);
        int noX = contentX + yesWidth + GAP;
        addButton(yesLabel, contentX, buttonsY, yesWidth, true);
        Button cancel = addButton(noLabel, noX, buttonsY, Math.max(1, contentX + contentWidth - noX), false);
        setInitialFocus(cancel);
    }

    private Button addButton(Component label, int x, int y, int width, boolean decision) {
        Button button = TrainerEditorTheme.button(label.getString(), x, y, width,
                TrainerEditorTheme.CONTROL_HEIGHT, () -> resolve(decision));
        button.setMessage(TrainerEditorTheme.styled(label));
        return addRenderableWidget(button);
    }

    private void resolve(boolean decision) {
        if (resolved) return;
        resolved = true;
        callback.accept(decision);
    }

    @Override public void onClose() { resolve(false); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBlurredBackground(float partialTick) { }

    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(message); }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB010171C);
        TrainerEditorTheme.frame(graphics, frameX, frameY, frameWidth, frameHeight);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component heading = BoundedGuiText.fit(TrainerEditorTheme.styled(title),
                Math.max(1, frameWidth - PADDING * 2), font);
        graphics.drawString(font, heading, frameX + PADDING, frameY + 10, TrainerEditorTheme.TEXT, true);
    }
}
