package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** The site ledger stores the selected roles and identities independently of subsequent config changes. */
final class CraterRescueEvent {
    static final int MAX_PACK = 6;
    enum HowlState { OPEN, SILENCED, ANSWERED }

    record Actor(UUID id, BlockPos home, CompoundTag snapshot) {
        Pokemon restore(ServerLevel level) {
            Pokemon result = Pokemon.Companion.loadFromNBT(level.registryAccess(), snapshot.copy());
            if (!id.equals(result.getUuid())) throw new IllegalStateException("Saved rescue Pokémon has a different identity");
            return result;
        }
    }

    final UUID site;
    final BlockPos center;
    List<Actor> actors;
    final CraterEventKind kind;
    private final int carrierIndex;
    final int dustReward;
    final int scentReward;
    HowlState howlState = HowlState.OPEN;
    final int scatterDistance;
    @Nullable BlockPos refuge;
    private final Map<UUID, BlockPos> escortHomes = new HashMap<>();
    private final java.util.Set<UUID> travelling = new HashSet<>();
    boolean interiorHomes = true;
    long retryRefugeAt;
    UUID trainer;
    private int cleared;
    int interventionSeconds;
    long absentSince = -1;
    boolean assembled;
    int failedAssemblies;
    long nextOfferAt;
    long retryAt;
    boolean reconcileStorage;
    final java.util.Set<UUID> notified = new HashSet<>();

    CraterRescueEvent(UUID site, BlockPos center, List<Actor> actors, int interventionSeconds) {
        this(site, center, actors, CraterEventKind.RESCUE, -1, interventionSeconds, 0, 0, 0);
    }

    static CraterRescueEvent outbreak(UUID site, BlockPos center, List<Actor> pack, int carrierIndex,
                                      int scatterDistance, int dustReward) {
        return new CraterRescueEvent(site, center, pack, CraterEventKind.OUTBREAK, carrierIndex, 0,
                scatterDistance, dustReward, 0);
    }

    static CraterRescueEvent howl(UUID site, BlockPos center, List<Actor> callerThenResponder, int scentReward) {
        return new CraterRescueEvent(site, center, callerThenResponder, CraterEventKind.HOWL, -1, 0, 0, 0, scentReward);
    }

    private CraterRescueEvent(UUID site, BlockPos center, List<Actor> actors, CraterEventKind kind, int carrierIndex,
                              int interventionSeconds, int scatterDistance, int dustReward, int scentReward) {
        int maxActors = kind == CraterEventKind.RESCUE ? MAX_PACK + 1 : MAX_PACK;
        if (actors.size() < 2 || actors.size() > maxActors) throw new IllegalArgumentException("Invalid crater event cast");
        if (kind == CraterEventKind.OUTBREAK && (carrierIndex < 0 || carrierIndex >= actors.size()
                || scatterDistance < 1 || scatterDistance > 8 || dustReward < 0 || dustReward > 64))
            throw new IllegalArgumentException("Invalid outbreak carrier or reward");
        if (kind == CraterEventKind.HOWL && (actors.size() != 2 || scentReward < 0 || scentReward > 64))
            throw new IllegalArgumentException("Invalid howl pair or reward");
        this.site = site;
        this.center = center;
        this.actors = List.copyOf(actors);
        this.kind = kind;
        this.carrierIndex = carrierIndex;
        this.scatterDistance = scatterDistance;
        this.dustReward = dustReward;
        this.scentReward = scentReward;
        this.interventionSeconds = interventionSeconds;
    }

    boolean outbreak() { return kind == CraterEventKind.OUTBREAK; }
    boolean rescue() { return kind == CraterEventKind.RESCUE; }
    boolean howl() { return kind == CraterEventKind.HOWL; }
    @Nullable Actor victim() { return rescue() ? actors.getLast() : null; }
    Actor caller() { return actors.getFirst(); }
    Actor responder() { return actors.get(1); }
    Actor carrier() { return actors.get(carrierIndex); }
    int packSize() {
        if (howl()) return howlState == HowlState.ANSWERED ? 2 : 1;
        return rescue() ? actors.size() - 1 : actors.size();
    }
    int remaining() { return packSize() - Integer.bitCount(cleared); }
    boolean objectiveComplete() { return outbreak() ? (cleared & (1 << carrierIndex)) != 0 : remaining() == 0; }
    boolean isolated(UUID pokemon) { return refuge != null && outbreak() && carrier().id().equals(pokemon); }
    BlockPos home(Actor actor) { return isolated(actor.id()) ? refuge : escortHomes.getOrDefault(actor.id(), actor.home()); }
    @Nullable Actor actor(UUID id) {
        for (Actor actor : actors) if (actor.id().equals(id)) return actor;
        return null;
    }
    boolean travelling(UUID id) { return travelling.contains(id); }
    boolean hasDeparted(UUID id) { return isolated(id) || escortHomes.containsKey(id); }

