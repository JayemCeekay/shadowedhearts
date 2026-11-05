package com.jayemceekay.shadowedhearts.common.aura;

import java.util.List;

/** Deterministic navigation through signals already authorized by their providers. */
public final class AuraReaderSignalSelection {
    private AuraReaderSignalSelection() {}

    public static String cycle(List<String> ids, String current, boolean previous) {
        List<String> available = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (available.isEmpty()) return "";
        int index = available.indexOf(current);
        if (index < 0) return available.get(previous ? available.size() - 1 : 0);
        return available.get(Math.floorMod(index + (previous ? -1 : 1), available.size()));
    }
}
