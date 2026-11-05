package com.jayemceekay.shadowedhearts.client.aura;

import java.util.Comparator;
import java.util.List;

/** Screen-space ordering only; navigation selection is independent of a saved pin. */
public final class AuraReaderCompassSelection {
    private AuraReaderCompassSelection() {
    }

    public static List<Candidate> order(List<Candidate> readings, String selectedId) {
        if (readings == null || readings.isEmpty()) return List.of();
        String navigationId = selectedId == null ? "" : selectedId;
        return readings.stream()
                .filter(reading -> !reading.outOfDimension())
                .sorted(Comparator
                        .comparing((Candidate reading) -> !reading.id().equals(navigationId))
                        .thenComparing(reading -> !reading.pinned())
                        .thenComparing(reading -> !reading.locked())
                        .thenComparing(reading -> !reading.focused())
                        .thenComparing(Comparator.comparingInt(Candidate::priority).reversed())
                        .thenComparing(Comparator.comparingDouble(Candidate::strength).reversed())
                        .thenComparing(Candidate::id))
                .toList();
    }

    /** A missing navigation target must not silently turn the readout into a different pin. */
    public static String primaryId(List<Candidate> readings, String selectedId) {
        List<Candidate> ordered = order(readings, selectedId);
        if (selectedId != null && !selectedId.isBlank()) {
            return ordered.stream().filter(reading -> reading.id().equals(selectedId))
                    .map(Candidate::id).findFirst().orElse(null);
        }
        return ordered.stream().filter(reading -> reading.pinned() || reading.locked())
                .map(Candidate::id).findFirst().orElse(null);
    }

    public record Candidate(String id, boolean outOfDimension, boolean pinned,
                            boolean locked, boolean focused, int priority, float strength) {
        public Candidate {
            id = id == null ? "" : id;
        }
    }
}
