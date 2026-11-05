package com.jayemceekay.shadowedhearts.common.moonwound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-owned event state. World checks, battle results, inventory and payouts belong to the controller. */
public final class MoonwoundEvent {
    public static final int MAX_MEMBERS = 4;
    public static final int MAX_ORIGINAL_BLOCKS = 750;
    public static final int MAX_WAVE_ENEMIES = 64;
    public static final int MAX_RECOVERY_TICKS = 20 * 60 * 60 * 24 * 7;
    private static final int VERSION = 4;

    public enum Layer { SHELL, MANTLE, CASING }
    public enum Phase {
        LOBBY, EXCAVATE_SHELL, WAVE_SHELL, EXCAVATE_MANTLE, WAVE_MANTLE,
        EXCAVATE_CASING, WAVE_CASING, VOTE, OVERLOAD, COMPLETE, ABANDONED;

        public Layer layer() {
            return switch (this) {
                case EXCAVATE_SHELL, WAVE_SHELL -> Layer.SHELL;
                case EXCAVATE_MANTLE, WAVE_MANTLE -> Layer.MANTLE;
                case EXCAVATE_CASING, WAVE_CASING -> Layer.CASING;
                default -> null;
            };
        }

        public boolean isExcavation() {
            return this == EXCAVATE_SHELL || this == EXCAVATE_MANTLE || this == EXCAVATE_CASING;
        }

        public boolean isWave() { return this == WAVE_SHELL || this == WAVE_MANTLE || this == WAVE_CASING; }
        public boolean isTerminal() { return this == COMPLETE || this == ABANDONED; }
    }
    public enum Outcome { HARVEST, SEAL, OVERLOAD }
    public enum FinaleMode { ALPHA, RAID }
    public enum VoteResult { REJECTED, RECORDED, MAJORITY, REVOTE }
    public enum PaceBonus { NONE, COMBAT_HEAL, MINING_HASTE }

    private final UUID siteId;
    private final UUID eventId;
    private final MoonwoundDifficulty difficulty;
    private UUID host;
    private Phase phase = Phase.LOBBY;
    // Optional for pre-animation saves; unknown age means an already settled portal.
    private long portalStartedAt = -1;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> abandonedMembers = new LinkedHashSet<>();
    // Retain the last live phase so a terminal partial excavation still validates its progress.
    private Phase abandonedFrom;
    private final Set<UUID> joinRequests = new LinkedHashSet<>();
    private final Set<UUID> ready = new LinkedHashSet<>();
    private final Map<UUID, Integer> missingTicks = new LinkedHashMap<>();
    private final Set<UUID> votingSuspended = new LinkedHashSet<>();
    private int lobbyIdleTicks;
    private boolean paceConfigured;
    private int excavationPaceTicks;
    private int wavePaceTicksPerOpponent;
    private int paceTargetTicks;
    private int paceElapsedTicks;
    private boolean paceTargetMissed;
    private PaceBonus paceBonus = PaceBonus.NONE;
    private final Set<UUID> paceBonusClaims = new LinkedHashSet<>();
    private final EnumMap<Layer, Integer> requestedTargets = new EnumMap<>(Layer.class);
    private final EnumMap<Layer, Integer> targets = new EnumMap<>(Layer.class);
    private final EnumMap<Layer, Integer> waveTargets = new EnumMap<>(Layer.class);
    private final EnumMap<Layer, Set<Long>> eligible = new EnumMap<>(Layer.class);
    private final EnumMap<Layer, Set<Long>> mined = new EnumMap<>(Layer.class);
    private final EnumMap<Layer, Set<Long>> removed = new EnumMap<>(Layer.class);
    private final EnumMap<Layer, Set<UUID>> issued = new EnumMap<>(Layer.class);
    private final EnumMap<Layer, Set<UUID>> defeated = new EnumMap<>(Layer.class);
    private final Map<UUID, Outcome> votes = new LinkedHashMap<>();
    // Preserve the reward roster and the smaller voting electorate independently at commitment.
    // The decision remains valid when its voters later leave during the finale.
    private final Set<UUID> resolutionMembers = new LinkedHashSet<>();
    private final Set<UUID> resolutionElectorate = new LinkedHashSet<>();
    private final Map<UUID, Outcome> resolutionVotes = new LinkedHashMap<>();
    private final Set<UUID> claimedRewards = new LinkedHashSet<>();
    private int voteRound = 1;
    private Outcome outcome;
    private FinaleMode finaleMode = FinaleMode.ALPHA;
    private UUID alphaId;
    private CompoundTag alphaSnapshot;
    private UUID raidId;
    private CompoundTag raidSnapshot;
    private CompoundTag raidDefinition;
    private boolean overloadSucceeded;
    private Runnable changed = () -> {};

    public MoonwoundEvent(UUID siteId, UUID host, int[] excavationTargets, int[] waveTargets) {
        this(siteId, host, excavationTargets, waveTargets, MoonwoundDifficulty.DEFAULT);
    }

    public MoonwoundEvent(UUID siteId, UUID host, int[] excavationTargets, int[] waveTargets, MoonwoundDifficulty difficulty) {
        this(siteId, UUID.randomUUID(), host, excavationTargets, waveTargets, difficulty);
    }

    public MoonwoundEvent(UUID siteId, UUID eventId, UUID host, int[] excavationTargets, int[] waveTargets) {
        this(siteId, eventId, host, excavationTargets, waveTargets, MoonwoundDifficulty.DEFAULT);
    }

    public MoonwoundEvent(UUID siteId, UUID eventId, UUID host, int[] excavationTargets, int[] waveTargets, MoonwoundDifficulty difficulty) {
        this.siteId = Objects.requireNonNull(siteId);
        this.eventId = Objects.requireNonNull(eventId);
        this.difficulty = Objects.requireNonNull(difficulty);
        this.host = Objects.requireNonNull(host);
        if (excavationTargets.length != 3 || waveTargets.length != 3) throw invalid("Three layer budgets required");
        members.add(host);
        for (Layer layer : Layer.values()) {
            int index = layer.ordinal();
            requestedTargets.put(layer, bounded(excavationTargets[index], 1, MAX_ORIGINAL_BLOCKS));
            targets.put(layer, excavationTargets[index]);
            this.waveTargets.put(layer, bounded(waveTargets[index], 1, MAX_WAVE_ENEMIES / MAX_MEMBERS));
            eligible.put(layer, new LinkedHashSet<>());
            mined.put(layer, new LinkedHashSet<>());
            removed.put(layer, new LinkedHashSet<>());
            issued.put(layer, new LinkedHashSet<>());
            defeated.put(layer, new LinkedHashSet<>());
        }
    }