    void beginTravel(Actor actor, BlockPos destination) {
        setDestination(actor, destination);
        travelling.add(actor.id());
    }

    void settle(Actor actor, BlockPos position) {
        setDestination(actor, position);
        travelling.remove(actor.id());
    }

    private void setDestination(Actor actor, BlockPos destination) {
        if (actor.id().equals(carrier().id())) refuge = destination.immutable();
        else escortHomes.put(actor.id(), destination.immutable());
    }

    void moveAssembly(List<BlockPos> homes) {
        if (homes.size() != actors.size()) throw new IllegalArgumentException("Incomplete crater event homes");
        var moved = new java.util.ArrayList<Actor>(actors.size());
        for (int i = 0; i < actors.size(); i++) {
            Actor actor = actors.get(i);
            moved.add(new Actor(actor.id(), homes.get(i), actor.snapshot()));
        }
        actors = List.copyOf(moved);
        interiorHomes = true;
    }

    boolean active(UUID pokemon) {
        if (rescue() && victim().id().equals(pokemon)) return true;
        for (int i = 0; i < packSize(); i++) {
            if (actors.get(i).id().equals(pokemon)) return (cleared & (1 << i)) == 0;
        }
        return false;
    }

    boolean mayClaim(UUID player) {
        return !objectiveComplete() && (trainer == null || trainer.equals(player));
    }

    boolean finishCall(UUID player, HowlState outcome) {
        if (!howl() || howlState != HowlState.OPEN || outcome == HowlState.OPEN
                || !player.equals(trainer) || !active(caller().id())) return false;
        howlState = outcome;
        return true;
    }

    boolean engage(UUID player) {
        if (!mayClaim(player)) return false;
        trainer = player;
        absentSince = -1;
        interventionSeconds = 0;
        return true;
    }

    boolean resolve(UUID player, UUID pokemon) {
        if (!player.equals(trainer) || !active(pokemon)) return false;
        for (int i = 0; i < packSize(); i++) {
            if (actors.get(i).id().equals(pokemon)) {
                cleared |= 1 << i;
                travelling.remove(pokemon);
                return true;
            }
        }
        return false;
    }

    /** A finished rescue belongs to its winner even if they disconnect before answering. */
    boolean releaseIfAbsent(long now, int graceTicks) {
        if (trainer == null || objectiveComplete() || absentSince < 0 || now - absentSince < graceTicks) return false;
        trainer = null;
        absentSince = -1;
        return true;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Site", site);
        tag.putString("Kind", kind.name());
        tag.putBoolean("InteriorHomes", interiorHomes);
        if (outbreak()) {
            tag.putInt("Carrier", carrierIndex);
            tag.putInt("ScatterDistance", scatterDistance);
            tag.putInt("DustReward", dustReward);
            if (refuge != null) tag.putLong("Refuge", refuge.asLong());
        } else if (howl()) {
            tag.putString("HowlState", howlState.name());
            tag.putInt("ScentReward", scentReward);
        }
        tag.putLong("Center", center.asLong());
        if (trainer != null) tag.putUUID("Trainer", trainer);
        tag.putInt("Cleared", cleared);
        tag.putInt("InterventionSeconds", interventionSeconds);
        tag.putLong("AbsentSince", absentSince);
        var list = new ListTag();
        for (Actor actor : actors) {
            var entry = new CompoundTag();
            entry.putUUID("Id", actor.id());
            entry.putLong("Home", actor.home().asLong());
            if (escortHomes.containsKey(actor.id())) entry.putLong("ScatterHome", escortHomes.get(actor.id()).asLong());
            if (travelling(actor.id())) entry.putBoolean("Travelling", true);
            entry.put("Pokemon", actor.snapshot().copy());
            list.add(entry);
        }
        tag.put("Actors", list);
        return tag;
    }

