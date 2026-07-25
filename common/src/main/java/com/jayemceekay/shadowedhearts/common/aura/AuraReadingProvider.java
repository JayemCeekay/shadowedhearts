package com.jayemceekay.shadowedhearts.common.aura;

import java.util.List;

public interface AuraReadingProvider {
    default boolean supports(AuraScanContext context) {
        return true;
    }

    List<AuraReading> scan(AuraScanContext context);

    default int priority() {
        return 0;
    }
}
