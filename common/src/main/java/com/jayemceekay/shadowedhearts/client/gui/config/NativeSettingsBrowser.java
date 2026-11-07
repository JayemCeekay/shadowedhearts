package com.jayemceekay.shadowedhearts.client.gui.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** A searchable index of the same topics as the YACL screen, using only Minecraft widgets. */
public final class NativeSettingsBrowser extends Screen {
    @FunctionalInterface
    public interface Editor {
        Screen create(Screen parent, ModConfigSpec spec, Component title,
                      List<ModConfigSpec.ConfigValue<?>> values, BooleanSupplier canEdit);
    }

    private static final Component SEARCH = Component.translatable("gui.shadowedhearts.settings.search");
    private static final Component NO_RESULTS = Component.translatable("gui.shadowedhearts.settings.no_results");
    private static final int LIST_TOP = 54;
    private final Screen parent;
    private final ModConfigSpec spec;
    private final BooleanSupplier canEdit;
    private final Editor editor;
    private final Map<String, List<ModConfigSpec.ConfigValue<?>>> topics;
    private String query = "";
    private ResultList results;
    private int returnToRow = -1;

    public NativeSettingsBrowser(Screen parent, ModConfigSpec spec, Component title, BooleanSupplier canEdit, Editor editor) {
        super(title);
        this.parent = parent;
        this.spec = spec;
        this.canEdit = canEdit;
        this.editor = editor;
        this.topics = SettingsLayout.groups(spec);
    }

    @Override protected void init() {
        double scroll = results == null ? 0 : results.getScrollAmount();
        int rowWidth = rowWidth();
        EditBox search = addRenderableWidget(new EditBox(font, (width - rowWidth) / 2, 27, rowWidth, 20, SEARCH));
        search.setMaxLength(128);
        search.setValue(query);
        search.setSuggestion(query.isEmpty() ? SEARCH.getString() : null);
        search.setResponder(text -> {
            query = text;
            search.setSuggestion(text.isEmpty() ? SEARCH.getString() : null);
            returnToRow = -1;
            rebuildList(0);
        });
        rebuildList(scroll);
        int backWidth = Math.min(rowWidth, 160);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds((width - backWidth) / 2, height - 26, backWidth, 20).build());
        if (returnToRow >= 0 && returnToRow < results.children().size()) {
            Row row = results.children().get(returnToRow);
            setFocused(results);
            results.setFocused(row);
            row.setFocused(row.button);
        } else setInitialFocus(search);
    }

    private int rowWidth() { return Math.max(1, Math.min(520, width - 28)); }

    private void rebuildList(double scroll) {
        if (results != null) removeWidget(results);
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        List<Result> matches = new ArrayList<>();
        List<Result> contextualMatches = new ArrayList<>();
        topics.forEach((id, values) -> {
            Component topic = SettingsLayout.title(id);
            if (words.length == 0) {
                matches.add(new Result(topic, SettingsLayout.description(id), values));
                return;
            }
            String topicName = topic.getString().toLowerCase(Locale.ROOT);
            boolean wholeTopic = containsWords(topicName, words);
            for (var value : values) {
                Component name = SettingsLayout.label(value);
                String optionName = name.getString().toLowerCase(Locale.ROOT);
                if (wholeTopic || containsWords(optionName, words)) {
                    matches.add(new Result(name, topic, List.of(value)));
                } else if (containsWords(topicName + " " + optionName, words)) {
                    contextualMatches.add(new Result(name, topic, List.of(value)));
                }
            }
        });
        // An exact option match such as "Enable Hyper Mode" takes precedence over words
        // scattered across a different option and its topic (e.g. "Enable Reverse Mode").
        if (matches.isEmpty()) matches.addAll(contextualMatches);
        int textWidth = Math.max(1, rowWidth() - 16);
        int lineCount = 2;
        for (var match : matches) {
            lineCount = Math.max(lineCount, font.split(match.name, textWidth).size() + font.split(match.context, textWidth).size());
        }
        results = addRenderableWidget(new ResultList(lineCount * font.lineHeight + 14));
        for (var match : matches) results.append(match, textWidth, words.length != 0);
        results.setScrollAmount(scroll);
    }

    private static boolean containsWords(String text, String[] words) {
        return Arrays.stream(words).allMatch(text::contains);
    }

    private record Result(Component name, Component context, List<ModConfigSpec.ConfigValue<?>> values) {}

    private final class ResultList extends ContainerObjectSelectionList<Row> {
        ResultList(int rowHeight) {
            super(NativeSettingsBrowser.this.minecraft, NativeSettingsBrowser.this.width,
                    Math.max(1, NativeSettingsBrowser.this.height - LIST_TOP - 33), LIST_TOP, rowHeight);
        }

        void append(Result match, int textWidth, boolean searchResult) {
            addEntry(new Row(match, textWidth, itemHeight - 4, searchResult));
        }

        @Override public int getRowWidth() { return rowWidth(); }
        @Override protected int getScrollbarPosition() { return getRowRight() + 3; }
    }

    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Button button;

        Row(Result result, int textWidth, int height, boolean searchResult) {
            List<FormattedCharSequence> name = font.split(result.name, textWidth);
            List<FormattedCharSequence> context = font.split(result.context, textWidth);
            button = new Button(0, 0, rowWidth() - 4, height, result.name, ignored -> {
                if (!canEdit.getAsBoolean()) { onClose(); return; }
                returnToRow = results.children().indexOf(this);
                minecraft.setScreen(editor.create(NativeSettingsBrowser.this, spec, result.name, result.values, canEdit));
            }, narration -> narration.get().append(". ").append(result.context)) {
                @Override public void renderString(GuiGraphics graphics, Font font, int color) {
                    int x = getX() + 6;
                    int y = getY() + (getHeight() - (name.size() + context.size()) * font.lineHeight - 2) / 2;
                    for (var line : name) { graphics.drawString(font, line, x, y, color); y += font.lineHeight; }
                    y += 2;
                    for (var line : context) { graphics.drawString(font, line, x, y, 0xCCCCCC); y += font.lineHeight; }
                }
            };
            if (searchResult) button.setTooltip(Tooltip.create(SettingsLayout.optionDescription(result.values.getFirst())));
        }

        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            button.setPosition(x, y);
            button.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override public List<? extends GuiEventListener> children() { return List.of(button); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(button); }
    }

    @Override public void tick() { if (!canEdit.getAsBoolean()) onClose(); }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        if (results.children().isEmpty()) graphics.drawCenteredString(font, NO_RESULTS, width / 2, LIST_TOP + 10, 0xCCCCCC);
    }
}
