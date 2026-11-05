package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.PaceBonus;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Coalesces chat progress while keeping event transitions immediate, without owning client state. */
public final class MoonwoundChatUpdates {
    private static final int MAX_SITES = 64;
    private static final long PROGRESS_INTERVAL_TICKS = 60;
    private final LinkedHashMap<UUID, Emission> emissions = new LinkedHashMap<>(16, .75F, true);

    /** Poll the latest eligible snapshot each client tick. A missing snapshot does not reset deduplication. */
    public @Nullable String poll(@Nullable MoonwoundSnapshot snapshot, long tick) {
        if (snapshot == null) return null;
        String message = MoonwoundText.tr("gui.shadowedhearts.moonwound.chat.message",
                MoonwoundHudPresentation.title(snapshot), MoonwoundHudPresentation.chatDetail(snapshot));
        var recovery = MoonwoundRecoveryPresentation.chatState(snapshot);
        Emission previous = emissions.get(snapshot.siteId());
        boolean transition = previous == null || !previous.eventId().equals(snapshot.eventId())
                || previous.phase() != snapshot.phase() || previous.voteRound() != snapshot.voteRound()
                || previous.paused() != snapshot.recovery().paused() || !previous.recovery().equals(recovery)
                || previous.targetPassed() != snapshot.pace().targetPassed() || previous.bonus() != snapshot.pace().bonus();
        if (!transition && (previous.message().equals(message) || tick - previous.tick() < PROGRESS_INTERVAL_TICKS)) {
            return null;
        }
        emissions.put(snapshot.siteId(), new Emission(snapshot.eventId(), snapshot.phase(), snapshot.voteRound(),
                snapshot.recovery().paused(), recovery, snapshot.pace().targetPassed(), snapshot.pace().bonus(), message, tick));
        if (emissions.size() > MAX_SITES) emissions.remove(emissions.keySet().iterator().next());
        return message;
    }

    public void clear() { emissions.clear(); }

    public void forget(UUID siteId) { emissions.remove(siteId); }

    private record Emission(UUID eventId, Phase phase, int voteRound, boolean paused,
                            List<MoonwoundRecoveryPresentation.MemberRecovery> recovery, boolean targetPassed,
                            PaceBonus bonus, String message, long tick) { }
}
