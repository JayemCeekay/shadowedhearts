package com.jayemceekay.shadowedhearts.client.moonwound;

import static com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundText.tr;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot.Availability;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot.Member;

import java.util.List;
import java.util.UUID;

/** Formats server recovery data without advancing clocks or deciding event eligibility. */
public final class MoonwoundRecoveryPresentation {
    private MoonwoundRecoveryPresentation() { }

    public static String duration(int ticks) {
        int seconds = (Math.clamp(ticks, 0, MoonwoundSnapshot.MAX_RECOVERY_TICKS) + 19) / 20;
        if (seconds >= 3600) return tr("gui.shadowedhearts.moonwound.duration.hours", seconds / 3600, seconds % 3600 / 60);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    public static String memberDetail(Member member, Phase phase) {
        String availability = switch (member.availability()) {
            case PRESENT -> "";
            case AWAY -> tr("gui.shadowedhearts.moonwound.recovery.away");
            case OFFLINE -> tr("gui.shadowedhearts.moonwound.recovery.offline");
        };
        if (usesVotingRecovery(phase) && member.votingSuspended()) return availability.isEmpty() ? tr("gui.shadowedhearts.moonwound.recovery.vote_paused") : tr("gui.shadowedhearts.moonwound.recovery.availability_vote_paused", availability);
        if (!availability.isEmpty()) return (usesVotingRecovery(phase) && member.graceRemainingTicks() > 0
                ? tr("gui.shadowedhearts.moonwound.joined", availability, duration(member.graceRemainingTicks())) : availability);
        if (phase == Phase.LOBBY) return member.ready() ? tr("gui.shadowedhearts.moonwound.recovery.ready") : tr("gui.shadowedhearts.moonwound.recovery.preparing");
        if (phase == Phase.VOTE) return member.vote() == null ? tr("gui.shadowedhearts.moonwound.recovery.not_voted") : switch (member.vote()) {
            case HARVEST -> tr("gui.shadowedhearts.moonwound.recovery.harvest");
            case SEAL -> tr("gui.shadowedhearts.moonwound.recovery.seal");
            case OVERLOAD -> tr("gui.shadowedhearts.moonwound.recovery.overload");
        };
        return tr("gui.shadowedhearts.moonwound.recovery.in_party");
    }

    public static String memberExplanation(Member member, Phase phase) {
        String detail = tr("gui.shadowedhearts.moonwound.joined", member.name(), memberDetail(member, phase));
        if (!usesVotingRecovery(phase)) {
            if (member.availability() == Availability.PRESENT) return detail;
            return phase == Phase.COMPLETE ? tr("gui.shadowedhearts.moonwound.recovery.member_complete", detail) : phase == Phase.ABANDONED ? tr("gui.shadowedhearts.moonwound.recovery.member_abandoned", detail) : tr("gui.shadowedhearts.moonwound.recovery.member_finale", detail);
        }
        if (member.availability() == Availability.PRESENT && !member.votingSuspended()) return detail;
        if (phase == Phase.LOBBY) return tr("gui.shadowedhearts.moonwound.recovery.member_lobby", detail);
        return member.votingSuspended() ? tr("gui.shadowedhearts.moonwound.recovery.member_suspended", detail) : tr("gui.shadowedhearts.moonwound.recovery.member_grace", detail);
    }

    /** After a core decision these saved flags are historical vote evidence, not current restrictions. */
    static boolean usesVotingRecovery(Phase phase) {
        return phase != Phase.OVERLOAD && !phase.isTerminal();
    }

    public static long eligibleVoters(MoonwoundSnapshot snapshot) {
        return snapshot.members().stream().filter(member -> !member.votingSuspended()).count();
    }

    public static long recordedVotes(MoonwoundSnapshot snapshot) {
        return snapshot.members().stream().filter(member -> !member.votingSuspended() && member.vote() != null).count();
    }

    /** Timer-only broadcasts must not recreate buttons and discard keyboard focus. */
    static boolean controlsChanged(MoonwoundSnapshot previous, MoonwoundSnapshot current) {
        return !previous.eventId().equals(current.eventId()) || previous.phase() != current.phase()
                || !previous.host().equals(current.host()) || !previous.requests().equals(current.requests())
                || previous.canSeal() != current.canSeal() || previous.raidFinale() != current.raidFinale()
                || previous.recovery().paused() != current.recovery().paused()
                || previous.pace().bonus() != current.pace().bonus()
                || !controls(previous).equals(controls(current));
    }

    private static List<MemberControl> controls(MoonwoundSnapshot snapshot) {
        return snapshot.members().stream().map(member -> new MemberControl(member.id(), member.name(),
                member.ready(), member.vote(), member.availability(),
                usesVotingRecovery(snapshot.phase()) && member.votingSuspended())).toList();
    }

    private record MemberControl(UUID id, String name, boolean ready,
                                 com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Outcome vote,
                                 Availability availability, boolean votingSuspended) { }

    /** Semantic recovery changes for chat; seconds ticking down are deliberately excluded. */
    static List<MemberRecovery> chatState(MoonwoundSnapshot snapshot) {
        return snapshot.members().stream().map(member -> new MemberRecovery(member.id(), member.availability(),
                usesVotingRecovery(snapshot.phase()) && member.votingSuspended())).toList();
    }

    record MemberRecovery(UUID id, Availability availability, boolean votingSuspended) { }
}
