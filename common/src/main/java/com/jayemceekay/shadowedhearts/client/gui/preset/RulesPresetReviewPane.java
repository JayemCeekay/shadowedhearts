package com.jayemceekay.shadowedhearts.client.gui.preset;

import com.jayemceekay.shadowedhearts.client.gui.BoundedGuiText;
import com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Focusable text review with mouse, keyboard, and narrated access to every wrapped line. */
final class RulesPresetReviewPane extends AbstractWidget {
    private static final int LINE_HEIGHT = 11;
    private final Font font;
    private List<Component> lines = List.of();
    private int offset;
    private boolean dragging;
    private double dragOffset;

    RulesPresetReviewPane(Font font, int x, int y, int width, int height) {
        super(x, y, width, height, TrainerEditorTheme.styled(Component.translatable("screen.shadowedhearts.rules_presets.review")));
        this.font = font;
    }

    void setContent(List<Component> content, boolean reset) {
        List<Component> wrapped = new ArrayList<>();
        for (Component line : content) {
            if (line.getString().isBlank()) wrapped.add(Component.empty());
            else wrapped.addAll(BoundedGuiText.wrap(TrainerEditorTheme.styled(line), Math.max(1, getWidth() - 18), font));
        }
        lines = List.copyOf(wrapped);
        offset = reset ? 0 : Math.clamp(offset, 0, maximum());
    }

    private int visibleLines() { return Math.max(1, (getHeight() - 8) / LINE_HEIGHT); }
    private int right() { return getX() + getWidth(); }
    private int bottom() { return getY() + getHeight(); }
    private int maximum() { return Math.max(0, lines.size() - visibleLines()); }
    private int trackHeight() { return Math.max(1, getHeight() - 4); }
    private int thumbHeight() {
        return Math.min(trackHeight(), Math.max(12, (int) ((long) trackHeight() * visibleLines() / Math.max(1, lines.size()))));
    }
    private int thumbOffset() {
        return maximum() == 0 ? 0 : (int) ((long) offset * (trackHeight() - thumbHeight()) / maximum());
    }
    private void scroll(int amount) { offset = Math.clamp(offset + amount, 0, maximum()); }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        TrainerEditorTheme.panel(graphics, getX(), getY(), getWidth(), getHeight());
        if (isFocused()) graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), TrainerEditorTheme.SELECTED);
        int textLeft = Math.min(right(), getX() + 4);
        int textTop = Math.min(bottom(), getY() + 3);
        graphics.enableScissor(textLeft, textTop, Math.max(textLeft, right() - 12), Math.max(textTop, bottom() - 3));
        for (int i = offset; i < Math.min(lines.size(), offset + visibleLines()); i++) {
            graphics.drawString(font, lines.get(i), getX() + 4, getY() + 4 + (i - offset) * LINE_HEIGHT, TrainerEditorTheme.TEXT, true);
        }
        graphics.disableScissor();
        if (maximum() > 0 && getWidth() >= 12 && getHeight() >= 5) {
            int x = right() - 10;
            int y = getY() + 2 + thumbOffset();
            int thumbBottom = y + thumbHeight();
            graphics.fill(x, getY() + 2, x + 8, bottom() - 2, TrainerEditorTheme.INSET);
            graphics.fill(x + 7, getY() + 2, x + 8, bottom() - 2, TrainerEditorTheme.BORDER);
            int color = !active ? TrainerEditorTheme.PANEL
                    : dragging || isHoveredOrFocused() ? TrainerEditorTheme.SELECTED : TrainerEditorTheme.BACKGROUND;
            graphics.fill(x + 1, y, x + 7, thumbBottom, color);
            graphics.fill(x + 1, y, x + 7, y + 1, TrainerEditorTheme.BORDER);
            if (thumbHeight() >= 10) {
                int middle = (y + thumbBottom) / 2;
                graphics.fill(x + 2, middle - 1, x + 6, middle, TrainerEditorTheme.PANEL);
                graphics.fill(x + 2, middle + 1, x + 6, middle + 2, TrainerEditorTheme.PANEL);
            }
        }
    }

    @Override public void onClick(double mouseX, double mouseY) {
        if (mouseX < right() - 11 || maximum() == 0) return;
        double local = mouseY - getY() - 2;
        dragging = local >= thumbOffset() && local < thumbOffset() + thumbHeight();
        if (dragging) dragOffset = local - thumbOffset();
        else scroll(local < thumbOffset() ? -visibleLines() : visibleLines());
    }

    @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        int travel = trackHeight() - thumbHeight();
        if (dragging && travel > 0) {
            double fraction = (mouseY - getY() - 2 - dragOffset) / travel;
            offset = Math.clamp((int) Math.round(fraction * maximum()), 0, maximum());
        }
    }

    @Override public void onRelease(double mouseX, double mouseY) { dragging = false; }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!isMouseOver(mouseX, mouseY) || vertical == 0) return false;
        scroll(vertical > 0 ? -3 : 3);
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> scroll(-1);
            case GLFW.GLFW_KEY_DOWN -> scroll(1);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll(-visibleLines());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll(visibleLines());
            case GLFW.GLFW_KEY_HOME -> offset = 0;
            case GLFW.GLFW_KEY_END -> offset = maximum();
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        var visible = Component.empty().append(getMessage()).append(". ");
        for (int i = offset; i < Math.min(lines.size(), offset + visibleLines()); i++) visible.append(lines.get(i)).append(". ");
        output.add(NarratedElementType.TITLE, visible);
        output.add(NarratedElementType.USAGE, Component.translatable("screen.shadowedhearts.rules_presets.scroll_help"));
    }
}
