package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudRect;
import com.jayemceekay.shadowedhearts.config.MoonwoundObjectivePlacement;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/** Places the objective around known HUD rectangles without shrinking its readable content. */
public final class MoonwoundHudLayout {
    private static final int VIEWPORT_MARGIN = 8;
    private static final int OBSTACLE_GAP = 4;
    private static final int AUTO_TOP = 64;

    private MoonwoundHudLayout() { }

    /**
     * Offsets move the preferred anchor in GUI pixels. When it is obstructed, the nearest clear
     * placement wins; equal distances prefer the upper, then leftmost position. An empty result
     * lets the caller retry a smaller readable card or omit the overlay for this frame.
     */
    public static Optional<AuraReaderHudRect> resolve(int width, int height, int cardWidth, int cardHeight,
                                                     MoonwoundObjectivePlacement placement,
                                                     int offsetX, int offsetY,
                                                     List<AuraReaderHudRect> occupied) {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(occupied, "occupied");
        if (cardWidth <= 0 || cardHeight <= 0
                || (long) width < (long) cardWidth + VIEWPORT_MARGIN * 2
                || (long) height < (long) cardHeight + VIEWPORT_MARGIN * 2) return Optional.empty();

        int maxX = width - VIEWPORT_MARGIN - cardWidth;
        int maxY = height - VIEWPORT_MARGIN - cardHeight;
        int anchorX = switch (placement) {
            case TOP_LEFT, BOTTOM_LEFT -> VIEWPORT_MARGIN;
            case TOP_CENTER -> (width - cardWidth) / 2;
            case AUTO, TOP_RIGHT, BOTTOM_RIGHT -> maxX;
        };
        int anchorY = switch (placement) {
            case AUTO -> AUTO_TOP;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> maxY;
            case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> VIEWPORT_MARGIN;
        };
        int preferredX = clamp((long) anchorX + offsetX, maxX);
        int preferredY = clamp((long) anchorY + offsetY, maxY);

        SortedSet<Integer> xs = new TreeSet<>();
        SortedSet<Integer> ys = new TreeSet<>();
        xs.add(VIEWPORT_MARGIN);
        xs.add(maxX);
        xs.add((width - cardWidth) / 2);
        xs.add(preferredX);
        ys.add(VIEWPORT_MARGIN);
        ys.add(maxY);
        ys.add((height - cardHeight) / 2);
        ys.add(preferredY);
        for (AuraReaderHudRect obstacle : occupied) {
            Objects.requireNonNull(obstacle, "occupied rectangle");
            if (obstacle.left() == obstacle.right() || obstacle.top() == obstacle.bottom()) continue;
            xs.add(clamp((long) obstacle.left() - OBSTACLE_GAP - cardWidth, maxX));
            xs.add(clamp((long) obstacle.right() + OBSTACLE_GAP, maxX));
            ys.add(clamp((long) obstacle.top() - OBSTACLE_GAP - cardHeight, maxY));
            ys.add(clamp((long) obstacle.bottom() + OBSTACLE_GAP, maxY));
        }

        AuraReaderHudRect best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int y : ys) {
            for (int x : xs) {
                AuraReaderHudRect candidate = new AuraReaderHudRect(x, y, x + cardWidth, y + cardHeight);
                if (!clear(candidate, occupied)) continue;
                long dx = (long) x - preferredX;
                long dy = (long) y - preferredY;
                long distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean clear(AuraReaderHudRect card, List<AuraReaderHudRect> occupied) {
        for (AuraReaderHudRect obstacle : occupied) {
            if (obstacle.left() == obstacle.right() || obstacle.top() == obstacle.bottom()) continue;
            if ((long) card.left() < (long) obstacle.right() + OBSTACLE_GAP
                    && (long) card.right() > (long) obstacle.left() - OBSTACLE_GAP
                    && (long) card.top() < (long) obstacle.bottom() + OBSTACLE_GAP
                    && (long) card.bottom() > (long) obstacle.top() - OBSTACLE_GAP) return false;
        }
        return true;
    }

    private static int clamp(long value, int maximum) {
        return (int) Math.max(VIEWPORT_MARGIN, Math.min(value, maximum));
    }
}
