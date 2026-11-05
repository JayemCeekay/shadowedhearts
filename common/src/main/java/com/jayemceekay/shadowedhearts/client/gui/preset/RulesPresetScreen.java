package com.jayemceekay.shadowedhearts.client.gui.preset;

import com.google.gson.JsonObject;
import com.jayemceekay.shadowedhearts.client.gui.BoundedGuiText;
import com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorTheme;
import com.jayemceekay.shadowedhearts.config.preset.RulesPresetCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Catalog browsing never mutates settings; only Apply sends the reviewed server revision. */
final class RulesPresetScreen extends Screen {
    private final Screen parent;
    private final RulesPresetSession session;
    private RulesPresetLayout layout;
    private Button selector;
    private final List<Button> presetButtons = new ArrayList<>();
    private RulesPresetReviewPane review, overview;
    private EditBox filter;
    private Button showChanges, previewButton, applyButton;
    private Button snapshotsTab;
    private Screen child;
    private boolean initialized;
    private boolean rebuildPresetList;
    private boolean changesOnly = true;
    private String search = "";
    private long requestSentAt;

    RulesPresetScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        this.session = new RulesPresetSession(RulesPresetCatalog.all().getFirst().id());
    }

    @Override protected void init() {
        clearWidgets();
        presetButtons.clear();
        selector = null;
        review = overview = null;
        layout = RulesPresetLayout.forScreen(width, height);
        if (layout.showsPresetList()) {
            int index = 0;
            for (var preset : RulesPresetCatalog.all()) {
                Button entry = addRenderableWidget(TrainerEditorTheme.rosterButton(
                        Component.translatable(preset.titleKey()).getString(), text("subtitle." + preset.id()).getString(),
                        layout.contentX() + 2, layout.presetY(index++), layout.railWidth() - 4,
                        preset.id().equals(session.presetId()), () -> selectPreset(preset)));
                entry.setTooltip(Tooltip.create(Component.translatable(preset.titleKey()).append("\n")
                        .append(Component.translatable(preset.descriptionKey())).append("\n").append(text("select_help"))));
                presetButtons.add(entry);
            }
        } else {
            selector = addRenderableWidget(TrainerEditorTheme.cycleButton(
                    CycleButton.<RulesPresetCatalog.Preset>builder(p -> Component.translatable(p.titleKey()))
                            .withValues(RulesPresetCatalog.all()).withInitialValue(selected()).displayOnlyValue()
                            .create(layout.contentX(), layout.selectorY(), layout.contentWidth(), TrainerEditorTheme.CONTROL_HEIGHT,
                                    text("preset"), (button, preset) -> selectPreset(preset))));
            selector.setTooltip(Tooltip.create(text("select_help")));
        }
        int x = layout.reviewX();
        int contentWidth = layout.reviewWidth();
        int toggleWidth = Math.min(contentWidth / 2, Math.max(
                font.width(TrainerEditorTheme.styled(text("changes_only"))),
                font.width(TrainerEditorTheme.styled(text("all_settings")))) + 12);
        filter = addRenderableWidget(TrainerEditorTheme.textBox(font, x, layout.filterY(),
                contentWidth - toggleWidth - 6, TrainerEditorTheme.CONTROL_HEIGHT, text("filter")));
        filter.setMaxLength(128);
        filter.setHint(TrainerEditorTheme.styled(text("filter")));
        filter.setValue(search);
        filter.setResponder(value -> { search = value; refreshContent(true); });
        showChanges = button(filterLabel(), x + contentWidth - toggleWidth, layout.filterY(), toggleWidth, () -> {
            changesOnly = !changesOnly;
            showChanges.setMessage(TrainerEditorTheme.styled(filterLabel()));
            refreshContent(true);
        });
        showChanges.setTooltip(Tooltip.create(text("filter_help")));
        review = addRenderableWidget(new RulesPresetReviewPane(font, x, layout.reviewY(), contentWidth, layout.reviewHeight()));
        if (layout.showsOverview()) {
            overview = addRenderableWidget(new RulesPresetReviewPane(font, layout.contentX() + 2, layout.overviewY(),
                    layout.railWidth() - 4, layout.overviewHeight()));
            overview.setMessage(text("overview"));
        }
        int buttonWidth = layout.buttonWidth();
        previewButton = button(text("preview_button"), layout.previewX(), layout.controlsY(), buttonWidth, this::requestPreview);
        applyButton = button(text("apply_button"), layout.applyX(), layout.controlsY(), buttonWidth, this::requestApply);
        applyButton.setTooltip(Tooltip.create(text("apply_help").append("\n").append(text("scope"))));
        button(Component.translatable("gui.done"), layout.doneX(), layout.headerY(), layout.doneWidth(), this::onClose);
        Button selectedTab = button(Component.translatable("screen.shadowedhearts.snapshots.presets"), layout.contentX(), layout.headerY(), layout.presetsTabWidth(), () -> {});
        selectedTab.active = false;
        selectedTab.setMessage(selectedTab.getMessage().copy().withStyle(style -> style.withColor(TrainerEditorTheme.SELECTED)));
        snapshotsTab = button(Component.translatable("screen.shadowedhearts.snapshots.title"), layout.snapshotsTabX(),
                layout.headerY(), layout.snapshotsTabWidth(), () -> RulesPresetClient.openSnapshots(parent));
        refreshContent(false);
        updateControls();
        if (!initialized) {
            initialized = true;
            requestPreview();
        }
    }

    private RulesPresetCatalog.Preset selected() { return RulesPresetCatalog.find(session.presetId()).orElseThrow(); }
    private Component filterLabel() { return text(changesOnly ? "changes_only" : "all_settings"); }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        Button button = TrainerEditorTheme.button(label.getString(), x, y, width, TrainerEditorTheme.CONTROL_HEIGHT, action);
        button.setMessage(TrainerEditorTheme.styled(label));
        return addRenderableWidget(button);
    }

    private void selectPreset(RulesPresetCatalog.Preset preset) {
        if (session.waiting() || preset.id().equals(session.presetId())) return;
        session.select(preset.id());
        search = "";
        filter.setValue("");
        // Finish the native button event before replacing roster widgets and restoring keyboard focus.
        rebuildPresetList = layout.showsPresetList();
        requestPreview();
    }

    private void requestPreview() {
        if (!RulesPresetClient.connected(this)) {
            session.disconnect();
            refreshContent(true);
            updateControls();
            return;
        }
        if (!session.waiting()) send(session.beginPreview(RulesPresetClient.nextRequestId()));
    }

    private void requestApply() {
        if (RulesPresetClient.connected(this) && session.canApply()) {
            send(session.beginApply(RulesPresetClient.nextRequestId()));
        }
    }

    private void send(JsonObject request) {
        requestSentAt = System.currentTimeMillis();
        try { RulesPresetClient.send(this, request); }
        catch (RuntimeException failure) { session.responseFailed(); }
        refreshContent(true);
        updateControls();
    }

    void receive(JsonObject response) {
        if (session.receive(response)) {
            refreshContent(true);
            updateControls();
            if (minecraft.screen == child) {
                child = null;
                minecraft.setScreen(this);
            }
        }
    }

    void responseFailed() {
        session.responseFailed();
        refreshContent(false);
        updateControls();
    }

    private void updateControls() {
        if (previewButton == null) return;
        if (selector != null) selector.active = !session.waiting();
        presetButtons.forEach(button -> button.active = !session.waiting());
        previewButton.active = RulesPresetClient.connected(this) && !session.waiting();
        applyButton.active = RulesPresetClient.connected(this) && session.canApply();
        snapshotsTab.active = !session.waiting();
    }

    private void refreshContent(boolean reset) {
        if (review == null) return;
        List<Component> lines = new ArrayList<>();
        var preset = selected();
        lines.add(colored(Component.translatable(session.messageKey()), statusColor()));
        lines.add(Component.empty());
        List<Component> description = new ArrayList<>();
        description.add(colored(Component.translatable(preset.titleKey()), TrainerEditorTheme.ACCENT));
        description.add(Component.empty());
        description.add(Component.translatable(preset.descriptionKey()));
        description.add(Component.empty());
        description.add(colored(text("one_shot"), TrainerEditorTheme.WARNING));
        if (!preset.id().equals("mod_defaults")) description.add(colored(text("acquisition"), TrainerEditorTheme.WARNING));
        description.add(Component.translatable(preset.notesKey()));
        description.add(Component.empty());
        if (overview != null) overview.setContent(description, reset);
        else lines.addAll(description);
        var preview = session.preview();
        if (preview != null) {
            lines.add(colored(text("source", preview.source()), TrainerEditorTheme.MUTED));
            lines.add(text("count", preview.changedCount(), preview.rows().size()));
            lines.add(colored(text("scope"), TrainerEditorTheme.WARNING));
            lines.add(Component.empty());
            String query = search.strip().toLowerCase(Locale.ROOT);
            int shown = 0;
            for (var row : preview.rows()) {
                if (changesOnly && !row.changed()) continue;
                Component label = row.labelKey().isBlank() ? Component.literal(row.path()) : Component.translatable(row.labelKey());
                String searchable = (row.path() + " " + label.getString() + " " + row.currentValue() + " " + row.proposedValue()).toLowerCase(Locale.ROOT);
                if (!query.isBlank() && !searchable.contains(query)) continue;
                lines.add(colored(label, row.changed() ? TrainerEditorTheme.SELECTED : TrainerEditorTheme.TEXT));
                lines.add(colored(Component.literal(row.path()), TrainerEditorTheme.MUTED));
                lines.add(colored(text("current", row.currentValue()), TrainerEditorTheme.MUTED));
                lines.add(text(row.changed() ? "proposed" : "unchanged", row.proposedValue()));
                lines.add(Component.empty());
                shown++;
            }
            if (shown == 0) lines.add(text(preview.changedCount() == 0 ? "already_matches" : "no_matches"));
        } else if (!RulesPresetClient.connected(this)) {
            lines.add(text("world_hint"));
        }
        review.setContent(lines, reset);
    }

    @Override public void tick() {
        super.tick();
        if (rebuildPresetList) {
            boolean restoreFocus = presetButtons.contains(getFocused());
            rebuildPresetList = false;
            init();
            if (restoreFocus) {
                for (int index = 0; index < RulesPresetCatalog.all().size(); index++) {
                    if (RulesPresetCatalog.all().get(index).id().equals(session.presetId()) && index < presetButtons.size()) {
                        setFocused(presetButtons.get(index));
                        break;
                    }
                }
            }
        }
        if (!RulesPresetClient.connected(this) && (session.waiting() || session.preview() != null)) {
            session.disconnect();
            refreshContent(true);
            updateControls();
        }
        if (session.waiting() && !session.timedOut() && System.currentTimeMillis() - requestSentAt > 15000) {
            session.timeout();
            refreshContent(false);
            updateControls();
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB010171C);
        TrainerEditorTheme.frame(graphics, layout.x(), layout.y(), layout.width(), layout.height());
        graphics.fill(layout.contentX(), layout.y() + 6, layout.x() + layout.width() - 6, layout.y() + 24, TrainerEditorTheme.INSET);
        if (layout.showsPresetList()) {
            TrainerEditorTheme.panel(graphics, layout.contentX(), layout.railY(), layout.railWidth(), layout.railHeight());
        }
    }

    @Override public void renderBlurredBackground(float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (layout.showsPresetList()) {
            drawLabel(graphics, text("workspace_hint"), layout.contentX(), layout.selectorY() + 4,
                    layout.contentWidth(), TrainerEditorTheme.MUTED);
            drawLabel(graphics, text("templates"), layout.contentX() + 4, layout.railY() + 4,
                    layout.railWidth() - 8, TrainerEditorTheme.ACCENT);
        }
        int countWidth = layout.previewX() - layout.contentX() - 6;
        if (session.preview() != null && countWidth >= 100) {
            drawLabel(graphics, text("count", session.preview().changedCount(), session.preview().rows().size()),
                    layout.contentX(), layout.controlsY() + 4, countWidth, TrainerEditorTheme.MUTED);
        }
        drawLabel(graphics, Component.translatable(session.messageKey()), layout.contentX(), layout.statusY(), layout.contentWidth(), statusColor());
    }

    private int statusColor() { return session.status().equals("error") ? TrainerEditorTheme.ERROR : TrainerEditorTheme.MUTED; }

    private static Component colored(Component component, int color) {
        return component.copy().withStyle(style -> style.withColor(color));
    }

    private void drawLabel(GuiGraphics graphics, Component component, int x, int y, int width, int color) {
        graphics.drawString(font, BoundedGuiText.fit(TrainerEditorTheme.styled(component), width, font), x, y, color, true);
    }

    @Override public void onClose() {
        if (session.pending() == RulesPresetSession.Pending.APPLY) {
            child = new PresetConfirmScreen(leave -> {
                if (leave) close();
                else { child = null; minecraft.setScreen(this); }
            }, text("leave_title"), text("leave_pending"), text("leave"), text("keep_waiting")) {
                @Override public boolean isPauseScreen() { return false; }
            };
            minecraft.setScreen(child);
        } else close();
    }

    private void close() {
        RulesPresetClient.closed(this);
        minecraft.setScreen(parent);
    }

    boolean isOpenAt(Screen screen) { return screen == this || child != null && screen == child; }
    @Override public boolean isPauseScreen() { return false; }
    private static MutableComponent text(String key, Object... args) {
        return Component.translatable("screen.shadowedhearts.rules_presets." + key, args);
    }
}
