package com.jayemceekay.shadowedhearts.client.gui.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorTheme;
import com.jayemceekay.shadowedhearts.config.preset.RulesPresetService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** A sibling tab to templates, with local preferences and permission-checked server history. */
final class ConfigSnapshotScreen extends Screen {
    private record Entry(String id, String name, long time, boolean automatic) { }
    private final Screen parent;
    private final ConfigSnapshotSession session = new ConfigSnapshotSession();
    private final List<Button> actions = new ArrayList<>();
    private List<Entry> entries = List.of();
    private RulesPresetReviewPane review;
    private RulesPresetLayout layout;
    private ConfigSnapshotLayout geometry;
    private final List<Button> entryButtons = new ArrayList<>();
    private Button scopeButton, snapshotSelector;
    private EditBox name;
    private Button restore, delete, preview, save, previousPage, nextPage;
    private String draft = "";
    private int page, pageCount, pageLabelX, pageLabelY;
    private boolean initialized, rebuild;
    private long sentAt;
    private Screen child;

    ConfigSnapshotScreen(Screen parent) { super(text("title")); this.parent = parent; }

    @Override protected void init() {
        clearWidgets(); actions.clear(); entryButtons.clear();
        restore = delete = preview = save = previousPage = nextPage = snapshotSelector = null;
        layout = RulesPresetLayout.forScreen(width, height);
        geometry = new ConfigSnapshotLayout(layout);
        int x = layout.contentX(), w = layout.contentWidth(), h = ConfigSnapshotLayout.CONTROL_HEIGHT;
        action(text("presets"), x, layout.headerY(), layout.presetsTabWidth(), () -> RulesPresetClient.open(parent));
        Button selectedTab = action(text("title"), layout.snapshotsTabX(), layout.headerY(), layout.snapshotsTabWidth(), () -> {});
        selectedTab.active = false;
        selectedTab.setMessage(TrainerEditorTheme.styled(text("title")).withStyle(style -> style.withColor(TrainerEditorTheme.SELECTED)));
        // Done remains available during a delayed write, with the existing confirmation on close.
        Button done = TrainerEditorTheme.button(Component.translatable("gui.done").getString(), layout.doneX(),
                layout.headerY(), layout.doneWidth(), h, this::onClose);
        addRenderableWidget(done);
        scopeButton = addRenderableWidget(TrainerEditorTheme.cycleButton(CycleButton.<String>builder(value -> text("scope." + value))
                .withValues(List.of("client", "gameplay", "world")).withInitialValue(session.scope()).displayOnlyValue()
                .create(x, geometry.scopeY(), geometry.inputWidth(), h, text("scope"), (button, value) -> {
                    session.scope(value); entries = List.of(); page = 0; rebuild = true; request("snapshot_list");
                })));
        actions.add(scopeButton);
        action(text("refresh"), geometry.sideActionX(), geometry.scopeY(), geometry.sideActionWidth(), () -> request("snapshot_list"));
        name = addRenderableWidget(TrainerEditorTheme.textBox(font, x, geometry.nameY(), geometry.inputWidth(), h, text("name")));
        name.setMaxLength(64); name.setHint(TrainerEditorTheme.styled(text("name"))); name.setValue(draft);
        name.setResponder(value -> { draft = value; controls(); });
        save = action(text("save"), geometry.sideActionX(), geometry.nameY(), geometry.sideActionWidth(), () -> request("snapshot_create"));
        pageCount = Math.max(1, (entries.size() + geometry.rows() - 1) / geometry.rows());
        page = Math.min(page, pageCount - 1);
        if (geometry.showsList()) {
            for (int row = 0; row < geometry.rows(); row++) {
                int index = page * geometry.rows() + row;
                if (index >= entries.size()) break;
                Entry entry = entries.get(index);
                Component detail = text(entry.automatic() ? "automatic_entry" : "named_entry", date(entry.time()));
                Button button = TrainerEditorTheme.rosterButton(entry.name(), detail.getString(), x, geometry.entryY(row),
                        geometry.listWidth(), entry.id().equals(session.selected()), () -> select(entry));
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(TrainerEditorTheme.styled(Component.literal(entry.name())
                        .append("\n").append(detail))));
                entryButtons.add(button); actions.add(button); addRenderableWidget(button);
            }
            previousPage = action(Component.literal("<"), x, geometry.navigationY(), 22, () -> { page--; rebuild = true; });
            nextPage = action(Component.literal(">"), x + geometry.listWidth() - 22, geometry.navigationY(), 22, () -> { page++; rebuild = true; });
            pageLabelX = x + geometry.listWidth() / 2; pageLabelY = geometry.navigationY() + (h - 8) / 2;
        } else {
            if (entries.isEmpty()) {
                snapshotSelector = action(text("no_entries"), x, geometry.bodyY(), w, () -> {});
            } else {
                // A neutral initial option keeps the first real snapshot selectable with one click.
                List<Entry> choices = new ArrayList<>();
                if (session.selected().isEmpty()) choices.add(new Entry("", text("choose_entry").getString(), 0, false));
                choices.addAll(entries);
                Entry selected = choices.stream().filter(entry -> entry.id().equals(session.selected())).findFirst().orElse(choices.getFirst());
                snapshotSelector = addRenderableWidget(TrainerEditorTheme.cycleButton(CycleButton.<Entry>builder(entry -> Component.literal(entry.name()))
                        .withValues(choices).withInitialValue(selected).displayOnlyValue()
                        .create(x, geometry.bodyY(), w, h, text("choose_entry"), (button, entry) -> select(entry))));
                actions.add(snapshotSelector);
            }
        }
        review = addRenderableWidget(new RulesPresetReviewPane(font, geometry.reviewX(), geometry.reviewY(),
                geometry.reviewWidth(), geometry.reviewHeight()));
        preview = action(text("preview"), geometry.footerX(0), layout.controlsY(), geometry.footerWidth(0), () -> request("snapshot_preview"));
        restore = action(text("restore"), geometry.footerX(1), layout.controlsY(), geometry.footerWidth(1), () -> confirm(false));
        delete = action(text("delete"), geometry.footerX(2), layout.controlsY(), geometry.footerWidth(2), () -> confirm(true));
        content(); controls();
        if (!initialized) { initialized = true; request("snapshot_list"); }
    }

    private void select(Entry entry) {
        session.select(entry.id()); rebuild = true;
        if (!entry.id().isEmpty()) request("snapshot_preview");
        else { content(); controls(); }
    }

    private Button action(Component label, int x, int y, int w, Runnable runnable) {
        Button button = TrainerEditorTheme.button(label.getString(), x, y, Math.max(1, w), ConfigSnapshotLayout.CONTROL_HEIGHT, runnable);
        button.setMessage(TrainerEditorTheme.styled(label));
        actions.add(button); return addRenderableWidget(button);
    }

    private boolean available() { return session.scope().equals("client") || RulesPresetClient.connected(this); }

    private void request(String action) {
        if (session.waiting()) return;
        if (!available()) { session.disconnect(); rebuild = true; content(); controls(); return; }
        JsonObject request = session.begin(RulesPresetClient.nextRequestId(), action, draft);
        sentAt = System.currentTimeMillis();
        try {
            if (session.scope().equals("client")) receive(JsonParser.parseString(RulesPresetService.handleLocalSnapshots(request.toString())).getAsJsonObject());
            else RulesPresetClient.send(this, request);
        } catch (RuntimeException failure) { session.failed(); }
        content(); controls();
    }

    private void confirm(boolean deleting) {
        if (session.waiting() || !deleting && !session.canRestore()) return;
        child = new PresetConfirmScreen(yes -> {
            child = null; minecraft.setScreen(this);
            if (yes) request(deleting ? "snapshot_delete" : "snapshot_restore");
        }, text(deleting ? "delete" : "restore"), text(deleting ? "delete_confirm" : "restore_confirm")) {
            @Override public boolean isPauseScreen() { return false; }
        };
        minecraft.setScreen(child);
    }

    void receive(JsonObject response) {
        if (!session.receive(response)) return;
        if (response.has("snapshots")) {
            List<Entry> received = new ArrayList<>();
            for (var value : response.getAsJsonArray("snapshots")) {
                JsonObject entry = value.getAsJsonObject();
                received.add(new Entry(entry.get("id").getAsString(), entry.get("name").getAsString(),
                        entry.get("createdAt").getAsLong(), entry.get("automatic").getAsBoolean()));
            }
            entries = List.copyOf(received);
            if (entries.stream().noneMatch(entry -> entry.id().equals(session.selected()))) session.select("");
            rebuild = true;
        }
        content(); controls();
        if (child != null && minecraft.screen == child) { child = null; minecraft.setScreen(this); }
    }

    void responseFailed() { session.failed(); content(); controls(); }

    private void controls() {
        boolean enabled = !session.waiting();
        actions.forEach(button -> button.active = enabled);
        if (actions.size() > 1) actions.get(1).active = false;
        if (name != null) name.setEditable(enabled);
        if (restore == null) return;
        save.active = enabled && available() && !draft.isBlank();
        preview.active = delete.active = enabled && available() && !session.selected().isEmpty();
        restore.active = available() && session.canRestore();
        if (previousPage != null) previousPage.active = enabled && page > 0;
        if (nextPage != null) nextPage.active = enabled && page + 1 < pageCount;
        if (snapshotSelector != null) snapshotSelector.active = enabled && !entries.isEmpty();
    }

    private void content() {
        if (review == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(session.message()));
        lines.add(Component.empty());
        entries.stream().filter(entry -> entry.id().equals(session.selected())).findFirst().ifPresent(entry -> {
            lines.add(Component.literal(entry.name())); lines.add(Component.literal(date(entry.time())));
        });
        JsonObject response = session.response();
        if (response != null && response.has("changedCount")) {
            lines.add(text("count", response.get("changedCount").getAsInt()));
            for (var value : response.getAsJsonArray("rows")) {
                JsonObject row = value.getAsJsonObject();
                lines.add(Component.empty());
                lines.add(Component.literal(row.get("path").getAsString()));
                lines.add(Component.literal(row.get("current").getAsString() + " → " + row.get("proposed").getAsString()));
            }
        } else {
            lines.add(text("help"));
            lines.add(Component.empty());
            lines.add(text("retention"));
        }
        lines.add(Component.empty());
        lines.add(text("restore_help"));
        if (response != null && response.has("source")) {
            lines.add(Component.empty());
            lines.add(text("source", response.get("source").getAsString()));
        }
        if (entries.isEmpty()) lines.add(text("empty"));
        review.setContent(lines, true);
    }

    @Override public void tick() {
        if (rebuild) {
            var focused = getFocused();
            boolean entryFocus = focused != null && (entryButtons.contains(focused) || focused == snapshotSelector);
            boolean scopeFocus = focused == scopeButton, nameFocus = focused == name;
            boolean previousFocus = focused == previousPage, nextFocus = focused == nextPage;
            int cursor = name.getCursorPosition();
            rebuild = false; init();
            if (scopeFocus) setFocused(scopeButton);
            else if (nameFocus) { setFocused(name); name.setCursorPosition(cursor); name.setHighlightPos(cursor); }
            else if (previousFocus && previousPage != null) setFocused(previousPage.active ? previousPage : nextPage);
            else if (nextFocus && nextPage != null) setFocused(nextPage.active ? nextPage : previousPage);
            else if (entryFocus) {
                if (snapshotSelector != null) setFocused(snapshotSelector);
                else for (int i = 0; i < entryButtons.size(); i++) {
                    if (entries.get(page * geometry.rows() + i).id().equals(session.selected())) { setFocused(entryButtons.get(i)); break; }
                }
            }
        }
        if (!session.scope().equals("client") && !available() && (session.waiting() || session.response() != null)) {
            session.disconnect(); content(); controls();
        }
        if (session.waiting() && !session.timedOut() && System.currentTimeMillis() - sentAt > 15000) {
            session.timeout(); content(); controls();
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xB010171C);
        TrainerEditorTheme.frame(graphics, layout.x(), layout.y(), layout.width(), layout.height());
    }
    @Override public void renderBlurredBackground(float delta) { }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        if (geometry.showsList()) graphics.drawCenteredString(font,
                TrainerEditorTheme.styled((page + 1) + " / " + pageCount), pageLabelX, pageLabelY, TrainerEditorTheme.MUTED);
    }
    @Override public boolean isPauseScreen() { return false; }
    boolean isOpenAt(Screen screen) { return screen == this || child != null && screen == child; }

    @Override public void onClose() {
        if (session.writing()) {
            child = new PresetConfirmScreen(leave -> {
                child = null;
                if (leave) close(); else minecraft.setScreen(this);
            }, text("pending_title"), text("write_pending")) {
                @Override public boolean isPauseScreen() { return false; }
            };
            minecraft.setScreen(child);
        } else close();
    }
    private void close() { RulesPresetClient.closed(this); minecraft.setScreen(parent); }
    private static String date(long time) { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time)); }
    private static Component text(String key, Object... args) { return Component.translatable("screen.shadowedhearts.snapshots." + key, args); }
}
