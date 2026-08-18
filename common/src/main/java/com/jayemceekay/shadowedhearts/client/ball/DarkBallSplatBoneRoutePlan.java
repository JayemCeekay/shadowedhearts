package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Builds compact, topology-independent bone routes for the retained Dark Ball
 * surface splats.
 *
 * <p>The captured hierarchy is treated as an undirected graph only while this
 * one-time plan is prepared. A virtual root is projected onto the meaningful
 * segment nearest the inlet, the graph is re-rooted there, and final splat
 * centers are attached to nearby meaningful segments. The resulting palette
 * is deliberately small and has fixed-depth parent chains so the render path
 * can traverse it with bounded vertex-shader work.</p>
 *
 * <p>This planner does not consume extracted mesh connectivity and has no
 * topology or deformation-authority dependency. Finite samples recover
 * through an inward parent segment or the virtual surface hierarchy. A
 * completely unavailable route is reported explicitly so released surfels
 * can retire instead of exposing direct-to-inlet motion.</p>
 */
final class DarkBallSplatBoneRoutePlan {
    static final int MAX_PALETTE_NODES = 32;
    static final int MAX_PARENT_HOPS = 8;
    static final int NEIGHBOR_COUNT = 6;

    private static final int RESERVED_ROOT_NODES = 2;
    private static final int MAX_CAPTURED_PALETTE_NODES =
            MAX_PALETTE_NODES - RESERVED_ROOT_NODES;
    private static final int VIRTUAL_ROOT = -2;
    private static final int NO_PARENT = -1;
    private static final int CANCELLATION_CHECK_MASK = 0x03FF;
    private static final float MIN_SEGMENT_LENGTH = 1.0e-5f;
    private static final float DISTANCE_EPSILON = 1.0e-6f;
    private static final float MONOTONIC_RADIUS_EPSILON = 1.0e-5f;
    private static final float METADATA_QUANTIZATION_STEPS = 255.0f;
    private static final float MIN_ENCODED_PATH =
            2.0f / METADATA_QUANTIZATION_STEPS;
    private static final float LOW_CONFIDENCE_THRESHOLD = 0.72f;
    private static final float BRANCH_VOTE_MARGIN = 0.45f;
    private static final float MIN_BRANCH_CONSENSUS = 0.55f;
    private static final float INLET_EMPTY_NODE_RELATIVE_MARGIN = 0.25f;
    private static final float INLET_EMPTY_NODE_ABSOLUTE_MARGIN = 0.05f;

    private DarkBallSplatBoneRoutePlan() {
    }

    static Result build(float[] samplePositions,
                        Vector3fc inlet,
                        List<Node> capturedNodes,
                        BooleanSupplier cancellationRequested) {
        if (samplePositions == null || samplePositions.length % 3 != 0) {
            throw new IllegalArgumentException(
                    "sample positions must contain complete xyz triples");
        }
        BooleanSupplier cancellation = cancellationRequested == null
                ? () -> false
                : cancellationRequested;
        throwIfCancellationRequested(cancellation);

        int sampleCount = samplePositions.length / 3;
        if (!finite(inlet)
                || capturedNodes == null
                || capturedNodes.size() < 2) {
            return fallback(
                    samplePositions,
                    inlet,
                    capturedNodes == null ? 0 : capturedNodes.size(),
                    0,
                    "missing-hierarchy");
        }

        int sourceNodeCount = capturedNodes.size();
        capturedNodes = compactEmptyRoutingNodes(
                capturedNodes,
                inlet,
                cancellation);
        if (capturedNodes.size() < 2) {
            return fallback(
                    samplePositions,
                    inlet,
                    sourceNodeCount,
                    0,
                    "insufficient-structural-hierarchy");
        }

        boolean[] validNode = new boolean[capturedNodes.size()];
        int validNodeCount = 0;
        for (int nodeIndex = 0;
             nodeIndex < capturedNodes.size();
             nodeIndex++) {
            if ((nodeIndex & CANCELLATION_CHECK_MASK) == 0) {
                throwIfCancellationRequested(cancellation);
            }
            Node node = capturedNodes.get(nodeIndex);
            validNode[nodeIndex] =
                    node != null && finite(node.position());
            if (validNode[nodeIndex]) {
                validNodeCount++;
            }
        }
        if (validNodeCount < 2) {
            return fallback(
                    samplePositions,
                    inlet,
                    sourceNodeCount,
                    0,
                    "insufficient-valid-nodes");
        }

        List<OriginalSegment> originalSegments =
                buildOriginalSegments(
                        capturedNodes,
                        validNode,
                        cancellation);
        if (originalSegments.isEmpty()) {
            return fallback(
                    samplePositions,
                    inlet,
                    sourceNodeCount,
                    0,
                    "missing-segments");
        }

        OriginalProjection rootProjection = nearestOriginalSegment(
                inlet.x(),
                inlet.y(),
                inlet.z(),
                originalSegments,
                true);
        if (rootProjection == null) {
            return fallback(
                    samplePositions,
                    inlet,
                    sourceNodeCount,
                    originalSegments.size(),
                    "missing-meaningful-root");
        }

        Vector3f virtualRootPosition =
                pointOn(rootProjection.segment(), rootProjection.t());
        List<List<GraphEdge>> adjacency = buildAdjacency(
                sourceNodeCount, originalSegments);
        RootedGraph rooted = rootGraph(
                sourceNodeCount,
                adjacency,
                rootProjection,
                cancellation);
        List<RootedSegment> rootedSegments = orientSegments(
                originalSegments,
                rootProjection,
                virtualRootPosition,
                rooted,
                adjacency);
        List<RootedSegment> assignmentSegments = rootedSegments.stream()
                .filter(RootedSegment::meaningful)
                .toList();
        if (assignmentSegments.isEmpty()) {
            return fallback(
                    samplePositions,
                    inlet,
                    sourceNodeCount,
                    originalSegments.size(),
                    "missing-assignment-segments");
        }

        Assignment[] assignments = assignSamples(
                samplePositions,
                assignmentSegments,
                cancellation);
        int initiallyAssigned = countAssigned(assignments);
        if (sampleCount > 0 && initiallyAssigned == 0) {
            return fallback(
                    samplePositions,
                    inlet,
                    sourceNodeCount,
                    originalSegments.size(),
                    "no-finite-sample-assignments");
        }
        int coherenceAdjusted = smoothAmbiguousBranches(
                samplePositions,
                assignments,
                assignmentSegments,
                cancellation);

        Palette palette = buildPalette(
                inlet,
                virtualRootPosition,
                capturedNodes,
                rooted,
                adjacency,
                assignments,
                cancellation);
        if (palette.parents().length <= RESERVED_ROOT_NODES) {
            return fallback(
                    samplePositions,
                    inlet,
                    sourceNodeCount,
                    originalSegments.size(),
                    "empty-compact-palette");
        }

        List<PaletteSegment> paletteSegments =
                buildPaletteSegments(palette);
        if (paletteSegments.isEmpty()) {
            return fallback(
                    samplePositions,
                    inlet,
                    sourceNodeCount,
                    originalSegments.size(),
                    "empty-palette-segments");
        }

        int[] attachmentNodes = new int[sampleCount];
        float[] attachmentT = new float[sampleCount];
        float[] routeConfidence = new float[sampleCount];
        float[] pathLength = new float[sampleCount];
        float[] normalizedPathLength = new float[sampleCount];
        int[] sampleBranches = new int[sampleCount];
        Arrays.fill(attachmentNodes, -1);
        Arrays.fill(sampleBranches, -1);

        float[] palettePathLength = palettePathLengths(palette);
        int assignedSamples = 0;
        float maximumPathLength = 0.0f;
        int monotonicAdjustedSamples = 0;
        int neighborRecoveredSamples = 0;
        int branchRemappedSamples = 0;
        int quantizationAdjustedSamples = 0;
        int invalidSampleFallbacks = 0;
        int unroutableFallbacks = 0;
        boolean[] assigned = new boolean[sampleCount];
        for (int sample = 0; sample < sampleCount; sample++) {
            if ((sample & CANCELLATION_CHECK_MASK) == 0) {
                throwIfCancellationRequested(cancellation);
            }
            int offset = sample * 3;
            float x = samplePositions[offset];
            float y = samplePositions[offset + 1];
            float z = samplePositions[offset + 2];
            float directDistance = finite(x, y, z)
                    ? distance(x, y, z, inlet.x(), inlet.y(), inlet.z())
                    : 0.0f;
            pathLength[sample] = directDistance;

            Assignment fullAssignment = assignments[sample];
            if (fullAssignment == null) {
                invalidSampleFallbacks++;
                maximumPathLength =
                        Math.max(maximumPathLength, directDistance);
                continue;
            }
            int branch = fullAssignment.segment().branch();
            RouteCandidate candidate = routeCandidate(
                    x,
                    y,
                    z,
                    inlet,
                    fullAssignment,
                    palette,
                    palette.positions(),
                    paletteSegments,
                    palettePathLength,
                    branch);
            if (candidate == null) {
                maximumPathLength =
                        Math.max(maximumPathLength, directDistance);
                continue;
            }
            installCandidate(
                    sample,
                    candidate,
                    attachmentNodes,
                    attachmentT,
                    routeConfidence,
                    pathLength,
                    sampleBranches,
                    assigned);
            if (candidate.monotonicAdjusted()) {
                monotonicAdjustedSamples++;
            }
            if (candidate.quantizationAdjusted()) {
                quantizationAdjustedSamples++;
            }
            maximumPathLength =
                    Math.max(maximumPathLength, candidate.totalPath());
            assignedSamples++;
        }

        // A low-confidence or compacted-away branch should not turn into a
        // direct sink. First inherit the nearest successfully routed sample's
        // branch, preserving local surface coherence without adding runtime
        // shader work.
        for (int sample = 0; sample < sampleCount; sample++) {
            if (assigned[sample]
                    || assignments[sample] == null) {
                continue;
            }
            int neighbor = nearestAssignedSample(
                    samplePositions, sample, assigned);
            if (neighbor < 0 || sampleBranches[neighbor] < 0) {
                continue;
            }
            int offset = sample * 3;
            RouteCandidate candidate = routeCandidate(
                    samplePositions[offset],
                    samplePositions[offset + 1],
                    samplePositions[offset + 2],
                    inlet,
                    assignments[sample],
                    palette,
                    palette.positions(),
                    paletteSegments,
                    palettePathLength,
                    sampleBranches[neighbor]);
            if (candidate == null) {
                continue;
            }
            installCandidate(
                    sample,
                    candidate,
                    attachmentNodes,
                    attachmentT,
                    routeConfidence,
                    pathLength,
                    sampleBranches,
                    assigned);
            neighborRecoveredSamples++;
            if (candidate.monotonicAdjusted()) {
                monotonicAdjustedSamples++;
            }
            if (candidate.quantizationAdjusted()) {
                quantizationAdjustedSamples++;
            }
            maximumPathLength =
                    Math.max(maximumPathLength, candidate.totalPath());
            assignedSamples++;
        }

        // Last, remap any remaining finite sample to the nearest monotonic
        // palette segment. The palette includes its inlet-to-virtual-root
        // segment, so unusual branch counts and samples inside their nearest
        // anatomical bone still receive a routed, non-expanding path.
        for (int sample = 0; sample < sampleCount; sample++) {
            if (assigned[sample]
                    || assignments[sample] == null) {
                continue;
            }
            int offset = sample * 3;
            RouteCandidate candidate = routeCandidate(
                    samplePositions[offset],
                    samplePositions[offset + 1],
                    samplePositions[offset + 2],
                    inlet,
                    assignments[sample],
                    palette,
                    palette.positions(),
                    paletteSegments,
                    palettePathLength,
                    -1);
            if (candidate == null) {
                unroutableFallbacks++;
                continue;
            }
            installCandidate(
                    sample,
                    candidate,
                    attachmentNodes,
                    attachmentT,
                    routeConfidence,
                    pathLength,
                    sampleBranches,
                    assigned);
            branchRemappedSamples++;
            if (candidate.monotonicAdjusted()) {
                monotonicAdjustedSamples++;
            }
            if (candidate.quantizationAdjusted()) {
                quantizationAdjustedSamples++;
            }
            maximumPathLength =
                    Math.max(maximumPathLength, candidate.totalPath());
            assignedSamples++;
        }

        // Recovery can replace an initially direct length with a routed one.
        // Recompute the authoritative scale so a discarded provisional
        // distance cannot compress every encoded route.
        maximumPathLength = 0.0f;
        for (float samplePathLength : pathLength) {
            if (finite(samplePathLength)) {
                maximumPathLength = Math.max(
                        maximumPathLength,
                        samplePathLength);
            }
        }
        if (!(maximumPathLength > DISTANCE_EPSILON)
                || !finite(maximumPathLength)) {
            maximumPathLength = 0.0f;
        } else {
            for (int sample = 0; sample < sampleCount; sample++) {
                normalizedPathLength[sample] = clampUnit(
                        pathLength[sample] / maximumPathLength);
                if (assigned[sample]) {
                    normalizedPathLength[sample] = Math.max(
                            normalizedPathLength[sample],
                            MIN_ENCODED_PATH);
                }
            }
        }
        throwIfCancellationRequested(cancellation);

        int fallbackSamples = sampleCount - assignedSamples;
        return new Result(
                palette.positions(),
                palette.parents(),
                palette.originalNodes(),
                attachmentNodes,
                attachmentT,
                routeConfidence,
                pathLength,
                normalizedPathLength,
                sampleBranches,
                assignedSamples > 0,
                sourceNodeCount,
                originalSegments.size(),
                assignedSamples,
                fallbackSamples,
                coherenceAdjusted,
                monotonicAdjustedSamples,
                neighborRecoveredSamples,
                branchRemappedSamples,
                quantizationAdjustedSamples,
                invalidSampleFallbacks,
                unroutableFallbacks,
                inletPath(capturedNodes, rootProjection),
                maximumPathLength,
                "guided");
    }

