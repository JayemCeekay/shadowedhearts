package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;

import java.util.List;
import java.util.Objects;

/** Compass bearings and navigation selections never authorize a reticle scan. */
public final class AuraReaderFocusSelector {
    private AuraReaderFocusSelector() {
    }

    /** The caller supplies the nearest unobstructed entity hit inside the server's scan range. */
    public static String select(List<Candidate> candidates, String reticleTargetId) {
        Objects.requireNonNull(candidates, "candidates");
        if (reticleTargetId == null || reticleTargetId.isBlank()) {
            return null;
        }
        for (Candidate candidate : candidates) {
            if (candidate.id().equals(reticleTargetId)
                    && candidate.type() == AuraReadingType.SHADOW_POKEMON
                    && !candidate.outOfDimension()) {
                return candidate.id();
            }
        }
        return null;
    }

    public record Candidate(String id, AuraReadingType type, boolean outOfDimension) {
        public Candidate {
            Objects.requireNonNull(id, "id");
        }
    }
}
