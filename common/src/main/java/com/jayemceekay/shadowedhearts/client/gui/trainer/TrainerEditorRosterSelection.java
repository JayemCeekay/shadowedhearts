package com.jayemceekay.shadowedhearts.client.gui.trainer;

import com.google.gson.JsonObject;

import java.util.List;

/** A roster highlight identifies the draft being edited, never an unrelated first search result. */
final class TrainerEditorRosterSelection {
    private TrainerEditorRosterSelection() {}

    static int select(List<JsonObject> rows, String rosterCategory, TrainerEditorDraft draft, String previous) {
        String id = previous;
        if (draft != null) {
            if (draft.category.equals("rules") && (rosterCategory.equals("trainers") || rosterCategory.equals("types"))) {
                String expected = draft.value("kind", "trainer").equals("type") ? "types" : "trainers";
                if (!rosterCategory.equals(expected)) return -1;
                id = draft.value("target", "");
            } else {
                if (!rosterCategory.equals(draft.category)) return -1;
                id = draft.originalId;
            }
        }
        for (int i = 0; i < rows.size(); i++)
            if (TrainerEditorDraft.text(rows.get(i), "id", "").equals(id)) return i;
        return draft != null || rows.isEmpty() ? -1 : 0;
    }
}
