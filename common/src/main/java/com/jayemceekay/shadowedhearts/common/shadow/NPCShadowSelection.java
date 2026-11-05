package com.jayemceekay.shadowedhearts.common.shadow;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Bounded party selection shared by runtime injection and focused tests. */
final class NPCShadowSelection {
    private NPCShadowSelection() { }

    static <T> List<T> appendCopies(List<T> party, int count, boolean unique,
                                    Function<T, T> copy, Function<T, String> species,
                                    Predicate<T> eligible) {
        if (count <= 0 || party.isEmpty()) return List.of();
        int requested = Math.min(count, Math.max(0, 6 - party.size()));
        List<T> selected = new ArrayList<>();
        // Every source gets a chance for each requested slot. Rejections never extend this budget.
        for (int attempt = 0; attempt < party.size() * requested && selected.size() < requested; attempt++) {
            T source = party.get(attempt % party.size());
            if (source == null) continue;
            if (unique && containsSpecies(party, species.apply(source), -1, species)) continue;
            T candidate = copy.apply(source);
            if (candidate == null || !eligible.test(candidate)) continue;
            if (unique && containsSpecies(selected, species.apply(candidate), -1, species)) continue;
            selected.add(candidate);
        }
        return selected;
    }

    static <T> boolean containsSpecies(List<T> party, String candidate, int excludedSlot,
                                       Function<T, String> species) {
        if (candidate == null) return false;
        String normalized = normalize(candidate);
        for (int slot = 0; slot < party.size(); slot++) {
            if (slot == excludedSlot || party.get(slot) == null) continue;
            String current = species.apply(party.get(slot));
            if (current != null && normalized.equals(normalize(current))) return true;
        }
        return false;
    }

    static <E, T> List<T> poolCandidates(List<E> entries, int count, ToIntFunction<E> weight,
                                        Function<E, T> create, Predicate<T> eligible,
                                        Function<T, String> species, Set<String> present,
                                        boolean unique, Random rng) {
        List<E> remaining = new ArrayList<>(entries);
        List<T> result = new ArrayList<>();
        Set<String> selectedSpecies = new HashSet<>();
        present.forEach(id -> selectedSpecies.add(normalize(id)));
        int requested = Math.min(6, Math.max(0, count));
        int budget = remaining.size() + requested;
        for (int attempt = 0; attempt < budget && result.size() < requested && !remaining.isEmpty(); attempt++) {
            long total = 0;
            for (E entry : remaining) total += Math.max(1, weight.applyAsInt(entry));
            long roll = rng.nextLong(total);
            int index = 0;
            while (index < remaining.size() - 1) {
                roll -= Math.max(1, weight.applyAsInt(remaining.get(index)));
                if (roll < 0) break;
                index++;
            }
            T candidate = create.apply(remaining.get(index));
            String id = candidate == null ? null : species.apply(candidate);
            if (candidate == null || !eligible.test(candidate) || (unique && (id == null
                    || selectedSpecies.contains(normalize(id))))) {
                // Retain the selected entry index; candidate factories can return independent copies.
                remaining.remove(index);
                continue;
            }
            result.add(candidate);
            if (unique) selectedSpecies.add(normalize(id));
        }
        return result;
    }

    static String normalize(String species) {
        String normalized = species.toLowerCase(Locale.ROOT);
        return normalized.startsWith("cobblemon:") ? normalized.substring("cobblemon:".length()) : normalized;
    }
}
