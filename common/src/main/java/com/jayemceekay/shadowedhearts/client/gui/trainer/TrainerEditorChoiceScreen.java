package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Searchable value picker. Choosing a value changes only the unsaved parent draft. */
final class TrainerEditorChoiceScreen extends Screen {
    record Choice(String value, String label) {}
    private final TrainerEditorScreen parent;
    private final List<Choice> choices;
    private final Consumer<String> selected;
    private String search = "";
    private int offset;
    private int visibleCount = 1;
    private int filteredCount;
    private int frameLeft, frameTop, frameWidth, frameHeight;
    private int listLeft, listRight, listTop, listBottom, footerY;
    private EditBox filter;
    private TrainerEditorScrollbar scrollbar;
    private boolean rebuildPending;

    TrainerEditorChoiceScreen(TrainerEditorScreen parent, String title, List<Choice> choices, Consumer<String> selected) {
        super(TrainerEditorTheme.styled(title));
        this.parent = parent;
        this.choices = List.copyOf(choices);
        this.selected = selected;
    }

    @Override protected void init() {
        GuiEventListener focused = getFocused();
        boolean dragging = isDragging();
        boolean firstInit = filter == null;
        clearWidgets();
        frameWidth = Math.max(1, Math.min(480, width - 16));
        frameHeight = Math.max(1, Math.min(300, height - 16));
        frameLeft = (width - frameWidth) / 2;
        frameTop = (height - frameHeight) / 2;
        int left = frameLeft + 12;
        int areaWidth = Math.max(20, frameWidth - 24);
        listTop = frameTop + 51;
        footerY = frameTop + frameHeight - 24;
        if (filter == null) {
            filter = TrainerEditorTheme.textBox(font, left, frameTop + 28, areaWidth, TrainerEditorTheme.CONTROL_HEIGHT, Component.literal(tr("screen.shadowedhearts.trainer_editor.search_choices")));
            filter.setMaxLength(256);
            filter.setHint(Component.literal(tr("screen.shadowedhearts.trainer_editor.search_choices")));
            filter.setValue(search);
            filter.setResponder(value -> { search = value; offset = 0; rebuildPicker(); });
        } else {
            // Retain the editor itself so filtering does not move its cursor or lose a selection.
            filter.setX(left);
            filter.setY(frameTop + 28);
            filter.setWidth(areaWidth);
        }
        addRenderableWidget(filter);
        List<Choice> filtered = new ArrayList<>();
        String query = search.toLowerCase(Locale.ROOT);
        for (Choice choice : choices) if (choice.label().toLowerCase(Locale.ROOT).contains(query) || choice.value().toLowerCase(Locale.ROOT).contains(query)) filtered.add(choice);
        filteredCount = filtered.size();
        visibleCount = Math.max(1, (footerY - listTop - 6) / TrainerEditorTheme.ROW_HEIGHT);
        int listHeight = visibleCount * TrainerEditorTheme.ROW_HEIGHT - 4;
        listLeft = left;
        listRight = left + areaWidth;
        listBottom = listTop + listHeight;
        if (scrollbar == null) scrollbar = new TrainerEditorScrollbar(tr("screen.shadowedhearts.trainer_editor.choices"), listRight - TrainerEditorScrollbar.WIDTH, listTop, listHeight,
                value -> { offset = value; rebuildPicker(); });
        scrollbar.setX(listRight - TrainerEditorScrollbar.WIDTH);
        scrollbar.setY(listTop);
        scrollbar.setHeight(listHeight);
        scrollbar.setRange(filteredCount, visibleCount, offset);
        offset = scrollbar.position();
        int choiceWidth = areaWidth - (scrollbar.maximum() > 0 ? TrainerEditorScrollbar.GUTTER : 0);
        for (int index = offset; index < Math.min(filteredCount, offset + visibleCount); index++) {
            Choice choice = filtered.get(index);
            int y = listTop + (index - offset) * TrainerEditorTheme.ROW_HEIGHT;
            var button = addRenderableWidget(TrainerEditorTheme.listButton(choice.label(), left, y, choiceWidth, () -> {
                selected.accept(choice.value());
                minecraft.setScreen(parent);
            }));
            String detail = choice.label().equals(choice.value()) || choice.value().isBlank()
                    ? choice.label() : choice.label() + "\n" + choice.value();
            button.setTooltip(Tooltip.create(Component.literal(detail)));
        }
        addRenderableWidget(scrollbar);
        var previous = addRenderableWidget(TrainerEditorTheme.button("<", left, footerY, 22, TrainerEditorTheme.CONTROL_HEIGHT, () -> scrollbar.scrollBy(-visibleCount)));
        previous.active = offset > 0;
        previous.setTooltip(Tooltip.create(Component.literal(tr("screen.shadowedhearts.trainer_editor.previous_choices"))));
        var next = addRenderableWidget(TrainerEditorTheme.button(">", left + 26, footerY, 22, TrainerEditorTheme.CONTROL_HEIGHT, () -> scrollbar.scrollBy(visibleCount)));
        next.active = offset < scrollbar.maximum();
        next.setTooltip(Tooltip.create(Component.literal(tr("screen.shadowedhearts.trainer_editor.next_choices"))));
        addRenderableWidget(TrainerEditorTheme.button(tr("screen.shadowedhearts.trainer_editor.cancel"), left + areaWidth - 62, footerY, 62, TrainerEditorTheme.CONTROL_HEIGHT, this::onClose));
        if (firstInit) setInitialFocus(filter);
        else if (focused == filter || focused == scrollbar && scrollbar.visible) {
            setFocused(focused);
            // A thumb drag spans tick-driven list rebuilds; keep its captured widget and gesture.
            setDragging(dragging);
        }
    }

    private void rebuildPicker() {
        rebuildPending = true;
    }

    @Override public void tick() {
        if (rebuildPending) {
            rebuildPending = false;
            init();
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (scrollbar != null && scrollbar.maximum() > 0 && mouseX >= listLeft && mouseX < listRight
                && mouseY >= listTop && mouseY <= listBottom && vertical != 0) {
            scrollbar.scrollBy(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollbar != null) scrollbar.onRelease(mouseX, mouseY);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TrainerEditorTheme.frame(graphics, frameLeft, frameTop, frameWidth, frameHeight);
        TrainerEditorTheme.panel(graphics, listLeft - 2, listTop - 3, listRight - listLeft + 4, listBottom - listTop + 6);
    }

    @Override public void renderBlurredBackground(float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        var heading = TrainerEditorTheme.styled(font.substrByWidth(title, frameWidth - 24).getString());
        graphics.drawString(font, heading, frameLeft + 12, frameTop + 10, TrainerEditorTheme.TEXT, true);
        String range = filteredCount == 0 ? tr("screen.shadowedhearts.trainer_editor.0_choices") : tr("screen.shadowedhearts.trainer_editor.s_s_of_s", offset + 1, Math.min(filteredCount, offset + visibleCount), filteredCount);
        graphics.drawCenteredString(font, TrainerEditorTheme.styled(range), frameLeft + frameWidth / 2, footerY + 4, TrainerEditorTheme.MUTED);
        if (filteredCount == 0) graphics.drawCenteredString(font, TrainerEditorTheme.styled(tr("screen.shadowedhearts.trainer_editor.no_choices_match_your_search")), frameLeft + frameWidth / 2, listTop + 8, TrainerEditorTheme.MUTED);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
