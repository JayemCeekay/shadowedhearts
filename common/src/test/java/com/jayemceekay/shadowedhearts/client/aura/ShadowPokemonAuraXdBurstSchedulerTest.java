package com.jayemceekay.shadowedhearts.client.aura;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowPokemonAuraXdBurstSchedulerTest {

    @Test
    void simultaneousSelectionsRemainSpatiallyDistinctWhenGeometryAllows() {
        List<Vec3> candidates = List.of(
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(0.2, 0.0, 0.0),
                new Vec3(3.0, 0.0, 0.0),
                new Vec3(-3.0, 0.0, 0.0),
                new Vec3(0.0, 0.0, 3.0));
        List<Vec3> occupied = new ArrayList<>();
        Set<Integer> selectedIndices = new HashSet<>();
        double minimumSeparation = 2.0;

        for (int emitter = 0; emitter < 4; emitter++) {
            int selected = ShadowPokemonAuraSystem.selectXdBurstAnchorIndex(
                    candidates,
                    occupied,
                    emitter,
                    minimumSeparation);
            assertTrue(selected >= 0, "expected another separated emitter");
            assertTrue(selectedIndices.add(selected),
                    "one simultaneous batch must not reuse an anchor");

            Vec3 selectedPosition = candidates.get(selected);
            for (Vec3 previous : occupied) {
                assertTrue(selectedPosition.distanceTo(previous)
                                >= minimumSeparation,
                        "simultaneous emitters must occupy distinct model regions");
            }
            occupied.add(selectedPosition);
        }
    }

    @Test
    void selectionUsesFarthestEligiblePointAndCursorBreaksTies() {
        List<Vec3> occupied = List.of(Vec3.ZERO);
        List<Vec3> unequal = List.of(
                new Vec3(0.5, 0.0, 0.0),
                new Vec3(2.0, 0.0, 0.0),
                new Vec3(5.0, 0.0, 0.0));
        assertEquals(2,
                ShadowPokemonAuraSystem.selectXdBurstAnchorIndex(
                        unequal, occupied, 0, 0.25));

        List<Vec3> tied = List.of(
                new Vec3(4.0, 0.0, 0.0),
                new Vec3(-4.0, 0.0, 0.0));
        assertEquals(0,
                ShadowPokemonAuraSystem.selectXdBurstAnchorIndex(
                        tied, occupied, 0, 1.0));
        assertEquals(1,
                ShadowPokemonAuraSystem.selectXdBurstAnchorIndex(
                        tied, occupied, 1, 1.0));
    }

    @Test
    void selectionFallsBackToTheFarthestRegionOnCompactModels() {
        int selected = ShadowPokemonAuraSystem.selectXdBurstAnchorIndex(
                List.of(
                        new Vec3(0.10, 0.0, 0.0),
                        new Vec3(-0.20, 0.0, 0.0)),
                List.of(Vec3.ZERO),
                0,
                1.0);

        assertEquals(1, selected,
                "when strict separation is impossible, use the least crowded region");
    }

    @Test
    void spawnDelayMapsDeterministicallyIntoTheFourToSevenTickWindow() {
        assertEquals(4, ShadowPokemonAuraSystem.xdBurstSpawnDelay(0.0f));
        assertEquals(7, ShadowPokemonAuraSystem.xdBurstSpawnDelay(1.0f));

        int previous = 4;
        for (int step = 0; step <= 100; step++) {
            int delay = ShadowPokemonAuraSystem.xdBurstSpawnDelay(step / 100.0f);
            assertTrue(delay >= 4 && delay <= 7);
            assertTrue(delay >= previous,
                    "larger random samples must not produce earlier delays");
            previous = delay;
        }
    }

    @Test
    void burstEnvelopeHasIndependentFadeInAndFadeOutPhases() {
        float lifetime = 40.0f;

        assertEquals(0.0f,
                ShadowPokemonAuraSystem.xdBurstEnvelope(-4.0f, lifetime));
        assertEquals(0.0f,
                ShadowPokemonAuraSystem.xdBurstEnvelope(0.0f, lifetime));
        assertEquals(0.0f,
                ShadowPokemonAuraSystem.xdBurstEnvelope(lifetime, lifetime));
        assertEquals(0.0f,
                ShadowPokemonAuraSystem.xdBurstEnvelope(10.0f, 0.0f));

        float youngEarly = ShadowPokemonAuraSystem.xdBurstEnvelope(1.0f, lifetime);
        float youngLater = ShadowPokemonAuraSystem.xdBurstEnvelope(5.0f, lifetime);
        float oldEarlier = ShadowPokemonAuraSystem.xdBurstEnvelope(30.0f, lifetime);
        float oldLater = ShadowPokemonAuraSystem.xdBurstEnvelope(38.0f, lifetime);

        assertTrue(youngLater > youngEarly,
                "a young emitter must independently fade in");
        assertTrue(oldLater < oldEarlier,
                "an old emitter must independently fade out");
        assertTrue(youngLater > 0.0f && oldEarlier > 0.0f);

        // Interleaving another emitter must not advance or phase-lock the first.
        assertEquals(youngLater,
                ShadowPokemonAuraSystem.xdBurstEnvelope(5.0f, lifetime));
    }
}
