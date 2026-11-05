package com.jayemceekay.shadowedhearts.common.editor;

import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Resolves the prospective graph without publishing temporary presets or pools to the live registries. */
final class TrainerEditorDraftReferences {
    private TrainerEditorDraftReferences() {}

    static void validate(List<NPCShadowProfile.Layer> layers, Map<String, List<String>> presets, Set<String> pools,
                         String self, List<String> selfTags, Function<String, List<String>> existingPreset,
                         Consumer<String> existingPool) {
        var resolution = NPCShadowProfile.resolve(layers, referenced -> {
            if (referenced.equals(self)) return selfTags;
            if (presets.containsKey(referenced)) return presets.get(referenced);
            return existingPreset.apply(referenced);
        });
        if (!resolution.valid()) throw new TrainerEditorMessageException(TrainerEditorMessages.join(resolution.errors()));
        String pool = resolution.profile().poolId();
        if (pool != null && !pools.contains(pool)) existingPool.accept(pool);
    }
}
