package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.jayemceekay.shadowedhearts.network.voidshadow.VoidShadowTransformPacket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Waits for Cobblemon's tracked appearance without replaying a live transformation event. */
final class VoidShadowTransformationQueue {
    static final int MAX_WAIT_TICKS = 20;
    static final int MAX_PENDING = 64;
    static final int MAX_REMEMBERED = 256;
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();
    private final Set<UUID> seen = new LinkedHashSet<>();
    private Object world, player;
    private long lastTick = Long.MIN_VALUE;

    void syncSession(Object world, Object player, long tick) {
        if (this.world != world || this.player != player || tick < lastTick) {
            pending.clear();
            seen.clear();
            this.world = world;
            this.player = player;
        }
        lastTick = tick;
    }

    void accept(VoidShadowTransformPacket event, long tick) {
        if (!seen.add(event.getEventId())) return;
        while (seen.size() > MAX_REMEMBERED) seen.remove(seen.iterator().next());
        // A return can supersede a disguise whose metadata never reached this observer.
        pending.remove(event.getEntityUuid());
        pending.put(event.getEntityUuid(), new Pending(event, tick));
        while (pending.size() > MAX_PENDING) pending.remove(pending.keySet().iterator().next());
    }

    boolean isPending(UUID entity) { return pending.containsKey(entity); }

    List<VoidShadowTransformPacket> ready(long tick, Function<VoidShadowTransformPacket, Readiness> appearance) {
        List<VoidShadowTransformPacket> ready = new ArrayList<>();
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Pending candidate = iterator.next();
            long age = tick - candidate.receivedAt;
            if (tick < candidate.receivedAt || age < 0 || age >= MAX_WAIT_TICKS) {
                iterator.remove();
                continue;
            }
            Readiness state = appearance.apply(candidate.event);
            if (state != Readiness.WAIT) {
                iterator.remove();
                if (state == Readiness.READY) ready.add(candidate.event);
            }
        }
        return List.copyOf(ready);
    }

    enum Readiness { WAIT, READY, DROP }
    private record Pending(VoidShadowTransformPacket event, long receivedAt) { }
}