    public UUID siteId() { return siteId; }
    public UUID eventId() { return eventId; }
    public MoonwoundDifficulty difficulty() { return difficulty; }
    public UUID host() { return host; }
    public Phase phase() { return phase; }
    public long portalStartedAt() { return portalStartedAt; }

    /** Bounded presentation age; legacy saves and a rewound world clock stay fully open. */
    public int portalAgeTicks(long gameTime) {
        if (portalStartedAt < 0 || gameTime < portalStartedAt) return 1200;
        return (int) Math.min(1200L, gameTime - portalStartedAt);
    }
    public Set<UUID> members() { return Collections.unmodifiableSet(members); }
    public Set<UUID> abandonedMembers() { return Collections.unmodifiableSet(abandonedMembers); }
    public Set<UUID> activeMembers() {
        Set<UUID> active = new LinkedHashSet<>(members);
        active.removeAll(abandonedMembers);
        return Collections.unmodifiableSet(active);
    }
    public boolean isActiveMember(UUID player) { return members.contains(player) && !abandonedMembers.contains(player); }
    public int missingTicks(UUID player) { return missingTicks.getOrDefault(player, 0); }
    public boolean votingSuspended(UUID player) { return votingSuspended.contains(player); }
    public Set<UUID> votingMembers() {
        Set<UUID> voters = new LinkedHashSet<>(activeMembers());
        voters.removeAll(votingSuspended);
        return Collections.unmodifiableSet(voters);
    }
    public int lobbyIdleTicks() { return lobbyIdleTicks; }
    public PaceBonus paceBonus() { return paceBonus; }
    public int paceTargetTicks() { return paceTargetTicks; }
    public int paceElapsedTicks() { return paceElapsedTicks; }
    public boolean paceBonusClaimed(UUID player) { return paceBonusClaims.contains(player); }

    /** Snapshot tuning once. An already-active legacy stage stays untimed until its next transition. */
    public boolean configurePace(int excavationTicks, int waveTicksPerOpponent) {
        bounded(excavationTicks, 1, MAX_RECOVERY_TICKS);
        bounded(waveTicksPerOpponent, 1, MAX_RECOVERY_TICKS);
        if (paceConfigured || phase.isTerminal()) return false;
        paceConfigured = true;
        excavationPaceTicks = excavationTicks;
        wavePaceTicksPerOpponent = waveTicksPerOpponent;
        changed.run();
        return true;
    }

    /** The controller advances only while participants and the required terrain are present. */
    public boolean advancePace(int elapsedTicks) {
        if (elapsedTicks < 0) throw invalid("Negative pace elapsed time");
        if (paceTargetTicks == 0 || (!phase.isExcavation() && !phase.isWave())) return false;
        long elapsed = (long) paceElapsedTicks + elapsedTicks;
        int next = (int) Math.min(MAX_RECOVERY_TICKS, elapsed);
        boolean missed = paceTargetMissed || elapsed > paceTargetTicks;
        if (next == paceElapsedTicks && missed == paceTargetMissed) return false;
        paceElapsedTicks = next;
        paceTargetMissed = missed;
        changed.run();
        return true;
    }

    /** Record the assistance receipt before applying it; ordinary outcome rewards are separate. */
    public boolean claimPaceBonus(UUID player) {
        if (!isActiveMember(player)) return false;
        boolean available = switch (paceBonus) {
            case NONE -> false;
            case COMBAT_HEAL -> phase.isWave() || phase == Phase.OVERLOAD;
            case MINING_HASTE -> phase.isExcavation();
        };
        return available && update(paceBonusClaims.add(player));
    }

    private boolean quickPace() { return paceTargetTicks > 0 && !paceTargetMissed; }

    private int targetForPacePhase() {
        if (!paceConfigured) return 0;
        if (phase.isExcavation()) return excavationPaceTicks;
        if (phase.isWave()) return (int) Math.min(MAX_RECOVERY_TICKS,
                (long) wavePaceTicksPerOpponent * waveTargets.get(phase.layer()));
        return 0;
    }

    private void enterPacePhase(Phase next, PaceBonus bonus) {
        phase = next;
        paceTargetTicks = targetForPacePhase();
        paceElapsedTicks = 0;
        paceTargetMissed = false;
        paceBonus = bonus;
        paceBonusClaims.clear();
    }

    private void clearPace() {
        paceTargetTicks = 0;
        paceElapsedTicks = 0;
        paceTargetMissed = false;
        paceBonus = PaceBonus.NONE;
        paceBonusClaims.clear();
    }

    /** Presence affects voting only; original participation, rewards and wave budgets remain intact. */
    public boolean updatePresence(Set<UUID> present, int elapsedTicks, int graceTicks) {
        Objects.requireNonNull(present);
        if (elapsedTicks < 0) throw invalid("Negative presence elapsed time");
        bounded(graceTicks, 1, MAX_RECOVERY_TICKS);
        if (phase.isTerminal()) return false;
        Set<UUID> previousVoters = votingMembers();
        boolean modified = false;
        for (UUID member : activeMembers()) {
            int previous = missingTicks(member);
            int next = present.contains(member) ? 0 : (int) Math.min(MAX_RECOVERY_TICKS, (long) previous + elapsedTicks);
            if (next != previous) {
                if (next == 0) missingTicks.remove(member);
                else missingTicks.put(member, next);
                modified = true;
            }
            // A committed decision keeps its electorate proof even when someone returns later.
            if (phase != Phase.LOBBY && outcome == null) {
                modified |= next >= graceTicks ? votingSuspended.add(member) : votingSuspended.remove(member);
            }
        }
        if (phase == Phase.LOBBY) {
            int nextIdle = (int) Math.min(MAX_RECOVERY_TICKS, (long) lobbyIdleTicks + elapsedTicks);
            if (nextIdle != lobbyIdleTicks) { lobbyIdleTicks = nextIdle; modified = true; }
        } else if (phase == Phase.VOTE && !previousVoters.equals(votingMembers())) {
            resetVoteRound();
        }
        return update(modified);
    }

