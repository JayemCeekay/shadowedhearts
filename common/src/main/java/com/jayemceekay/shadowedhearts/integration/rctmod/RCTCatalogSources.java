package com.jayemceekay.shadowedhearts.integration.rctmod;

import net.minecraft.network.chat.Component;
import static com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.message;

import java.util.*;

/** Pure index of the resources accepted by RCT's pack scan; registry rows remain authoritative. */
final class RCTCatalogSources {
    private final Map<String, List<String>> packsByPath = new HashMap<>();
    private final Map<String, SortedSet<String>> pathsById = new TreeMap<>();

    void add(String path, String pack) {
        if (!path.endsWith(".json")) return;
        for (String prefix : List.of("trainers/", "trainer_types/")) {
            if (!path.startsWith(prefix)) continue;
            String filename = path.substring(path.lastIndexOf('/') + 1);
            String id = filename.substring(0, filename.length() - ".json".length()).toLowerCase(Locale.ROOT);
            pathsById.computeIfAbsent(prefix + id, ignored -> new TreeSet<>()).add(path);
            packsByPath.computeIfAbsent(path, ignored -> new ArrayList<>()).add(pack);
        }
    }

    List<String> paths(String prefix, String id) {
        return List.copyOf(pathsById.getOrDefault(prefix + id, Collections.emptySortedSet()));
    }

    String sourcePack(String prefix, String id) {
        List<String> paths = paths(prefix, id);
        String expected = prefix + id + ".json";
        if (paths.size() != 1 || !paths.getFirst().equals(expected)) return "";
        return packsByPath.get(expected).getLast();
    }

    List<Component> summaries(String prefix, Set<String> loadedIds, String kind) {
        List<String> missing = new ArrayList<>();
        List<String> collisions = new ArrayList<>();
        pathsById.forEach((key, paths) -> {
            if (!key.startsWith(prefix)) return;
            String id = key.substring(prefix.length());
            if (!loadedIds.contains(id)) missing.add(id);
            if (paths.size() > 1) collisions.add(id);
        });
        List<Component> diagnostics = new ArrayList<>();
        if (!missing.isEmpty()) diagnostics.add(message("rct.diagnostic.unloaded_definitions", missing.size(), message("rct.kind." + kind), examples(missing)));
        if (!collisions.isEmpty()) diagnostics.add(message("rct.diagnostic.conflicting_definitions", collisions.size(), message("rct.kind." + kind), examples(collisions)));
        return List.copyOf(diagnostics);
    }

    private static String examples(List<String> values) {
        return String.join(", ", values.subList(0, Math.min(4, values.size())));
    }
}
