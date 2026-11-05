package com.jayemceekay.shadowedhearts.common.aura;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Independent, bounded player grants for a shared entity. No entity persistence is changed. */
public final class AuraLockGrants {
    private final Map<UUID, Map<UUID, Long>> grants = new ConcurrentHashMap<>();

    public long grant(UUID entityId, UUID playerId, long untilTick) {
        return grants.computeIfAbsent(entityId, ignored -> new ConcurrentHashMap<>())
                .merge(playerId, untilTick, Math::max);
    }

    public boolean isGranted(UUID entityId, UUID playerId, long nowTick) {
        Map<UUID, Long> players = grants.get(entityId);
        return players != null && players.getOrDefault(playerId, 0L) > nowTick;
    }

    public List<UUID> players(UUID entityId, long nowTick) {
        Map<UUID, Long> players = grants.get(entityId);
        if (players == null) return List.of();
        players.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
        if (players.isEmpty()) grants.remove(entityId, players);
        return List.copyOf(players.keySet());
    }

    public void clear(UUID entityId, UUID playerId) {
        Map<UUID, Long> players = grants.get(entityId);
        if (players == null) return;
        players.remove(playerId);
        if (players.isEmpty()) grants.remove(entityId, players);
    }

    public void clearPlayer(UUID playerId) {
        for (UUID entityId : List.copyOf(grants.keySet())) clear(entityId, playerId);
    }

    public void clearAll() {
        grants.clear();
    }
}