    /** Only a meaningful lobby mutation should reset this lease; snapshots and presence do not. */
    public boolean resetLobbyIdle() {
        if (phase != Phase.LOBBY || lobbyIdleTicks == 0) return false;
        lobbyIdleTicks = 0;
        changed.run();
        return true;
    }

    public Set<UUID> expiredLobbyMembers(int graceTicks) {
        bounded(graceTicks, 1, MAX_RECOVERY_TICKS);
        if (phase != Phase.LOBBY) return Set.of();
        Set<UUID> expired = new LinkedHashSet<>();
        for (UUID member : members) if (missingTicks(member) >= graceTicks) expired.add(member);
        return Collections.unmodifiableSet(expired);
    }
    public Set<UUID> joinRequests() { return Collections.unmodifiableSet(joinRequests); }
    public Set<UUID> ready() { return Collections.unmodifiableSet(ready); }
    public int target(Layer layer) { return targets.get(layer); }
    /** Lobby preview scales with accepted members; the roster and therefore this budget freeze on start. */
    public int waveTarget(Layer layer) { return waveTargets.get(layer) * members.size(); }
    public Set<Long> eligiblePositions(Layer layer) { return Collections.unmodifiableSet(eligible.get(layer)); }
    public Set<Long> mined(Layer layer) { return Collections.unmodifiableSet(mined.get(layer)); }
    public Set<Long> removedPositions(Layer layer) { return Collections.unmodifiableSet(removed.get(layer)); }
    public Set<UUID> issuedEnemies(Layer layer) { return Collections.unmodifiableSet(issued.get(layer)); }
    public Set<UUID> defeatedEnemies(Layer layer) { return Collections.unmodifiableSet(defeated.get(layer)); }
    public Map<UUID, Outcome> votes() { return Collections.unmodifiableMap(votes); }
    public int voteRound() { return voteRound; }
    public Outcome outcome() { return outcome; }
    public FinaleMode finaleMode() { return finaleMode; }
    public UUID alphaId() { return alphaId; }
    public CompoundTag alphaSnapshot() { return alphaSnapshot == null ? null : alphaSnapshot.copy(); }
    public UUID raidId() { return raidId; }
    public CompoundTag raidSnapshot() { return raidSnapshot == null ? null : raidSnapshot.copy(); }
    public CompoundTag raidDefinition() { return raidDefinition == null ? null : raidDefinition.copy(); }

    /** Select the installed finale implementation before committing the Overload vote. */
    public boolean selectFinaleMode(FinaleMode mode) {
        if (phase != Phase.VOTE || mode == null) return false;
        if (finaleMode != mode) {
            finaleMode = mode;
            changed.run();
        }
        return true;
    }

    /** Commit the native Pokemon before its first send-out so recovery cannot reroll the finale. */
    public boolean rememberAlphaSnapshot(CompoundTag snapshot) {
        if (phase != Phase.OVERLOAD || finaleMode != FinaleMode.ALPHA || alphaId == null || alphaSnapshot != null
                || snapshot == null || snapshot.isEmpty()) return false;
        alphaSnapshot = snapshot.copy();
        changed.run();
        return true;
    }
    public boolean overloadSucceeded() { return overloadSucceeded; }
    public Set<UUID> claimedRewards() { return Collections.unmodifiableSet(claimedRewards); }
    void onChanged(Runnable listener) { changed = Objects.requireNonNull(listener); }

    public boolean requestJoin(UUID player) {
        Objects.requireNonNull(player);
        if (phase != Phase.LOBBY || members.contains(player) || joinRequests.size() >= 32) return false;
        if (!joinRequests.add(player)) return false;
        lobbyIdleTicks = 0;
        changed.run();
        return true;
    }

    public boolean acceptJoin(UUID actor, UUID player) {
        if (phase != Phase.LOBBY || !host.equals(actor) || members.size() >= MAX_MEMBERS
                || !joinRequests.remove(player)) return false;
        members.add(player);
        ready.clear();
        lobbyIdleTicks = 0;
        changed.run();
        return true;
    }

    public boolean declineJoin(UUID actor, UUID player) {
        if (phase != Phase.LOBBY || !host.equals(actor) || !joinRequests.remove(player)) return false;
        lobbyIdleTicks = 0;
        changed.run();
        return true;
    }

    /** A host can leave only after explicitly transferring the lobby. The started roster remains historical. */
    public boolean leaveLobby(UUID player) {
        if (phase != Phase.LOBBY || host.equals(player)) return false;
        if (members.remove(player)) {
            ready.clear();
            missingTicks.remove(player);
            lobbyIdleTicks = 0;
            changed.run();
            return true;
        }
        if (!joinRequests.remove(player)) return false;
        lobbyIdleTicks = 0;
        changed.run();
        return true;
    }

    public boolean transferHost(UUID actor, UUID nextHost) {
        if (phase != Phase.LOBBY || !host.equals(actor) || host.equals(nextHost) || !members.contains(nextHost)) return false;
        host = nextHost;
        ready.clear();
        lobbyIdleTicks = 0;
        changed.run();
        return true;
    }

    /** Permanently leave an active event without changing its original wave budget. */
    public boolean abandon(UUID player) {
        if (phase == Phase.LOBBY || phase == Phase.COMPLETE || phase == Phase.ABANDONED || !isActiveMember(player)) return false;
        abandonedMembers.add(player);
        missingTicks.remove(player);
        votingSuspended.remove(player);
        votes.remove(player);
        if (phase == Phase.VOTE) {
            resetVoteRound();
        }
        Set<UUID> active = activeMembers();
        if (active.isEmpty()) {
            abandonedFrom = phase;
            phase = Phase.ABANDONED;
            clearPace();
            outcome = null;
            votes.clear();
            resolutionMembers.clear();
            resolutionElectorate.clear();
            resolutionVotes.clear();
        } else if (host.equals(player)) {
            host = active.iterator().next();
        }
        changed.run();
        return true;
    }

