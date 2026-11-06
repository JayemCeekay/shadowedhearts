package com.jayemceekay.shadowedhearts.client.aura.reader;

import java.util.List;
import java.util.Map;

/** Separates an overlapping navigation selection and saved pin without changing their bearings. */
public final class AuraReaderCompassPlacement {
    private AuraReaderCompassPlacement() {
    }

    public static Map<String, Integer> verticalOffsets(List<Marker> markers) {
        Marker selected = markers.stream().filter(Marker::navigationSelected).findFirst().orElse(null);
        if (selected == null) return Map.of();
        Marker pin = markers.stream().filter(marker -> marker.pinned() && !marker.id().equals(selected.id()))
                .filter(marker -> Math.abs(marker.x() - selected.x()) <= marker.radius() + selected.radius() + 4)
                .findFirst().orElse(null);
        if (pin == null) return Map.of();
        return Map.of(selected.id(), -pin.radius() - 1, pin.id(), selected.radius() + 1);
    }

    public record Marker(String id, int x, int radius, boolean navigationSelected, boolean pinned) {
        public Marker {
            radius = Math.max(1, radius);
        }
    }
}
