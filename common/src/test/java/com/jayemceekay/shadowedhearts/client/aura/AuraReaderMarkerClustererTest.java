package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuraReaderMarkerClustererTest {
    @Test
    void clustersNearbyMarkersOfTheSameReadingType() {
        var first = marker("first", AuraReadingType.SHADOW_POKEMON, -0.12f, false, false);
        var second = marker("second", AuraReadingType.SHADOW_POKEMON, -0.08f, false, false);

        var clusters = AuraReaderMarkerClusterer.cluster(List.of(first, second), 0.05f);

        assertEquals(1, clusters.size());
        assertEquals(2, clusters.getFirst().count());
    }

    @Test
    void keepsDifferentReadingTypesSeparate() {
        var shadow = marker("shadow", AuraReadingType.SHADOW_POKEMON, 0.1f, false, false);
        var core = marker("core", AuraReadingType.UMBRAFALL_CORE, 0.11f, false, false);

        assertEquals(2, AuraReaderMarkerClusterer.cluster(List.of(shadow, core), 0.05f).size());
    }

    @Test
    void selectedAndLockedMarkersAreNeverHiddenInsideACluster() {
        var ordinary = marker("ordinary", AuraReadingType.SHADOW_POKEMON, 0.0f, false, false);
        var selected = marker("selected", AuraReadingType.SHADOW_POKEMON, 0.01f, true, false);
        var locked = marker("locked", AuraReadingType.SHADOW_POKEMON, 0.02f, false, true);

        var clusters = AuraReaderMarkerClusterer.cluster(List.of(ordinary, selected, locked), 0.05f);

        assertEquals(3, clusters.size());
        assertSame(selected, clusters.get(1).representative());
        assertSame(locked, clusters.get(2).representative());
    }

    private static AuraReaderMarkerClusterer.Marker marker(
            String id,
            AuraReadingType type,
            float x,
            boolean selected,
            boolean locked
    ) {
        return new AuraReaderMarkerClusterer.Marker(
                id,
                type,
                AuraReadingSource.PASSIVE,
                x,
                0,
                selected,
                locked
        );
    }
}
