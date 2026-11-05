package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Target -> behavior -> review. Inline definitions belong to the same local workflow and are
 * submitted with their owner; opening a picker or returning from an inline editor never saves.
 */
public final class TrainerEditorScreen extends Screen {
    private enum View { BROWSE, EDIT, REVIEW }
    private enum Query { BROWSE, ROSTER, OPEN_NPC, INLINE, REFERENCE, REFRESH_MAIN, REFRESH_DEFINITION }
    private record Label(int x, int y, int width, String text, int color) {}
    private record Field(String key, String label, String value, Consumer<String> change,
                         List<TrainerEditorChoiceScreen.Choice> choices, boolean editable,
                         boolean searchable, String help) {}
    private record InlineLink(String field, boolean assign, int offset, boolean advanced) {}
    private record DefinitionQuery(String category, String id) {}

    private final Screen parent;
    private String category;
    private String rosterCategory = "trainers", rosterRevision = "";
    private String target;
    private View view = View.BROWSE;
    private boolean initialized, waiting, mutationPending, saveTimedOut, advanced, choosingTarget, deleting, reviewDetails;
    private int pendingRequest, page, pages = 1, total, selected = -1, rowOffset, detailOffset, formOffset, poolIndex;
    private int fieldCount;
    private long requestSentAt, searchChangedAt;
    private String revision = "", search = "", status = tr("screen.shadowedhearts.trainer_editor.loading_server_settings");
    private boolean statusError;
    private List<JsonObject> rows = List.of();
    private List<String> presets = List.of(), pools = List.of(), diagnostics = List.of();
    private List<String> draftMessages = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final java.util.Map<String, AbstractWidget> fieldControls = new java.util.HashMap<>();
    private JsonObject definitionRevisions = new JsonObject();
    private JsonArray knownDefinitions = new JsonArray();
    private TrainerEditorWorkflow workflow;
    private final Deque<InlineLink> inlineLinks = new ArrayDeque<>();
    private final Deque<DefinitionQuery> refreshQueue = new ArrayDeque<>();
    private Query pendingQuery = Query.BROWSE;
    private String queryCategory = "", queryId = "";
    private boolean inlineCopy;
    private String inlineField = "";
    private Screen ownedChild;
    private EditBox browserInput;
    private boolean rebuildPending;
    private final TrainerEditorScrollbar listScrollbar = new TrainerEditorScrollbar(tr("screen.shadowedhearts.trainer_editor.entries_on_this_page"), 0, 0, 1,
            position -> { rowOffset = position; rebuildPending = true; });
    private final TrainerEditorScrollbar formScrollbar = new TrainerEditorScrollbar(tr("screen.shadowedhearts.trainer_editor.settings"), 0, 0, 1,
            position -> { formOffset = position * formLayout().columns(); rebuildPending = true; });
    private final TrainerEditorScrollbar detailScrollbar = new TrainerEditorScrollbar(tr("screen.shadowedhearts.trainer_editor.details"), 0, 0, 1,
            position -> detailOffset = position);
    private TrainerEditorLayout.Bounds detailArea;

    public TrainerEditorScreen(Screen parent, String targetUuid) {
        super(Component.literal(tr("screen.shadowedhearts.trainer_editor.trainer_settings")));
        this.parent = parent;
        target = targetUuid == null ? "" : targetUuid;
        category = target.isBlank() ? "trainers" : "npc";
    }

    @Override protected void init() {
        rebuild();
        if (!initialized) {
            initialized = true;
            if (!target.isBlank()) query(Query.OPEN_NPC, "npc", target);
            else queryBrowse();
        }
    }

    private TrainerEditorDraft active() { return workflow == null ? null : workflow.active(); }
    private String activeCategory() { return active() == null ? category : active().category; }
    private void label(int x, int y, int width, String text, int color) {
        labels.add(new Label(x, y, Math.max(1, width), text, color));
    }
    private TrainerEditorWorkspace workspace() {
        return TrainerEditorWorkspace.forScreen(width, height, view != View.BROWSE);
    }
    private TrainerEditorLayout formLayout() { return TrainerEditorLayout.forArea(workspace().form()); }

    private void rebuild() {
        TrainerEditorScrollbar focusedScrollbar = getFocused() instanceof TrainerEditorScrollbar scrollbar ? scrollbar : null;
        boolean dragging = isDragging();
        String focusedField = fieldControls.entrySet().stream().filter(entry -> entry.getValue() == getFocused())
                .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
        boolean focused = browserInput != null && getFocused() == browserInput;
        int cursor = focused ? browserInput.getCursorPosition() : 0;
        browserInput = null;
        clearWidgets(); labels.clear(); fieldControls.clear(); detailArea = null;
        listScrollbar.visible = formScrollbar.visible = detailScrollbar.visible = false;
        listScrollbar.active = formScrollbar.active = detailScrollbar.active = !waiting;
        var area = workspace();
        navigation();
        if (view == View.BROWSE) browse();
        else {
            if (area.showsRoster()) roster(true);
            if (view == View.EDIT) edit(); else review();
        }
        var frame = area.frame();
        Button closeButton = button(tr("screen.shadowedhearts.trainer_editor.close"), frame.right() - 50, frame.y() + 7, 42, this::onClose);
        addWidget(detailScrollbar);
        if (focused && browserInput != null) {
            setFocused(browserInput);
            browserInput.setCursorPosition(Math.min(cursor, browserInput.getValue().length()));
            browserInput.setHighlightPos(browserInput.getCursorPosition());
        }
        if (waiting) children().forEach(child -> { if (child instanceof AbstractWidget widget) widget.active = false; });
        if (saveTimedOut) closeButton.active = true;
        if (focusedField != null && fieldControls.containsKey(focusedField) && fieldControls.get(focusedField).active)
            setFocused(fieldControls.get(focusedField));
        if (focusedScrollbar != null && focusedScrollbar.active && children().contains(focusedScrollbar)) {
            setFocused(focusedScrollbar); setDragging(dragging);
        }
    }

    private void navigation() {
        var frame = workspace().frame();
        String group = category.equals("npc") ? "npc" : isDefinition(category) ? "library" : "trainers";
        String[] names = {tr("screen.shadowedhearts.trainer_editor.rct_trainer_rules"), tr("screen.shadowedhearts.trainer_editor.npc_settings"), tr("screen.shadowedhearts.trainer_editor.library")};
        String[] groups = {"trainers", "npc", "library"};
        int[] widths = {106, 88, 54};
        int x = frame.x() + 6;
        for (int i = 0; i < names.length; i++) {
            String destination = groups[i].equals("library") ? "presets" : groups[i];
            Button tab = button(names[i], x, frame.y() + 28, widths[i], () -> navigate(destination));
            tab.active = !groups[i].equals(group) && !choosingTarget && (workflow == null || !workflow.nested());
            x += widths[i] + 4;
        }
    }