    public boolean setReady(UUID player, boolean value) {
        if (phase != Phase.LOBBY || !members.contains(player)) return false;
        if (!(value ? ready.add(player) : ready.remove(player))) return false;
        lobbyIdleTicks = 0;
        changed.run();
        return true;
    }

    /** Claim access is fresh consent: restoring access does not restore an old ready flag or ballot. */
    public void revokeConsent(UUID player) {
        boolean modified = phase == Phase.LOBBY && ready.remove(player);
        if (phase == Phase.VOTE && votes.containsKey(player)) {
            resetVoteRound();
            modified = true;
        }
        if (modified) changed.run();
    }

    /** The controller captures still-present original blocks immediately before activation. */
    public boolean setEligiblePositions(Map<Layer, Set<Long>> positions) {
        if (phase != Phase.LOBBY || positions == null || positions.size() != 3) return false;
        Set<Long> all = new LinkedHashSet<>();
        for (Layer layer : Layer.values()) {
            Set<Long> cells = positions.get(layer);
            if (cells == null || cells.isEmpty()) return false;
            for (Long cell : cells) if (cell == null || !all.add(cell)) return false;
            if (all.size() > MAX_ORIGINAL_BLOCKS) return false;
        }
        for (Layer layer : Layer.values()) {
            eligible.get(layer).clear();
            eligible.get(layer).addAll(positions.get(layer));
            targets.put(layer, Math.min(requestedTargets.get(layer), eligible.get(layer).size()));
        }
        changed.run();
        return true;
    }

    public boolean start(UUID actor) {
        return start(actor, -1);
    }

    /** Capture the server game clock in the same mutation that activates the event. */
    public boolean start(UUID actor, long gameTime) {
        if (gameTime < -1) return false;
        if (phase != Phase.LOBBY || !host.equals(actor) || !ready.containsAll(members)) return false;
        for (Layer layer : Layer.values()) if (eligible.get(layer).isEmpty()) return false;
        joinRequests.clear();
        lobbyIdleTicks = 0;
        portalStartedAt = gameTime;
        enterPacePhase(Phase.EXCAVATE_SHELL, PaceBonus.NONE);
        changed.run();
        return true;
    }

    public boolean recordMined(UUID player, Layer layer, long originalPosition) {
        if (!phase.isExcavation() || phase.layer() != layer || !isActiveMember(player)
                || !eligible.get(layer).contains(originalPosition) || removed.get(layer).contains(originalPosition)
                || !mined.get(layer).add(originalPosition)) return false;
        removed.get(layer).add(originalPosition);
        if (mined.get(layer).size() == target(layer)) {
            enterPacePhase(wavePhase(layer), quickPace() ? PaceBonus.COMBAT_HEAL : PaceBonus.NONE);
        }
        changed.run();
        return true;
    }

    /** Release a consumed original coordinate even when its layer objective has already finished. */
    public boolean markRemoved(Layer layer, long originalPosition) {
        if (layer == null || !eligible.get(layer).contains(originalPosition)) return false;
        boolean accessible = phase == Phase.VOTE || (phase.isExcavation() && layer.ordinal() <= phase.layer().ordinal());
        return accessible && update(removed.get(layer).add(originalPosition));
    }

    /** Register the actual successfully spawned enemy's stable UUID; never issue more than the finite budget. */
    public boolean issueEnemy(UUID enemy) {
        Objects.requireNonNull(enemy);
        if (!phase.isWave() || enemy.equals(alphaId) || enemy.equals(raidId) || enemyWasIssued(enemy)) return false;
        Layer layer = phase.layer();
        if (issued.get(layer).size() >= waveTarget(layer)) return false;
        issued.get(layer).add(enemy);
        changed.run();
        return true;
    }

    /** Only the controller's verified battle-victory path may call this, never entity removal/unload. */
    public boolean recordDefeat(UUID enemy) {
        if (!phase.isWave()) return false;
        Layer layer = phase.layer();
        if (!issued.get(layer).contains(enemy) || !defeated.get(layer).add(enemy)) return false;
        if (defeated.get(layer).size() == waveTarget(layer)) {
            Phase next = switch (layer) {
                case SHELL -> Phase.EXCAVATE_MANTLE;
                case MANTLE -> Phase.EXCAVATE_CASING;
                case CASING -> Phase.VOTE;
            };
            PaceBonus bonus = quickPace()
                    ? (layer == Layer.CASING ? PaceBonus.COMBAT_HEAL : PaceBonus.MINING_HASTE) : PaceBonus.NONE;
            enterPacePhase(next, bonus);
        }
        changed.run();
        return true;
    }

    public VoteResult vote(UUID player, Outcome choice) {
        if (phase != Phase.VOTE || !votingMembers().contains(player) || choice == null) return VoteResult.REJECTED;
        votes.put(player, choice);
        VoteResult result = VoteResult.RECORDED;
        if (majorityOutcome() != null) result = VoteResult.MAJORITY;
        else if (!votingMembers().isEmpty() && votes.size() == votingMembers().size()) {
            resetVoteRound();
            result = VoteResult.REVOTE;
        }
        changed.run();
        return result;
    }

    public Outcome majorityOutcome() {
        return majorityOutcome(votes, votingMembers());
    }

    private void resetVoteRound() {
        votes.clear();
        finaleMode = FinaleMode.ALPHA;
        if (voteRound < Integer.MAX_VALUE) voteRound++;
    }

    private static Outcome majorityOutcome(Map<UUID, Outcome> ballots, Set<UUID> electorate) {
        for (Outcome choice : Outcome.values()) {
            long count = ballots.entrySet().stream()
                    .filter(ballot -> electorate.contains(ballot.getKey()) && ballot.getValue() == choice).count();
            if (count > electorate.size() / 2) return choice;
        }
        return null;
    }

    /** For SEAL, the controller must consume one stabilizer before committing this transition. */
    public boolean resolveVote(Outcome choice) {
        if (phase != Phase.VOTE || choice == null || majorityOutcome() != choice) return false;
        resolutionMembers.addAll(activeMembers());
        resolutionElectorate.addAll(votingMembers());
        resolutionVotes.putAll(votes);
        outcome = choice;
        if (choice != Outcome.OVERLOAD) finaleMode = FinaleMode.ALPHA;
        phase = choice == Outcome.OVERLOAD ? Phase.OVERLOAD : Phase.COMPLETE;
        if (phase == Phase.COMPLETE) clearPace();
        changed.run();
        return true;
    }

