package com.jayemceekay.shadowedhearts.client.gui.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Edits a private copy. Done stages it in the parent option; the file's Save button persists it. */
final class StringListEditScreen extends Screen {
    private static final String PREFIX = "gui.shadowedhearts.config.list.";
    private static final int HELP_Y = 27, ROW_HEIGHT = 23, CONTROL = 20, GAP = 3;
    private final YaclSettingsScreen.FileScreen parent;
    private final StringListController controller;
    private final List<String> entries;
    private int left, contentWidth, rowTop, rows, page, pages;
    private int focusEntry = -1;
    private Component error;
    private Button reset;
    private EditBox focusAfterAction;
    private Component instructions = Component.empty();
    private List<FormattedCharSequence> helpLines = List.of();

    StringListEditScreen(YaclSettingsScreen.FileScreen parent, StringListController controller) {
        super(controller.option().name());
        this.parent = parent;
        this.controller = controller;
        this.entries = new ArrayList<>(controller.option().pendingValue());
    }

    @Override
    protected void init() {
        focusAfterAction = null;
        contentWidth = Math.min(width - 24, 620);
        left = (width - contentWidth) / 2;
        // Show the authored guidance without the generated default/range lines in the TOML comment.
        String help = controller.option().description().text().getString().strip().lines().findFirst().orElse("");
        instructions = Component.literal(help);
        var wrapped = help.isEmpty() ? List.<FormattedCharSequence>of() : font.split(instructions, contentWidth);
        int lineHeight = font.lineHeight + 1;
        int maxLines = Math.max(1, (height - HELP_Y - 6 - CONTROL - 9 - 47 - 2 * ROW_HEIGHT) / lineHeight);
        helpLines = wrapped.subList(0, Math.min(wrapped.size(), maxLines));
        int toolY = HELP_Y + helpLines.size() * lineHeight + 6;
        rowTop = toolY + CONTROL + 9;
        rows = Math.max(1, (height - rowTop - 47) / ROW_HEIGHT);
        pages = Math.max(1, Math.ceilDiv(entries.size(), rows));
        page = Math.min(page, pages - 1);

        int toolWidth = Math.min(95, (contentWidth - 70) / 3);
        button(Component.translatable(PREFIX + "add"), left, toolY, toolWidth, () -> {
            entries.add(controller.newElement().get());
            showEntry(entries.size() - 1);
        }).active = entries.size() < controller.spec().getSizeRange().getMax();
        reset = button(Component.translatable("controls.reset"), left + toolWidth + GAP, toolY, toolWidth, () -> {
            entries.clear();
            entries.addAll(controller.defaults());
            page = 0;
            error = null;
            rebuildWidgets();
        });
        reset.active = !entries.equals(controller.defaults());
        if (pages > 1) {
            int right = left + contentWidth;
            iconButton("<", Component.translatable("spectatorMenu.previous_page"), right - 2 * CONTROL - GAP, toolY,
                    () -> changePage(-1)).active = page > 0;
            iconButton(">", Component.translatable("spectatorMenu.next_page"), right - CONTROL, toolY,
                    () -> changePage(1)).active = page + 1 < pages;
        }
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= entries.size()) break;
            addRow(index, rowTop + row * ROW_HEIGHT);
        }
        int widthPerButton = Math.min(125, (contentWidth - GAP) / 2);
        button(CommonComponents.GUI_CANCEL, width / 2 - widthPerButton - GAP, height - 27, widthPerButton, this::onClose);
        button(CommonComponents.GUI_DONE, width / 2 + GAP, height - 27, widthPerButton, this::accept);
        focusEntry = -1;
    }

    private void addRow(int index, int y) {
        int textWidth = contentWidth - 3 * (CONTROL + GAP);
        Component entryLabel = title.copy().append(" " + (index + 1));
        EditBox field = addRenderableWidget(new EditBox(font, left, y, textWidth, CONTROL, entryLabel));
        field.setMaxLength(Integer.MAX_VALUE);
        field.setValue(entries.get(index));
        field.setTextColor(controller.spec().testElement(entries.get(index)) ? 0xE0E0E0 : 0xFF7777);
        field.setResponder(text -> {
            entries.set(index, text);
            field.setTextColor(controller.spec().testElement(text) ? 0xE0E0E0 : 0xFF7777);
            reset.active = !entries.equals(controller.defaults());
            error = null;
        });
        if (index == focusEntry) focusAfterAction = field;
        int x = left + textWidth + GAP;
        rowButton("↑", "up", index, x, y, () -> move(index, -1)).active = index > 0;
        rowButton("↓", "down", index, x + CONTROL + GAP, y, () -> move(index, 1)).active = index + 1 < entries.size();
        rowButton("−", "remove", index, x + 2 * (CONTROL + GAP), y, () -> {
            entries.remove(index);
            showEntry(Math.min(index, entries.size() - 1));
        }).active = entries.size() > controller.spec().getSizeRange().getMin();
    }

    private Button button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(label, ignored -> action.run()).bounds(x, y, w, CONTROL).build());
    }

    private Button rowButton(String symbol, String actionKey, int index, int x, int y, Runnable action) {
        return iconButton(symbol, Component.translatable(PREFIX + actionKey).append(" (" + (index + 1) + ")"), x, y, action);
    }

    private Button iconButton(String symbol, Component label, int x, int y, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(symbol), ignored -> action.run())
                .bounds(x, y, CONTROL, CONTROL).tooltip(Tooltip.create(label)).createNarration(ignored -> label.copy()).build());
    }

    private void changePage(int delta) {
        page += delta;
        rebuildWidgets();
    }

    private void showEntry(int index) {
        focusEntry = index;
        page = Math.max(0, index / rows);
        error = null;
        rebuildWidgets();
    }

    private void move(int index, int delta) {
        Collections.swap(entries, index, index + delta);
        showEntry(index + delta);
    }

    private void accept() {
        if (!parent.canEdit()) {
            parent.onClose();
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            if (!controller.spec().testElement(entries.get(index))) {
                showEntry(index);
                error = Component.translatable(PREFIX + "invalid", index + 1);
                return;
            }
        }
        if (!controller.spec().test(entries)) {
            error = Component.translatable("gui.shadowedhearts.config.invalid_value");
            return;
        }
        controller.option().requestSet(List.copyOf(entries));
        onClose();
    }

    @Override public void onClose() {
        if (parent.canEdit()) minecraft.setScreen(parent);
        else parent.onClose();
    }

    @Override public Component getNarrationMessage() {
        return title.copy().append("\n").append(instructions);
    }

    @Override public void tick() {
        if (!parent.canEdit()) parent.onClose();
        else if (focusAfterAction != null) {
            // The click which rebuilt the rows finishes dispatching before the new field takes focus.
            setFocused(focusAfterAction);
            focusAfterAction = null;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        super.renderBackground(graphics, mouseX, mouseY, tick);
        // Keep instructions and empty-list text readable over bright terrain.
        graphics.fill(0, 0, width, height, 0xA8181520);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        super.render(graphics, mouseX, mouseY, tick);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(title.getString(), contentWidth), width / 2, 13, 0xFFFFFF);
        for (int i = 0; i < helpLines.size(); i++)
            graphics.drawString(font, helpLines.get(i), left, HELP_Y + i * (font.lineHeight + 1), 0xD0D0D0);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("yacl.list.empty"), width / 2, rowTop + 5, 0xAAAAAA);
        }
        if (error != null) {
            graphics.drawCenteredString(font, error, width / 2, height - 42, 0xFF7777);
        } else if (pages > 1) {
            graphics.drawCenteredString(font, Component.translatable("book.pageIndicator", page + 1, pages),
                    width / 2, height - 42, 0xCCCCCC);
        }
    }
}