    private void browse() {
        var area = workspace(); var frame = area.frame();
        int x = frame.x() + 6;
        if (!category.equals("npc")) {
            List<TrainerEditorChoiceScreen.Choice> kinds = isDefinition(category)
                    ? List.of(new TrainerEditorChoiceScreen.Choice("presets", tr("screen.shadowedhearts.trainer_editor.category.presets")), new TrainerEditorChoiceScreen.Choice("pools", tr("screen.shadowedhearts.trainer_editor.pokemon_pools")))
                    : List.of(new TrainerEditorChoiceScreen.Choice("trainers", tr("screen.shadowedhearts.trainer_editor.rct_trainers")), new TrainerEditorChoiceScreen.Choice("types", tr("screen.shadowedhearts.trainer_editor.rct_types")),
                    new TrainerEditorChoiceScreen.Choice("rules", tr("screen.shadowedhearts.trainer_editor.saved_rules")));
            if (choosingTarget) kinds = kinds.stream().filter(c -> !c.value().equals("rules")).toList();
            cycle(tr("screen.shadowedhearts.trainer_editor.browse"), category, kinds, x, area.toolbarY(), 84, this::navigate);
            x += 88;
        }
        int refreshWidth = Math.max(48, font.width(TrainerEditorTheme.styled(tr("screen.shadowedhearts.trainer_editor.refresh"))) + 10);
        int refreshX = frame.right() - 6 - refreshWidth;
        boolean extra = category.equals("npc") || canCreate() && !choosingTarget;
        int extraWidth = category.equals("npc") ? 42 : 30;
        int extraX = refreshX - extraWidth - 4;
        browserInput = textBox(x, area.toolbarY(), (extra ? extraX - 4 : refreshX - 4) - x, search,
                value -> { search = value; searchChangedAt = System.currentTimeMillis(); }, 128, tr("screen.shadowedhearts.trainer_editor.search_names_ids_types_or_source"));
        browserInput.setHint(Component.literal(category.equals("npc") ? tr("screen.shadowedhearts.trainer_editor.find_a_loaded_battle_npc_entity") : tr("screen.shadowedhearts.trainer_editor.search_names_or_ids")));
        if (category.equals("npc")) button(tr("screen.shadowedhearts.trainer_editor.uuid"), extraX, area.toolbarY(), extraWidth, this::lookupNpc);
        else if (extra) button(tr("screen.shadowedhearts.trainer_editor.new"), extraX, area.toolbarY(), extraWidth, this::create);
        button(tr("screen.shadowedhearts.trainer_editor.refresh"), refreshX, area.toolbarY(), refreshWidth, this::queryBrowse);
        roster(false);
        JsonObject row = selectedRow();
        if (choosingTarget) button(tr("screen.shadowedhearts.trainer_editor.back"), frame.x() + 6, area.footerY(), 44,
                () -> { category = workflow.main().category; choosingTarget = false; view = View.EDIT; rebuild(); });
        if (row == null) return;
        int actionWidth = Math.min(88, (area.detail().width() - 4) / 2);
        int primaryX = frame.right() - 6 - actionWidth;
        int secondaryX = primaryX - 4 - actionWidth;
        if (category.equals("trainers") || category.equals("types")) {
            button(choosingTarget ? tr("screen.shadowedhearts.trainer_editor.use_target") : tr("screen.shadowedhearts.trainer_editor.set_behavior"), primaryX, area.footerY(), actionWidth, () -> useTarget(row));
            if (!choosingTarget) button(category.equals("types") ? tr("screen.shadowedhearts.trainer_editor.find_trainers") : tr("screen.shadowedhearts.trainer_editor.view_type"), secondaryX, area.footerY(), actionWidth, () -> {
                search = category.equals("types") ? text(row, "id", "") : text(row, "type", "");
                category = category.equals("types") ? "trainers" : "types"; page = rowOffset = 0; queryBrowse();
            });
        } else {
            button(category.equals("npc") ? tr("screen.shadowedhearts.trainer_editor.edit_settings") : tr("screen.shadowedhearts.trainer_editor.edit"), primaryX, area.footerY(), actionWidth, () -> openRow(row));
            if (isDefinition(category)) button(tr("screen.shadowedhearts.trainer_editor.duplicate"), secondaryX, area.footerY(), actionWidth, () -> beginEdit(row, true));
            else if (category.equals("rules")) button(tr("screen.shadowedhearts.trainer_editor.delete_rule"), secondaryX, area.footerY(), actionWidth,
                    () -> { beginEdit(row, false); deleting = true; view = View.REVIEW; rebuild(); });
        }
    }

    /** The same loaded page stays beside the draft. Switching never writes or silently drops a draft. */
    private void roster(boolean editing) {
        var area = workspace(); var rail = area.roster();
        if (editing) {
            selected = TrainerEditorRosterSelection.select(rows, rosterCategory,
                    workflow == null ? null : workflow.main(), "");
            browserInput = textBox(rail.x(), area.toolbarY(), rail.width(), search,
                    value -> { search = value; searchChangedAt = System.currentTimeMillis(); }, 128, tr("screen.shadowedhearts.trainer_editor.search_roster"));
            browserInput.setHint(Component.literal(rosterCategory.equals("npc") ? tr("screen.shadowedhearts.trainer_editor.loaded_npcs_entities") : tr("screen.shadowedhearts.trainer_editor.search_rct_roster")));
            browserInput.active = workflow == null || !workflow.nested();
        }
        int visible = area.rosterRows();
        rowOffset = Math.clamp(rowOffset, 0, Math.max(0, rows.size() - visible));
        listScrollbar.setX(rail.right() - TrainerEditorScrollbar.WIDTH - 2);
        listScrollbar.setY(rail.y() + 2);
        listScrollbar.setHeight(Math.max(16, rail.height() - 26));
        listScrollbar.setRange(rows.size(), visible, rowOffset);
        addRenderableWidget(listScrollbar);
        int gutter = listScrollbar.visible ? TrainerEditorScrollbar.GUTTER : 0;
        for (int local = 0; local < Math.min(visible, rows.size() - rowOffset); local++) {
            int index = rowOffset + local; JsonObject row = rows.get(index);
            String subtitle = text(row, "targetKindLabel", text(row, "type", text(row, "source", "")));
            Button b = addRenderableWidget(TrainerEditorTheme.rosterButton(text(row, "name", text(row, "id", tr("screen.shadowedhearts.trainer_editor.unnamed"))),
                    subtitle, rail.x() + 2, rail.y() + 2 + local * TrainerEditorTheme.ROW_HEIGHT,
                    rail.width() - 4 - gutter, selected == index, () -> {
                        if (editing) selectRoster(index); else { selected = index; detailOffset = 0; rebuild(); }
                    }));
            b.active = !editing || workflow == null || !workflow.nested();
            b.setTooltip(Tooltip.create(Component.literal(text(row, "id", "") + "\n" + text(row, "subtitle", "")
                    + "\n" + text(row, "targetHelp", ""))));
        }
        int bottom = rail.bottom() - 18;
        Button prev = button("<", rail.x() + 2, bottom, 18, () -> { page--; rowOffset = 0; if (editing) queryRoster(); else queryBrowse(); });
        prev.active = page > 0 && (!editing || workflow == null || !workflow.nested());
        Button next = button(">", rail.x() + 24, bottom, 18, () -> { page++; rowOffset = 0; if (editing) queryRoster(); else queryBrowse(); });
        next.active = page + 1 < pages && (!editing || workflow == null || !workflow.nested());
        label(rail.x() + 47, bottom + 4, rail.width() - 50, (page + 1) + "/" + pages + " (" + total + ")", TrainerEditorTheme.MUTED);
    }

    private void openRow(JsonObject row) {
        if (category.equals("npc")) { target = text(row, "id", ""); query(Query.OPEN_NPC, "npc", target); }
        else beginEdit(row, false);
    }
    private void selectRoster(int index) {
        if (workflow != null && workflow.nested() || index < 0 || index >= rows.size()) return;
        JsonObject row = rows.get(index);
        Runnable change = () -> {
            workflow = null; inlineLinks.clear(); draftMessages.clear();
            selected = index; category = rosterCategory; revision = rosterRevision;
            choosingTarget = deleting = false; detailOffset = formOffset = 0;
            if (category.equals("trainers") || category.equals("types")) useTarget(row); else openRow(row);
        };
        if (workflow != null && workflow.dirty()) confirm(tr("screen.shadowedhearts.trainer_editor.discard_this_setup"), tr("screen.shadowedhearts.trainer_editor.switching_targets_discards_this_draft_and_its_unsaved_inline_definitions"), change);
        else change.run();
    }

