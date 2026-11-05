package com.jayemceekay.shadowedhearts.network.moonwound;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Outcome;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.PaceBonus;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable presentation data. It never grants permission to perform a server action. */
public record MoonwoundSnapshot(UUID siteId, UUID eventId, ResourceLocation dimension,
                                BlockPos center, int portalY, float portalSize, Phase phase,
                                UUID host, List<Member> members, List<Applicant> requests,
                                int mined, int miningTarget, int defeated, int waveTarget,
                                int voteRound, boolean canSeal, Component statusText, boolean raidFinale, int portalAgeTicks,
                                Recovery recovery, Pace pace) {
    public static final int MAX_MEMBERS = 4;
    public static final int MAX_REQUESTS = 32;
    public static final int MAX_PORTAL_AGE_TICKS = 1200;
    public static final int MAX_RECOVERY_TICKS = 7 * 24 * 60 * 60 * 20;

    public enum Availability { PRESENT, AWAY, OFFLINE }

    public record Pace(int targetTicks, int elapsedTicks, PaceBonus bonus) {
        public static final Pace NONE = new Pace(0, 0, PaceBonus.NONE);

        public Pace {
            targetTicks = Math.clamp(targetTicks, 0, MAX_RECOVERY_TICKS);
            elapsedTicks = Math.clamp(elapsedTicks, 0, MAX_RECOVERY_TICKS);
            Objects.requireNonNull(bonus);
        }

        public boolean targetPassed() { return targetTicks > 0 && elapsedTicks > targetTicks; }
    }

    public record Recovery(boolean paused, int lobbyRemainingTicks) {
        public static final Recovery NONE = new Recovery(false, 0);

        public Recovery {
            lobbyRemainingTicks = Math.clamp(lobbyRemainingTicks, 0, MAX_RECOVERY_TICKS);
        }
    }

    public record Member(UUID id, String name, boolean ready, Outcome vote, Availability availability,
                         int graceRemainingTicks, boolean votingSuspended) {
        public Member(UUID id, String name, boolean ready, Outcome vote) {
            this(id, name, ready, vote, Availability.PRESENT, 0, false);
        }

        public Member {
            Objects.requireNonNull(id);
            name = bounded(name, 64);
            Objects.requireNonNull(availability);
            graceRemainingTicks = Math.clamp(graceRemainingTicks, 0, MAX_RECOVERY_TICKS);
        }
    }

    public record Applicant(UUID id, String name) {
        public Applicant {
            Objects.requireNonNull(id);
            name = bounded(name, 64);
        }
    }

    /** Existing Alpha-only callers and fixtures retain their original presentation. */
    public MoonwoundSnapshot(UUID siteId, UUID eventId, ResourceLocation dimension, BlockPos center,
                             int portalY, float portalSize, Phase phase, UUID host, List<Member> members,
                             List<Applicant> requests, int mined, int miningTarget, int defeated, int waveTarget,
                             int voteRound, boolean canSeal, String status) {
        this(siteId, eventId, dimension, center, portalY, portalSize, phase, host, members, requests,
                mined, miningTarget, defeated, waveTarget, voteRound, canSeal, status, false);
    }

    /** Untimed callers describe a settled portal, including older presentation fixtures. */
    public MoonwoundSnapshot(UUID siteId, UUID eventId, ResourceLocation dimension, BlockPos center,
                             int portalY, float portalSize, Phase phase, UUID host, List<Member> members,
                             List<Applicant> requests, int mined, int miningTarget, int defeated, int waveTarget,
                             int voteRound, boolean canSeal, String status, boolean raidFinale) {
        this(siteId, eventId, dimension, center, portalY, portalSize, phase, host, members, requests,
                mined, miningTarget, defeated, waveTarget, voteRound, canSeal, status, raidFinale,
                MAX_PORTAL_AGE_TICKS);
    }

    /** Existing callers without recovery metadata keep their original presentation. */
    public MoonwoundSnapshot(UUID siteId, UUID eventId, ResourceLocation dimension, BlockPos center,
                             int portalY, float portalSize, Phase phase, UUID host, List<Member> members,
                             List<Applicant> requests, int mined, int miningTarget, int defeated, int waveTarget,
                             int voteRound, boolean canSeal, String status, boolean raidFinale, int portalAgeTicks) {
        this(siteId, eventId, dimension, center, portalY, portalSize, phase, host, members, requests,
                mined, miningTarget, defeated, waveTarget, voteRound, canSeal, status, raidFinale,
                portalAgeTicks, Recovery.NONE);
    }

    /** Recovery-only callers retain untimed stages and no earned assistance. */
    public MoonwoundSnapshot(UUID siteId, UUID eventId, ResourceLocation dimension, BlockPos center,
                             int portalY, float portalSize, Phase phase, UUID host, List<Member> members,
                             List<Applicant> requests, int mined, int miningTarget, int defeated, int waveTarget,
                             int voteRound, boolean canSeal, String status, boolean raidFinale, int portalAgeTicks,
                             Recovery recovery) {
        this(siteId, eventId, dimension, center, portalY, portalSize, phase, host, members, requests,
                mined, miningTarget, defeated, waveTarget, voteRound, canSeal, status, raidFinale,
                portalAgeTicks, recovery, Pace.NONE);
    }

    /** Compatibility for locally authored plain-text snapshots; server messages retain their Components. */
    public MoonwoundSnapshot(UUID siteId, UUID eventId, ResourceLocation dimension, BlockPos center,
                             int portalY, float portalSize, Phase phase, UUID host, List<Member> members,
                             List<Applicant> requests, int mined, int miningTarget, int defeated, int waveTarget,
                             int voteRound, boolean canSeal, String status, boolean raidFinale, int portalAgeTicks,
                             Recovery recovery, Pace pace) {
        this(siteId, eventId, dimension, center, portalY, portalSize, phase, host, members, requests,
                mined, miningTarget, defeated, waveTarget, voteRound, canSeal, Component.literal(bounded(status, 256)),
                raidFinale, portalAgeTicks, recovery, pace);
    }

    /** Resolve only at the client's presentation boundary, never before network serialization. */
    public String status() { return statusText.getString(); }

    public MoonwoundSnapshot {
        Objects.requireNonNull(siteId);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(dimension);
        center = Objects.requireNonNull(center).immutable();
        Objects.requireNonNull(phase);
        Objects.requireNonNull(host);
        Objects.requireNonNull(recovery);
        Objects.requireNonNull(pace);
        members = List.copyOf(members);
        requests = List.copyOf(requests);
        if (members.size() > MAX_MEMBERS || requests.size() > MAX_REQUESTS) {
            throw new IllegalArgumentException("Moonwound party snapshot exceeds its bounded roster");
        }
        if (!Float.isFinite(portalSize) || portalSize < 0 || portalSize > 32) {
            throw new IllegalArgumentException("Invalid Moonwound portal size");
        }
        mined = Math.max(0, mined);
        miningTarget = Math.max(0, miningTarget);
        defeated = Math.max(0, defeated);
        waveTarget = Math.max(0, waveTarget);
        voteRound = Math.max(0, voteRound);
        portalAgeTicks = Math.clamp(portalAgeTicks, 0, MAX_PORTAL_AGE_TICKS);
        recovery = new Recovery(phase != Phase.LOBBY && !phase.isTerminal() && recovery.paused(),
                phase == Phase.LOBBY ? recovery.lobbyRemainingTicks() : 0);
        statusText = Objects.requireNonNull(statusText).copy();
    }

    public Member member(UUID id) {
        return members.stream().filter(member -> member.id().equals(id)).findFirst().orElse(null);
    }

    public boolean portalActive() { return phase != Phase.LOBBY && !phase.isTerminal(); }

    private static String bounded(String value, int length) {
        Objects.requireNonNull(value);
        return value.length() > length ? value.substring(0, length) : value;
    }
}