    public boolean issueAlpha(UUID enemy) {
        Objects.requireNonNull(enemy);
        if (phase != Phase.OVERLOAD || finaleMode != FinaleMode.ALPHA || alphaId != null || raidId != null || enemyWasIssued(enemy)) return false;
        alphaId = enemy;
        changed.run();
        return true;
    }

    public boolean completeOverload(UUID enemy, boolean successful) {
        return finaleMode == FinaleMode.ALPHA && completeFinale(alphaId, enemy, successful);
    }

    public boolean issueRaid(UUID enemy) {
        Objects.requireNonNull(enemy);
        if (phase != Phase.OVERLOAD || finaleMode != FinaleMode.RAID || raidId != null || alphaId != null || enemyWasIssued(enemy)) return false;
        raidId = enemy;
        changed.run();
        return true;
    }

    /** Save both the native boss and copied adapter definition before creating the first raid instance. */
    public boolean rememberRaidSnapshot(CompoundTag pokemon, CompoundTag definition) {
        if (phase != Phase.OVERLOAD || finaleMode != FinaleMode.RAID || raidId == null || raidSnapshot != null
                || raidDefinition != null || pokemon == null || pokemon.isEmpty() || definition == null || definition.isEmpty()) return false;
        raidSnapshot = pokemon.copy();
        raidDefinition = definition.copy();
        changed.run();
        return true;
    }

    public boolean completeRaid(UUID enemy, boolean successful) {
        return finaleMode == FinaleMode.RAID && completeFinale(raidId, enemy, successful);
    }

    private boolean completeFinale(UUID issuedId, UUID enemy, boolean successful) {
        if (phase != Phase.OVERLOAD || issuedId == null || !issuedId.equals(enemy)) return false;
        overloadSucceeded = successful;
        phase = Phase.COMPLETE;
        clearPace();
        changed.run();
        return true;
    }

    /** Mark before granting inventory rewards; this prevents duplicate delivery after re-entry/reload. */
    public boolean claimReward(UUID player) {
        return phase == Phase.COMPLETE && isActiveMember(player) && update(claimedRewards.add(player));
    }

    private boolean enemyWasIssued(UUID enemy) {
        return issued.values().stream().anyMatch(values -> values.contains(enemy));
    }

    private boolean update(boolean modified) {
        if (modified) changed.run();
        return modified;
    }

    private static Phase excavationPhase(Layer layer) {
        return switch (layer) {
            case SHELL -> Phase.EXCAVATE_SHELL;
            case MANTLE -> Phase.EXCAVATE_MANTLE;
            case CASING -> Phase.EXCAVATE_CASING;
        };
    }

