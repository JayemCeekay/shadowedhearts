package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Native, focusable scrollbar shared by trainer lists, forms, and inspectors. */
final class TrainerEditorScrollbar extends AbstractWidget {
    static final int WIDTH = 8;
    static final int GUTTER = 12;

    private final IntConsumer changed;
    private int contentSize;
    private int visibleSize = 1;
    private int position;
    private boolean draggingThumb;
    private double grabOffset;

    TrainerEditorScrollbar(String label, int x, int y, int height, IntConsumer changed) {
        super(x, y, WIDTH, height, Component.literal(label));
        this.changed = Objects.requireNonNull(changed);
        visible = false;
    }

    /** Synchronizes external state without notifying the owner or changing its active flag. */
    void setRange(int contentSize, int visibleSize, int position) {
        this.contentSize = Math.max(0, contentSize);
        this.visibleSize = Math.max(1, visibleSize);
        this.position = Math.clamp(position, 0, maximum());
        visible = maximum() > 0;
        if (!visible) draggingThumb = false;
    }

    int position() { return position; }
    int maximum() { return Math.max(0, contentSize - visibleSize); }

    boolean scrollBy(int amount) {
        if (!active || !visible) return false;
        return moveTo(Math.clamp((long) position + amount, 0L, maximum()));
    }

    private boolean moveTo(long requested) {
        int next = (int) Math.clamp(requested, 0L, maximum());
        if (next == position) return false;
        position = next;
        changed.accept(next);
        return true;
    }

    private TrainerEditorScrollMetrics metrics() {
        return TrainerEditorScrollMetrics.forRange(getHeight(), contentSize, visibleSize, position);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var geometry = metrics();
        int x = getX(), y = getY();
        graphics.fill(x, y, x + getWidth(), y + getHeight(), TrainerEditorTheme.INSET);
        graphics.fill(x + getWidth() - 1, y, x + getWidth(), y + getHeight(), TrainerEditorTheme.BORDER);
        int top = y + geometry.thumbOffset();
        int bottom = top + geometry.thumbSize();
        int color = !active ? TrainerEditorTheme.PANEL
                : draggingThumb || isHoveredOrFocused() ? TrainerEditorTheme.SELECTED : TrainerEditorTheme.BACKGROUND;
        graphics.fill(x + 1, top, x + getWidth() - 1, bottom, color);
        graphics.fill(x + 1, top, x + getWidth() - 1, top + 1, TrainerEditorTheme.BORDER);
        if (geometry.thumbSize() >= 10) {
            int middle = (top + bottom) / 2;
            graphics.fill(x + 2, middle - 1, x + getWidth() - 2, middle, TrainerEditorTheme.PANEL);
            graphics.fill(x + 2, middle + 1, x + getWidth() - 2, middle + 2, TrainerEditorTheme.PANEL);
        }
    }

    @Override public void onClick(double mouseX, double mouseY) {
        var geometry = metrics();
        double localY = mouseY - getY();
        draggingThumb = localY >= geometry.thumbOffset()
                && localY < geometry.thumbOffset() + geometry.thumbSize();
        if (draggingThumb) grabOffset = localY - geometry.thumbOffset();
        else scrollBy(localY < geometry.thumbOffset() ? -visibleSize : visibleSize);
    }

    @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (active && visible && draggingThumb) {
            moveTo(metrics().positionAt(mouseY - getY() - grabOffset));
        }
    }

    @Override public void onRelease(double mouseX, double mouseY) { draggingThumb = false; }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused()) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> scrollBy(-1);
            case GLFW.GLFW_KEY_DOWN -> scrollBy(1);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-visibleSize);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(visibleSize);
            case GLFW.GLFW_KEY_HOME -> moveTo(0);
            case GLFW.GLFW_KEY_END -> moveTo(maximum());
            default -> { return false; }
        }
        return true;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("screen.shadowedhearts.trainer_editor.scrollbar_narration", getMessage()));
        output.add(NarratedElementType.POSITION, tr("screen.shadowedhearts.trainer_editor.items_s_to_s_of_s", position + 1, Math.min(contentSize, (long) position + visibleSize), contentSize));
        if (active) output.add(NarratedElementType.USAGE,
                tr("screen.shadowedhearts.trainer_editor.use_up_and_down_to_scroll_page_up_and_page_down_to_move_a_page_or_home_and_end_t"));
    }
}