    /**
     * Removes empty locator/control nodes from the anatomical route without
     * relying on model-specific names. Geometry-owning and chainable nodes are
     * always retained. Empty nodes survive only when they join two or more
     * relevant child branches, or when one is the empty endpoint nearest the
     * inlet and is at least as plausible as the nearest structural node.
     *
     * <p>Children of discarded nodes are reparented to the nearest retained
     * ancestor. This preserves the posed world-space endpoints while preventing
     * ordinary locator chains from consuming palette slots or bending surfel
     * motion through authoring-only pivots.</p>
     */
    static List<Node> compactEmptyRoutingNodes(
            List<Node> source,
            Vector3fc inlet,
            BooleanSupplier cancellation) {
        int nodeCount = source.size();
        boolean[] valid = new boolean[nodeCount];
        boolean[] structural = new boolean[nodeCount];
        List<List<Integer>> children = new ArrayList<>(nodeCount);
        List<List<Integer>> adjacency = new ArrayList<>(nodeCount);
        for (int node = 0; node < nodeCount; node++) {
            children.add(new ArrayList<>());
            adjacency.add(new ArrayList<>());
            Node candidate = source.get(node);
            valid[node] = candidate != null
                    && finite(candidate.position());
            structural[node] = valid[node]
                    && (candidate.ownsGeometry()
                    || candidate.chainable());
        }

        for (int child = 0; child < nodeCount; child++) {
            if ((child & CANCELLATION_CHECK_MASK) == 0) {
                throwIfCancellationRequested(cancellation);
            }
            if (!valid[child]) {
                continue;
            }
            int parent = source.get(child).parentIndex();
            if (parent < 0
                    || parent >= nodeCount
                    || parent == child
                    || !valid[parent]) {
                continue;
            }
            children.get(parent).add(child);
            adjacency.get(parent).add(child);
            adjacency.get(child).add(parent);
        }

        boolean[] connectedToStructure = new boolean[nodeCount];
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        for (int node = 0; node < nodeCount; node++) {
            if (structural[node]) {
                connectedToStructure[node] = true;
                frontier.addLast(node);
            }
        }
        while (!frontier.isEmpty()) {
            int current = frontier.removeFirst();
            for (int neighbor : adjacency.get(current)) {
                if (!connectedToStructure[neighbor]) {
                    connectedToStructure[neighbor] = true;
                    frontier.addLast(neighbor);
                }
            }
        }

        int inletTerminal = selectInletEmptyTerminal(
                source,
                inlet,
                valid,
                structural,
                connectedToStructure);
        boolean[] relevantSubtree =
                Arrays.copyOf(structural, structural.length);
        if (inletTerminal >= 0) {
            relevantSubtree[inletTerminal] = true;
        }
        for (int pass = 0; pass < nodeCount; pass++) {
            boolean changed = false;
            for (int child = 0; child < nodeCount; child++) {
                if (!relevantSubtree[child] || !valid[child]) {
                    continue;
                }
                int parent = source.get(child).parentIndex();
                if (parent >= 0
                        && parent < nodeCount
                        && valid[parent]
                        && !relevantSubtree[parent]) {
                    relevantSubtree[parent] = true;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        boolean[] retained = Arrays.copyOf(
                structural, structural.length);
        if (inletTerminal >= 0) {
            retained[inletTerminal] = true;
        }
        for (int node = 0; node < nodeCount; node++) {
            if (!valid[node] || retained[node]) {
                continue;
            }
            int relevantChildBranches = 0;
            for (int child : children.get(node)) {
                if (relevantSubtree[child]) {
                    relevantChildBranches++;
                }
            }
            if (relevantChildBranches >= 2) {
                retained[node] = true;
            }
        }

        int[] compactIndex = new int[nodeCount];
        Arrays.fill(compactIndex, -1);
        int retainedCount = 0;
        for (int node = 0; node < nodeCount; node++) {
            if (retained[node]) {
                compactIndex[node] = retainedCount++;
            }
        }
        if (retainedCount == nodeCount) {
            return source;
        }

        List<Node> compacted = new ArrayList<>(retainedCount);
        for (int node = 0; node < nodeCount; node++) {
            if (!retained[node]) {
                continue;
            }
            Node original = source.get(node);
            int retainedParent = nearestRetainedSourceAncestor(
                    source,
                    node,
                    retained);
            compacted.add(new Node(
                    original.position(),
                    retainedParent < 0
                            ? -1
                            : compactIndex[retainedParent],
                    original.path(),
                    original.ownsGeometry(),
                    original.chainable()));
        }
        return List.copyOf(compacted);
    }

    private static int selectInletEmptyTerminal(
            List<Node> nodes,
            Vector3fc inlet,
            boolean[] valid,
            boolean[] structural,
            boolean[] connectedToStructure) {
        int nearestEmpty = -1;
        float nearestEmptyDistance = Float.POSITIVE_INFINITY;
        float nearestStructuralDistance = Float.POSITIVE_INFINITY;
        for (int node = 0; node < nodes.size(); node++) {
            if (!valid[node] || !connectedToStructure[node]) {
                continue;
            }
            float distance = nodes.get(node).position().distance(inlet);
            if (!finite(distance)) {
                continue;
            }
            if (structural[node]) {
                nearestStructuralDistance = Math.min(
                        nearestStructuralDistance,
                        distance);
            } else if (distance < nearestEmptyDistance) {
                nearestEmpty = node;
                nearestEmptyDistance = distance;
            }
        }
        if (nearestEmpty < 0
                || !finite(nearestStructuralDistance)) {
            return -1;
        }
        float margin = Math.max(
                INLET_EMPTY_NODE_ABSOLUTE_MARGIN,
                nearestStructuralDistance
                        * INLET_EMPTY_NODE_RELATIVE_MARGIN);
        return nearestEmptyDistance
                <= nearestStructuralDistance + margin
                ? nearestEmpty
                : -1;
    }

    private static int nearestRetainedSourceAncestor(
            List<Node> nodes,
            int node,
            boolean[] retained) {
        int cursor = nodes.get(node).parentIndex();
        int guard = 0;
        while (cursor >= 0
                && cursor < nodes.size()
                && guard++ <= nodes.size()) {
            if (retained[cursor]) {
                return cursor;
            }
            Node ancestor = nodes.get(cursor);
            if (ancestor == null) {
                return -1;
            }
            cursor = ancestor.parentIndex();
        }
        return -1;
    }

    private static List<OriginalSegment> buildOriginalSegments(
            List<Node> nodes,
            boolean[] validNode,
            BooleanSupplier cancellation) {
        List<OriginalSegment> segments =
                new ArrayList<>(Math.max(0, nodes.size() - 1));
        Set<Long> retainedEdges = new HashSet<>();
        for (int child = 0; child < nodes.size(); child++) {
            if ((child & CANCELLATION_CHECK_MASK) == 0) {
                throwIfCancellationRequested(cancellation);
            }
            Node childNode = nodes.get(child);
            if (childNode == null || !validNode[child]) {
                continue;
            }
            int parent = childNode.parentIndex();
            if (parent < 0
                    || parent >= nodes.size()
                    || parent == child
                    || !validNode[parent]) {
                continue;
            }
            int lower = Math.min(parent, child);
            int upper = Math.max(parent, child);
            long edgeKey = ((long) lower << 32)
                    | (upper & 0xFFFFFFFFL);
            if (!retainedEdges.add(edgeKey)) {
                continue;
            }

            Vector3f a = nodes.get(parent).position();
            Vector3f b = childNode.position();
            float length = distance(a, b);
            if (!(length > MIN_SEGMENT_LENGTH) || !finite(length)) {
                continue;
            }
            Node parentNode = nodes.get(parent);
            boolean meaningful = parentNode.ownsGeometry()
                    || childNode.ownsGeometry()
                    || parentNode.chainable()
                    || childNode.chainable();
            segments.add(new OriginalSegment(
                    segments.size(),
                    parent,
                    child,
                    a,
                    b,
                    length,
                    meaningful));
        }
        return segments;
    }

    private static List<List<GraphEdge>> buildAdjacency(
            int nodeCount,
            List<OriginalSegment> segments) {
        List<List<GraphEdge>> adjacency =
                new ArrayList<>(nodeCount);
        for (int node = 0; node < nodeCount; node++) {
            adjacency.add(new ArrayList<>());
        }
        for (OriginalSegment segment : segments) {
            adjacency.get(segment.a()).add(new GraphEdge(
                    segment.b(), segment.index(), segment.length()));
            adjacency.get(segment.b()).add(new GraphEdge(
                    segment.a(), segment.index(), segment.length()));
        }
        for (List<GraphEdge> edges : adjacency) {
            edges.sort(Comparator
                    .comparingInt(GraphEdge::node)
                    .thenComparingInt(GraphEdge::segmentIndex));
        }
        return adjacency;
    }

    private static RootedGraph rootGraph(
            int nodeCount,
            List<List<GraphEdge>> adjacency,
            OriginalProjection root,
            BooleanSupplier cancellation) {
        double[] distanceToRoot = new double[nodeCount];
        Arrays.fill(distanceToRoot, Double.POSITIVE_INFINITY);
        int[] parent = new int[nodeCount];
        Arrays.fill(parent, NO_PARENT);
        PriorityQueue<QueueNode> frontier = new PriorityQueue<>(
                Comparator.comparingDouble(QueueNode::distance)
                        .thenComparingInt(QueueNode::node));

        OriginalSegment rootSegment = root.segment();
        seedRoot(
                rootSegment.a(),
                root.t() * rootSegment.length(),
                distanceToRoot,
                parent,
                frontier);
        seedRoot(
                rootSegment.b(),
                (1.0 - root.t()) * rootSegment.length(),
                distanceToRoot,
                parent,
                frontier);

        int processed = 0;
        while (!frontier.isEmpty()) {
            if ((processed++ & CANCELLATION_CHECK_MASK) == 0) {
                throwIfCancellationRequested(cancellation);
            }
            QueueNode current = frontier.remove();
            if (current.distance()
                    > distanceToRoot[current.node()] + DISTANCE_EPSILON) {
                continue;
            }
            for (GraphEdge edge : adjacency.get(current.node())) {
                double candidate = current.distance() + edge.length();
                if (candidate + DISTANCE_EPSILON
                        < distanceToRoot[edge.node()]) {
                    distanceToRoot[edge.node()] = candidate;
                    parent[edge.node()] = current.node();
                    frontier.add(new QueueNode(edge.node(), candidate));
                }
            }
        }
        return new RootedGraph(distanceToRoot, parent);
    }

    private static void seedRoot(
            int node,
            double distance,
            double[] distances,
            int[] parents,
            PriorityQueue<QueueNode> frontier) {
        if (distance < distances[node]) {
            distances[node] = distance;
            parents[node] = VIRTUAL_ROOT;
            frontier.add(new QueueNode(node, distance));
        }
    }

    private static List<RootedSegment> orientSegments(
            List<OriginalSegment> originalSegments,
            OriginalProjection rootProjection,
            Vector3f virtualRootPosition,
            RootedGraph rooted,
            List<List<GraphEdge>> adjacency) {
        List<RootedSegment> rootedSegments = new ArrayList<>();
        OriginalSegment rootSegment = rootProjection.segment();
        for (OriginalSegment segment : originalSegments) {
            if (!Double.isFinite(rooted.distanceToRoot()[segment.a()])
                    || !Double.isFinite(
                    rooted.distanceToRoot()[segment.b()])) {
                continue;
            }
            if (segment.index() == rootSegment.index()) {
                float toA = (float) (
                        rootProjection.t() * segment.length());
                float toB = (float) (
                        (1.0 - rootProjection.t())
                                * segment.length());
                if (toA > MIN_SEGMENT_LENGTH) {
                    rootedSegments.add(new RootedSegment(
                            rootedSegments.size(),
                            VIRTUAL_ROOT,
                            segment.a(),
                            virtualRootPosition,
                            segment.positionA(),
                            toA,
                            segment.meaningful(),
                            branchFor(
                                    segment.a(),
                                    rooted.parent(),
                                    adjacency)));
                }
                if (toB > MIN_SEGMENT_LENGTH) {
                    rootedSegments.add(new RootedSegment(
                            rootedSegments.size(),
                            VIRTUAL_ROOT,
                            segment.b(),
                            virtualRootPosition,
                            segment.positionB(),
                            toB,
                            segment.meaningful(),
                            branchFor(
                                    segment.b(),
                                    rooted.parent(),
                                    adjacency)));
                }
                continue;
            }

            int outer;
            int inner;
            if (rooted.parent()[segment.a()] == segment.b()) {
                outer = segment.a();
                inner = segment.b();
            } else if (rooted.parent()[segment.b()] == segment.a()) {
                outer = segment.b();
                inner = segment.a();
            } else if (rooted.distanceToRoot()[segment.a()]
                    > rooted.distanceToRoot()[segment.b()]) {
                outer = segment.a();
                inner = segment.b();
            } else {
                outer = segment.b();
                inner = segment.a();
            }
            Vector3f innerPosition = inner == segment.a()
                    ? segment.positionA()
                    : segment.positionB();
            Vector3f outerPosition = outer == segment.a()
                    ? segment.positionA()
                    : segment.positionB();
            rootedSegments.add(new RootedSegment(
                    rootedSegments.size(),
                    inner,
                    outer,
                    innerPosition,
                    outerPosition,
                    segment.length(),
                    segment.meaningful(),
                    branchFor(outer, rooted.parent(), adjacency)));
        }
        return rootedSegments;
    }

    private static int branchFor(
            int outerNode,
            int[] parents,
            List<List<GraphEdge>> adjacency) {
        int cursor = outerNode;
        int child = outerNode;
        int depth = 0;
        while (cursor >= 0 && depth++ <= parents.length) {
            int parent = parents[cursor];
            if (parent == VIRTUAL_ROOT || parent < 0) {
                return child;
            }
            if (adjacency.get(parent).size() != 2) {
                return cursor;
            }
            child = parent;
            cursor = parent;
        }
        return outerNode;
    }

    private static Assignment[] assignSamples(
            float[] samplePositions,
            List<RootedSegment> segments,
            BooleanSupplier cancellation) {
        int sampleCount = samplePositions.length / 3;
        Assignment[] assignments = new Assignment[sampleCount];
        for (int sample = 0; sample < sampleCount; sample++) {
            if ((sample & CANCELLATION_CHECK_MASK) == 0) {
                throwIfCancellationRequested(cancellation);
            }
            int offset = sample * 3;
            float x = samplePositions[offset];
            float y = samplePositions[offset + 1];
            float z = samplePositions[offset + 2];
            if (!finite(x, y, z)) {
                continue;
            }
            RootedProjection projection =
                    nearestRootedSegment(x, y, z, segments, -1);
            if (projection == null) {
                continue;
            }
            assignments[sample] = new Assignment(
                    projection.segment(),
                    (float) projection.t(),
                    projection.distanceSquared(),
                    distanceConfidence(projection));
        }
        return assignments;
    }

    private static int smoothAmbiguousBranches(
            float[] samplePositions,
            Assignment[] assignments,
            List<RootedSegment> assignmentSegments,
            BooleanSupplier cancellation) {
        int sampleCount = assignments.length;
        if (sampleCount < 4) {
            return 0;
        }
        Set<Integer> branches = new HashSet<>();
        for (Assignment assignment : assignments) {
            if (assignment != null) {
                branches.add(assignment.segment().branch());
            }
        }
        if (branches.size() < 2) {
            return 0;
        }

        int neighborCount =
                Math.min(NEIGHBOR_COUNT, sampleCount - 1);
        int[] nearest =
                new int[Math.multiplyExact(sampleCount, neighborCount)];
        float[] nearestDistanceSquared =
                new float[nearest.length];
        Arrays.fill(nearest, -1);
        Arrays.fill(nearestDistanceSquared, Float.POSITIVE_INFINITY);
        int compared = 0;
        for (int first = 0; first < sampleCount; first++) {
            int firstOffset = first * 3;
            if (!finite(
                    samplePositions[firstOffset],
                    samplePositions[firstOffset + 1],
                    samplePositions[firstOffset + 2])) {
                continue;
            }
            for (int second = first + 1;
                 second < sampleCount;
                 second++) {
                if ((compared++ & CANCELLATION_CHECK_MASK) == 0) {
                    throwIfCancellationRequested(cancellation);
                }
                int secondOffset = second * 3;
                float dx = samplePositions[firstOffset]
                        - samplePositions[secondOffset];
                float dy = samplePositions[firstOffset + 1]
                        - samplePositions[secondOffset + 1];
                float dz = samplePositions[firstOffset + 2]
                        - samplePositions[secondOffset + 2];
                float distanceSquared = dx * dx + dy * dy + dz * dz;
                if (!finite(distanceSquared)) {
                    continue;
                }
                retainNearest(
                        nearest,
                        nearestDistanceSquared,
                        neighborCount,
                        first,
                        second,
                        distanceSquared);
                retainNearest(
                        nearest,
                        nearestDistanceSquared,
                        neighborCount,
                        second,
                        first,
                        distanceSquared);
            }
        }

        Assignment[] adjusted = assignments.clone();
        int adjustedCount = 0;
        for (int sample = 0; sample < sampleCount; sample++) {
            if ((sample & CANCELLATION_CHECK_MASK) == 0) {
                throwIfCancellationRequested(cancellation);
            }
            Assignment current = assignments[sample];
            if (current == null
                    || current.confidence()
                    >= LOW_CONFIDENCE_THRESHOLD) {
                continue;
            }

            Map<Integer, BranchVote> votes = new TreeMap<>();
            accumulateVote(
                    votes,
                    current.segment().branch(),
                    1.0f,
                    1);
            int base = sample * neighborCount;
            float nearestNeighborDistance = Float.POSITIVE_INFINITY;
            for (int slot = 0; slot < neighborCount; slot++) {
                int neighbor = nearest[base + slot];
                float distanceSquared =
                        nearestDistanceSquared[base + slot];
                if (neighbor < 0
                        || !finite(distanceSquared)
                        || assignments[neighbor] == null) {
                    continue;
                }
                float distance = (float) Math.sqrt(
                        Math.max(distanceSquared, 0.0f));
                nearestNeighborDistance =
                        Math.min(nearestNeighborDistance, distance);
                float scale = finite(nearestNeighborDistance)
                        ? Math.max(nearestNeighborDistance, 1.0e-4f)
                        : 1.0f;
                float weight = 1.0f / (1.0f + distance / scale);
                accumulateVote(
                        votes,
                        assignments[neighbor].segment().branch(),
                        weight,
                        1);
            }
            BranchVote winner = null;
            int winningBranch = -1;
            float totalWeight = 0.0f;
            for (Map.Entry<Integer, BranchVote> entry :
                    votes.entrySet()) {
                BranchVote vote = entry.getValue();
                totalWeight += vote.weight();
                if (winner == null
                        || vote.weight() > winner.weight()
                        || vote.weight() == winner.weight()
                        && entry.getKey() < winningBranch) {
                    winner = vote;
                    winningBranch = entry.getKey();
                }
            }
            BranchVote currentVote =
                    votes.get(current.segment().branch());
            if (winner == null
                    || winningBranch == current.segment().branch()
                    || winner.count() < 3
                    || !(winner.weight()
                    > currentVote.weight() + BRANCH_VOTE_MARGIN)
                    || !(winner.weight()
                    / Math.max(totalWeight, DISTANCE_EPSILON)
                    >= MIN_BRANCH_CONSENSUS)) {
                continue;
            }

            int offset = sample * 3;
            RootedProjection replacement = nearestRootedSegment(
                    samplePositions[offset],
                    samplePositions[offset + 1],
                    samplePositions[offset + 2],
                    assignmentSegments,
                    winningBranch);
            if (replacement == null) {
                continue;
            }
            float currentDistance = (float) Math.sqrt(
                    Math.max(current.distanceSquared(), 0.0));
            float replacementDistance = (float) Math.sqrt(
                    Math.max(replacement.distanceSquared(), 0.0));
            float localTolerance = finite(nearestNeighborDistance)
                    ? nearestNeighborDistance * 0.75f
                    : currentDistance * 0.35f;
            if (replacementDistance
                    > currentDistance + Math.max(
                    localTolerance, 1.0e-4f)) {
                continue;
            }
            float consensus = winner.weight()
                    / Math.max(totalWeight, DISTANCE_EPSILON);
            adjusted[sample] = new Assignment(
                    replacement.segment(),
                    (float) replacement.t(),
                    replacement.distanceSquared(),
                    clampUnit(Math.max(
                            current.confidence(),
                            consensus * 0.80f)));
            adjustedCount++;
        }
        System.arraycopy(
                adjusted, 0, assignments, 0, assignments.length);
        return adjustedCount;
    }

    private static void accumulateVote(
            Map<Integer, BranchVote> votes,
            int branch,
            float weight,
            int count) {
        BranchVote existing = votes.get(branch);
        votes.put(
                branch,
                existing == null
                        ? new BranchVote(weight, count)
                        : new BranchVote(
                        existing.weight() + weight,
                        existing.count() + count));
    }

    private static Palette buildPalette(
            Vector3fc inlet,
            Vector3f virtualRootPosition,
            List<Node> nodes,
            RootedGraph rooted,
            List<List<GraphEdge>> adjacency,
            Assignment[] assignments,
            BooleanSupplier cancellation) {
        int nodeCount = nodes.size();
        int[] directAssignments = new int[nodeCount];
        int[] subtreeAssignments = new int[nodeCount];
        Map<Integer, Integer> branchPopulation = new HashMap<>();
        Map<Integer, Integer> branchRepresentative = new HashMap<>();
        for (Assignment assignment : assignments) {
            if (assignment == null) {
                continue;
            }
            int outer = assignment.segment().outerOriginal();
            if (outer < 0) {
                continue;
            }
            directAssignments[outer]++;
            subtreeAssignments[outer]++;
            branchPopulation.merge(
                    assignment.segment().branch(), 1, Integer::sum);
            branchRepresentative.compute(
                    assignment.segment().branch(),
                    (ignored, current) -> betterRepresentative(
                            current,
                            outer,
                            directAssignments,
                            rooted.distanceToRoot()));
        }

        Integer[] reachable = new Integer[nodeCount];
        int reachableCount = 0;
        for (int node = 0; node < nodeCount; node++) {
            if (Double.isFinite(rooted.distanceToRoot()[node])) {
                reachable[reachableCount++] = node;
            }
        }
        Arrays.sort(
                reachable,
                0,
                reachableCount,
                Comparator
                        .<Integer>comparingDouble(
                                node -> rooted.distanceToRoot()[node])
                        .reversed()
                        .thenComparingInt(Integer::intValue));
        for (int index = 0; index < reachableCount; index++) {
            int node = reachable[index];
            int parent = rooted.parent()[node];
            if (parent >= 0) {
                subtreeAssignments[parent] +=
                        subtreeAssignments[node];
            }
        }

        Set<Integer> selected = new HashSet<>();
        List<Map.Entry<Integer, Integer>> orderedBranches =
                new ArrayList<>(branchPopulation.entrySet());
        orderedBranches.sort(Comparator
                .<Map.Entry<Integer, Integer>>comparingInt(
                        Map.Entry::getValue)
                .reversed()
                .thenComparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, Integer> branch : orderedBranches) {
            if (selected.size() >= MAX_CAPTURED_PALETTE_NODES) {
                break;
            }
            Integer representative =
                    branchRepresentative.get(branch.getKey());
            if (representative != null) {
                selected.add(representative);
            }
        }

        List<Integer> candidates = new ArrayList<>();
        for (int node = 0; node < nodeCount; node++) {
            if (!Double.isFinite(rooted.distanceToRoot()[node])
                    || subtreeAssignments[node] <= 0) {
                continue;
            }
            Node source = nodes.get(node);
            boolean routingLandmark =
                    adjacency.get(node).size() != 2;
            if (directAssignments[node] > 0
                    || source.ownsGeometry()
                    || source.chainable()
                    || routingLandmark) {
                candidates.add(node);
            }
        }
        candidates.sort((first, second) -> {
            int directOrder = Integer.compare(
                    directAssignments[second],
                    directAssignments[first]);
            if (directOrder != 0) {
                return directOrder;
            }
            int subtreeOrder = Integer.compare(
                    subtreeAssignments[second],
                    subtreeAssignments[first]);
            if (subtreeOrder != 0) {
                return subtreeOrder;
            }
            int firstJunction =
                    adjacency.get(first).size() != 2 ? 1 : 0;
            int secondJunction =
                    adjacency.get(second).size() != 2 ? 1 : 0;
            int junctionOrder =
                    Integer.compare(secondJunction, firstJunction);
            if (junctionOrder != 0) {
                return junctionOrder;
            }
            Node firstNode = nodes.get(first);
            Node secondNode = nodes.get(second);
            int geometryOrder = Boolean.compare(
                    secondNode.ownsGeometry(),
                    firstNode.ownsGeometry());
            if (geometryOrder != 0) {
                return geometryOrder;
            }
            int chainOrder = Boolean.compare(
                    secondNode.chainable(),
                    firstNode.chainable());
            if (chainOrder != 0) {
                return chainOrder;
            }
            int distanceOrder = Double.compare(
                    rooted.distanceToRoot()[second],
                    rooted.distanceToRoot()[first]);
            return distanceOrder != 0
                    ? distanceOrder
                    : Integer.compare(first, second);
        });
        for (int candidate : candidates) {
            if (selected.size() >= MAX_CAPTURED_PALETTE_NODES) {
                break;
            }
            selected.add(candidate);
        }

        List<Integer> paletteOrder = new ArrayList<>(selected);
        paletteOrder.sort(Comparator
                .<Integer>comparingDouble(
                        node -> rooted.distanceToRoot()[node])
                .thenComparingInt(Integer::intValue));

        List<Float> positions = new ArrayList<>();
        List<Integer> parents = new ArrayList<>();
        List<Integer> originalNodes = new ArrayList<>();
        addPaletteNode(
                positions, parents, originalNodes,
                inlet.x(), inlet.y(), inlet.z(), -1, -1);
        addPaletteNode(
                positions, parents, originalNodes,
                virtualRootPosition.x,
                virtualRootPosition.y,
                virtualRootPosition.z,
                0,
                -1);

        int[] originalToPalette = new int[nodeCount];
        Arrays.fill(originalToPalette, -1);
        int[] paletteDepth = new int[MAX_PALETTE_NODES];
        float[] paletteRadius = new float[MAX_PALETTE_NODES];
        paletteDepth[0] = 0;
        paletteDepth[1] = 1;
        paletteRadius[0] = 0.0f;
        paletteRadius[1] = distance(
                virtualRootPosition.x,
                virtualRootPosition.y,
                virtualRootPosition.z,
                inlet.x(), inlet.y(), inlet.z());

        int processed = 0;
        for (int original : paletteOrder) {
            if ((processed++ & CANCELLATION_CHECK_MASK) == 0) {
                throwIfCancellationRequested(cancellation);
            }
            if (parents.size() >= MAX_PALETTE_NODES) {
                break;
            }
            Vector3f position = nodes.get(original).position();
            float childRadius = distance(
                    position.x, position.y, position.z,
                    inlet.x(), inlet.y(), inlet.z());
            int paletteParent = nearestRetainedAncestor(
                    original,
                    rooted.parent(),
                    originalToPalette,
                    parents,
                    paletteDepth,
                    paletteRadius,
                    childRadius);
            int paletteIndex = parents.size();
            addPaletteNode(
                    positions,
                    parents,
                    originalNodes,
                    position.x,
                    position.y,
                    position.z,
                    paletteParent,
                    original);
            originalToPalette[original] = paletteIndex;
            paletteDepth[paletteIndex] =
                    paletteDepth[paletteParent] + 1;
            paletteRadius[paletteIndex] = childRadius;
        }

        return new Palette(
                toFloatArray(positions),
                parents.stream().mapToInt(Integer::intValue).toArray(),
                originalNodes.stream()
                        .mapToInt(Integer::intValue)
                        .toArray(),
                branchByPaletteNode(
                        originalNodes,
                        rooted.parent(),
                        adjacency));
    }

    private static int betterRepresentative(
            Integer current,
            int candidate,
            int[] assignments,
            double[] distances) {
        if (current == null
                || assignments[candidate] > assignments[current]
                || assignments[candidate] == assignments[current]
                && distances[candidate] > distances[current]
                || assignments[candidate] == assignments[current]
                && distances[candidate] == distances[current]
                && candidate < current) {
            return candidate;
        }
        return current;
    }

    private static int nearestRetainedAncestor(
            int original,
            int[] rootedParents,
            int[] originalToPalette,
            List<Integer> paletteParents,
            int[] paletteDepth,
            float[] paletteRadius,
            float childRadius) {
        int cursor = rootedParents[original];
        int guard = 0;
        while (cursor >= 0 && guard++ <= rootedParents.length) {
            int retained = originalToPalette[cursor];
            if (retained >= 0
                    && paletteDepth[retained] < MAX_PARENT_HOPS
                    && paletteRadius[retained]
                    <= childRadius + MONOTONIC_RADIUS_EPSILON) {
                return retained;
            }
            cursor = rootedParents[cursor];
        }

        int retained = 1;
        while (retained > 0
                && (paletteDepth[retained] >= MAX_PARENT_HOPS
                || paletteRadius[retained]
                > childRadius + MONOTONIC_RADIUS_EPSILON)) {
            retained = paletteParents.get(retained);
        }
        return Math.max(retained, 0);
    }

    private static int[] branchByPaletteNode(
            List<Integer> originalNodes,
            int[] rootedParents,
            List<List<GraphEdge>> adjacency) {
        int[] branches = new int[originalNodes.size()];
        Arrays.fill(branches, -1);
        for (int paletteNode = RESERVED_ROOT_NODES;
             paletteNode < originalNodes.size();
             paletteNode++) {
            branches[paletteNode] = branchFor(
                    originalNodes.get(paletteNode),
                    rootedParents,
                    adjacency);
        }
        return branches;
    }

    private static void addPaletteNode(
            List<Float> positions,
            List<Integer> parents,
            List<Integer> originals,
            float x,
            float y,
            float z,
            int parent,
            int original) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        parents.add(parent);
        originals.add(original);
    }

    private static List<PaletteSegment> buildPaletteSegments(
            Palette palette) {
        List<PaletteSegment> segments = new ArrayList<>();
        for (int child = 1;
             child < palette.parents().length;
             child++) {
            int parent = palette.parents()[child];
            if (parent < 0 || parent >= child) {
                continue;
            }
            int childOffset = child * 3;
            int parentOffset = parent * 3;
            float length = distance(
                    palette.positions()[parentOffset],
                    palette.positions()[parentOffset + 1],
                    palette.positions()[parentOffset + 2],
                    palette.positions()[childOffset],
                    palette.positions()[childOffset + 1],
                    palette.positions()[childOffset + 2]);
            if (!(length > MIN_SEGMENT_LENGTH) || !finite(length)) {
                continue;
            }
            segments.add(new PaletteSegment(
                    segments.size(),
                    parent,
                    child,
                    length,
                    palette.branches()[child]));
        }
        return segments;
    }

    private static RouteCandidate routeCandidate(
            float x,
            float y,
            float z,
            Vector3fc inlet,
            Assignment fullAssignment,
            Palette palette,
            float[] palettePositions,
            List<PaletteSegment> paletteSegments,
            float[] palettePathLength,
            int requiredBranch) {
        float sourceRadius = distance(
                x, y, z, inlet.x(), inlet.y(), inlet.z());
        PaletteProjection projection =
                nearestMonotonicPaletteSegment(
                        x,
                        y,
                        z,
                        inlet,
                        palettePositions,
                        paletteSegments,
                        requiredBranch,
                        sourceRadius);
        if (projection == null) {
            // The nearest anatomical branch can begin outside a small
            // surfel's inlet radius. Walk that branch's compact parents
            // inward until an ancestor segment can accept a non-expanding
            // attachment. This preserves anatomical transport instead of
            // dropping the sample into the direct-to-inlet family.
            projection = nearestInwardParentChainSegment(
                    x,
                    y,
                    z,
                    inlet,
                    palette,
                    palettePositions,
                    paletteSegments,
                    requiredBranch,
                    sourceRadius);
            if (projection == null) {
                return null;
            }
        }

        PaletteSegment segment = projection.segment();
        int attachmentNode = segment.outerPaletteNode();
        int parentNode = palette.parents()[attachmentNode];
        if (parentNode < 0
                || parentNode >= attachmentNode
                || parentNode >= palettePathLength.length) {
            return null;
        }

        int childOffset = attachmentNode * 3;
        int parentOffset = parentNode * 3;
        Vector3f parentPosition = new Vector3f(
                palettePositions[parentOffset],
                palettePositions[parentOffset + 1],
                palettePositions[parentOffset + 2]);
        Vector3f childPosition = new Vector3f(
                palettePositions[childOffset],
                palettePositions[childOffset + 1],
                palettePositions[childOffset + 2]);
        float parentRadius = parentPosition.distance(inlet);
        float childRadius = childPosition.distance(inlet);
        float projectedT = clampUnit((float) projection.t());
        float maximumT = 1.0f;
        float radiusSpan = childRadius - parentRadius;
        boolean monotonicAdjusted = false;
        if (radiusSpan > DISTANCE_EPSILON) {
            maximumT = clampUnit(
                    (sourceRadius - parentRadius)
                            / radiusSpan);
            if (projectedT > maximumT) {
                projectedT = maximumT;
                monotonicAdjusted = true;
            }
        }

        // AttachmentT is uploaded as an unsigned normalized byte. Validate and
        // store the exact value the shader will decode, biased inward when a
        // cap was necessary so rounding cannot recreate an outward first hop.
        float quantizedT = monotonicAdjusted
                ? quantizeUnitInward(projectedT)
                : quantizeUnitNearest(projectedT);
        if (quantizedT > maximumT) {
            quantizedT = quantizeUnitInward(maximumT);
            monotonicAdjusted = true;
        }
        boolean quantizationAdjusted =
                Math.abs(quantizedT - (float) projection.t())
                        > 0.5f / METADATA_QUANTIZATION_STEPS;

        Vector3f attachment = polarSegmentPoint(
                parentPosition,
                childPosition,
                inlet,
                quantizedT);
        float attachmentRadius = attachment.distance(inlet);
        if (attachmentRadius
                > sourceRadius + MONOTONIC_RADIUS_EPSILON) {
            float safeRadius = Math.max(0.0f, sourceRadius);
            Vector3f relative = attachment.sub(
                    inlet.x(), inlet.y(), inlet.z(),
                    new Vector3f());
            if (relative.lengthSquared()
                    > DISTANCE_EPSILON * DISTANCE_EPSILON) {
                relative.normalize().mul(safeRadius);
                attachment.set(
                        inlet.x() + relative.x,
                        inlet.y() + relative.y,
                        inlet.z() + relative.z);
            } else {
                attachment.set(inlet);
            }
            monotonicAdjusted = true;
        }

        float sourceToAttachment = distance(
                x, y, z,
                attachment.x, attachment.y, attachment.z);
        float attachmentToParent =
                attachment.distance(parentPosition);
        float totalPath = sourceToAttachment
                + attachmentToParent
                + palettePathLength[parentNode];
        if (!finite(totalPath) || totalPath < 0.0f) {
            return null;
        }

        float fitPenalty = fullAssignment == null
                ? 1.0f
                : routeFitPenalty(
                fullAssignment,
                projection,
                segment.length());
        float compactConfidence = distanceConfidence(projection);
        float confidence = clampUnit(
                Math.max(
                        fullAssignment == null
                                ? 1.0f
                                : fullAssignment.confidence(),
                        0.30f + compactConfidence * 0.70f)
                        * fitPenalty);
        confidence = Math.max(
                quantizeUnitNearest(confidence),
                1.0f / METADATA_QUANTIZATION_STEPS);
        return new RouteCandidate(
                attachmentNode,
                quantizedT,
                confidence,
                totalPath,
                segment.branch(),
                monotonicAdjusted,
                quantizationAdjusted);
    }

    private static void installCandidate(
            int sample,
            RouteCandidate candidate,
            int[] attachmentNodes,
            float[] attachmentT,
            float[] routeConfidence,
            float[] pathLength,
            int[] sampleBranches,
            boolean[] assigned) {
        attachmentNodes[sample] = candidate.attachmentNode();
        attachmentT[sample] = candidate.attachmentT();
        routeConfidence[sample] = candidate.confidence();
        pathLength[sample] = candidate.totalPath();
        sampleBranches[sample] = candidate.branch();
        assigned[sample] = true;
    }

    private static int nearestAssignedSample(
            float[] samplePositions,
            int sample,
            boolean[] assigned) {
        int offset = sample * 3;
        float x = samplePositions[offset];
        float y = samplePositions[offset + 1];
        float z = samplePositions[offset + 2];
        int nearest = -1;
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        for (int candidate = 0;
             candidate < assigned.length;
             candidate++) {
            if (!assigned[candidate] || candidate == sample) {
                continue;
            }
            int candidateOffset = candidate * 3;
            float dx = x - samplePositions[candidateOffset];
            float dy = y - samplePositions[candidateOffset + 1];
            float dz = z - samplePositions[candidateOffset + 2];
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (!finite(distanceSquared)) {
                continue;
            }
            if (distanceSquared < nearestDistanceSquared
                    || distanceSquared == nearestDistanceSquared
                    && candidate < nearest) {
                nearest = candidate;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private static float quantizeUnitNearest(float value) {
        return Math.round(clampUnit(value)
                * METADATA_QUANTIZATION_STEPS)
                / METADATA_QUANTIZATION_STEPS;
    }

    private static float quantizeUnitInward(float value) {
        return (float) Math.floor(clampUnit(value)
                * METADATA_QUANTIZATION_STEPS)
                / METADATA_QUANTIZATION_STEPS;
    }

    private static float[] palettePathLengths(Palette palette) {
        float[] lengths = new float[palette.parents().length];
        for (int node = 1; node < lengths.length; node++) {
            int parent = palette.parents()[node];
            if (parent < 0 || parent >= node) {
                continue;
            }
            int nodeOffset = node * 3;
            int parentOffset = parent * 3;
            lengths[node] = lengths[parent] + distance(
                    palette.positions()[nodeOffset],
                    palette.positions()[nodeOffset + 1],
                    palette.positions()[nodeOffset + 2],
                    palette.positions()[parentOffset],
                    palette.positions()[parentOffset + 1],
                    palette.positions()[parentOffset + 2]);
        }
        return lengths;
    }

    private static RootedProjection nearestRootedSegment(
            float x,
            float y,
            float z,
            List<RootedSegment> segments,
            int requiredBranch) {
        RootedSegment best = null;
        double bestT = 0.0;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        double secondDistanceSquared = Double.POSITIVE_INFINITY;
        for (RootedSegment segment : segments) {
            if (requiredBranch >= 0
                    && segment.branch() != requiredBranch) {
                continue;
            }
            SegmentDistance projection = project(
                    x, y, z,
                    segment.innerPosition(),
                    segment.outerPosition());
            if (projection == null) {
                continue;
            }
            double distanceSquared = projection.distanceSquared();
            if (distanceSquared < bestDistanceSquared
                    || distanceSquared == bestDistanceSquared
                    && best != null
                    && segment.index() < best.index()) {
                secondDistanceSquared = bestDistanceSquared;
                bestDistanceSquared = distanceSquared;
                bestT = projection.t();
                best = segment;
            } else if (distanceSquared < secondDistanceSquared) {
                secondDistanceSquared = distanceSquared;
            }
        }
        return best == null
                ? null
                : new RootedProjection(
                best,
                bestT,
                bestDistanceSquared,
                secondDistanceSquared);
    }

    private static PaletteProjection nearestMonotonicPaletteSegment(
            float x,
            float y,
            float z,
            Vector3fc inlet,
            float[] palettePositions,
            List<PaletteSegment> segments,
            int requiredBranch,
            float sourceRadius) {
        PaletteSegment best = null;
        double bestT = 0.0;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        double secondDistanceSquared = Double.POSITIVE_INFINITY;
        for (PaletteSegment segment : segments) {
            if (segment.innerPaletteNode() == 0) {
                // The inlet-to-root collar is a material handoff, not an
                // attachment segment. Allowing surfels to attach here makes
                // their first visible hop indistinguishable from a direct
                // inlet fallback.
                continue;
            }
            if (requiredBranch >= 0
                    && segment.branch() != requiredBranch) {
                continue;
            }
            int innerOffset = segment.innerPaletteNode() * 3;
            int outerOffset = segment.outerPaletteNode() * 3;
            Vector3f inner = new Vector3f(
                    palettePositions[innerOffset],
                    palettePositions[innerOffset + 1],
                    palettePositions[innerOffset + 2]);
            if (inner.distance(inlet)
                    > sourceRadius + MONOTONIC_RADIUS_EPSILON) {
                continue;
            }
            Vector3f outer = new Vector3f(
                    palettePositions[outerOffset],
                    palettePositions[outerOffset + 1],
                    palettePositions[outerOffset + 2]);
            SegmentDistance projection =
                    project(x, y, z, inner, outer);
            if (projection == null) {
                continue;
            }
            double distanceSquared = projection.distanceSquared();
            if (distanceSquared < bestDistanceSquared
                    || distanceSquared == bestDistanceSquared
                    && best != null
                    && segment.index() < best.index()) {
                secondDistanceSquared = bestDistanceSquared;
                bestDistanceSquared = distanceSquared;
                bestT = projection.t();
                best = segment;
            } else if (distanceSquared < secondDistanceSquared) {
                secondDistanceSquared = distanceSquared;
            }
        }
        return best == null
                ? null
                : new PaletteProjection(
                best,
                bestT,
                bestDistanceSquared,
                secondDistanceSquared);
    }

    /**
     * Starts from the nearest segment on the requested anatomical branch and
     * walks its compact parent chain toward the inlet until the segment's
     * inner endpoint is no farther out than the surfel. The inlet-to-root
     * collar is deliberately excluded: a sample that cannot reach an
     * anatomical segment retires instead of acquiring direct inlet motion.
     */
    private static PaletteProjection nearestInwardParentChainSegment(
            float x,
            float y,
            float z,
            Vector3fc inlet,
            Palette palette,
            float[] palettePositions,
            List<PaletteSegment> segments,
            int requiredBranch,
            float sourceRadius) {
        PaletteProjection nearest = nearestPaletteSegment(
                x,
                y,
                z,
                palettePositions,
                segments,
                requiredBranch);
        if (nearest == null) {
            return null;
        }

        int outerNode = nearest.segment().outerPaletteNode();
        while (outerNode > 0) {
            int parentNode = palette.parents()[outerNode];
            if (parentNode < 0
                    || parentNode >= outerNode) {
                return null;
            }
            if (parentNode == 0) {
                return null;
            }
            PaletteSegment parentSegment =
                    paletteSegmentForOuterNode(
                            segments, outerNode);
            if (parentSegment == null) {
                return null;
            }
            int parentOffset = parentNode * 3;
            float parentRadius = distance(
                    palettePositions[parentOffset],
                    palettePositions[parentOffset + 1],
                    palettePositions[parentOffset + 2],
                    inlet.x(),
                    inlet.y(),
                    inlet.z());
            if (parentRadius
                    <= sourceRadius + MONOTONIC_RADIUS_EPSILON) {
                int outerOffset = outerNode * 3;
                SegmentDistance projection = project(
                        x,
                        y,
                        z,
                        new Vector3f(
                                palettePositions[parentOffset],
                                palettePositions[parentOffset + 1],
                                palettePositions[parentOffset + 2]),
                        new Vector3f(
                                palettePositions[outerOffset],
                                palettePositions[outerOffset + 1],
                                palettePositions[outerOffset + 2]));
                return projection == null
                        ? null
                        : new PaletteProjection(
                        parentSegment,
                        projection.t(),
                        projection.distanceSquared(),
                        Double.POSITIVE_INFINITY);
            }
            outerNode = parentNode;
        }
        return null;
    }

    private static PaletteProjection nearestPaletteSegment(
            float x,
            float y,
            float z,
            float[] palettePositions,
            List<PaletteSegment> segments,
            int requiredBranch) {
        PaletteSegment best = null;
        double bestT = 0.0;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        double secondDistanceSquared = Double.POSITIVE_INFINITY;
        for (PaletteSegment segment : segments) {
            if (segment.innerPaletteNode() == 0) {
                continue;
            }
            if (requiredBranch >= 0
                    && segment.branch() != requiredBranch) {
                continue;
            }
            int innerOffset =
                    segment.innerPaletteNode() * 3;
            int outerOffset =
                    segment.outerPaletteNode() * 3;
            SegmentDistance projection = project(
                    x,
                    y,
                    z,
                    new Vector3f(
                            palettePositions[innerOffset],
                            palettePositions[innerOffset + 1],
                            palettePositions[innerOffset + 2]),
                    new Vector3f(
                            palettePositions[outerOffset],
                            palettePositions[outerOffset + 1],
                            palettePositions[outerOffset + 2]));
            if (projection == null) {
                continue;
            }
            double distanceSquared =
                    projection.distanceSquared();
            if (distanceSquared < bestDistanceSquared
                    || distanceSquared == bestDistanceSquared
                    && best != null
                    && segment.index() < best.index()) {
                secondDistanceSquared = bestDistanceSquared;
                bestDistanceSquared = distanceSquared;
                bestT = projection.t();
                best = segment;
            } else if (distanceSquared
                    < secondDistanceSquared) {
                secondDistanceSquared = distanceSquared;
            }
        }
        return best == null
                ? null
                : new PaletteProjection(
                best,
                bestT,
                bestDistanceSquared,
                secondDistanceSquared);
    }

    private static PaletteSegment paletteSegmentForOuterNode(
            List<PaletteSegment> segments,
            int outerNode) {
        for (PaletteSegment segment : segments) {
            if (segment.outerPaletteNode() == outerNode) {
                return segment;
            }
        }
        return null;
    }

    private static OriginalProjection nearestOriginalSegment(
            float x,
            float y,
            float z,
            List<OriginalSegment> segments,
            boolean meaningfulOnly) {
        OriginalSegment best = null;
        double bestT = 0.0;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        for (OriginalSegment segment : segments) {
            if (meaningfulOnly && !segment.meaningful()) {
                continue;
            }
            SegmentDistance projection = project(
                    x, y, z,
                    segment.positionA(),
                    segment.positionB());
            if (projection == null) {
                continue;
            }
            if (projection.distanceSquared() < bestDistanceSquared
                    || projection.distanceSquared()
                    == bestDistanceSquared
                    && best != null
                    && segment.index() < best.index()) {
                best = segment;
                bestT = projection.t();
                bestDistanceSquared =
                        projection.distanceSquared();
            }
        }
        return best == null
                ? null
                : new OriginalProjection(
                best, bestT, bestDistanceSquared);
    }

    private static SegmentDistance project(
            float x,
            float y,
            float z,
            Vector3f inner,
            Vector3f outer) {
        double dx = outer.x - (double) inner.x;
        double dy = outer.y - (double) inner.y;
        double dz = outer.z - (double) inner.z;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (!(lengthSquared
                > MIN_SEGMENT_LENGTH * MIN_SEGMENT_LENGTH)
                || !Double.isFinite(lengthSquared)) {
            return null;
        }
        double px = x - (double) inner.x;
        double py = y - (double) inner.y;
        double pz = z - (double) inner.z;
        double t = clampUnit(
                (px * dx + py * dy + pz * dz)
                        / lengthSquared);
        double ex = px - dx * t;
        double ey = py - dy * t;
        double ez = pz - dz * t;
        double distanceSquared = ex * ex + ey * ey + ez * ez;
        return Double.isFinite(distanceSquared)
                ? new SegmentDistance(t, distanceSquared)
                : null;
    }

    private static float routeFitPenalty(
            Assignment full,
            PaletteProjection compact,
            float compactSegmentLength) {
        double fullDistance = Math.sqrt(
                Math.max(full.distanceSquared(), 0.0));
        double compactDistance = Math.sqrt(
                Math.max(compact.distanceSquared(), 0.0));
        double extra = Math.max(0.0, compactDistance - fullDistance);
        double scale = Math.max(
                compactSegmentLength * 0.55,
                fullDistance + 1.0e-4);
        return clampUnit((float) (1.0 / (1.0 + extra / scale)));
    }

    private static int countAssigned(Assignment[] assignments) {
        int count = 0;
        for (Assignment assignment : assignments) {
            if (assignment != null) {
                count++;
            }
        }
        return count;
    }

    private static void retainNearest(
            int[] nearest,
            float[] distances,
            int neighborCount,
            int sample,
            int candidate,
            float distanceSquared) {
        int base = sample * neighborCount;
        int insertion = -1;
        for (int slot = 0; slot < neighborCount; slot++) {
            if (distanceSquared < distances[base + slot]
                    || distanceSquared == distances[base + slot]
                    && candidate < nearest[base + slot]) {
                insertion = slot;
                break;
            }
        }
        if (insertion < 0) {
            return;
        }
        for (int slot = neighborCount - 1;
             slot > insertion;
             slot--) {
            nearest[base + slot] = nearest[base + slot - 1];
            distances[base + slot] = distances[base + slot - 1];
        }
        nearest[base + insertion] = candidate;
        distances[base + insertion] = distanceSquared;
    }

    private static float distanceConfidence(
            RootedProjection projection) {
        return distanceConfidence(
                projection.distanceSquared(),
                projection.secondDistanceSquared());
    }

    private static float distanceConfidence(
            PaletteProjection projection) {
        return distanceConfidence(
                projection.distanceSquared(),
                projection.secondDistanceSquared());
    }

    private static float distanceConfidence(
            double nearestSquared,
            double secondSquared) {
        if (!Double.isFinite(secondSquared)) {
            return 1.0f;
        }
        double nearest = Math.sqrt(Math.max(nearestSquared, 0.0));
        double second = Math.sqrt(Math.max(secondSquared, 0.0));
        if (!(second > DISTANCE_EPSILON)) {
            return 0.0f;
        }
        return clampUnit((float) ((second - nearest) / second));
    }

    private static Result fallback(
            float[] samplePositions,
            Vector3fc inlet,
            int sourceNodeCount,
            int sourceSegmentCount,
            String reason) {
        Result virtualSurfaceRoute = virtualSurfaceFallback(
                samplePositions,
                inlet,
                sourceNodeCount,
                sourceSegmentCount,
                reason);
        if (virtualSurfaceRoute != null) {
            return virtualSurfaceRoute;
        }

        int sampleCount = samplePositions.length / 3;
        float inletX = finite(inlet) ? inlet.x() : 0.0f;
        float inletY = finite(inlet) ? inlet.y() : 0.0f;
        float inletZ = finite(inlet) ? inlet.z() : 0.0f;
        int[] attachmentNodes = new int[sampleCount];
        int[] branches = new int[sampleCount];
        Arrays.fill(attachmentNodes, -1);
        Arrays.fill(branches, -1);
        float[] pathLength = new float[sampleCount];
        float[] normalized = new float[sampleCount];
        float maximum = 0.0f;
        for (int sample = 0; sample < sampleCount; sample++) {
            int offset = sample * 3;
            if (!finite(
                    samplePositions[offset],
                    samplePositions[offset + 1],
                    samplePositions[offset + 2])) {
                continue;
            }
            pathLength[sample] = distance(
                    samplePositions[offset],
                    samplePositions[offset + 1],
                    samplePositions[offset + 2],
                    inletX, inletY, inletZ);
            maximum = Math.max(maximum, pathLength[sample]);
        }
        if (maximum > DISTANCE_EPSILON) {
            for (int sample = 0; sample < sampleCount; sample++) {
                normalized[sample] =
                        clampUnit(pathLength[sample] / maximum);
            }
        }
        return new Result(
                new float[]{inletX, inletY, inletZ},
                new int[]{-1},
                new int[]{-1},
                attachmentNodes,
                new float[sampleCount],
                new float[sampleCount],
                pathLength,
                normalized,
                branches,
                false,
                sourceNodeCount,
                sourceSegmentCount,
                0,
                sampleCount,
                0,
                0,
                0,
                0,
                0,
                0,
                sampleCount,
                "<direct>",
                maximum,
                reason);
    }

    private static Result virtualSurfaceFallback(
            float[] samplePositions,
            Vector3fc inlet,
            int sourceNodeCount,
            int sourceSegmentCount,
            String reason) {
        if (!finite(inlet)) {
            return null;
        }
        int sampleCount = samplePositions.length / 3;
        List<Integer> finiteSamples = new ArrayList<>();
        for (int sample = 0; sample < sampleCount; sample++) {
            int offset = sample * 3;
            if (finite(
                    samplePositions[offset],
                    samplePositions[offset + 1],
                    samplePositions[offset + 2])
                    && distance(
                    samplePositions[offset],
                    samplePositions[offset + 1],
                    samplePositions[offset + 2],
                    inlet.x(), inlet.y(), inlet.z())
                    > MIN_SEGMENT_LENGTH) {
                finiteSamples.add(sample);
            }
        }
        if (finiteSamples.isEmpty()) {
            return null;
        }

        List<Integer> anchors = selectVirtualAnchors(
                samplePositions,
                inlet,
                finiteSamples,
                MAX_PALETTE_NODES - 1);
        if (anchors.isEmpty()) {
            return null;
        }
        anchors.sort(Comparator
                .<Integer>comparingDouble(sample -> {
                    int offset = sample * 3;
                    return distance(
                            samplePositions[offset],
                            samplePositions[offset + 1],
                            samplePositions[offset + 2],
                            inlet.x(), inlet.y(), inlet.z());
                })
                .thenComparingInt(Integer::intValue));

        float[] palettePositions =
                new float[(anchors.size() + 1) * 3];
        int[] paletteParents = new int[anchors.size() + 1];
        int[] paletteOriginalNodes = new int[anchors.size() + 1];
        int[] paletteBranches = new int[anchors.size() + 1];
        int[] paletteDepth = new int[anchors.size() + 1];
        palettePositions[0] = inlet.x();
        palettePositions[1] = inlet.y();
        palettePositions[2] = inlet.z();
        paletteParents[0] = -1;
        paletteOriginalNodes[0] = -1;
        Arrays.fill(paletteBranches, 0);
        for (int anchorIndex = 0;
             anchorIndex < anchors.size();
             anchorIndex++) {
            int node = anchorIndex + 1;
            int sample = anchors.get(anchorIndex);
            int sourceOffset = sample * 3;
            int nodeOffset = node * 3;
            palettePositions[nodeOffset] =
                    samplePositions[sourceOffset];
            palettePositions[nodeOffset + 1] =
                    samplePositions[sourceOffset + 1];
            palettePositions[nodeOffset + 2] =
                    samplePositions[sourceOffset + 2];
            paletteOriginalNodes[node] = -1;

            int parent = nearestVirtualParent(
                    node,
                    palettePositions,
                    paletteDepth);
            paletteParents[node] = parent;
            paletteDepth[node] = paletteDepth[parent] + 1;
        }

        Palette palette = new Palette(
                palettePositions,
                paletteParents,
                paletteOriginalNodes,
                paletteBranches);
        List<PaletteSegment> segments = buildPaletteSegments(palette);
        if (segments.isEmpty()) {
            return null;
        }
        float[] palettePathLength = palettePathLengths(palette);
        int[] attachmentNodes = new int[sampleCount];
        float[] attachmentT = new float[sampleCount];
        float[] routeConfidence = new float[sampleCount];
        float[] pathLength = new float[sampleCount];
        float[] normalizedPathLength = new float[sampleCount];
        int[] sampleBranches = new int[sampleCount];
        Arrays.fill(attachmentNodes, -1);
        Arrays.fill(sampleBranches, -1);

        int assignedSamples = 0;
        int invalidSamples = 0;
        int unroutableSamples = 0;
        int monotonicAdjusted = 0;
        int quantizationAdjusted = 0;
        float maximumPathLength = 0.0f;
        for (int sample = 0; sample < sampleCount; sample++) {
            int offset = sample * 3;
            float x = samplePositions[offset];
            float y = samplePositions[offset + 1];
            float z = samplePositions[offset + 2];
            if (!finite(x, y, z)) {
                invalidSamples++;
                continue;
            }
            RouteCandidate candidate = routeCandidate(
                    x,
                    y,
                    z,
                    inlet,
                    null,
                    palette,
                    palettePositions,
                    segments,
                    palettePathLength,
                    -1);
            if (candidate == null) {
                unroutableSamples++;
                pathLength[sample] = distance(
                        x, y, z,
                        inlet.x(), inlet.y(), inlet.z());
                maximumPathLength = Math.max(
                        maximumPathLength,
                        pathLength[sample]);
                continue;
            }
            attachmentNodes[sample] = candidate.attachmentNode();
            attachmentT[sample] = candidate.attachmentT();
            routeConfidence[sample] = candidate.confidence();
            pathLength[sample] = candidate.totalPath();
            sampleBranches[sample] = 0;
            assignedSamples++;
            maximumPathLength = Math.max(
                    maximumPathLength,
                    candidate.totalPath());
            if (candidate.monotonicAdjusted()) {
                monotonicAdjusted++;
            }
            if (candidate.quantizationAdjusted()) {
                quantizationAdjusted++;
            }
        }
        if (assignedSamples == 0
                || !(maximumPathLength > DISTANCE_EPSILON)) {
            return null;
        }
        for (int sample = 0; sample < sampleCount; sample++) {
            normalizedPathLength[sample] = clampUnit(
                    pathLength[sample] / maximumPathLength);
            if (attachmentNodes[sample] > 0) {
                normalizedPathLength[sample] = Math.max(
                        normalizedPathLength[sample],
                        MIN_ENCODED_PATH);
            }
        }
        int fallbackSamples = sampleCount - assignedSamples;
        return new Result(
                palettePositions,
                paletteParents,
                paletteOriginalNodes,
                attachmentNodes,
                attachmentT,
                routeConfidence,
                pathLength,
                normalizedPathLength,
                sampleBranches,
                true,
                sourceNodeCount,
                sourceSegmentCount,
                assignedSamples,
                fallbackSamples,
                0,
                monotonicAdjusted,
                0,
                assignedSamples,
                quantizationAdjusted,
                invalidSamples,
                unroutableSamples,
                "<virtual-surface>",
                maximumPathLength,
                "virtual-surface:" + reason);
    }

    private static List<Integer> selectVirtualAnchors(
            float[] samplePositions,
            Vector3fc inlet,
            List<Integer> finiteSamples,
            int maximumAnchors) {
        List<Integer> selected = new ArrayList<>();
        int closest = finiteSamples.get(0);
        float closestRadius = Float.POSITIVE_INFINITY;
        for (int sample : finiteSamples) {
            int offset = sample * 3;
            float radius = distance(
                    samplePositions[offset],
                    samplePositions[offset + 1],
                    samplePositions[offset + 2],
                    inlet.x(), inlet.y(), inlet.z());
            if (radius < closestRadius
                    || radius == closestRadius && sample < closest) {
                closest = sample;
                closestRadius = radius;
            }
        }
        selected.add(closest);

        int target = Math.min(maximumAnchors, finiteSamples.size());
        while (selected.size() < target) {
            int best = -1;
            float bestDistanceSquared = -1.0f;
            for (int candidate : finiteSamples) {
                if (selected.contains(candidate)) {
                    continue;
                }
                int candidateOffset = candidate * 3;
                float nearestSquared = Float.POSITIVE_INFINITY;
                for (int retained : selected) {
                    int retainedOffset = retained * 3;
                    float dx = samplePositions[candidateOffset]
                            - samplePositions[retainedOffset];
                    float dy = samplePositions[candidateOffset + 1]
                            - samplePositions[retainedOffset + 1];
                    float dz = samplePositions[candidateOffset + 2]
                            - samplePositions[retainedOffset + 2];
                    nearestSquared = Math.min(
                            nearestSquared,
                            dx * dx + dy * dy + dz * dz);
                }
                if (nearestSquared > bestDistanceSquared
                        || nearestSquared == bestDistanceSquared
                        && candidate < best) {
                    best = candidate;
                    bestDistanceSquared = nearestSquared;
                }
            }
            if (best < 0) {
                break;
            }
            selected.add(best);
        }
        return selected;
    }

    private static int nearestVirtualParent(
            int node,
            float[] palettePositions,
            int[] paletteDepth) {
        if (node == 1) {
            return 0;
        }
        int nodeOffset = node * 3;
        int parent = 1;
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        for (int candidate = 1; candidate < node; candidate++) {
            if (paletteDepth[candidate]
                    >= MAX_PARENT_HOPS) {
                continue;
            }
            int candidateOffset = candidate * 3;
            float dx = palettePositions[nodeOffset]
                    - palettePositions[candidateOffset];
            float dy = palettePositions[nodeOffset + 1]
                    - palettePositions[candidateOffset + 1];
            float dz = palettePositions[nodeOffset + 2]
                    - palettePositions[candidateOffset + 2];
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < nearestDistanceSquared
                    || distanceSquared == nearestDistanceSquared
                    && candidate < parent) {
                parent = candidate;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return parent;
    }

    private static String inletPath(
            List<Node> nodes,
            OriginalProjection root) {
        OriginalSegment segment = root.segment();
        int nodeIndex = root.t() <= 0.5
                ? segment.a()
                : segment.b();
        String path = nodes.get(nodeIndex).path();
        return path == null || path.isBlank()
                ? "<unnamed>"
                : path;
    }

    private static Vector3f pointOn(
            OriginalSegment segment,
            double t) {
        return new Vector3f(
                lerp(
                        segment.positionA().x,
                        segment.positionB().x,
                        (float) t),
                lerp(
                        segment.positionA().y,
                        segment.positionB().y,
                        (float) t),
                lerp(
                        segment.positionA().z,
                        segment.positionB().z,
                        (float) t));
    }

    /**
     * Interpolates a route segment in inlet-centered polar form. Direction
     * bends between the endpoints while radius changes independently, so a
     * child-to-parent traversal with monotonic endpoint radii cannot dip
     * inward and then expand again along a straight chord.
     */
    static Vector3f polarSegmentPoint(
            Vector3fc start,
            Vector3fc end,
            Vector3fc inlet,
            float amount) {
        float t = clampUnit(amount);
        Vector3f startRelative = new Vector3f(start).sub(inlet);
        Vector3f endRelative = new Vector3f(end).sub(inlet);
        float startRadius = startRelative.length();
        float endRadius = endRelative.length();

        Vector3f fallbackDirection = startRadius > DISTANCE_EPSILON
                ? startRelative.div(startRadius, new Vector3f())
                : endRadius > DISTANCE_EPSILON
                ? endRelative.div(endRadius, new Vector3f())
                : new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f startDirection = startRadius > DISTANCE_EPSILON
                ? startRelative.div(startRadius, new Vector3f())
                : new Vector3f(fallbackDirection);
        Vector3f endDirection = endRadius > DISTANCE_EPSILON
                ? endRelative.div(endRadius, new Vector3f())
                : new Vector3f(fallbackDirection);
        Vector3f direction = startDirection.lerp(
                endDirection,
                t,
                new Vector3f());
        if (direction.lengthSquared()
                <= DISTANCE_EPSILON * DISTANCE_EPSILON) {
            direction.set(fallbackDirection);
        } else {
            direction.normalize();
        }
        return direction
                .mul(lerp(startRadius, endRadius, t))
                .add(inlet);
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static float distance(Vector3fc first, Vector3fc second) {
        return distance(
                first.x(), first.y(), first.z(),
                second.x(), second.y(), second.z());
    }

    private static float distance(
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz) {
        double dx = bx - (double) ax;
        double dy = by - (double) ay;
        double dz = bz - (double) az;
        double squared = dx * dx + dy * dy + dz * dz;
        return Double.isFinite(squared)
                ? (float) Math.sqrt(Math.max(squared, 0.0))
                : Float.NaN;
    }

    private static float lerp(float first, float second, float amount) {
        return first + (second - first) * amount;
    }

    private static float clampUnit(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static double clampUnit(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static boolean finite(Vector3fc vector) {
        return vector != null
                && finite(vector.x(), vector.y(), vector.z());
    }

    private static boolean finite(float x, float y, float z) {
        return finite(x) && finite(y) && finite(z);
    }

    private static boolean finite(float value) {
        return Float.isFinite(value);
    }

    private static void throwIfCancellationRequested(
            BooleanSupplier cancellation) {
        if (cancellation.getAsBoolean()) {
            throw new CancellationException(
                    "Dark Ball splat bone-route build cancelled");
        }
    }

    record Node(
            Vector3f position,
            int parentIndex,
            String path,
            boolean ownsGeometry,
            boolean chainable
    ) {
        Node {
            position = position == null
                    ? null
                    : new Vector3f(position);
            path = path == null ? "" : path;
        }

        Node(Vector3f position,
             int parentIndex,
             String path,
             boolean ownsGeometry) {
            this(
                    position,
                    parentIndex,
                    path,
                    ownsGeometry,
                    ownsGeometry);
        }
    }

    record Result(
            float[] palettePositions,
            int[] paletteParents,
            int[] paletteOriginalNodes,
            int[] attachmentNodes,
            float[] attachmentT,
            float[] routeConfidence,
            float[] pathLength,
            float[] normalizedPathLength,
            int[] sampleBranches,
            boolean guided,
            int sourceNodeCount,
            int sourceSegmentCount,
            int assignedSamples,
            int fallbackSamples,
            int coherenceAdjustedSamples,
            int monotonicAdjustedSamples,
            int neighborRecoveredSamples,
            int branchRemappedSamples,
            int quantizationAdjustedSamples,
            int invalidSampleFallbacks,
            int unroutableFallbacks,
            String inletPath,
            float maximumPathLength,
            String status
    ) {
        Result {
            palettePositions = palettePositions == null
                    ? new float[0]
                    : palettePositions;
            paletteParents = paletteParents == null
                    ? new int[0]
                    : paletteParents;
            paletteOriginalNodes = paletteOriginalNodes == null
                    ? new int[paletteParents.length]
                    : paletteOriginalNodes;
            attachmentNodes = attachmentNodes == null
                    ? new int[0]
                    : attachmentNodes;
            attachmentT = attachmentT == null
                    ? new float[attachmentNodes.length]
                    : attachmentT;
            routeConfidence = routeConfidence == null
                    ? new float[attachmentNodes.length]
                    : routeConfidence;
            pathLength = pathLength == null
                    ? new float[attachmentNodes.length]
                    : pathLength;
            normalizedPathLength = normalizedPathLength == null
                    ? new float[attachmentNodes.length]
                    : normalizedPathLength;
            sampleBranches = sampleBranches == null
                    ? new int[attachmentNodes.length]
                    : sampleBranches;
            inletPath = inletPath == null ? "<unnamed>" : inletPath;
            status = status == null ? "unknown" : status;

            if (palettePositions.length != paletteParents.length * 3
                    || paletteOriginalNodes.length
                    != paletteParents.length
                    || paletteParents.length > MAX_PALETTE_NODES) {
                throw new IllegalArgumentException(
                        "bone palette fields are inconsistent");
            }
            int sampleCount = attachmentNodes.length;
            if (attachmentT.length != sampleCount
                    || routeConfidence.length != sampleCount
                    || pathLength.length != sampleCount
                    || normalizedPathLength.length != sampleCount
                    || sampleBranches.length != sampleCount) {
                throw new IllegalArgumentException(
                        "per-sample bone-route fields are inconsistent");
            }
            for (int node = 0; node < paletteParents.length; node++) {
                int parent = paletteParents[node];
                if (node == 0 && parent != -1
                        || node > 0 && (parent < 0 || parent >= node)) {
                    throw new IllegalArgumentException(
                            "bone palette parent order is invalid");
                }
                int hops = 0;
                int cursor = node;
                while (cursor > 0) {
                    cursor = paletteParents[cursor];
                    if (++hops > MAX_PARENT_HOPS) {
                        throw new IllegalArgumentException(
                                "bone palette exceeds fixed shader depth");
                    }
                }
            }
            for (int sample = 0; sample < sampleCount; sample++) {
                int attachment = attachmentNodes[sample];
                if (attachment < -1
                        || attachment >= paletteParents.length
                        || !finite(attachmentT[sample])
                        || attachmentT[sample] < 0.0f
                        || attachmentT[sample] > 1.0f
                        || !finite(routeConfidence[sample])
                        || routeConfidence[sample] < 0.0f
                        || routeConfidence[sample] > 1.0f
                        || !finite(pathLength[sample])
                        || pathLength[sample] < 0.0f
                        || !finite(normalizedPathLength[sample])
                        || normalizedPathLength[sample] < 0.0f
                        || normalizedPathLength[sample] > 1.0f) {
                    throw new IllegalArgumentException(
                            "invalid per-sample bone route at " + sample);
                }
            }
        }

        int paletteCount() {
            return paletteParents.length;
        }

        int sampleCount() {
            return attachmentNodes.length;
        }

        String summary() {
            return String.format(
                    Locale.ROOT,
                    "%s(nodes=%d/%d, segments=%d, assigned=%d, "
                            + "fallback=%d, coherent=%d, "
                            + "repair=monotonic:%d/neighbor:%d/remap:%d/"
                            + "quantized:%d, unresolved=invalid:%d/"
                            + "unroutable:%d, inlet=%s, "
                            + "maxPath=%.5f, status=%s)",
                    guided ? "bone-routed" : "route-unavailable",
                    paletteCount(),
                    sourceNodeCount,
                    sourceSegmentCount,
                    assignedSamples,
                    fallbackSamples,
                    coherenceAdjustedSamples,
                    monotonicAdjustedSamples,
                    neighborRecoveredSamples,
                    branchRemappedSamples,
                    quantizationAdjustedSamples,
                    invalidSampleFallbacks,
                    unroutableFallbacks,
                    inletPath,
                    maximumPathLength,
                    status);
        }
    }

    private record OriginalSegment(
            int index,
            int a,
            int b,
            Vector3f positionA,
            Vector3f positionB,
            float length,
            boolean meaningful
    ) {
    }

    private record OriginalProjection(
            OriginalSegment segment,
            double t,
            double distanceSquared
    ) {
    }

    private record RootedSegment(
            int index,
            int innerOriginal,
            int outerOriginal,
            Vector3f innerPosition,
            Vector3f outerPosition,
            float length,
            boolean meaningful,
            int branch
    ) {
    }

    private record RootedProjection(
            RootedSegment segment,
            double t,
            double distanceSquared,
            double secondDistanceSquared
    ) {
    }

    private record Palette(
            float[] positions,
            int[] parents,
            int[] originalNodes,
            int[] branches
    ) {
    }

    private record PaletteSegment(
            int index,
            int innerPaletteNode,
            int outerPaletteNode,
            float length,
            int branch
    ) {
    }

    private record PaletteProjection(
            PaletteSegment segment,
            double t,
            double distanceSquared,
            double secondDistanceSquared
    ) {
    }

    private record RouteCandidate(
            int attachmentNode,
            float attachmentT,
            float confidence,
            float totalPath,
            int branch,
            boolean monotonicAdjusted,
            boolean quantizationAdjusted
    ) {
    }

    private record Assignment(
            RootedSegment segment,
            float t,
            double distanceSquared,
            float confidence
    ) {
    }

    private record SegmentDistance(
            double t,
            double distanceSquared
    ) {
    }

    private record GraphEdge(
            int node,
            int segmentIndex,
            float length
    ) {
    }

    private record QueueNode(int node, double distance) {
    }

    private record RootedGraph(
            double[] distanceToRoot,
            int[] parent
    ) {
    }

    private record BranchVote(float weight, int count) {
    }
}
