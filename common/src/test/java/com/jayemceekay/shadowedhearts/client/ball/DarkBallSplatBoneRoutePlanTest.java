package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSplatBoneRoutePlanTest {
    private static final float EPSILON = 1.0e-5f;

    @Test
    void routesFinalSamplesAcrossAnInletRootedCapturedHierarchy() {
        Vector3f inlet = new Vector3f(1.0f, 0.0f, 0.0f);
        List<DarkBallSplatBoneRoutePlan.Node> nodes = List.of(
                node(0.0f, 0.0f, 0.0f, -1, "root", true, true),
                node(1.0f, 0.0f, 0.0f, 0, "root/inlet", true, true),
                node(0.0f, 1.0f, 0.0f, 0, "root/limb", false, true),
                node(1.0f, 1.0f, 0.0f, 2, "root/limb/tip", true, true),
                node(0.0f, -0.8f, 0.0f, 0, "root/other", true, true)
        );
        // Keep the samples outside the internal bone plane, as captured
        // surface samples are in production. The shared offset preserves the
        // hierarchy-routing layout while avoiding an outward first hop.
        float[] samples = {
                0.95f, 1.00f, 1.10f,
                0.55f, 1.02f, 1.10f,
                0.10f, 0.82f, 1.10f,
                0.78f, 0.02f, 1.10f
        };

        DarkBallSplatBoneRoutePlan.Result result =
                DarkBallSplatBoneRoutePlan.build(
                        samples, inlet, nodes, () -> false);

        assertTrue(result.guided(), result.summary());
        assertTrue(result.paletteCount() >= 3, result.summary());
        assertTrue(result.paletteCount()
                <= DarkBallSplatBoneRoutePlan.MAX_PALETTE_NODES);
        assertEquals(samples.length / 3, result.sampleCount());
        assertEquals(result.sampleCount(), result.assignedSamples());
        assertEquals(0, result.fallbackSamples());
        assertEquals("root/inlet", result.inletPath());

        for (int sample = 0; sample < result.sampleCount(); sample++) {
            assertTrue(result.attachmentNodes()[sample] >= 2);
            assertTrue(result.attachmentT()[sample] >= 0.0f);
            assertTrue(result.attachmentT()[sample] <= 1.0f);
            assertTrue(result.routeConfidence()[sample] > 0.0f);
            assertTrue(result.pathLength()[sample] > 0.0f);
            assertEquals(
                    Math.round(result.attachmentT()[sample] * 255.0f),
                    result.attachmentT()[sample] * 255.0f,
                    EPSILON,
                    "the CPU route must use the byte value decoded by GLSL");
            assertTrue(
                    result.normalizedPathLength()[sample] >= 2.0f / 255.0f,
                    "assigned paths must remain non-zero after packing");
            assertEquals(
                    Math.max(
                            result.pathLength()[sample]
                                    / result.maximumPathLength(),
                            2.0f / 255.0f),
                    result.normalizedPathLength()[sample],
                    EPSILON);
        }
        float directRemoteDistance = new Vector3f(
                samples[0], samples[1], samples[2]).distance(inlet);
        assertTrue(
                result.pathLength()[0] > directRemoteDistance,
                "the remote splat should retain a non-direct bone route");
        assertMonotonicBoundedPalette(result, inlet);
        assertQuantizedAttachmentsMonotonic(result, samples, inlet);
    }

    @Test
    void capsLargeHierarchiesDeterministicallyAtEightParentHops() {
        List<DarkBallSplatBoneRoutePlan.Node> nodes =
                new ArrayList<>();
        float[] samples = new float[80 * 3];
        for (int node = 0; node < 80; node++) {
            nodes.add(node(
                    node,
                    (float) Math.sin(node * 0.13) * 0.2f,
                    0.0f,
                    node - 1,
                    "root/node-" + node,
                    true,
                    true));
            samples[node * 3] = node + 0.05f;
            samples[node * 3 + 1] =
                    (float) Math.sin(node * 0.13) * 0.2f;
        }
        Vector3f inlet = new Vector3f();

        DarkBallSplatBoneRoutePlan.Result first =
                DarkBallSplatBoneRoutePlan.build(
                        samples, inlet, nodes, () -> false);
        DarkBallSplatBoneRoutePlan.Result second =
                DarkBallSplatBoneRoutePlan.build(
                        samples, inlet, nodes, () -> false);

        assertTrue(first.guided(), first.summary());
        assertEquals(
                DarkBallSplatBoneRoutePlan.MAX_PALETTE_NODES,
                first.paletteCount(),
                first.summary());
        assertArrayEquals(
                first.palettePositions(),
                second.palettePositions());
        assertArrayEquals(
                first.paletteParents(),
                second.paletteParents());
        assertArrayEquals(
                first.attachmentNodes(),
                second.attachmentNodes());
        assertArrayEquals(
                first.attachmentT(),
                second.attachmentT());
        assertArrayEquals(
                first.routeConfidence(),
                second.routeConfidence());
        assertArrayEquals(
                first.normalizedPathLength(),
                second.normalizedPathLength());
        assertMonotonicBoundedPalette(first, inlet);
    }

    @Test
    void neighborhoodVotingMovesAmbiguousSampleOntoLocalMajorityBranch() {
        Vector3f inlet = new Vector3f(0.0f, -1.0f, 0.0f);
        List<DarkBallSplatBoneRoutePlan.Node> nodes = List.of(
                node(0.0f, 0.0f, 0.0f, -1, "root", true, true),
                node(0.0f, -1.0f, 0.0f, 0, "root/inlet", true, true),
                node(1.0f, 0.0f, 0.0f, 0, "root/a", true, true),
                node(2.0f, 0.0f, 0.0f, 2, "root/a/tip", true, true),
                node(1.0f, 0.2f, 0.0f, 0, "root/b", true, true),
                node(2.0f, 0.2f, 0.0f, 4, "root/b/tip", true, true)
        );
        // A uniform out-of-plane surface offset leaves the branch ambiguity
        // and neighborhood graph unchanged, while keeping every sample
        // outside the internal branch it may be reassigned onto.
        float[] samples = {
                1.50f, 0.09f, 0.80f,
                1.42f, 0.19f, 0.80f,
                1.46f, 0.20f, 0.80f,
                1.50f, 0.21f, 0.80f,
                1.54f, 0.20f, 0.80f,
                1.58f, 0.19f, 0.80f,
                1.50f, 0.00f, 0.80f
        };

        DarkBallSplatBoneRoutePlan.Result result =
                DarkBallSplatBoneRoutePlan.build(
                        samples, inlet, nodes, () -> false);

        assertTrue(result.guided(), result.summary());
        assertTrue(
                result.coherenceAdjustedSamples() > 0,
                result.summary());
        assertEquals(
                result.sampleBranches()[1],
                result.sampleBranches()[0],
                "the ambiguous center sample should follow its "
                        + "five-neighbor branch majority");
    }

    @Test
    void degenerateHierarchyBuildsFiniteVirtualSurfaceRoute() {
        float[] samples = {
                1.0f, 0.0f, 0.0f,
                0.0f, 2.0f, 0.0f
        };
        List<DarkBallSplatBoneRoutePlan.Node> nodes = List.of(
                node(0.0f, 0.0f, 0.0f, -1, "root", true, true),
                node(0.0f, 0.0f, 0.0f, 0, "root/zero", true, true)
        );

        DarkBallSplatBoneRoutePlan.Result result =
                DarkBallSplatBoneRoutePlan.build(
                        samples, new Vector3f(), nodes, () -> false);

        assertTrue(result.guided(), result.summary());
        assertTrue(result.paletteCount() >= 2, result.summary());
        assertEquals(2, result.assignedSamples(), result.summary());
        assertEquals(0, result.fallbackSamples(), result.summary());
        assertEquals("<virtual-surface>", result.inletPath());
        assertTrue(
                result.status().startsWith("virtual-surface:"),
                result.summary());
        for (int sample = 0; sample < result.sampleCount(); sample++) {
            assertTrue(result.attachmentNodes()[sample] >= 2,
                    "the virtual inlet-to-root collar must remain a "
                            + "handoff-only segment");
            assertTrue(result.routeConfidence()[sample] > 0.0f);
            assertTrue(result.pathLength()[sample] > 0.0f);
            assertTrue(result.normalizedPathLength()[sample] > 0.0f);
        }
        assertMonotonicBoundedPalette(result, new Vector3f());
        assertQuantizedAttachmentsMonotonic(
                result, samples, new Vector3f());
    }

    @Test
    void polarSegmentsDecreaseRadiusWithoutChordReExpansion() {
        Vector3f inlet = new Vector3f();
        Vector3f start = new Vector3f(2.0f, 0.0f, 0.0f);
        Vector3f end = new Vector3f(-1.0f, 0.0f, 0.0f);
        float previousRadius = start.distance(inlet);

        for (int step = 1; step <= 100; step++) {
            float t = step / 100.0f;
            Vector3f point =
                    DarkBallSplatBoneRoutePlan.polarSegmentPoint(
                            start,
                            end,
                            inlet,
                            t);
            float expectedRadius = 2.0f - t;
            float radius = point.distance(inlet);
            assertEquals(expectedRadius, radius, EPSILON);
            assertTrue(
                    radius <= previousRadius + EPSILON,
                    "a curved route segment must never move outward");
            previousRadius = radius;
        }
    }

    @Test
    void sampleInsideEveryAnatomicalSegmentRetiresWithoutRootChord() {
        Vector3f inlet = new Vector3f();
        List<DarkBallSplatBoneRoutePlan.Node> nodes = List.of(
                node(1.0f, 0.0f, 0.0f, -1,
                        "root", true, true),
                node(2.0f, 0.0f, 0.0f, 0,
                        "root/outer", true, true)
        );

        DarkBallSplatBoneRoutePlan.Result result =
                DarkBallSplatBoneRoutePlan.build(
                        new float[]{0.25f, 0.0f, 0.0f},
                        inlet,
                        nodes,
                        () -> false);

        assertFalse(result.guided(), result.summary());
        assertEquals(-1, result.attachmentNodes()[0], result.summary());
        assertEquals(0.0f, result.routeConfidence()[0], EPSILON,
                result.summary());
        assertEquals(1, result.fallbackSamples(), result.summary());
        assertEquals(0, result.branchRemappedSamples(), result.summary());
        assertTrue(result.unroutableFallbacks() > 0, result.summary());
    }

    @Test
    void compactPaletteRecoversSamplesFromOmittedMinorBranches() {
        Vector3f inlet = new Vector3f();
        List<DarkBallSplatBoneRoutePlan.Node> nodes =
                new ArrayList<>();
        nodes.add(node(
                0.0f, 0.0f, 0.0f, -1,
                "root", true, true));
        int branchCount = 40;
        float[] samples = new float[branchCount * 3];
        for (int branch = 0; branch < branchCount; branch++) {
            float angle = (float) (Math.PI * 2.0
                    * branch / branchCount);
            float x = (float) Math.cos(angle) * 2.0f;
            float y = (float) Math.sin(angle) * 2.0f;
            nodes.add(node(
                    x, y, 0.0f, 0,
                    "root/branch-" + branch,
                    true, true));
            samples[branch * 3] = x * 1.05f;
            samples[branch * 3 + 1] = y * 1.05f;
            samples[branch * 3 + 2] = 0.25f;
        }

        DarkBallSplatBoneRoutePlan.Result result =
                DarkBallSplatBoneRoutePlan.build(
                        samples, inlet, nodes, () -> false);

        assertTrue(result.guided(), result.summary());
        assertEquals(
                DarkBallSplatBoneRoutePlan.MAX_PALETTE_NODES,
                result.paletteCount(),
                result.summary());
        assertEquals(branchCount, result.assignedSamples(), result.summary());
        assertEquals(0, result.fallbackSamples(), result.summary());
        assertTrue(
                result.neighborRecoveredSamples()
                        + result.branchRemappedSamples() > 0,
                "minor branches omitted from the compact palette must "
                        + "inherit a nearby routed branch");
        assertQuantizedAttachmentsMonotonic(result, samples, inlet);
    }

    @Test
    void collapsesOrdinaryEmptyLocatorChains() {
        List<DarkBallSplatBoneRoutePlan.Node> compacted =
                DarkBallSplatBoneRoutePlan.compactEmptyRoutingNodes(
                        List.of(
                                node(0.0f, 0.0f, 0.0f, -1,
                                        "root", true, true),
                                node(0.8f, 1.5f, 0.0f, 0,
                                        "root/locator-a", false, false),
                                node(1.6f, -1.5f, 0.0f, 1,
                                        "root/locator-a/locator-b",
                                        false, false),
                                node(2.4f, 0.0f, 0.0f, 2,
                                        "root/limb", true, true)),
                        new Vector3f(),
                        () -> false);

        assertEquals(2, compacted.size());
        assertEquals("root", compacted.get(0).path());
        assertEquals("root/limb", compacted.get(1).path());
        assertEquals(0, compacted.get(1).parentIndex(),
                "the structural child must bypass both empty locators");

        DarkBallSplatBoneRoutePlan.Result result =
                DarkBallSplatBoneRoutePlan.build(
                        new float[]{2.35f, 0.2f, 0.0f},
                        new Vector3f(),
                        List.of(
                                node(0.0f, 0.0f, 0.0f, -1,
                                        "root", true, true),
                                node(0.8f, 1.5f, 0.0f, 0,
                                        "root/locator-a", false, false),
                                node(1.6f, -1.5f, 0.0f, 1,
                                        "root/locator-a/locator-b",
                                        false, false),
                                node(2.4f, 0.0f, 0.0f, 2,
                                        "root/limb", true, true)),
                        () -> false);
        assertTrue(result.guided(), result.summary());
        assertEquals(0, result.fallbackSamples(), result.summary());
    }

    @Test
    void preservesOnlyTheInletNearestEmptyTerminal() {
        List<DarkBallSplatBoneRoutePlan.Node> compacted =
                DarkBallSplatBoneRoutePlan.compactEmptyRoutingNodes(
                        List.of(
                                node(0.0f, 0.0f, 0.0f, -1,
                                        "root", true, true),
                                node(1.0f, 0.0f, 0.0f, 0,
                                        "root/head", true, true),
                                node(1.5f, 0.0f, 0.0f, 1,
                                        "root/head/mouth-locator",
                                        false, false),
                                node(0.0f, 4.0f, 0.0f, 0,
                                        "root/unrelated-locator",
                                        false, false)),
                        new Vector3f(1.5f, 0.0f, 0.0f),
                        () -> false);

        assertEquals(3, compacted.size());
        assertEquals(
                List.of("root", "root/head",
                        "root/head/mouth-locator"),
                compacted.stream()
                        .map(DarkBallSplatBoneRoutePlan.Node::path)
                        .toList());
        assertEquals(1, compacted.get(2).parentIndex());

        DarkBallSplatBoneRoutePlan.Result result =
                DarkBallSplatBoneRoutePlan.build(
                        new float[]{1.1f, 0.2f, 0.0f},
                        new Vector3f(1.5f, 0.0f, 0.0f),
                        List.of(
                                node(0.0f, 0.0f, 0.0f, -1,
                                        "root", true, true),
                                node(1.0f, 0.0f, 0.0f, 0,
                                        "root/head", true, true),
                                node(1.5f, 0.0f, 0.0f, 1,
                                        "root/head/mouth-locator",
                                        false, false),
                                node(0.0f, 4.0f, 0.0f, 0,
                                        "root/unrelated-locator",
                                        false, false)),
                        () -> false);
        assertTrue(result.guided(), result.summary());
        assertEquals(
                "root/head/mouth-locator",
                result.inletPath());
    }

    @Test
    void retainsEmptyBranchJunctionButNotTransitLocators() {
        List<DarkBallSplatBoneRoutePlan.Node> compacted =
                DarkBallSplatBoneRoutePlan.compactEmptyRoutingNodes(
                        List.of(
                                node(0.0f, 0.0f, 0.0f, -1,
                                        "root", true, true),
                                node(1.0f, 0.0f, 0.0f, 0,
                                        "root/junction", false, false),
                                node(2.0f, 0.5f, 0.0f, 1,
                                        "root/junction/transit",
                                        false, false),
                                node(3.0f, 1.0f, 0.0f, 2,
                                        "root/junction/upper",
                                        true, true),
                                node(3.0f, -1.0f, 0.0f, 1,
                                        "root/junction/lower",
                                        true, true)),
                        new Vector3f(),
                        () -> false);

        assertEquals(
                List.of(
                        "root",
                        "root/junction",
                        "root/junction/upper",
                        "root/junction/lower"),
                compacted.stream()
                        .map(DarkBallSplatBoneRoutePlan.Node::path)
                        .toList());
        assertEquals(1, compacted.get(2).parentIndex());
        assertEquals(1, compacted.get(3).parentIndex());
    }

    @Test
    void malformedInletLeavesRouteUnavailableForSafeRetirement() {
        float[] samples = {
                1.0f, 0.0f, 0.0f,
                0.0f, 2.0f, 0.0f
        };
        List<DarkBallSplatBoneRoutePlan.Node> nodes = List.of(
                node(0.0f, 0.0f, 0.0f, -1,
                        "root", true, true),
                node(1.0f, 0.0f, 0.0f, 0,
                        "root/child", true, true)
        );

        DarkBallSplatBoneRoutePlan.Result result =
                DarkBallSplatBoneRoutePlan.build(
                        samples,
                        new Vector3f(Float.NaN, 0.0f, 0.0f),
                        nodes,
                        () -> false);

        assertFalse(result.guided(), result.summary());
        assertEquals(2, result.fallbackSamples());
        assertEquals(2, result.unroutableFallbacks());
    }

    @Test
    void supportsCooperativeCancellation() {
        List<DarkBallSplatBoneRoutePlan.Node> nodes = List.of(
                node(0.0f, 0.0f, 0.0f, -1, "root", true, true),
                node(1.0f, 0.0f, 0.0f, 0, "root/child", true, true)
        );

        assertThrows(
                CancellationException.class,
                () -> DarkBallSplatBoneRoutePlan.build(
                        new float[]{0.5f, 0.0f, 0.0f},
                        new Vector3f(),
                        nodes,
                        () -> true));
    }

    private static void assertMonotonicBoundedPalette(
            DarkBallSplatBoneRoutePlan.Result result,
            Vector3f inlet) {
        for (int node = 1; node < result.paletteCount(); node++) {
            int parent = result.paletteParents()[node];
            int offset = node * 3;
            int parentOffset = parent * 3;
            float radius = new Vector3f(
                    result.palettePositions()[offset],
                    result.palettePositions()[offset + 1],
                    result.palettePositions()[offset + 2])
                    .distance(inlet);
            float parentRadius = new Vector3f(
                    result.palettePositions()[parentOffset],
                    result.palettePositions()[parentOffset + 1],
                    result.palettePositions()[parentOffset + 2])
                    .distance(inlet);
            assertTrue(
                    parentRadius <= radius + EPSILON,
                    "palette parent must never be farther from the inlet");

            int hops = 0;
            int cursor = node;
            while (cursor > 0) {
                cursor = result.paletteParents()[cursor];
                hops++;
            }
            assertTrue(
                    hops <= DarkBallSplatBoneRoutePlan.MAX_PARENT_HOPS,
                    "palette route exceeded fixed shader hop count");
        }
    }

    private static void assertQuantizedAttachmentsMonotonic(
            DarkBallSplatBoneRoutePlan.Result result,
            float[] samples,
            Vector3f inlet) {
        for (int sample = 0; sample < result.sampleCount(); sample++) {
            int child = result.attachmentNodes()[sample];
            if (child <= 0) {
                continue;
            }
            int parent = result.paletteParents()[child];
            int childOffset = child * 3;
            int parentOffset = parent * 3;
            Vector3f attachment =
                    DarkBallSplatBoneRoutePlan.polarSegmentPoint(
                            new Vector3f(
                                    result.palettePositions()[parentOffset],
                                    result.palettePositions()[parentOffset + 1],
                                    result.palettePositions()[parentOffset + 2]),
                            new Vector3f(
                                    result.palettePositions()[childOffset],
                                    result.palettePositions()[childOffset + 1],
                                    result.palettePositions()[childOffset + 2]),
                            inlet,
                            result.attachmentT()[sample]);
            int sampleOffset = sample * 3;
            float sourceRadius = new Vector3f(
                    samples[sampleOffset],
                    samples[sampleOffset + 1],
                    samples[sampleOffset + 2]).distance(inlet);
            assertTrue(
                    attachment.distance(inlet)
                            <= sourceRadius + EPSILON,
                    "the packed attachment must not start outside its surfel");
        }
    }

    private static DarkBallSplatBoneRoutePlan.Node node(
            float x,
            float y,
            float z,
            int parent,
            String path,
            boolean ownsGeometry,
            boolean chainable) {
        return new DarkBallSplatBoneRoutePlan.Node(
                new Vector3f(x, y, z),
                parent,
                path,
                ownsGeometry,
                chainable);
    }
}
