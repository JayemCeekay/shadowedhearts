package com.jayemceekay.shadowedhearts.common.aura;

import java.util.List;

public record AuraReaderPulseResult(
        boolean accepted,
        List<AuraReading> readings
) {
    public AuraReaderPulseResult {
        readings = readings == null ? List.of() : List.copyOf(readings);
    }

    public static AuraReaderPulseResult accepted(List<AuraReading> readings) {
        return new AuraReaderPulseResult(true, readings);
    }

    public static AuraReaderPulseResult rejected() {
        return new AuraReaderPulseResult(false, List.of());
    }
}