    private void edit() {
        if (workflow == null) return;
        TrainerEditorDraft draft = active(); String kind = activeCategory();
        var area = workspace(); var frame = area.frame(); var detail = area.detail();
        int toolsY = area.compact() ? detail.bottom() - 18 : area.toolbarY();
        int toolsX = detail.x() + 4;
        if (!kind.equals("pools")) {
            button(tr("screen.shadowedhearts.trainer_editor.presets"), toolsX, toolsY, 54, () -> manageDefinition("presets", "preset"));
            button(tr("screen.shadowedhearts.trainer_editor.pools"), toolsX + 58, toolsY, 48, () -> manageDefinition("pools", "pool"));
        } else {
            button(tr("screen.shadowedhearts.trainer_editor.add"), toolsX, toolsY, 54, () -> { draft.addEntry(); poolIndex = draft.entries().size() - 1; rebuild(); });
            Button remove = button(tr("screen.shadowedhearts.trainer_editor.remove"), toolsX + 58, toolsY, 48, () -> {
                if (!draft.entries().isEmpty()) draft.entries().remove(poolIndex);
                poolIndex = Math.max(0, poolIndex - 1); rebuild();
            });
            remove.active = !draft.entries().isEmpty();
        }
        button(advanced ? tr("screen.shadowedhearts.trainer_editor.basic") : tr("screen.shadowedhearts.trainer_editor.advanced"), toolsX + 110, toolsY, 62,
                () -> { advanced = !advanced; formOffset = 0; rebuild(); });
        if (!area.showsRoster()) button(tr("screen.shadowedhearts.trainer_editor.targets"), frame.x() + 6, area.compact() ? area.toolbarY() : detail.y() + 3, 52, this::browseTargets);
        if (kind.equals("rules") && !workflow.nested())
            button(tr("screen.shadowedhearts.trainer_editor.target"), detail.right() - 58, area.compact() ? area.toolbarY() : detail.y() + 3, 52, this::chooseTarget);
        List<Field> fields = new ArrayList<>();
        if (draft.created && isDefinition(kind) && advanced) fields.add(new Field("id", tr("screen.shadowedhearts.trainer_editor.resource_id"), draft.id, v -> draft.id = v, List.of(), true, false,
                tr("screen.shadowedhearts.trainer_editor.generated_automatically_change_it_only_if_you_want_a_particular_namespace_path")));
        if (kind.equals("pools")) poolFields(fields);
        else {
            if (kind.equals("rules") && advanced) ruleFields(fields);
            if (!kind.equals("rules") || !draft.value("excluded", "false").equals("true")) profileFields(fields);
        }
        fieldCount = fields.size();
        var layout = formLayout();
        formScrollbar.setX(layout.contentLeft() + layout.formWidth() - TrainerEditorScrollbar.WIDTH);
        formScrollbar.setY(layout.fieldTop());
        formScrollbar.setHeight(Math.max(1, layout.editBottom() - layout.fieldTop()));
        formScrollbar.setRange(fieldCount, layout.rowsPerColumn(), formOffset);
        formOffset = formScrollbar.position();
        addRenderableWidget(formScrollbar);
        for (int i = 0; i < layout.visibleFieldsCount() && formOffset + i < fieldCount; i++) {
            var bounds = layout.field(i, formScrollbar.visible ? TrainerEditorScrollbar.GUTTER : 0);
            addField(fields.get(formOffset + i), bounds.x(), bounds.y(), bounds.width());
        }
        if (workflow.nested()) {
            button(tr("screen.shadowedhearts.trainer_editor.cancel_inline"), frame.x() + 6, area.footerY(), 78, this::cancelInline);
            button(tr("screen.shadowedhearts.trainer_editor.use_in_draft"), frame.right() - 86, area.footerY(), 80, this::acceptInline);
        } else {
            button(tr("screen.shadowedhearts.trainer_editor.cancel"), frame.x() + 6, area.footerY(), 48, this::cancelDraft);
            button(tr("screen.shadowedhearts.trainer_editor.reset_draft"), frame.x() + 58, area.footerY(), 68, () -> {
                ownedChild = new ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) { workflow.revert(); draftMessages.clear(); formOffset = 0; rebuild(); } },
                        Component.literal(tr("screen.shadowedhearts.trainer_editor.reset_unsaved_changes")), Component.literal(tr("screen.shadowedhearts.trainer_editor.this_also_resets_inline_preset_and_pool_drafts_saved_server_values_are_unchanged")),
                        Component.literal(tr("screen.shadowedhearts.trainer_editor.reset_draft")), Component.literal(tr("screen.shadowedhearts.trainer_editor.keep_editing")));
                minecraft.setScreen(ownedChild);
            });
            if (isDefinition(kind) && !draft.created && text(workflow.activeRow(), "source", "").equals("runtime"))
                button(tr("screen.shadowedhearts.trainer_editor.delete"), frame.x() + 130, area.footerY(), 48, () -> { deleting = true; view = View.REVIEW; rebuild(); });
            button(tr("screen.shadowedhearts.trainer_editor.review"), frame.right() - 74, area.footerY(), 68, this::reviewDraft);
        }
    }

    private void browseTargets() {
        navigate(activeCategory().equals("npc") ? "npc" : isDefinition(activeCategory()) ? activeCategory() : rosterCategory);
    }

    private void profileFields(List<Field> fields) {
        fields.add(profileChoice("enabled", tr("screen.shadowedhearts.trainer_editor.shadow_injection"), values("inherit", "true", "false")));
        fields.add(profileChoice("mode", tr("screen.shadowedhearts.trainer_editor.behavior"), values("inherit", "convert", "append", "replace")));
        fields.add(profileChoice("count", tr("screen.shadowedhearts.trainer_editor.maximum_pokemon"), values("inherit", "0", "1", "2", "3", "4", "5", "6")));
        if (workflow.visible("chance")) {
            String chance = profileValue("chance", "inherit");
            String policy = chance.equals("inherit") || chance.equals("default") ? chance : "percent";
            fields.add(new Field("chancePolicy", tr("screen.shadowedhearts.trainer_editor.conversion_chance"), policy,
                    v -> workflow.setProfileValue("chance", v.equals("percent") ? "100" : v),
                    List.of(new TrainerEditorChoiceScreen.Choice("inherit", tr("screen.shadowedhearts.trainer_editor.use_inherited_chance")),
                            new TrainerEditorChoiceScreen.Choice("default", tr("screen.shadowedhearts.trainer_editor.value.default")),
                            new TrainerEditorChoiceScreen.Choice("percent", tr("screen.shadowedhearts.trainer_editor.set_a_percentage"))), false, false,
                    tr("screen.shadowedhearts.trainer_editor.inherited_follows_the_lower_layer_no_percentage_gate_attempts_eligible_members_u")));
            if (policy.equals("percent")) fields.add(new Field("chance", tr("screen.shadowedhearts.trainer_editor.chance_per_eligible_pokemon"), chance,
                    v -> workflow.setProfileValue("chance", v), List.of(), true, false, tr("screen.shadowedhearts.trainer_editor.a_whole_number_from_0_to_100_checked_until_the_maximum_is_reached")));
        }
        if (workflow.visible("pool")) {
            fields.add(profileChoice("pool", tr("screen.shadowedhearts.trainer_editor.pokemon_pool"), resourceChoices(allIds("pools"), true)));
            fields.add(profileChoice("levelMode", tr("screen.shadowedhearts.trainer_editor.candidate_levels"), values("inherit", "match", "fixed", "plus")));
            if (workflow.visible("levelValue")) fields.add(new Field("levelValue", tr("screen.shadowedhearts.trainer_editor.level_offset"), profileValue("levelValue", "0"),
                    v -> workflow.setProfileValue("levelValue", v), List.of(), true, false, tr("screen.shadowedhearts.trainer_editor.fixed_level_or_signed_offset_from_the_lead_pokemon_s_level")));
            fields.add(profileChoice("unique", tr("screen.shadowedhearts.trainer_editor.avoid_duplicate_species"), values("inherit", "true", "false")));
            fields.add(profileChoice("enforceEvo", tr("screen.shadowedhearts.trainer_editor.minimum_evolution_level"), values("inherit", "true", "false")));
        }
        fields.add(profileChoice("preset", tr("screen.shadowedhearts.trainer_editor.use_preset"), resourceChoices(allIds("presets"), false)));
        if (advanced) {
            // Hidden values remain authored; expose them for compatibility and deliberate overrides.
            if (!workflow.visible("chance")) fields.add(new Field("chance", tr("screen.shadowedhearts.trainer_editor.conversion_chance_unused"), profileValue("chance", "inherit"),
                    v -> workflow.setProfileValue("chance", v), List.of(), true, false, tr("screen.shadowedhearts.trainer_editor.retained_for_other_behavior_modes_unused_by_add_or_replace")));
            if (!workflow.visible("pool")) {
                fields.add(profileChoice("pool", tr("screen.shadowedhearts.trainer_editor.pool_unused_by_convert"), resourceChoices(allIds("pools"), true)));
                fields.add(profileChoice("levelMode", tr("screen.shadowedhearts.trainer_editor.level_policy_retained"), values("inherit", "match", "fixed", "plus")));
                fields.add(new Field("levelValue", tr("screen.shadowedhearts.trainer_editor.level_offset_retained"), profileValue("levelValue", "0"),
                        v -> workflow.setProfileValue("levelValue", v), List.of(), true, false, tr("screen.shadowedhearts.trainer_editor.unused_by_convert")));
                fields.add(profileChoice("unique", tr("screen.shadowedhearts.trainer_editor.unique_species_retained"), values("inherit", "true", "false")));
                fields.add(profileChoice("enforceEvo", tr("screen.shadowedhearts.trainer_editor.evolution_check_retained"), values("inherit", "true", "false")));
            }
            if (!activeCategory().equals("rules")) fields.add(tagsField("extraTags", tr("screen.shadowedhearts.trainer_editor.additional_legacy_tags"), workflow.profile()));
        }
    }
    private String profileValue(String key, String fallback) { return text(workflow.profile(), key, fallback); }
    private Field profileChoice(String key, String name, List<TrainerEditorChoiceScreen.Choice> choices) {
        String value = profileValue(key, key.equals("preset") ? "" : "inherit");
        JsonObject row = workflow.activeRow();
        String effective = workflow.effectiveValue(key, "");
        String source = row.has("inheritedSources") && value.equals("inherit")
                ? text(row.getAsJsonObject("inheritedSources"), key, "")
                : row.has("sources") ? text(row.getAsJsonObject("sources"), key, "") : "";
        if (row.has("data") && !profileValue("preset", "").equals(text(row.getAsJsonObject("data"), "preset", "")))
            source = tr("screen.shadowedhearts.trainer_editor.selected_preset_inherited_defaults");
        String help = tr("screen.shadowedhearts.trainer_editor.use_inherited_value_follows_the_preset_or_rule_it_does_not_mean_disabled_or_zero");
        if (!effective.isBlank()) help += tr("screen.shadowedhearts.trainer_editor.draft_value_s", source.isBlank() ? display(effective) : tr("screen.shadowedhearts.trainer_editor.value_from_source", display(effective), source));
        if (!effective.isBlank()) {
            String inheritedLabel = tr("screen.shadowedhearts.trainer_editor.inherited_s", display(effective));
            choices = choices.stream().map(choice -> choice.value().equals("inherit")
                    ? new TrainerEditorChoiceScreen.Choice("inherit", inheritedLabel) : choice).toList();
        }
        return new Field(key, name, value, v -> {
            workflow.setProfileValue(key, v);
            if ((key.equals("preset") || key.equals("pool")) && !v.isBlank() && !v.equals("inherit") && !v.equals("none"))
                loadReference(key.equals("preset") ? "presets" : "pools", v);
        }, choices, false, key.equals("preset") || key.equals("pool"), help);
    }
    private void ruleFields(List<Field> fields) {
        TrainerEditorDraft draft = active();
        fields.add(new Field("kind", tr("screen.shadowedhearts.trainer_editor.target_kind"), draft.value("kind", "trainer"), workflow::setRuleKind,
                values("trainer", "type"), false, false, tr("screen.shadowedhearts.trainer_editor.use_choose_who_for_a_searchable_catalog")));
        fields.add(new Field("target", tr("screen.shadowedhearts.trainer_editor.exact_trainer_type_id"), draft.value("target", ""), v -> draft.setValue("target", v),
                List.of(), true, false, tr("screen.shadowedhearts.trainer_editor.keep_the_exact_catalog_id_do_not_invent_a_namespace")));
        fields.add(new Field("section", tr("screen.shadowedhearts.trainer_editor.rule_section"), draft.value("section", "convert"), workflow::setRuleSection,
                values("convert", "append", "replace"), false, false, tr("screen.shadowedhearts.trainer_editor.same_specificity_priority_replace_add_convert_a_preset_s_explicit_behavior_may_o")));
        fields.add(new Field("excluded", tr("screen.shadowedhearts.trainer_editor.exclude_from_this_section"), draft.value("excluded", "false"),
                v -> draft.data().addProperty("excluded", Boolean.parseBoolean(v)), values("false", "true"), false, false,
                tr("screen.shadowedhearts.trainer_editor.trainer_ids_only_blocks_this_section_not_all_shadow_injection_other_matching_sec")));
        fields.add(tagsField("tags", tr("screen.shadowedhearts.trainer_editor.direct_rule_tags"), draft.data()));
    }
    private Field tagsField(String key, String title, JsonObject data) {
        return new Field(key, title, String.join(",", TrainerEditorDraft.strings(data.get(key))), value -> {
            JsonArray tags = new JsonArray();
            Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isBlank()).forEach(tags::add);
            data.add(key, tags);
        }, List.of(), true, false, tr("screen.shadowedhearts.trainer_editor.advanced_unrelated_native_data_is_retained_conflicting_shadow_tags_must_be_corre"));
    }
    private void poolFields(List<Field> fields) {
        TrainerEditorDraft draft = active();
        if (draft.entries().isEmpty()) return;
        poolIndex = Math.clamp(poolIndex, 0, draft.entries().size() - 1);
        List<TrainerEditorChoiceScreen.Choice> entries = new ArrayList<>();
        for (int i = 0; i < draft.entries().size(); i++)
            entries.add(new TrainerEditorChoiceScreen.Choice(Integer.toString(i), (i + 1) + ". " + text(TrainerEditorDraft.properties(draft.entries().get(i).getAsJsonObject()), "SpeciesText", tr("screen.shadowedhearts.trainer_editor.choose_species"))));
        fields.add(new Field("entry", tr("screen.shadowedhearts.trainer_editor.pokemon_entry"), Integer.toString(poolIndex), v -> poolIndex = Integer.parseInt(v), entries, false, true, tr("screen.shadowedhearts.trainer_editor.choose_an_entry_every_entry_stays_in_this_local_draft")));
        JsonObject entry = draft.entries().get(poolIndex).getAsJsonObject(), properties = TrainerEditorDraft.properties(entry);
        fields.add(new Field("species", tr("screen.shadowedhearts.trainer_editor.species"), text(properties, "SpeciesText", ""), v -> properties.addProperty("SpeciesText", v),
                speciesChoices(), true, true, tr("screen.shadowedhearts.trainer_editor.search_registered_species_or_enter_a_species_id")));
        fields.add(new Field("level", tr("screen.shadowedhearts.trainer_editor.level_blank_profile"), text(properties, "Level", ""), v -> {
            if (v.isBlank()) properties.remove("Level"); else setNumber(properties, "Level", v);
        }, List.of(), true, false, tr("screen.shadowedhearts.trainer_editor.an_entry_s_explicit_level_takes_priority_over_the_profile_s_level_policy")));
        fields.add(new Field("weight", tr("screen.shadowedhearts.trainer_editor.selection_weight"), text(entry, "weight", "1"), v -> setNumber(entry, "weight", v),
                List.of(), true, false, tr("screen.shadowedhearts.trainer_editor.positive_relative_weight_before_eligibility_filtering_a_weight_of_2_is_twice_a_w")));
        if (advanced) fields.add(new Field("native", tr("screen.shadowedhearts.trainer_editor.other_properties_retained"), properties.keySet().stream()
                .filter(k -> !List.of("SpeciesText", "Level", "OriginalText").contains(k)).sorted().reduce((a,b) -> a + ", " + b).orElse(tr("screen.shadowedhearts.trainer_editor.value.none")),
                ignored -> {}, List.of(), false, false, tr("screen.shadowedhearts.trainer_editor.moves_items_nature_and_other_native_properties_are_preserved")));
    }
    private void addField(Field field, int x, int y, int fieldWidth) {
        boolean inline = formLayout().inlineLabels();
        int labelWidth = inline ? Math.min(126, fieldWidth * 43 / 100) : fieldWidth;
        label(x, y + (inline ? 4 : 0), labelWidth, field.label(), TrainerEditorTheme.MUTED);
        int controlX = inline ? x + labelWidth + 6 : x;
        int controlY = inline ? y : y + 11;
        int controlWidth = inline ? fieldWidth - labelWidth - 6 : fieldWidth;
        String help = field.label() + "\n" + field.help();
        if (field.editable()) {
            int picker = field.choices().isEmpty() ? 0 : 30;
            EditBox box = textBox(controlX, controlY, controlWidth - picker, field.value(), field.change(), 4096, field.label());
            fieldControls.put(field.key(), box);
            box.setTooltip(Tooltip.create(Component.literal(help)));
            if (picker > 0) button("...", controlX + controlWidth - 26, controlY, 26,
                    () -> choose(field.label(), field.choices(), v -> { field.change().accept(v); rebuildPending = true; }));
        } else if (field.searchable() || field.choices().isEmpty()) {
            String current = field.choices().stream().filter(c -> c.value().equals(field.value())).map(TrainerEditorChoiceScreen.Choice::label).findFirst().orElse(display(field.value()));
            Button b = button(current, controlX, controlY, controlWidth, () -> choose(field.label(), field.choices(), v -> { field.change().accept(v); rebuildPending = true; }));
            fieldControls.put(field.key(), b);
            b.active = !field.choices().isEmpty(); b.setTooltip(Tooltip.create(Component.literal(help)));
        } else {
            var widget = cycle(field.label(), field.value(), field.choices(), controlX, controlY, controlWidth, value -> {
                field.change().accept(value); rebuildPending = true;
            });
            fieldControls.put(field.key(), widget);
            widget.setTooltip(Tooltip.create(Component.literal(help)));
        }
    }
    private Button cycle(String name, String value,
            List<TrainerEditorChoiceScreen.Choice> choices, int x, int y, int width, Consumer<String> changed) {
        var initial = choices.stream().filter(c -> c.value().equals(value)).findFirst().orElse(choices.getFirst());
        var nativeCycle = CycleButton.<TrainerEditorChoiceScreen.Choice>builder(c -> TrainerEditorTheme.styled(c.label()))
                .withValues(choices).withInitialValue(initial).displayOnlyValue()
                .create(x, y, Math.max(20, width), TrainerEditorTheme.CONTROL_HEIGHT, Component.literal(name), (b, selected) -> changed.accept(selected.value()));
        return addRenderableWidget(TrainerEditorTheme.cycleButton(nativeCycle));
    }

    private void manageDefinition(String type, String field) {
        String current = profileValue(field, "");
        if (current.equals("inherit") || current.isBlank())
            current = workflow.effectiveValue(field, "");
        String selectedId = current;
        List<TrainerEditorChoiceScreen.Choice> actions = new ArrayList<>();
        actions.add(new TrainerEditorChoiceScreen.Choice("new", tr("screen.shadowedhearts.trainer_editor.create_new_s_in_this_draft", singular(type))));
        actions.add(new TrainerEditorChoiceScreen.Choice("use", tr("screen.shadowedhearts.trainer_editor.choose_an_existing_s", singular(type))));
        if (!current.isBlank() && !current.equals("inherit") && !current.equals("none")) {
            actions.add(new TrainerEditorChoiceScreen.Choice("copy", tr("screen.shadowedhearts.trainer_editor.make_an_editable_copy_of_s", current)));
            actions.add(new TrainerEditorChoiceScreen.Choice("edit", tr("screen.shadowedhearts.trainer_editor.edit_shared_definition_s", current)));
        }
        choose(tr("screen.shadowedhearts.trainer_editor.manage_s", singular(type)), actions, action -> {
            if (action.equals("new")) {
                JsonObject data = TrainerEditorDraft.defaults(type);
                beginInline(type, generatedId(type), data, text(definitionRevisions, type, ""), true, new JsonObject(), field, true);
                if (type.equals("pools")) active().addEntry();
            } else if (action.equals("use")) {
                // Defer the next picker until the current picker returns to this screen.
                nextAction = () -> choose(tr("screen.shadowedhearts.trainer_editor.choose_s", singular(type)), resourceChoices(allIds(type), field.equals("pool")), value -> {
                    workflow.setProfileValue(field, value); loadReference(type, value);
                });
            } else if (action.equals("edit")) {
                nextAction = () -> {
                    ownedChild = new ConfirmScreen(confirmed -> {
                        minecraft.setScreen(this);
                        if (confirmed) openDefinition(type, selectedId, false, field);
                    }, Component.literal(tr("screen.shadowedhearts.trainer_editor.edit_a_shared_s", singular(type))),
                            Component.literal(tr("screen.shadowedhearts.trainer_editor.after_apply_every_user_of_s_will_see_these_changes_use_make_a_copy_to_change_onl", selectedId)),
                            Component.literal(tr("screen.shadowedhearts.trainer_editor.edit_shared")), Component.literal(tr("screen.shadowedhearts.trainer_editor.cancel")));
                    minecraft.setScreen(ownedChild);
                };
            } else openDefinition(type, selectedId, true, field);
        });
    }
    private Runnable nextAction;
    private void openDefinition(String type, String id, boolean copy, String field) {
        JsonObject cached = workflow.definition(type, id);
        if (cached != null) {
            beginInline(type, copy ? generatedId(type) : id, cached.getAsJsonObject("data"),
                    text(cached, "revision", text(definitionRevisions, type, "")), copy, cached, field, copy);
        } else {
            inlineCopy = copy; inlineField = field;
            query(Query.INLINE, type, id);
        }
    }
    private void beginInline(String type, String id, JsonObject data, String rev, boolean created, JsonObject row, String field, boolean assign) {
        inlineLinks.push(new InlineLink(field, assign, formOffset, advanced));
        workflow.beginInline(type, id, data, rev, created, row);
        view = View.EDIT; advanced = false; formOffset = poolIndex = 0; draftMessages.clear(); rebuild();
    }
    private void acceptInline() {
        List<String> errors = workflow.activeValidation();
        if (!errors.isEmpty()) { showError(errors.getFirst()); return; }
        TrainerEditorDraft accepted = workflow.acceptInline();
        InlineLink link = inlineLinks.pop();
        if (link.assign()) workflow.setProfileValue(link.field(), accepted.id);
        formOffset = link.offset(); advanced = link.advanced(); poolIndex = 0; draftMessages.clear(); rebuild();
    }
    private void cancelInline() {
        Runnable cancel = () -> {
            workflow.cancelInline(); InlineLink link = inlineLinks.pop();
            formOffset = link.offset(); advanced = link.advanced(); poolIndex = 0; draftMessages.clear(); rebuild();
        };
        if (workflow.inlineDirty()) confirm(tr("screen.shadowedhearts.trainer_editor.discard_inline_changes"), tr("screen.shadowedhearts.trainer_editor.your_parent_draft_will_be_kept"), cancel);
        else cancel.run();
    }
    private void loadReference(String type, String id) {
        if (id.isBlank() || id.equals("inherit") || id.equals("none") || workflow.definition(type, id) != null) return;
        query(Query.REFERENCE, type, id);
    }
    private List<String> allIds(String type) {
        List<String> result = new ArrayList<>(type.equals("presets") ? presets : pools);
        if (workflow != null) for (JsonElement e : workflow.definitions()) {
            JsonObject d = e.getAsJsonObject();
            if (text(d, "category", "").equals(type)) result.add(text(d, "id", ""));
        }
        return result.stream().distinct().sorted().toList();
    }

    private void useTarget(JsonObject row) {
        if (workflow == null) {
            JsonObject data = TrainerEditorDraft.defaults("rules");
            data.addProperty("kind", category.equals("types") ? "type" : "trainer");
            data.addProperty("target", text(row, "id", ""));
            workflow = new TrainerEditorWorkflow(new TrainerEditorDraft("rules", "", data, revision, true), row);
            configureWorkflow();
        } else {
            workflow.selectRuleTarget(category.equals("types") ? "type" : "trainer", text(row, "id", ""), row, revision);
        }
        category = "rules"; choosingTarget = false; view = View.EDIT; advanced = false; formOffset = 0; draftMessages.clear(); rebuild();
    }
    private void chooseTarget() {
        if (workflow == null || workflow.nested() || !workflow.main().category.equals("rules")) return;
        choosingTarget = true;
        category = workflow.main().value("kind", "trainer").equals("type") ? "types" : "trainers";
        view = View.BROWSE; search = ""; page = rowOffset = detailOffset = 0; queryBrowse();
    }
    private void create() {
        if (category.equals("rules")) {
            workflow = new TrainerEditorWorkflow(new TrainerEditorDraft("rules", "", TrainerEditorDraft.defaults("rules"), revision, true), new JsonObject());
            configureWorkflow(); chooseTarget(); return;
        }
        TrainerEditorDraft draft = new TrainerEditorDraft(category, generatedId(category), TrainerEditorDraft.defaults(category), revision, true);
        if (category.equals("pools")) draft.addEntry();
        workflow = new TrainerEditorWorkflow(draft, new JsonObject()); configureWorkflow();
        view = View.EDIT; formOffset = poolIndex = 0; advanced = false; deleting = false; draftMessages.clear(); rebuild();
    }
    private void beginEdit(JsonObject row, boolean duplicate) {
        String id = duplicate ? generatedId(category) : text(row, "id", "");
        TrainerEditorDraft draft = new TrainerEditorDraft(category, id, row.getAsJsonObject("data"), revision, duplicate);
        workflow = new TrainerEditorWorkflow(draft, row); configureWorkflow();
        view = View.EDIT; choosingTarget = false; formOffset = poolIndex = detailOffset = 0;
        advanced = category.equals("rules") && draft.value("excluded", "false").equals("true");
        deleting = false; draftMessages = new ArrayList<>(TrainerEditorDraft.strings(row.get("diagnostics"))); rebuild();
    }
    private void configureWorkflow() {
        workflow.setDefinitionRevisions(definitionRevisions);
        workflow.rememberDefinitions(knownDefinitions);
    }
    private void navigate(String next) {
        Runnable go = () -> {
            if (!choosingTarget) workflow = null;
            category = next; target = ""; view = View.BROWSE; search = ""; searchChangedAt = 0;
            page = rowOffset = detailOffset = 0; selected = -1; rows = List.of(); queryBrowse();
        };
        if (!choosingTarget && workflow != null && workflow.dirty()) confirm(tr("screen.shadowedhearts.trainer_editor.discard_this_setup"), tr("screen.shadowedhearts.trainer_editor.this_includes_unsaved_inline_presets_and_pools"), go);
        else go.run();
    }
    private void cancelDraft() {
        if (workflow != null && workflow.dirty()) confirm(tr("screen.shadowedhearts.trainer_editor.discard_this_setup"), tr("screen.shadowedhearts.trainer_editor.all_unsaved_inline_definitions_will_also_be_discarded_server_settings_are_unchan"), this::discardDraft);
        else discardDraft();
    }
    private void discardDraft() {
        if (workflow != null) category = workflow.main().category;
        workflow = null; inlineLinks.clear(); view = View.BROWSE; choosingTarget = deleting = false;
        target = ""; draftMessages.clear(); queryBrowse();
    }
    private void confirm(String title, String explanation, Runnable action) {
        ownedChild = new ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) action.run(); },
                Component.literal(title), Component.literal(explanation), Component.literal(tr("screen.shadowedhearts.trainer_editor.discard")), Component.literal(tr("screen.shadowedhearts.trainer_editor.keep_editing")));
        minecraft.setScreen(ownedChild);
    }
    private void lookupNpc() {
        ownedChild = new TrainerEditorTextScreen(this, tr("screen.shadowedhearts.trainer_editor.load_npc_entity_by_uuid"), tr("screen.shadowedhearts.trainer_editor.the_target_must_be_loaded_and_compatible_with_cobblemon_battles"), target, value -> {
            String entered = value.trim();
            try {
                if (entered.length() != 36) throw new IllegalArgumentException();
                target = java.util.UUID.fromString(entered).toString();
                query(Query.OPEN_NPC, "npc", target);
            } catch (IllegalArgumentException invalid) {
                showError(tr("screen.shadowedhearts.trainer_editor.enter_the_complete_uuid_or_find_the_loaded_npc_by_name"));
            }
        });
        minecraft.setScreen(ownedChild);
    }

    private void reviewDraft() {
        if (workflow == null || workflow.nested()) return;
        draftMessages = new ArrayList<>(workflow.validation());
        deleting = false; reviewDetails = false; view = View.REVIEW; detailOffset = 0; rebuild();
    }
    private void review() {
        var area = workspace(); var frame = area.frame(); var detail = area.detail();
        label(detail.x() + 6, area.toolbarY() + 4, detail.width() - 78, deleting ? tr("screen.shadowedhearts.trainer_editor.review_deletion") : tr("screen.shadowedhearts.trainer_editor.review_setup"), TrainerEditorTheme.ACCENT);
        if (!deleting) button(reviewDetails ? tr("screen.shadowedhearts.trainer_editor.overview") : tr("screen.shadowedhearts.trainer_editor.details"), detail.right() - 68, area.toolbarY(), 64,
                () -> { reviewDetails = !reviewDetails; detailOffset = 0; rebuild(); });
        button(tr("screen.shadowedhearts.trainer_editor.keep_editing"), frame.x() + 6, area.footerY(), 80, () -> { view = View.EDIT; deleting = false; rebuild(); });
        int refreshWidth = Math.max(88, font.width(TrainerEditorTheme.styled(tr("screen.shadowedhearts.trainer_editor.refresh_server"))) + 10);
        button(tr("screen.shadowedhearts.trainer_editor.refresh_server"), frame.x() + 90, area.footerY(), refreshWidth, this::refreshWorkflow);
        Button apply = button(deleting ? tr("screen.shadowedhearts.trainer_editor.delete") : tr("screen.shadowedhearts.trainer_editor.apply_setup"), frame.right() - 86, area.footerY(), 80, this::apply);
        apply.active = !workflow.main().revision.isBlank() && (deleting || workflow.validation().isEmpty());
    }

    private void apply() {
        if (workflow == null || workflow.nested()) return;
        try {
            JsonObject request;
            if (deleting) {
                request = new JsonObject();
                request.addProperty("action", "delete");
                request.addProperty("category", workflow.main().category);
                request.addProperty("target", target);
                request.addProperty("id", workflow.main().id);
                request.addProperty("revision", workflow.main().revision);
            } else {
                draftMessages = new ArrayList<>(workflow.validation());
                if (!draftMessages.isEmpty()) { showError(draftMessages.getFirst()); return; }
                request = workflow.saveRequest(target);
            }
            send(request);
        } catch (RuntimeException ex) { showError(ex.getMessage()); }
    }

    private void queryBrowse() { query(Query.BROWSE, category, ""); }
    private void queryRoster() { query(Query.ROSTER, rosterCategory, ""); }
    private void query(Query purpose, String type, String id) {
        pendingQuery = purpose; queryCategory = type; queryId = id;
        JsonObject request = new JsonObject();
        request.addProperty("action", "query"); request.addProperty("category", type);
        request.addProperty("target", type.equals("npc") && purpose != Query.BROWSE && purpose != Query.ROSTER ? target : "");
        request.addProperty("search", (purpose == Query.BROWSE || purpose == Query.ROSTER) ? search : "");
        request.addProperty("page", (purpose == Query.BROWSE || purpose == Query.ROSTER) ? page : 0);
        request.addProperty("id", purpose == Query.OPEN_NPC ? target : id);
        send(request);
    }
    private void send(JsonObject request) {
        try {
            pendingRequest = TrainerEditorClient.send(this, request);
            requestSentAt = System.currentTimeMillis();
            mutationPending = !text(request, "action", "query").equals("query");
            waiting = true; saveTimedOut = false; statusError = false; status = mutationPending ? tr("screen.shadowedhearts.trainer_editor.applying_complete_setup") : tr("screen.shadowedhearts.trainer_editor.loading_server_settings");
            rebuild();
        } catch (RuntimeException ex) { mutationPending = false; showError(ex.getMessage()); }
    }
    private void refreshWorkflow() {
        if (workflow == null) return;
        refreshQueue.clear();
        for (JsonElement element : workflow.definitions()) {
            JsonObject d = element.getAsJsonObject();
            refreshQueue.add(new DefinitionQuery(text(d, "category", ""), text(d, "id", "")));
        }
        draftMessages.clear();
        TrainerEditorDraft main = workflow.main();
        query(Query.REFRESH_MAIN, main.category, main.created ? (isDefinition(main.category) ? main.id : "") : main.originalId);
    }
    private void continueRefresh() {
        if (!refreshQueue.isEmpty()) {
            var next = refreshQueue.removeFirst();
            query(Query.REFRESH_DEFINITION, next.category(), next.id());
        } else {
            draftMessages.addAll(workflow.validation());
            status = tr("screen.shadowedhearts.trainer_editor.server_refreshed_your_local_changes_are_retained_review_conflicts_before_applyin");
            rebuild();
        }
    }

    void receive(JsonObject response) {
        if (integer(response, "requestId", -1) != pendingRequest) return;
        TrainerEditorText.localizeResponse(response);
        waiting = false; saveTimedOut = false;
        if (!response.has("ok") || !response.get("ok").getAsBoolean()) {
            mutationPending = false; showError(text(response, "message", tr("screen.shadowedhearts.trainer_editor.the_server_rejected_this_request"))); return;
        }
        if (!text(response, "action", "query").equals("query")) {
            mutationPending = false;
            String savedCategory = workflow == null ? category : workflow.main().category;
            String savedTarget = target;
            workflow = null; inlineLinks.clear(); deleting = false; advanced = false; formOffset = 0; view = View.BROWSE;
            category = savedCategory; status = text(response, "message", tr("screen.shadowedhearts.trainer_editor.saved_setup"));
            if (category.equals("npc") && !savedTarget.isBlank()) query(Query.OPEN_NPC, "npc", savedTarget);
            else { view = View.BROWSE; target = ""; queryBrowse(); }
            return;
        }
        JsonArray receivedDefinitions = response.has("definitions") ? response.getAsJsonArray("definitions") : new JsonArray();
        if (response.has("definitionRevisions")) definitionRevisions = response.getAsJsonObject("definitionRevisions").deepCopy();
        knownDefinitions = receivedDefinitions.deepCopy();
        if (workflow != null) {
            workflow.setDefinitionRevisions(definitionRevisions);
            workflow.rememberDefinitions(receivedDefinitions);
        }
        presets = TrainerEditorDraft.strings(response.get("presets"));
        pools = TrainerEditorDraft.strings(response.get("pools"));
        List<JsonObject> receivedRows = new ArrayList<>();
        if (response.has("rows")) for (JsonElement element : response.getAsJsonArray("rows"))
            if (element.isJsonObject()) receivedRows.add(element.getAsJsonObject());
        JsonObject row = receivedRows.stream().filter(r -> queryId.isBlank() || text(r, "id", "").equals(queryId)).findFirst().orElse(null);
        String latestRevision = text(response, "revision", "");
        if (pendingQuery == Query.INLINE || pendingQuery == Query.REFERENCE) {
            if (row == null) { showError(tr("screen.shadowedhearts.trainer_editor.that_definition_is_no_longer_available_refresh_and_choose_another")); return; }
            JsonObject definition = row.deepCopy();
            definition.addProperty("category", queryCategory); definition.addProperty("revision", latestRevision);
            JsonArray one = new JsonArray(); one.add(definition); workflow.rememberDefinitions(one);
            if (pendingQuery == Query.INLINE) beginInline(queryCategory, inlineCopy ? generatedId(queryCategory) : queryId,
                    row.getAsJsonObject("data"), latestRevision, inlineCopy, row, inlineField, inlineCopy);
            status = tr("screen.shadowedhearts.trainer_editor.definition_loaded_all_edits_remain_local"); rebuild(); return;
        }
        if (pendingQuery == Query.REFRESH_MAIN) {
            draftMessages.addAll(workflow.rebaseMain(workflow.main().created && workflow.main().category.equals("rules") ? null : row, latestRevision));
            continueRefresh(); return;
        }
        if (pendingQuery == Query.REFRESH_DEFINITION) {
            draftMessages.addAll(workflow.rebaseDefinition(queryCategory, queryId, row == null ? null : row.getAsJsonObject("data"), latestRevision));
            continueRefresh(); return;
        }
        if (pendingQuery == Query.OPEN_NPC) {
            if (row == null) { view = View.BROWSE; showError(tr("screen.shadowedhearts.trainer_editor.this_npc_entity_is_unavailable_or_no_longer_loaded")); return; }
            revision = latestRevision;
            category = rosterCategory = "npc";
            status = text(response, "message", ""); statusError = false;
            beginEdit(row, false);
            selected = TrainerEditorRosterSelection.select(rows, rosterCategory, workflow.main(), "");
            rebuild();
            if (rows.isEmpty()) queryRoster();
            return;
        }
        if (pendingQuery == Query.BROWSE) { revision = latestRevision; rosterCategory = category; }
        rosterRevision = latestRevision;
        page = integer(response, "page", 0); pages = Math.max(1, integer(response, "pages", 1)); total = integer(response, "total", 0);
        String previous = selectedRow() == null ? "" : text(selectedRow(), "id", "");
        rows = List.copyOf(receivedRows);
        selected = TrainerEditorRosterSelection.select(rows, rosterCategory,
                view != View.BROWSE && workflow != null ? workflow.main() : null, previous);
        diagnostics = TrainerEditorDraft.strings(response.get("diagnostics"));
        status = text(response, "message", ""); statusError = false;
        rebuild();
    }


    void showError(String message) {
        waiting = false; status = message == null ? tr("screen.shadowedhearts.trainer_editor.could_not_complete_this_operation") : message; statusError = true;
        if (workflow != null && !draftMessages.contains(status)) draftMessages.add(status);
        rebuild();
    }
    @Override public void tick() {
        super.tick();
        if (waiting && !saveTimedOut && System.currentTimeMillis() - requestSentAt > 15000) {
            if (mutationPending) {
                // Never unlock edits while a late save success can still arrive and replace them.
                saveTimedOut = true;
                status = tr("screen.shadowedhearts.trainer_editor.save_outcome_pending_draft_locked_until_the_server_replies_close_is_available");
                rebuild();
            } else {
                // A late read must not reopen an inline editor after the user has moved on.
                pendingRequest = -1;
                showError(tr("screen.shadowedhearts.trainer_editor.no_server_response_yet_your_complete_draft_is_retained"));
            }
        }
        if (rebuildPending) { rebuildPending = false; rebuild(); }
        if (nextAction != null && minecraft.screen == this) { Runnable action = nextAction; nextAction = null; action.run(); }
        if (!waiting && searchChangedAt != 0 && System.currentTimeMillis() - searchChangedAt >= 350) {
            searchChangedAt = 0; page = rowOffset = 0; if (view == View.BROWSE) queryBrowse(); else queryRoster();
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var area = workspace(); var frame = area.frame();
        TrainerEditorTheme.frame(graphics, frame.x(), frame.y(), frame.width(), frame.height());
        graphics.fill(frame.x() + 6, frame.y() + 6, frame.right() - 6, frame.y() + 24, TrainerEditorTheme.INSET);
        if (area.showsRoster()) {
            var rail = area.roster(); TrainerEditorTheme.panel(graphics, rail.x(), rail.y(), rail.width(), rail.height());
        }
        var detail = area.detail(); TrainerEditorTheme.panel(graphics, detail.x(), detail.y(), detail.width(), detail.height());
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        var area = workspace(); var frame = area.frame(); var detail = area.detail();
        graphics.drawString(font, TrainerEditorTheme.styled(tr("screen.shadowedhearts.trainer_editor.trainer_settings")), frame.x() + 10, frame.y() + 11, TrainerEditorTheme.TEXT, true);
        if (view == View.BROWSE) {
            List<String> details = new ArrayList<>(); JsonObject row = selectedRow();
            if (row == null) {
                details.add(waiting ? tr("screen.shadowedhearts.trainer_editor.loading") : tr("screen.shadowedhearts.trainer_editor.no_matching_entries"));
                if (category.equals("npc")) details.add(tr("screen.shadowedhearts.trainer_editor.loaded_compatible_npcs_and_entities_appear_here_use_uuid_lookup_for_a_specific_l"));
            } else {
                details.add(text(row, "name", "")); details.add(text(row, "targetKindLabel", ""));
                details.add(text(row, "subtitle", ""));
                if (category.equals("npc")) details.add(text(row, "targetHelp", ""));
                else { details.add(tr("screen.shadowedhearts.trainer_editor.id_s", text(row, "id", ""))); details.add(tr("screen.shadowedhearts.trainer_editor.source_s", com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.source(text(row, "source", "")).getString())); }
                if (row.has("effective")) {
                    details.add(""); JsonObject effective = row.getAsJsonObject("effective");
                    for (String key : List.of("enabled", "mode", "count", "chance", "pool")) {
                        String value = text(effective, key, "");
                        String source = row.has("sources") ? text(row.getAsJsonObject("sources"), key, "") : "";
                        if (!value.isBlank()) details.add(tr("screen.shadowedhearts.trainer_editor.field_value", TrainerEditorDraft.label(key), source.isBlank() ? display(value) : tr("screen.shadowedhearts.trainer_editor.value_from_source", display(value), source)));
                    }
                }
                details.addAll(TrainerEditorDraft.strings(row.get("details")));
                details.addAll(TrainerEditorDraft.strings(row.get("diagnostics")));
            }
            details.addAll(diagnostics);
            drawLines(graphics, details, detail.x() + 6, detail.y() + 6, detail.width() - 12, detail.height() - 12);
        } else if (workflow != null && view == View.REVIEW) {
            List<String> lines = new ArrayList<>();
            if (deleting) {
                lines.add(tr("screen.shadowedhearts.trainer_editor.review.delete", singular(activeCategory()), active().id));
                lines.add(tr("screen.shadowedhearts.trainer_editor.deleting_a_server_override_reveals_its_datapack_definition_if_one_exists_removin"));
            } else {
                lines.add(scopeDescription()); lines.add(""); lines.addAll(TrainerEditorPresentation.reviewLines(workflow, reviewDetails));
                lines.add(tr("screen.shadowedhearts.trainer_editor.used_when_preparing_future_battles_applying_does_not_rewrite_existing_pokemon_or"));
                if (reviewDetails) lines.add(tr("screen.shadowedhearts.trainer_editor.rct_priority_trainer_before_type_replace_add_convert_entity_overrides_apply_last"));
            }
            for (String message : draftMessages) if (!lines.contains(message)) lines.add(message);
            for (String error : workflow.validation()) if (!lines.contains(error)) lines.add(error);
            drawLines(graphics, lines, detail.x() + 6, detail.y() + 6, detail.width() - 12, detail.height() - 12);
        } else if (workflow != null) {
            String name = text(workflow.activeRow(), "name", active().id.isBlank() ? tr("screen.shadowedhearts.trainer_editor.new_rule") : active().id);
            int titleX = detail.x() + 6;
            int titleY = area.compact() ? area.toolbarY() + 4 : detail.y() + 5;
            if (!area.showsRoster()) titleX += 56;
            int titleRight = detail.right() - (activeCategory().equals("rules") && !workflow.nested() ? 62 : 6);
            graphics.drawString(font, clipped(name, Math.max(1, titleRight - titleX)),
                    titleX, titleY, TrainerEditorTheme.TEXT, true);
            if (!area.compact()) {
                String scope = workflow.nested() ? tr("screen.shadowedhearts.trainer_editor.inline_definition_unsaved") : scopeDescription();
                graphics.drawString(font, clipped(scope, detail.width() - 12),
                        detail.x() + 6, detail.y() + 17, TrainerEditorTheme.MUTED, true);
                var summary = area.summary();
                graphics.fill(summary.x(), summary.y(), summary.right(), summary.bottom(), TrainerEditorTheme.INSET);
                var summaryLines = font.split(TrainerEditorTheme.styled(workflow.requestedSummary()), summary.width() - 8);
                for (int i = 0; i < Math.min(2, summaryLines.size()); i++)
                    graphics.drawString(font, summaryLines.get(i), summary.x() + 4, summary.y() + 3 + i * 9, TrainerEditorTheme.TEXT, true);
            }
        }
        for (Label l : labels) graphics.drawString(font, clipped(l.text(), l.width()), l.x(), l.y(), l.color(), true);
        detailScrollbar.render(graphics, mouseX, mouseY, partialTick);
        String footer = status;
        if (footer.isBlank()) footer = switch (view) {
            case BROWSE -> choosingTarget ? tr("screen.shadowedhearts.trainer_editor.choose_a_target_for_this_draft") : category.equals("npc") ? tr("screen.shadowedhearts.trainer_editor.loaded_compatible_npcs_entities") : tr("screen.shadowedhearts.trainer_editor.rct_trainer_type_rules");
            case EDIT -> fieldCount > formLayout().visibleFieldsCount() ? tr("screen.shadowedhearts.trainer_editor.unsaved_scroll") : tr("screen.shadowedhearts.trainer_editor.unsaved_draft");
            case REVIEW -> tr("screen.shadowedhearts.trainer_editor.nothing_is_saved_until_apply");
        };
        graphics.drawString(font, clipped(footer, frame.width() - 16),
                frame.x() + 8, area.statusY(), statusError ? TrainerEditorTheme.ERROR : TrainerEditorTheme.MUTED, true);
    }

    private Component clipped(String text, int width) {
        return TrainerEditorTheme.styled(font.substrByWidth(TrainerEditorTheme.styled(text), Math.max(0, width)).getString());
    }


    private String scopeDescription() {
        if (workflow == null) return "";
        TrainerEditorDraft main = workflow.main();
        if (main.category.equals("npc")) return tr("screen.shadowedhearts.trainer_editor.scope.npc", workflow.nested() ? target : text(workflow.activeRow(), "name", target));
        if (main.category.equals("rules")) return tr("screen.shadowedhearts.trainer_editor.scope.rct", TrainerEditorText.display(main.value("kind", "trainer")), main.value("target", ""));
        return tr("screen.shadowedhearts.trainer_editor.scope.shared", singular(main.category), main.id);
    }
    private void drawLines(GuiGraphics graphics, List<String> lines, int x, int y, int lineWidth, int areaHeight) {
        int textWidth = Math.max(20, lineWidth - TrainerEditorScrollbar.GUTTER);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) wrapped.add(FormattedCharSequence.EMPTY);
            else wrapped.addAll(font.split(TrainerEditorTheme.styled(line), textWidth));
        }
        detailArea = new TrainerEditorLayout.Bounds(x, y, lineWidth, areaHeight);
        detailScrollbar.setX(x + lineWidth - TrainerEditorScrollbar.WIDTH);
        detailScrollbar.setY(y);
        detailScrollbar.setHeight(areaHeight);
        detailScrollbar.setRange(wrapped.size(), Math.max(1, areaHeight / 11), detailOffset);
        detailOffset = detailScrollbar.position();
        graphics.enableScissor(x, y, x + textWidth, y + areaHeight);
        for (int i = detailOffset; i < wrapped.size() && (i - detailOffset) * 11 < areaHeight; i++)
            graphics.drawString(font, wrapped.get(i), x, y + (i - detailOffset) * 11, TrainerEditorTheme.TEXT, true);
        graphics.disableScissor();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (waiting || vertical == 0) return false;
        int direction = vertical > 0 ? -1 : 1;
        if (detailArea != null && contains(detailArea, x, y)) return detailScrollbar.scrollBy(direction * 3);
        if (view == View.EDIT) {
            var layout = formLayout();
            if (x >= layout.contentLeft() && x < layout.contentLeft() + layout.formWidth()
                    && y >= layout.fieldTop() && y < layout.editBottom())
                return formScrollbar.scrollBy(direction);
        }
        if (workspace().showsRoster() && contains(workspace().roster(), x, y)) return listScrollbar.scrollBy(direction);
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    private static boolean contains(TrainerEditorLayout.Bounds bounds, double x, double y) {
        return x >= bounds.x() && x < bounds.right() && y >= bounds.y() && y < bounds.bottom();
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) {
            listScrollbar.onRelease(x, y);
            formScrollbar.onRelease(x, y);
            detailScrollbar.onRelease(x, y);
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public void onClose() {
        if (waiting && !mutationPending) {
            status = tr("screen.shadowedhearts.trainer_editor.waiting_for_the_current_lookup_to_finish");
            return;
        }
        if (waiting && mutationPending) {
            if (!saveTimedOut) { status = tr("screen.shadowedhearts.trainer_editor.waiting_for_the_server_to_finish_applying_this_setup"); return; }
            ownedChild = new ConfirmScreen(leave -> {
                if (leave) { TrainerEditorClient.closed(this); minecraft.setScreen(parent); }
                else minecraft.setScreen(this);
            }, Component.literal(tr("screen.shadowedhearts.trainer_editor.leave_while_the_save_is_pending")),
                    Component.literal(tr("screen.shadowedhearts.trainer_editor.the_server_may_still_apply_this_setup_reopen_and_inspect_the_server_values_befor")),
                    Component.literal(tr("screen.shadowedhearts.trainer_editor.leave_editor")), Component.literal(tr("screen.shadowedhearts.trainer_editor.keep_waiting")));
            minecraft.setScreen(ownedChild);
            return;
        }
        if (workflow != null && workflow.nested()) { cancelInline(); return; }
        Runnable close = () -> { TrainerEditorClient.closed(this); minecraft.setScreen(parent); };
        if (workflow != null && workflow.dirty()) confirm(tr("screen.shadowedhearts.trainer_editor.discard_this_setup"), tr("screen.shadowedhearts.trainer_editor.unsaved_rule_preset_and_pool_edits_will_be_discarded_together"), close);
        else close.run();
    }
    @Override public boolean isPauseScreen() { return false; }
    boolean isOpenAt(Screen screen) { return screen == this || screen == ownedChild; }
    private boolean canCreate() { return category.equals("rules") || isDefinition(category); }
    private static boolean isDefinition(String kind) { return kind.equals("presets") || kind.equals("pools"); }
    private JsonObject selectedRow() { return selected < 0 || selected >= rows.size() ? null : rows.get(selected); }
    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(TrainerEditorTheme.button(text, x, y, Math.max(16, w), TrainerEditorTheme.CONTROL_HEIGHT, action));
    }
    private EditBox textBox(int x, int y, int w, String value, Consumer<String> changed, int limit, String label) {
        EditBox box = TrainerEditorTheme.textBox(font, x, y, Math.max(20, w), TrainerEditorTheme.CONTROL_HEIGHT, Component.literal(label));
        box.setMaxLength(limit); box.setValue(value); box.setTextColor(TrainerEditorTheme.TEXT); box.setResponder(changed);
        return addRenderableWidget(box);
    }
    private void choose(String title, List<TrainerEditorChoiceScreen.Choice> choices, Consumer<String> selected) {
        ownedChild = new TrainerEditorChoiceScreen(this, title, choices, selected); minecraft.setScreen(ownedChild);
    }
    private static String generatedId(String type) { return TrainerEditorWorkflow.newId(type); }
    private static List<TrainerEditorChoiceScreen.Choice> values(String... values) {
        return Arrays.stream(values).map(v -> new TrainerEditorChoiceScreen.Choice(v, display(v))).toList();
    }
    private static List<TrainerEditorChoiceScreen.Choice> resourceChoices(List<String> ids, boolean pool) {
        List<TrainerEditorChoiceScreen.Choice> choices = new ArrayList<>();
        if (pool) choices.add(new TrainerEditorChoiceScreen.Choice("inherit", tr("screen.shadowedhearts.trainer_editor.inherited_pool")));
        choices.add(new TrainerEditorChoiceScreen.Choice(pool ? "none" : "", pool ? tr("screen.shadowedhearts.trainer_editor.no_pool_team_copy_fallback") : tr("screen.shadowedhearts.trainer_editor.no_preset")));
        ids.stream().distinct().sorted().forEach(id -> choices.add(new TrainerEditorChoiceScreen.Choice(id, id)));
        return choices;
    }
    private static List<TrainerEditorChoiceScreen.Choice> speciesChoices() {
        List<TrainerEditorChoiceScreen.Choice> choices = new ArrayList<>();
        try { PokemonSpecies.INSTANCE.getSpecies().forEach(s -> choices.add(new TrainerEditorChoiceScreen.Choice(
                s.getResourceIdentifier().toString(), tr("screen.shadowedhearts.trainer_editor.species_option",
                s.getTranslatedName(), s.getResourceIdentifier())))); }
        catch (RuntimeException ignored) {}
        choices.sort(java.util.Comparator.comparing(TrainerEditorChoiceScreen.Choice::label)); return choices;
    }
    private static void setNumber(JsonObject object, String key, String value) {
        try { object.addProperty(key, Integer.parseInt(value)); } catch (NumberFormatException ex) { object.addProperty(key, value); }
    }
    private static String text(JsonObject object, String key, String fallback) { return object == null ? fallback : TrainerEditorDraft.text(object, key, fallback); }
    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; } catch (RuntimeException ex) { return fallback; }
    }
    private static String display(String value) { return TrainerEditorText.display(value); }
    private static String singular(String category) { return TrainerEditorText.noun(category); }
}