    static CraterRescueEvent load(CompoundTag tag) {
        var entries = tag.getList("Actors", Tag.TAG_COMPOUND);
        // Before configurable casts existed, the only supported record was three attackers and one rescuee.
        boolean legacy = !tag.contains("Kind");
        if (!tag.hasUUID("Site") || !tag.contains("Center", Tag.TAG_LONG) || (legacy && entries.size() != 4)
                || entries.size() < 2 || entries.size() > MAX_PACK + 1) throw new IllegalArgumentException("Incomplete crater event");
        var kind = legacy ? CraterEventKind.RESCUE : CraterEventKind.valueOf(tag.getString("Kind"));
        BlockPos center = BlockPos.of(tag.getLong("Center"));
        var ids = new HashSet<UUID>();
        var actors = new java.util.ArrayList<Actor>();
        for (int i = 0; i < entries.size(); i++) {
            var actor = entries.getCompound(i);
            if (!actor.hasUUID("Id") || !ids.add(actor.getUUID("Id")) || !actor.contains("Home", Tag.TAG_LONG)
                    || actor.getCompound("Pokemon").isEmpty()) throw new IllegalArgumentException("Invalid rescue actor");
            BlockPos home = BlockPos.of(actor.getLong("Home"));
            if (!center.closerThan(home, 128)) throw new IllegalArgumentException("Actor home outside the site");
            actors.add(new Actor(actor.getUUID("Id"), home, actor.getCompound("Pokemon").copy()));
        }
        if (kind == CraterEventKind.OUTBREAK && (!tag.contains("Carrier", Tag.TAG_INT)
                || !tag.contains("DustReward", Tag.TAG_INT) || (!tag.contains("ScatterDistance", Tag.TAG_INT)
                && !tag.contains("BreakawaySeconds", Tag.TAG_INT))))
            throw new IllegalArgumentException("Incomplete outbreak");
        if (kind == CraterEventKind.HOWL && (!tag.contains("HowlState", Tag.TAG_STRING)
                || !tag.contains("ScentReward", Tag.TAG_INT) || tag.contains("Refuge")))
            throw new IllegalArgumentException("Incomplete or invalid howl");
        var event = new CraterRescueEvent(tag.getUUID("Site"), center, actors, kind,
                kind == CraterEventKind.OUTBREAK ? tag.getInt("Carrier") : -1, Math.max(0, tag.getInt("InterventionSeconds")),
                tag.contains("ScatterDistance", Tag.TAG_INT) ? tag.getInt("ScatterDistance") : 4,
                tag.getInt("DustReward"), tag.getInt("ScentReward"));
        if (event.howl()) event.howlState = HowlState.valueOf(tag.getString("HowlState"));
        event.interiorHomes = tag.getBoolean("InteriorHomes");
        if (event.outbreak() && tag.contains("Refuge")) {
            if (!tag.contains("Refuge", Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid refuge");
            event.refuge = BlockPos.of(tag.getLong("Refuge"));
            if (!event.center.closerThan(event.refuge, 64)) throw new IllegalArgumentException("Refuge outside the site");
        }
        for (int i = 0; i < actors.size(); i++) {
            var entry = entries.getCompound(i);
            var actor = actors.get(i);
            if (entry.contains("ScatterHome")) {
                if (event.refuge == null || i == event.carrierIndex || !entry.contains("ScatterHome", Tag.TAG_LONG))
                    throw new IllegalArgumentException("Invalid scatter home");
                BlockPos home = BlockPos.of(entry.getLong("ScatterHome"));
                if (!event.center.closerThan(home, 64)) throw new IllegalArgumentException("Scatter home outside the site");
                event.escortHomes.put(actor.id(), home);
            }
            if (entry.getBoolean("Travelling")) {
                if (!event.hasDeparted(actor.id())) throw new IllegalArgumentException("Missing scatter destination");
                event.travelling.add(actor.id());
            }
        }
        event.cleared = tag.getInt("Cleared");
        event.trainer = tag.hasUUID("Trainer") ? tag.getUUID("Trainer") : null;
        if ((event.cleared >>> event.packSize()) != 0 || (event.objectiveComplete() && event.trainer == null))
            throw new IllegalArgumentException("Invalid crater event progress");
        event.absentSince = tag.contains("AbsentSince") ? tag.getLong("AbsentSince") : -1;
        event.reconcileStorage = event.trainer != null;
        return event;
    }
}
