package com.jayemceekay.shadowedhearts.common.editor;

import static com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.*;

import java.util.ArrayList;
import java.util.List;

/** All validation/preparation precedes commit; failed commits restore each attempted write in reverse order. */
final class TrainerEditorTransaction {
    record Change(Runnable apply, Runnable rollback) {}

    private TrainerEditorTransaction() {}

    static void commit(List<Change> prepared) {
        List<Change> attempted = new ArrayList<>();
        try {
            for (Change change : prepared) {
                attempted.add(change);
                change.apply().run();
            }
        } catch (RuntimeException failure) {
            boolean rollbackFailed = false;
            for (int index = attempted.size() - 1; index >= 0; index--) {
                try { attempted.get(index).rollback().run(); }
                catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    rollbackFailed = true;
                }
            }
            if (rollbackFailed) throw TrainerEditorMessageException.state(message("rollback_failed" , TrainerEditorMessageException.component(failure)), failure);
            throw failure;
        }
    }
}
