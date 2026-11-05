package com.jayemceekay.shadowedhearts.client.gui.trainer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Cobblemon inset skin around the native editor, retaining its text, cursor and selection metrics. */
final class TrainerEditorEditBox extends EditBox {
    private static final int PADDING = 4;
    private static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/npc/text_input_base.png");

    TrainerEditorEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setBordered(false);
        setTextColor(TrainerEditorTheme.TEXT);
        setTextColorUneditable(TrainerEditorTheme.MUTED);
    }

    @Override public int getInnerWidth() { return Math.max(1, getWidth() - PADDING * 2); }

    @Override public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX - PADDING, mouseY);
    }

    @Override public int getScreenX(int position) { return super.getScreenX(position) + PADDING; }

    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(BASE, getX(), getY(), getWidth(), getHeight(), 0, 0, 1, 1, 1, 1);
        int edge = isFocused() ? TrainerEditorTheme.SELECTED : TrainerEditorTheme.INSET;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, edge);
        graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), edge);
        graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), TrainerEditorTheme.BORDER);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(PADDING, (getHeight() - 8) / 2F, 0);
            super.renderWidget(graphics, mouseX - PADDING, mouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
    }
}
