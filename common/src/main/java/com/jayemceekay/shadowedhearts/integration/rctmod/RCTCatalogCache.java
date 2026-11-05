package com.jayemceekay.shadowedhearts.integration.rctmod;

import net.minecraft.network.chat.Component;
import static com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.message;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Checks live registry identities at most once per second; never publishes partially reloaded data. */
final class RCTCatalogCache {
    private WeakReference<Object> owner = new WeakReference<>(null);
    private WeakReference<Object> resources = new WeakReference<>(null);
    private Map<String, WeakReference<Object>> identities = Map.of();
    private long checkedAt = Long.MIN_VALUE;
    private boolean refreshRequired;
    private RCTTrainerCatalog completed;

    RCTTrainerCatalog get(Object server, Object resourceManager, boolean loading, long tick,
                          Supplier<Map<String, Object>> registryIdentities, Supplier<RCTTrainerCatalog> build) {
        if (server != owner.get()) {
            owner = new WeakReference<>(server);
            resources = new WeakReference<>(null);
            identities = Map.of();
            completed = null;
            checkedAt = Long.MIN_VALUE;
            refreshRequired = true;
        }
        if (loading) {
            refreshRequired = true;
            RCTTrainerCatalog previous = completed == null
                    ? new RCTTrainerCatalog(true, false, List.of(), List.of(), List.of()) : completed;
            return previous.withStatus(true, message("rct.diagnostic.reloading"));
        }
        if (completed != null && resources.get() == resourceManager && !refreshRequired
                && tick >= checkedAt && tick - checkedAt < 20) return completed;

        Map<String, Object> current = registryIdentities.get();
        if (completed != null && resources.get() == resourceManager && !refreshRequired && sameObjects(identities, current)) {
            checkedAt = tick;
            return completed;
        }
        RCTTrainerCatalog next = build.get();
        // Commit only after every resource and registry read succeeds.
        Map<String, WeakReference<Object>> references = new java.util.HashMap<>();
        current.forEach((id, value) -> references.put(id, new WeakReference<>(value)));
        identities = Map.copyOf(references);
        resources = new WeakReference<>(resourceManager);
        checkedAt = tick;
        refreshRequired = false;
        completed = next;
        return next;
    }

    RCTTrainerCatalog failure(Object server, Component diagnostic) {
        if (server == owner.get() && completed != null) {
            return completed.withStatus(true, message("rct.diagnostic.cached_failure", diagnostic));
        }
        return new RCTTrainerCatalog(true, true, List.of(), List.of(), List.of(diagnostic));
    }

    private static boolean sameObjects(Map<String, WeakReference<Object>> previous, Map<String, Object> current) {
        if (previous.size() != current.size()) return false;
        for (var entry : current.entrySet()) {
            if (!previous.containsKey(entry.getKey()) || previous.get(entry.getKey()).get() != entry.getValue()) return false;
        }
        return true;
    }
}
