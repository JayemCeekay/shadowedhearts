package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Groups low-priority compass markers that would otherwise occupy the same
 * few horizontal pixels. Selected, tracked, and locked readings are always
 * emitted as their own cluster.
 */
public final class AuraReaderMarkerClusterer {
    private AuraReaderMarkerClusterer() {
    }

    public static List<Cluster> cluster(List<Marker> markers, float threshold) {
        if (markers == null || markers.isEmpty()) {
            return List.of();
        }

        float safeThreshold = Math.max(0.0f, threshold);
        List<Marker> sorted = markers.stream()
                .sorted(Comparator.comparingDouble(Marker::normalizedX))
                .toList();
        List<MutableCluster> working = new ArrayList<>();

        for (Marker marker : sorted) {
            if (marker.protectedFromClustering()) {
                working.add(new MutableCluster(marker));
                continue;
            }

            MutableCluster candidate = null;
            for (int i = working.size() - 1; i >= 0; i--) {
                MutableCluster current = working.get(i);
                if (current.protectedFromClustering()) {
                    continue;
                }
                if (marker.normalizedX() - current.normalizedX() > safeThreshold) {
                    break;
                }
                if (current.type() == marker.type()
                        && Math.abs(marker.normalizedX() - current.normalizedX()) <= safeThreshold) {
                    candidate = current;
                    break;
                }
            }

            if (candidate == null) {
                working.add(new MutableCluster(marker));
            } else {
                candidate.add(marker);
            }
        }

        return working.stream().map(MutableCluster::freeze).toList();
    }

    public record Marker(
            String id,
            AuraReadingType type,
            AuraReadingSource source,
            float normalizedX,
            int priority,
            boolean selected,
            boolean locked
    ) {
        public Marker {
            id = id == null ? "" : id;
            type = type == null ? AuraReadingType.UNKNOWN_ANOMALY : type;
            source = source == null ? AuraReadingSource.PASSIVE : source;
            normalizedX = Math.max(-1.0f, Math.min(1.0f, normalizedX));
        }

        public boolean protectedFromClustering() {
            return selected
                    || locked
                    || source == AuraReadingSource.TRACKED
                    || source == AuraReadingSource.LOCKED;
        }
    }

    public record Cluster(Marker representative, List<Marker> members, float normalizedX) {
        public Cluster {
            members = members == null ? List.of() : List.copyOf(members);
            normalizedX = Math.max(-1.0f, Math.min(1.0f, normalizedX));
        }

        public int count() {
            return members.size();
        }
    }

    private static final class MutableCluster {
        private final List<Marker> members = new ArrayList<>();
        private Marker representative;
        private float normalizedX;

        private MutableCluster(Marker marker) {
            add(marker);
        }

        private void add(Marker marker) {
            int oldCount = members.size();
            members.add(marker);
            normalizedX = ((normalizedX * oldCount) + marker.normalizedX()) / members.size();
            if (representative == null
                    || marker.priority() > representative.priority()
                    || marker.locked()
                    || (marker.selected() && !representative.locked())) {
                representative = marker;
            }
        }

        private boolean protectedFromClustering() {
            return representative != null && representative.protectedFromClustering();
        }

        private AuraReadingType type() {
            return representative == null ? AuraReadingType.UNKNOWN_ANOMALY : representative.type();
        }

        private float normalizedX() {
            return normalizedX;
        }

        private Cluster freeze() {
            return new Cluster(representative, members, normalizedX);
        }
    }
}
