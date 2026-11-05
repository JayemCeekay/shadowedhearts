package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Compact review copy. The expanded view retains every field in the original change list. */
public final class TrainerEditorPresentation {
    private static final int OVERVIEW_CHANGES = 4;

    private TrainerEditorPresentation() {}

    public static List<String> reviewLines(TrainerEditorWorkflow workflow, boolean details) {
        List<String> lines = new ArrayList<>();
        lines.add(workflow.requestedSummary());
        TrainerEditorDraft main = workflow.main();
        JsonArray definitions = workflow.definitions();
        List<String> actions = new ArrayList<>();
        if (main.created) actions.add(tr("screen.shadowedhearts.trainer_editor.review.create", TrainerEditorText.noun(main.category)));
        else if (workflow.dirty()) actions.add(tr("screen.shadowedhearts.trainer_editor.review.update", TrainerEditorText.noun(main.category)));
        for (String category : List.of("presets", "pools")) {
            for (boolean created : List.of(true, false)) {
                long count = definitions.asList().stream().map(JsonElement::getAsJsonObject)
                        .filter(definition -> text(definition, "category").equals(category)
                                && text(definition, "created").equals(Boolean.toString(created))).count();
                if (count > 0) actions.add(tr("screen.shadowedhearts.trainer_editor.review." + category + (created ? ".new" : ".updated") + (count == 1 ? ".one" : ".many"), count));
            }
        }
        lines.add(actions.isEmpty() ? tr("screen.shadowedhearts.trainer_editor.no_changes") : tr("screen.shadowedhearts.trainer_editor.review.apply", String.join(" + ", actions)));

        if (!main.created && main.dirty() && isDefinition(main.category))
            lines.add(sharedWarning(main.category, main.id));
        for (JsonElement element : definitions) {
            JsonObject definition = element.getAsJsonObject();
            if (!text(definition, "created").equals("true"))
                lines.add(sharedWarning(text(definition, "category"), text(definition, "id")));
        }

        if (details) {
            lines.add(tr("screen.shadowedhearts.trainer_editor.copy.all_field_changes"));
            if (!main.id.equals(main.originalId)) lines.add(tr("screen.shadowedhearts.trainer_editor.review.id_change", main.originalId, main.id));
            lines.addAll(workflow.changes());
            return List.copyOf(lines);
        }

        List<String> concise = new ArrayList<>();
        if (!main.id.equals(main.originalId)) concise.add(tr("screen.shadowedhearts.trainer_editor.review.id_change", main.originalId, main.id));
        concise.addAll(workflow.overviewChanges());
        lines.addAll(concise.subList(0, Math.min(OVERVIEW_CHANGES, concise.size())));
        if (concise.size() > OVERVIEW_CHANGES)
            lines.add(tr("screen.shadowedhearts.trainer_editor.review.more", concise.size() - OVERVIEW_CHANGES));
        if (!definitions.isEmpty()) lines.add(tr("screen.shadowedhearts.trainer_editor.copy.inline_definition_settings_are_included_open_details_to_inspect_them"));
        return List.copyOf(lines);
    }

    private static String sharedWarning(String category, String id) {
        return tr("screen.shadowedhearts.trainer_editor.review.shared", TrainerEditorText.noun(category), id);
    }

    private static boolean isDefinition(String category) { return category.equals("presets") || category.equals("pools"); }
    private static String text(JsonObject object, String key) { return TrainerEditorDraft.text(object, key, ""); }
}
