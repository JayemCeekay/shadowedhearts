package com.jayemceekay.shadowedhearts.common.shadow;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/** Dependency-free eligibility policy and weak, identity-based observations. */
final class NPCShadowTargetPolicy {
    private NPCShadowTargetPolicy() {}

    enum Kind {
        UNSUPPORTED("Unsupported entity", "Select a loaded Cobblemon NPC, RCT trainer, Pokemon sender, or an entity observed in a Cobblemon battle."),
        COBBLEMON_NPC("Cobblemon NPC", "Settings apply when this NPC's Cobblemon battle party is prepared."),
        RCT_TRAINER("RCT trainer", "Local settings override this trainer's matching RCT rules."),
        POKEMON_SENDER("Pokemon sender", "Send/recall capability detected. Settings apply only if this entity uses an entity-backed Cobblemon battle actor."),
        OBSERVED_ACTOR("Observed battle entity", "Seen as an entity-backed Cobblemon battle actor during this load. Discovery resets when the entity unloads or the server restarts.");

        private final String label;
        private final String help;
        Kind(String label, String help) { this.label = label; this.help = help; }
        String label() { return label; }
        String help() { return help; }
    }

    static Kind classify(boolean excluded, boolean cobblemonNpc, boolean rctTrainer,
                         boolean pokemonSender, boolean observedActor) {
        if (excluded) return Kind.UNSUPPORTED;
        if (cobblemonNpc) return Kind.COBBLEMON_NPC;
        if (rctTrainer) return Kind.RCT_TRAINER;
        if (observedActor) return Kind.OBSERVED_ACTOR;
        if (pokemonSender) return Kind.POKEMON_SENDER;
        return Kind.UNSUPPORTED;
    }

    /** Does not retain entities, share eligibility between equal objects, or persist a world tag. */
    static final class Observations<T> {
        private final ReferenceQueue<T> collected = new ReferenceQueue<>();
        private final Set<IdentityReference<T>> entries = new HashSet<>();

        synchronized void observe(T entity) {
            if (entity == null) return;
            expungeCollected();
            entries.add(new IdentityReference<>(entity, collected));
        }

        synchronized boolean contains(T entity) {
            expungeCollected();
            return entity != null && entries.contains(new IdentityReference<>(entity, null));
        }

        private void expungeCollected() {
            for (var reference = collected.poll(); reference != null; reference = collected.poll()) entries.remove(reference);
        }
    }

    private static final class IdentityReference<T> extends WeakReference<T> {
        private final int hash;
        IdentityReference(T value, ReferenceQueue<T> queue) { super(value, queue); hash = System.identityHashCode(value); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            T value = get();
            return other instanceof IdentityReference<?> reference && value != null && value == reference.get();
        }
    }
}
