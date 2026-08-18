package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuraReaderReadingLifecycleTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void pulseFadesOnlyDuringItsFinalWindow() {
        assertEquals(1.0f, AuraReaderReadingLifecycle.pulseAlpha(200L, 100L, 40), EPSILON);
        assertEquals(1.0f, AuraReaderReadingLifecycle.pulseAlpha(200L, 160L, 40), EPSILON);
        assertEquals(0.5f, AuraReaderReadingLifecycle.pulseAlpha(200L, 180L, 40), EPSILON);
        assertEquals(0.0f, AuraReaderReadingLifecycle.pulseAlpha(200L, 200L, 40), EPSILON);
    }

    @Test
    void nonExpiringLegacyPulseRemainsVisible() {
        assertFalse(AuraReaderReadingLifecycle.isExpired(0L, 500L));
        assertEquals(1.0f, AuraReaderReadingLifecycle.pulseAlpha(0L, 500L), EPSILON);
    }

    @Test
    void passiveTrackedAndLockedLayersSupersedePulse() {
        assertFalse(AuraReaderReadingLifecycle.supersedesPulse(AuraReadingSource.PULSE));
        assertTrue(AuraReaderReadingLifecycle.supersedesPulse(AuraReadingSource.PASSIVE));
        assertTrue(AuraReaderReadingLifecycle.supersedesPulse(AuraReadingSource.TRACKED));
        assertTrue(AuraReaderReadingLifecycle.supersedesPulse(AuraReadingSource.LOCKED));
    }

    @Test
    void ordersPresentationLayersDeterministically() {
        assertTrue(AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.LOCKED, false)
                > AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.TRACKED, false));
        assertTrue(AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.TRACKED, false)
                > AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.PASSIVE, false));
        assertTrue(AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.PASSIVE, false)
                > AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.PULSE, false));
        assertTrue(AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.PULSE, false)
                > AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.INTERFERENCE, false));
        assertEquals(
                AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.LOCKED, false),
                AuraReaderReadingLifecycle.presentationPriority(AuraReadingSource.PASSIVE, true)
        );
    }

    @Test
    void missingNonPulseLayerGetsShortGraceFade() {
        assertEquals(1.0f, AuraReaderReadingLifecycle.staleAlpha(-1L, 100L), EPSILON);
        assertEquals(1.0f, AuraReaderReadingLifecycle.staleAlpha(100L, 100L), EPSILON);
        assertEquals(0.5f, AuraReaderReadingLifecycle.staleAlpha(100L, 110L), EPSILON);
        assertEquals(0.0f, AuraReaderReadingLifecycle.staleAlpha(100L, 120L), EPSILON);
        assertFalse(AuraReaderReadingLifecycle.isStaleExpired(100L, 119L));
        assertTrue(AuraReaderReadingLifecycle.isStaleExpired(100L, 120L));
    }
}