    private static Phase wavePhase(Layer layer) {
        return switch (layer) {
            case SHELL -> Phase.WAVE_SHELL;
            case MANTLE -> Phase.WAVE_MANTLE;
            case CASING -> Phase.WAVE_CASING;
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.put("Difficulty", difficulty.save());
        tag.putUUID("Site", siteId);
        tag.putUUID("Event", eventId);
        tag.putUUID("Host", host);
        tag.putString("Phase", phase.name());
        if (portalStartedAt >= 0) tag.putLong("PortalStartedAt", portalStartedAt);
        tag.put("Members", saveIds(members));
        tag.put("AbandonedMembers", saveIds(abandonedMembers));
        if (abandonedFrom != null) tag.putString("AbandonedFrom", abandonedFrom.name());
        tag.put("JoinRequests", saveIds(joinRequests));
        tag.put("Ready", saveIds(ready));
        ListTag absences = new ListTag();
        missingTicks.forEach((player, ticks) -> {
            CompoundTag absence = new CompoundTag();
            absence.putUUID("Player", player);
            absence.putInt("Ticks", ticks);
            absences.add(absence);
        });
        tag.put("MissingTicks", absences);
        tag.put("VotingSuspended", saveIds(votingSuspended));
        tag.putInt("LobbyIdleTicks", lobbyIdleTicks);
        CompoundTag pace = new CompoundTag();
        pace.putBoolean("Configured", paceConfigured);
        pace.putInt("ExcavationTicks", excavationPaceTicks);
        pace.putInt("WaveTicksPerOpponent", wavePaceTicksPerOpponent);
        pace.putInt("TargetTicks", paceTargetTicks);
        pace.putInt("ElapsedTicks", paceElapsedTicks);
        pace.putBoolean("TargetMissed", paceTargetMissed);
        pace.putString("Bonus", paceBonus.name());
        pace.put("Claims", saveIds(paceBonusClaims));
        tag.put("Pace", pace);
        tag.put("Claims", saveIds(claimedRewards));
        tag.putInt("VoteRound", voteRound);
        tag.putString("FinaleMode", finaleMode.name());
        if (outcome != null) tag.putString("Outcome", outcome.name());
        if (alphaId != null) tag.putUUID("Alpha", alphaId);
        if (alphaSnapshot != null) tag.put("AlphaSnapshot", alphaSnapshot.copy());
        if (raidId != null) tag.putUUID("Raid", raidId);
        if (raidSnapshot != null) tag.put("RaidSnapshot", raidSnapshot.copy());
        if (raidDefinition != null) tag.put("RaidDefinition", raidDefinition.copy());
        tag.putBoolean("OverloadSucceeded", overloadSucceeded);
        tag.put("Votes", saveVotes(votes));
        tag.put("ResolutionMembers", saveIds(resolutionMembers));
        tag.put("ResolutionElectorate", saveIds(resolutionElectorate));
        tag.put("ResolutionVotes", saveVotes(resolutionVotes));
        for (Layer layer : Layer.values()) {
            CompoundTag band = new CompoundTag();
            band.putInt("RequestedTarget", requestedTargets.get(layer));
            band.putInt("Target", target(layer));
            band.putInt("WaveBaseTarget", waveTargets.get(layer));
            band.putInt("WaveTarget", waveTarget(layer));
            band.putLongArray("Eligible", eligible.get(layer).stream().mapToLong(Long::longValue).toArray());
            band.putLongArray("Mined", mined.get(layer).stream().mapToLong(Long::longValue).toArray());
            band.putLongArray("Removed", removed.get(layer).stream().mapToLong(Long::longValue).toArray());
            band.put("Issued", saveIds(issued.get(layer)));
            band.put("Defeated", saveIds(defeated.get(layer)));
            tag.put(layer.name(), band);
        }
        return tag;
    }

    /** Invalid or future records fail closed; SavedData retains their raw bytes and reservation. */
    public static MoonwoundEvent load(CompoundTag tag) {
        int version = integer(tag, "Version");
        if (version < 1 || version > VERSION) throw invalid("Unsupported Moonwound version");
        int[] excavation = new int[3], waves = new int[3];
        for (Layer layer : Layer.values()) {
            CompoundTag band = compound(tag, layer.name());
            excavation[layer.ordinal()] = integer(band, "RequestedTarget");
            waves[layer.ordinal()] = integer(band, "WaveBaseTarget");
        }
        MoonwoundDifficulty difficulty = version >= 4
                ? MoonwoundDifficulty.load(compound(tag, "Difficulty")) : MoonwoundDifficulty.DEFAULT;
        MoonwoundEvent event = new MoonwoundEvent(id(tag, "Site"), id(tag, "Event"), id(tag, "Host"), excavation, waves, difficulty);
        event.phase = Phase.valueOf(string(tag, "Phase"));
        if (tag.contains("PortalStartedAt")) {
            require(tag, "PortalStartedAt", Tag.TAG_LONG);
            event.portalStartedAt = tag.getLong("PortalStartedAt");
            if (event.portalStartedAt < 0 || event.phase == Phase.LOBBY) throw invalid("Invalid portal activation time");
        }
        event.members.clear();
        event.members.addAll(loadIds(tag, "Members", MAX_MEMBERS));
        if (version >= 2) {
            event.abandonedMembers.addAll(loadIds(tag, "AbandonedMembers", MAX_MEMBERS));
            if (tag.contains("AbandonedFrom")) event.abandonedFrom = Phase.valueOf(string(tag, "AbandonedFrom"));
        }
        event.joinRequests.addAll(loadIds(tag, "JoinRequests", 32));
        event.ready.addAll(loadIds(tag, "Ready", MAX_MEMBERS));
        if (version >= 3) {
            for (Tag raw : list(tag, "MissingTicks", MAX_MEMBERS)) {
                CompoundTag absence = (CompoundTag) raw;
                UUID player = id(absence, "Player");
                int ticks = bounded(integer(absence, "Ticks"), 1, MAX_RECOVERY_TICKS);
                if (event.missingTicks.putIfAbsent(player, ticks) != null) throw invalid("Duplicate missing member");
            }
            event.votingSuspended.addAll(loadIds(tag, "VotingSuspended", MAX_MEMBERS));
            event.lobbyIdleTicks = bounded(integer(tag, "LobbyIdleTicks"), 0, MAX_RECOVERY_TICKS);
            // Early unreleased v3 recovery records predate pace targets; their current stage is neutral.
            if (tag.contains("Pace")) {
                CompoundTag pace = compound(tag, "Pace");
                require(pace, "Configured", Tag.TAG_BYTE);
                event.paceConfigured = pace.getBoolean("Configured");
                event.excavationPaceTicks = bounded(integer(pace, "ExcavationTicks"), 0, MAX_RECOVERY_TICKS);
                event.wavePaceTicksPerOpponent = bounded(integer(pace, "WaveTicksPerOpponent"), 0, MAX_RECOVERY_TICKS);
                event.paceTargetTicks = bounded(integer(pace, "TargetTicks"), 0, MAX_RECOVERY_TICKS);
                event.paceElapsedTicks = bounded(integer(pace, "ElapsedTicks"), 0, MAX_RECOVERY_TICKS);
                require(pace, "TargetMissed", Tag.TAG_BYTE);
                event.paceTargetMissed = pace.getBoolean("TargetMissed");
                event.paceBonus = PaceBonus.valueOf(string(pace, "Bonus"));
                event.paceBonusClaims.addAll(loadIds(pace, "Claims", MAX_MEMBERS));
            }
        }
        event.claimedRewards.addAll(loadIds(tag, "Claims", MAX_MEMBERS));
        event.voteRound = bounded(integer(tag, "VoteRound"), 1, Integer.MAX_VALUE);
        if (version >= 2 && tag.contains("FinaleMode")) event.finaleMode = FinaleMode.valueOf(string(tag, "FinaleMode"));
        if (tag.contains("Outcome")) event.outcome = Outcome.valueOf(string(tag, "Outcome"));
        if (tag.contains("Alpha")) event.alphaId = id(tag, "Alpha");
        if (tag.contains("AlphaSnapshot")) event.alphaSnapshot = compound(tag, "AlphaSnapshot").copy();
        if (tag.contains("Raid")) event.raidId = id(tag, "Raid");
        if (tag.contains("RaidSnapshot")) event.raidSnapshot = compound(tag, "RaidSnapshot").copy();
        if (tag.contains("RaidDefinition")) event.raidDefinition = compound(tag, "RaidDefinition").copy();
        require(tag, "OverloadSucceeded", Tag.TAG_BYTE);
        event.overloadSucceeded = tag.getBoolean("OverloadSucceeded");
        event.votes.putAll(loadVotes(tag, "Votes"));
        if (version >= 2) {
            event.resolutionMembers.addAll(loadIds(tag, "ResolutionMembers", MAX_MEMBERS));
            event.resolutionVotes.putAll(loadVotes(tag, "ResolutionVotes"));
        } else if (event.outcome != null) {
            event.resolutionMembers.addAll(event.members);
            event.resolutionVotes.putAll(event.votes);
        }
        if (version >= 3) event.resolutionElectorate.addAll(loadIds(tag, "ResolutionElectorate", MAX_MEMBERS));
        else event.resolutionElectorate.addAll(event.resolutionMembers);
        for (Layer layer : Layer.values()) {
            CompoundTag band = compound(tag, layer.name());
            if (integer(band, "WaveTarget") != event.waveTarget(layer)) throw invalid("Wave budget does not match frozen roster");
            event.targets.put(layer, bounded(integer(band, "Target"), 1, event.requestedTargets.get(layer)));
            event.eligible.get(layer).addAll(loadPositions(band, "Eligible"));
            event.mined.get(layer).addAll(loadPositions(band, "Mined"));
            event.removed.get(layer).addAll(loadPositions(band, "Removed"));
            event.issued.get(layer).addAll(loadIds(band, "Issued", MAX_WAVE_ENEMIES));
            event.defeated.get(layer).addAll(loadIds(band, "Defeated", MAX_WAVE_ENEMIES));
        }
        event.validate();
        return event;
    }

    private void validate() {
        validatePace();
        Set<UUID> activeMembers = activeMembers();
        if (members.isEmpty() || !members.contains(host) || !members.containsAll(ready)
                || !members.containsAll(abandonedMembers) || !activeMembers.containsAll(votes.keySet())
                || !activeMembers.containsAll(claimedRewards)
                || !activeMembers.containsAll(missingTicks.keySet()) || !activeMembers.containsAll(votingSuspended)
                || !Collections.disjoint(members, joinRequests)) throw invalid("Invalid party membership");
        if (phase != Phase.LOBBY && lobbyIdleTicks != 0) throw invalid("Lobby timer on an activated event");
        if (phase == Phase.LOBBY && !votingSuspended.isEmpty()) throw invalid("Suspended lobby electorate");
        if (phase == Phase.ABANDONED) {
            if (!activeMembers.isEmpty() || abandonedFrom == null || abandonedFrom == Phase.LOBBY || abandonedFrom.isTerminal()
                    || outcome != null || !votes.isEmpty()) throw invalid("Invalid abandoned event");
        } else if (abandonedFrom != null || activeMembers.isEmpty() || !activeMembers.contains(host)) {
            throw invalid("Invalid active host");
        }
        if (phase == Phase.LOBBY && !abandonedMembers.isEmpty()) throw invalid("Abandoned lobby member");
        Phase checkpoint = phase == Phase.ABANDONED ? abandonedFrom : phase;
        boolean started = checkpoint != Phase.LOBBY;
        if (started && (!ready.containsAll(members) || !joinRequests.isEmpty())) throw invalid("Unfrozen active roster");
        Set<Long> allPositions = new LinkedHashSet<>();
        Set<UUID> allEnemies = new LinkedHashSet<>();
        boolean anyEligible = eligible.values().stream().anyMatch(cells -> !cells.isEmpty());
        for (Layer layer : Layer.values()) {
            Set<Long> cells = eligible.get(layer), broken = mined.get(layer);
            int target = target(layer), waveTarget = waveTarget(layer);
            if ((started || anyEligible) && (cells.isEmpty() || target != Math.min(requestedTargets.get(layer), cells.size())))
                throw invalid("Invalid original-block snapshot");
            if (!started && !anyEligible && target != requestedTargets.get(layer)) throw invalid("Invalid lobby target");
            for (long cell : cells) if (!allPositions.add(cell)) throw invalid("Overlapping layer positions");
            if (allPositions.size() > MAX_ORIGINAL_BLOCKS || !cells.containsAll(removed.get(layer))
                    || !removed.get(layer).containsAll(broken) || broken.size() > target)
                throw invalid("Invalid excavation progress");
            for (UUID enemy : issued.get(layer)) if (!allEnemies.add(enemy)) throw invalid("Reused wave enemy");
            if (issued.get(layer).size() > waveTarget || !issued.get(layer).containsAll(defeated.get(layer)))
                throw invalid("Invalid wave progress");
            int excavationOrder = excavationPhase(layer).ordinal(), waveOrder = wavePhase(layer).ordinal();
            if (checkpoint.ordinal() < excavationOrder && !removed.get(layer).isEmpty()) throw invalid("Removal in a future layer");
            if (checkpoint.ordinal() == excavationOrder && broken.size() >= target) throw invalid("Unadvanced excavation");
            if (checkpoint.ordinal() >= waveOrder && broken.size() != target) throw invalid("Skipped excavation");
            if (checkpoint.ordinal() < waveOrder && (!issued.get(layer).isEmpty() || !defeated.get(layer).isEmpty()))
                throw invalid("Enemies in a future wave");
            if (checkpoint.ordinal() == waveOrder && defeated.get(layer).size() >= waveTarget) throw invalid("Unadvanced wave");
            if (checkpoint.ordinal() > waveOrder && defeated.get(layer).size() != waveTarget) throw invalid("Skipped wave");
        }
        if (checkpoint.ordinal() < Phase.VOTE.ordinal() && (!votes.isEmpty() || voteRound != 1 || outcome != null))
            throw invalid("Premature vote");
        Set<UUID> votingMembers = votingMembers();
        if (phase == Phase.VOTE && (outcome != null || !votingMembers.containsAll(votes.keySet())
                || (!votingMembers.isEmpty() && votes.size() == votingMembers.size() && majorityOutcome() == null)))
            throw invalid("Invalid unresolved ballot");
        if (phase == Phase.OVERLOAD || phase == Phase.COMPLETE) {
            if (outcome == null || resolutionMembers.isEmpty() || !members.containsAll(resolutionMembers)
                    || !resolutionMembers.containsAll(activeMembers) || resolutionElectorate.isEmpty()
                    || !resolutionMembers.containsAll(resolutionElectorate)
                    || !resolutionElectorate.containsAll(resolutionVotes.keySet())
                    || majorityOutcome(resolutionVotes, resolutionElectorate) != outcome)
                throw invalid("Resolution lacks a majority");
            Set<UUID> remainingElectorate = new LinkedHashSet<>(resolutionElectorate);
            remainingElectorate.retainAll(activeMembers);
            if (!remainingElectorate.equals(votingMembers)) throw invalid("Resolved electorate changed");
            Map<UUID, Outcome> activeBallots = new LinkedHashMap<>(resolutionVotes);
            activeBallots.keySet().retainAll(activeMembers);
            if (!activeBallots.equals(votes)) throw invalid("Resolved ballots changed");
        } else if (!resolutionMembers.isEmpty() || !resolutionElectorate.isEmpty() || !resolutionVotes.isEmpty()) {
            throw invalid("Premature resolution");
        }
        if (phase == Phase.OVERLOAD && outcome != Outcome.OVERLOAD) throw invalid("Invalid finale branch");
        boolean overloadCheckpoint = outcome == Outcome.OVERLOAD || (phase == Phase.ABANDONED && checkpoint == Phase.OVERLOAD);
        if (finaleMode != FinaleMode.ALPHA && phase != Phase.VOTE && !overloadCheckpoint) throw invalid("Premature raid mode");
        if (alphaId != null && (finaleMode != FinaleMode.ALPHA || !overloadCheckpoint || allEnemies.contains(alphaId))) throw invalid("Invalid Alpha identity");
        if (alphaSnapshot != null && (alphaId == null || alphaSnapshot.isEmpty())) throw invalid("Invalid Alpha snapshot");
        if (raidId != null && (finaleMode != FinaleMode.RAID || !overloadCheckpoint || allEnemies.contains(raidId))) throw invalid("Invalid raid identity");
        if (raidSnapshot != null && (raidId == null || raidSnapshot.isEmpty() || raidDefinition == null || raidDefinition.isEmpty()))
            throw invalid("Invalid raid snapshot");
        if (raidSnapshot == null && raidDefinition != null) throw invalid("Raid definition lacks a Pokemon snapshot");
        UUID finaleId = finaleMode == FinaleMode.ALPHA ? alphaId : raidId;
        if (phase == Phase.COMPLETE && outcome == Outcome.OVERLOAD && finaleId == null) throw invalid("Finale lacks an issued identity");
        if (overloadSucceeded && (phase != Phase.COMPLETE || outcome != Outcome.OVERLOAD)) throw invalid("Premature finale success");
        if (phase != Phase.COMPLETE && !claimedRewards.isEmpty()) throw invalid("Premature reward claim");
    }

    private void validatePace() {
        if (!members.containsAll(paceBonusClaims)) throw invalid("Pace claim from an outsider");
        if (paceConfigured) {
            if (excavationPaceTicks == 0 || wavePaceTicksPerOpponent == 0) throw invalid("Missing pace configuration");
        } else if (excavationPaceTicks != 0 || wavePaceTicksPerOpponent != 0 || paceTargetTicks != 0
                || paceElapsedTicks != 0 || paceTargetMissed || paceBonus != PaceBonus.NONE || !paceBonusClaims.isEmpty()) {
            throw invalid("Unconfigured pace state");
        }
        if (paceTargetTicks != 0 && paceTargetTicks != targetForPacePhase()) throw invalid("Invalid phase pace target");
        if (paceTargetTicks == 0 && (paceElapsedTicks != 0 || paceTargetMissed)) throw invalid("Elapsed untimed phase");
        if (paceTargetTicks > 0) {
            if (!paceTargetMissed && paceElapsedTicks > paceTargetTicks) throw invalid("Missed pace target unrecorded");
            if (paceTargetMissed && paceElapsedTicks <= paceTargetTicks
                    && !(paceTargetTicks == MAX_RECOVERY_TICKS && paceElapsedTicks == MAX_RECOVERY_TICKS)) {
                throw invalid("Premature missed pace target");
            }
        }
        boolean validBonus = switch (paceBonus) {
            case NONE -> true;
            case COMBAT_HEAL -> phase.isWave() || phase == Phase.VOTE || phase == Phase.OVERLOAD;
            case MINING_HASTE -> phase == Phase.EXCAVATE_MANTLE || phase == Phase.EXCAVATE_CASING;
        };
        if (!validBonus || (paceBonus != PaceBonus.NONE && (phase.isExcavation() || phase.isWave()) && paceTargetTicks == 0)) {
            throw invalid("Pace bonus in the wrong phase");
        }
        if ((paceBonus == PaceBonus.NONE || phase == Phase.VOTE) && !paceBonusClaims.isEmpty()) {
            throw invalid("Unavailable pace bonus claimed");
        }
    }

    private static ListTag saveVotes(Map<UUID, Outcome> votes) {
        ListTag ballots = new ListTag();
        votes.forEach((player, choice) -> {
            CompoundTag ballot = new CompoundTag();
            ballot.putUUID("Player", player);
            ballot.putString("Choice", choice.name());
            ballots.add(ballot);
        });
        return ballots;
    }

    private static Map<UUID, Outcome> loadVotes(CompoundTag tag, String key) {
        Map<UUID, Outcome> votes = new LinkedHashMap<>();
        for (Tag raw : list(tag, key, MAX_MEMBERS)) {
            CompoundTag ballot = (CompoundTag) raw;
            UUID player = id(ballot, "Player");
            if (votes.putIfAbsent(player, Outcome.valueOf(string(ballot, "Choice"))) != null) throw invalid("Duplicate ballot");
        }
        return votes;
    }

    private static ListTag saveIds(Collection<UUID> values) {
        ListTag list = new ListTag();
        for (UUID value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", value);
            list.add(entry);
        }
        return list;
    }

    private static Set<UUID> loadIds(CompoundTag tag, String key, int maximum) {
        Set<UUID> values = new LinkedHashSet<>();
        for (Tag raw : list(tag, key, maximum)) if (!values.add(id((CompoundTag) raw, "Id"))) throw invalid("Duplicate " + key);
        return values;
    }

    private static Set<Long> loadPositions(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_LONG_ARRAY);
        long[] raw = tag.getLongArray(key);
        if (raw.length > MAX_ORIGINAL_BLOCKS) throw invalid("Oversized " + key);
        Set<Long> values = new LinkedHashSet<>();
        for (long cell : raw) if (!values.add(cell)) throw invalid("Duplicate " + key);
        return values;
    }

    private static ListTag list(CompoundTag tag, String key, int maximum) {
        require(tag, key, Tag.TAG_LIST);
        ListTag list = (ListTag) tag.get(key);
        if (list.size() > maximum || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) throw invalid("Invalid " + key);
        return list;
    }

    private static UUID id(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw invalid("Missing UUID " + key);
        return tag.getUUID(key);
    }

    private static CompoundTag compound(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_COMPOUND);
        return tag.getCompound(key);
    }

    private static String string(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_STRING);
        return tag.getString(key);
    }

    private static int integer(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_INT);
        return tag.getInt(key);
    }

    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw invalid("Missing or invalid " + key);
    }

    private static int bounded(int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) throw invalid("Invalid bounded value " + value);
        return value;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
