package com.jayemceekay.shadowedhearts.client.moonwound;

import static com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundText.tr;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;

/** Selection and compact text from server snapshots, independent of the client renderer. */
public final class MoonwoundHudPresentation {
    private static final int HUD_RANGE_SQUARED = 96 * 96;

    private MoonwoundHudPresentation() { }

    /** The caller supplies only unexpired snapshots from the current connection. */
    public static MoonwoundSnapshot select(Collection<MoonwoundSnapshot> snapshots, UUID playerId,
                                            ResourceLocation dimension, BlockPos playerPos) {
        return snapshots.stream()
                .filter(snapshot -> !snapshot.phase().isTerminal())
                .filter(snapshot -> isParticipantInRange(snapshot, playerId, dimension, playerPos))
                .min(Comparator.comparingDouble(snapshot -> playerPos.distSqr(snapshot.center())))
                .orElse(null);
    }

    /** Shared eligibility for the active objective and each site's final chat announcement. */
    public static boolean isParticipantInRange(MoonwoundSnapshot snapshot, UUID playerId,
                                               ResourceLocation dimension, BlockPos playerPos) {
        return snapshot.dimension().equals(dimension) && snapshot.member(playerId) != null
                && playerPos.distSqr(snapshot.center()) <= HUD_RANGE_SQUARED;
    }

    public static String title(MoonwoundSnapshot snapshot) {
        String stage = switch (snapshot.phase()) {
            case LOBBY -> tr("gui.shadowedhearts.moonwound.hud.party_lobby");
            case EXCAVATE_SHELL, WAVE_SHELL -> tr("gui.shadowedhearts.moonwound.hud.shell");
            case EXCAVATE_MANTLE, WAVE_MANTLE -> tr("gui.shadowedhearts.moonwound.hud.mantle");
            case EXCAVATE_CASING, WAVE_CASING -> tr("gui.shadowedhearts.moonwound.hud.core_casing");
            case VOTE -> tr("gui.shadowedhearts.moonwound.hud.core_vote");
            case OVERLOAD -> tr("gui.shadowedhearts.moonwound.hud.overload");
            case COMPLETE -> tr("gui.shadowedhearts.moonwound.hud.complete");
            case ABANDONED -> tr("gui.shadowedhearts.moonwound.hud.abandoned");
        };
        return tr("gui.shadowedhearts.moonwound.hud.title", stage);
    }

    public static String detail(MoonwoundSnapshot snapshot) {
        return detail(snapshot, true);
    }

    /** Chat reports recovery transitions without repeating the lobby countdown every few seconds. */
    public static String chatDetail(MoonwoundSnapshot snapshot) {
        return detail(snapshot, false);
    }

    private static String detail(MoonwoundSnapshot snapshot, boolean countdown) {
        if (snapshot.recovery().paused()) return tr("gui.shadowedhearts.moonwound.hud.paused_return_nearby_to_resume");
        String detail = switch (snapshot.phase()) {
            case LOBBY -> tr("gui.shadowedhearts.moonwound.hud.lobby", snapshot.members().stream().filter(MoonwoundSnapshot.Member::ready).count(), snapshot.members().size(), countdown && snapshot.recovery().lobbyRemainingTicks() > 0 ? tr("gui.shadowedhearts.moonwound.hud.expires", MoonwoundRecoveryPresentation.duration(snapshot.recovery().lobbyRemainingTicks())) : tr("gui.shadowedhearts.moonwound.hud.open_site"));
            case EXCAVATE_SHELL, EXCAVATE_MANTLE, EXCAVATE_CASING ->
                    tr("gui.shadowedhearts.moonwound.hud.excavate", snapshot.mined(), snapshot.miningTarget());
            case WAVE_SHELL, WAVE_MANTLE, WAVE_CASING ->
                    tr("gui.shadowedhearts.moonwound.hud.defeated", snapshot.defeated(), snapshot.waveTarget());
            case VOTE -> tr("gui.shadowedhearts.moonwound.hud.vote", snapshot.voteRound());
            case OVERLOAD -> snapshot.raidFinale() ? tr("gui.shadowedhearts.moonwound.hud.defeat_the_shadow_raid_boss") : tr("gui.shadowedhearts.moonwound.hud.capture_the_alpha_shadow");
            case COMPLETE, ABANDONED -> snapshot.status();
        };
        if (snapshot.phase().isTerminal()) return detail;
        String pace = countdown ? MoonwoundPacePresentation.hudSuffix(snapshot) : MoonwoundPacePresentation.chatSuffix(snapshot);
        if (!pace.isEmpty()) detail = tr("gui.shadowedhearts.moonwound.joined", detail, pace);
        long suspended = MoonwoundRecoveryPresentation.usesVotingRecovery(snapshot.phase())
                ? snapshot.members().stream().filter(MoonwoundSnapshot.Member::votingSuspended).count() : 0;
        long absent = snapshot.members().stream()
                .filter(member -> member.availability() != MoonwoundSnapshot.Availability.PRESENT).count();
        if (suspended > 0) return suspended == 1 ? tr("gui.shadowedhearts.moonwound.hud.one_vote_paused", detail, suspended) : tr("gui.shadowedhearts.moonwound.hud.votes_paused", detail, suspended);
        return absent > 0 ? tr("gui.shadowedhearts.moonwound.hud.away", detail, absent) : detail;
    }
}
