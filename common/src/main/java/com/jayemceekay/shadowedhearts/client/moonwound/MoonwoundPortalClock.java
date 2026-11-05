package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;

import java.util.Objects;
import java.util.UUID;

/** A received server age advanced by unpaused client ticks, independent of rendering and world-time sync. */
public record MoonwoundPortalClock(UUID eventId, boolean active, int ageTicks, long receivedTick) {
    public MoonwoundPortalClock {
        Objects.requireNonNull(eventId);
        ageTicks = Math.clamp(ageTicks, 0, MoonwoundSnapshot.MAX_PORTAL_AGE_TICKS);
    }

    public static MoonwoundPortalClock accept(MoonwoundPortalClock previous, UUID eventId,
                                              boolean active, int serverAgeTicks, long clientTick) {
        int age = Math.clamp(serverAgeTicks, 0, MoonwoundSnapshot.MAX_PORTAL_AGE_TICKS);
        if (!active) age = MoonwoundSnapshot.MAX_PORTAL_AGE_TICKS;
        else if (previous != null && previous.active && previous.eventId.equals(eventId)) {
            // A repeated or delayed broadcast must never rewind an already playing opening.
            age = Math.max(age, previous.ticksAt(clientTick));
        }
        return new MoonwoundPortalClock(eventId, active, age, clientTick);
    }

    public float seconds(long clientTick, float partialTicks) {
        float partial = Float.isFinite(partialTicks) ? Math.clamp(partialTicks, 0, 1) : 0;
        return Math.min(MoonwoundSnapshot.MAX_PORTAL_AGE_TICKS, ticksAt(clientTick) + partial) / 20f;
    }

    private int ticksAt(long clientTick) {
        long elapsed = clientTick <= receivedTick ? 0 : clientTick - receivedTick;
        if (elapsed < 0 || elapsed >= MoonwoundSnapshot.MAX_PORTAL_AGE_TICKS) {
            return MoonwoundSnapshot.MAX_PORTAL_AGE_TICKS;
        }
        return (int) Math.min(MoonwoundSnapshot.MAX_PORTAL_AGE_TICKS, ageTicks + elapsed);
    }
}
