package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import com.jayemceekay.shadowedhearts.integration.CraterSosCompatibility;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Authorize native wild battles against the issued pack, and settle each issued Pokémon exactly once in the site ledger. */
public final class CraterRescueBattles {
    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    private static final Map<EmptyPokeBallEntity, Throw> THROWS = new WeakHashMap<>();
    private static final Set<UUID> DISPERSING = new HashSet<>();
    private static Listener listener;

    private CraterRescueBattles() {}

    public interface Listener {
        boolean active(ServerLevel world, UUID event, UUID pokemon);
        boolean isolated(ServerLevel world, UUID event, UUID pokemon);
        boolean carrier(ServerLevel world, UUID event, UUID pokemon);
        boolean completedOutbreak(ServerLevel world, UUID event);
        boolean canEngage(ServerPlayer player, UUID event);
        boolean canContinue(ServerPlayer player, UUID event);
        void engaged(ServerPlayer player, UUID event, Set<UUID> opponents);
        void resolved(ServerLevel world, UUID event, UUID pokemon, UUID player);
    }

    public static void init(Listener controller) {
        if (listener != null) return;
        listener = controller;
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            FIGHTS.clear();
            THROWS.clear();
            DISPERSING.clear();
        });
        TickEvent.SERVER_POST.register(server -> finishDispersals());
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.LOWEST, event -> {
            guardBattle(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, event -> {
            remember(event.getBattle());
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, event -> {
            var battle = event.getBattle();
            Fight fight = FIGHTS.get(battle.getBattleId());
            var fainted = event.getKilled();
            UUID id = fainted.getUuid();
            if (fight != null && fainted.getFaintedAt() != null && fight.pokemon.contains(id)
                    && CraterRescueActors.isHowl(fainted.getEffectedPokemon()) && active(fight.world, fight.site, id)) {
                listener.resolved(fight.world, fight.site, id, fight.player);
            }
            if (fight != null && fight.pokemon.size() > 1 && fight.pokemon.contains(id)
                    && fainted.getFaintedAt() != null && listener.carrier(fight.world, fight.site, id)
                    && active(fight.world, fight.site, id)) {
                listener.resolved(fight.world, fight.site, id, fight.player);
                DISPERSING.add(battle.getBattleId());
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_ENDED.subscribe(Priority.NORMAL, event -> {
            settleFaints(event.getBattle());
            return Unit.INSTANCE;
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.LOWEST, event -> {
            guardCapture(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            captured(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe(Priority.LOWEST, event -> {
            PokemonEntity entity = event.getPokemonEntity();
            Pokemon pokemon = entity.getPokemon();
            UUID site = CraterRescueActors.site(pokemon);
            if (site != null && entity.level() instanceof ServerLevel world) {
                if (pokemon.isPlayerOwned()) CraterRescueActors.clearForAdoption(pokemon);
                else if (!active(world, site, pokemon.getUuid()) || !entity.getUUID().equals(pokemon.getUuid())
                        || (listener.completedOutbreak(world, site) && !busy(pokemon.getUuid()))) event.cancel();
                else CraterRescueActors.protect(entity);
            }
            return Unit.INSTANCE;
        });
    }

    public static boolean active(ServerLevel world, UUID site, UUID id) {
        return listener != null && listener.active(world, site, id);
    }

    public static List<PokemonEntity> gatherPack(PokemonEntity target) {
        Pokemon pokemon = target.getPokemon();
        UUID site = CraterRescueActors.site(pokemon);
        if (!(target.level() instanceof ServerLevel world) || site == null || CraterRescueActors.victim(pokemon)
                || target.isBusy() || target.isBattling() || !active(world, site, pokemon.getUuid())) return List.of();
        List<PokemonEntity> pack = new ArrayList<>();
        pack.add(target);
        if (listener.isolated(world, site, pokemon.getUuid())) return pack;
        for (PokemonEntity other : world.getEntitiesOfClass(PokemonEntity.class, target.getBoundingBox().inflate(24))) {
            Pokemon member = other.getPokemon();
            if (other != target && !other.isBusy() && !other.isBattling() && !member.isPlayerOwned()
                    && site.equals(CraterRescueActors.site(member)) && !CraterRescueActors.victim(member)
                    && other.getUUID().equals(member.getUuid()) && !busy(member.getUuid())
                    && active(world, site, member.getUuid()) && !listener.isolated(world, site, member.getUuid())) pack.add(other);
        }
        return pack;
    }

    private static void guardBattle(BattleStartedEvent.Pre event) {
        PokemonBattle battle = event.getBattle();
        UUID site = null;
        boolean unrelated = false;
        boolean denied = false;
        boolean isolated = false;
        int issuedOpponents = 0;
        for (var actor : battle.getActors()) {
            if (actor.getType() != ActorType.WILD) continue;
            for (var member : actor.getPokemonList()) {
                Pokemon pokemon = member.getEffectedPokemon();
                UUID marker = CraterRescueActors.site(pokemon);
                if (marker == null) {
                    unrelated = true;
                    continue;
                }
                PokemonEntity entity = member.getEntity();
                issuedOpponents++;
                denied |= site != null && !site.equals(marker);
                site = marker;
                if (entity != null && entity.level() instanceof ServerLevel world) {
                    isolated |= listener.isolated(world, site, pokemon.getUuid());
                    if (CraterRescueActors.isHowl(pokemon)) {
                        var howl = CraterRescueData.get(world).active.get(site);
                        denied |= howl == null || !howl.howl() || (howl.responder().id().equals(pokemon.getUuid())
                                && (howl.active(howl.caller().id()) || inProgress(world, site)));
                    }
                }
                denied |= CraterRescueActors.victim(pokemon) || entity == null || !(entity.level() instanceof ServerLevel world)
                        || !active(world, site, pokemon.getUuid()) || !entity.getUUID().equals(pokemon.getUuid()) || busy(pokemon.getUuid());
            }
        }
        if (site == null) return;
        if (denied || unrelated || (isolated && issuedOpponents > 1) || battle.getPlayers().size() != 1
                || !listener.canEngage(battle.getPlayers().getFirst(), site)) {
            event.setReason(Component.translatable("message.shadowedhearts.crater_rescue.unavailable"));
            event.cancel();
        }
    }

    private static void remember(PokemonBattle battle) {
        if (battle.getPlayers().size() != 1) return;
        ServerPlayer player = battle.getPlayers().getFirst();
        Set<UUID> ids = new HashSet<>();
        UUID site = null;
        for (var actor : battle.getActors()) {
            if (actor.getType() != ActorType.WILD) continue;
            for (var member : actor.getPokemonList()) {
                Pokemon pokemon = member.getEffectedPokemon();
                UUID marker = CraterRescueActors.site(pokemon);
                if (marker == null) continue;
                ids.add(pokemon.getUuid());
                site = marker;
            }
        }
        if (site == null) return;
        Fight fight = new Fight(player.serverLevel(), site, player.getUUID(), ids);
        FIGHTS.put(battle.getBattleId(), fight);
        listener.engaged(player, site, fight.pokemon);
    }

    /** Only the unresolved sentry needs the open partner slot in SOS Encounters' battle builder. */
    public static boolean allowsSos(PokemonEntity target) {
        if (!CraterRescueActors.isHowl(target.getPokemon()) || !(target.level() instanceof ServerLevel level)) return false;
        var event = CraterRescueData.get(level).active.get(CraterRescueActors.site(target.getPokemon()));
        return event != null && event.howl() && event.howlState == CraterRescueEvent.HowlState.OPEN
                && target.getUUID().equals(event.caller().id()) && event.active(target.getUUID());
    }

    /** Cobblemon dispatches this after the preceding turn's damage, status and faint instructions. */
    public static void howlTurn(PokemonBattle battle, int turn) {
        var fight = FIGHTS.get(battle.getBattleId());
        if (fight == null || battle.getEnded() || battle.getPlayers().size() != 1) return;
        var player = battle.getPlayers().getFirst();
        for (var slot : battle.getSide2().getActivePokemon()) {
            BattlePokemon member = slot.getBattlePokemon();
            if (member == null || !CraterRescueActors.isHowl(member.getEffectedPokemon()) || !fight.pokemon.contains(member.getUuid())) continue;
            if (turn == 1) {
                fight.nativeSos = "multi".equals(battle.getFormat().getBattleType().getName()) && CraterSosCompatibility.available();
                if (fight.nativeSos) battle.writeShowdownAction(">shcraterhowl prepare " + member.getUuid() + " " + turn);
                continue;
            }
            var event = CraterRescueService.howlCaller(player, member.getUuid());
            if (event == null || event.howlState != CraterRescueEvent.HowlState.OPEN || member.getHealth() <= 0
                    || member.getFaintedAt() != null) continue;
            boolean status = member.getEffectedPokemon().getStatus() != null;
            if (fight.warnedOn == 0) {
                if (!status && member.getHealth() * 2 < member.getMaxHealth() && CraterRescueService.howlWarning(player, event)) {
                    fight.warnedOn = turn;
                    if (fight.nativeSos) battle.writeShowdownAction(">shcraterhowl warn " + member.getUuid() + " " + turn);
                }
            } else if (turn > fight.warnedOn) {
                if (status) CraterRescueService.howlQuiet(player, event);
                else CraterRescueService.howlAnswer(player, event);
            }
        }
    }

    /** A status cured later in the same turn still interrupts a pending howl. */
    public static void howlStatus(Pokemon pokemon) {
        if (!CraterRescueActors.isHowl(pokemon)) return;
        var entity = pokemon.getEntity();
        var battle = entity == null ? null : entity.getBattle();
        if (battle == null || !(entity.level() instanceof ServerLevel) || battle.getPlayers().size() != 1) return;
        var player = battle.getPlayers().getFirst();
        if (!sosDue(player, entity)) return;
        var event = CraterRescueService.howlCaller(player, pokemon.getUuid());
        if (event != null) CraterRescueService.howlQuiet(player, event);
    }

    /** Both native SOS rolls are deterministic only during this trainer's one warned turn. */
    public static boolean sosDue(ServerPlayer player, PokemonEntity caller) {
        var battle = caller.getBattle();
        if (battle == null || battle.getEnded()) return false;
        var fight = FIGHTS.get(battle.getBattleId());
        if (fight == null || !fight.pokemon.contains(caller.getUUID()) || !fight.player.equals(player.getUUID())
                || fight.warnedOn == 0 || battle.getTurn() != fight.warnedOn) return false;
        var event = CraterRescueService.howlCaller(player, caller.getUUID());
        return event != null && event.howlState == CraterRescueEvent.HowlState.OPEN;
    }

    public static @Nullable Pokemon sosResponder(ServerPlayer player, PokemonEntity caller) {
        if (!sosDue(player, caller) || caller.getPokemon().getStatus() != null || caller.getPokemon().getCurrentHealth() <= 0) return null;
        var event = CraterRescueService.howlCaller(player, caller.getUUID());
        if (event == null || !CraterRescueService.howlAnswer(player, event)) return null;
        return event.responder().restore(player.serverLevel());
    }

    public static @Nullable PokemonEntity sendSosResponder(PokemonBattle battle, Pokemon issued) {
        Fight fight = FIGHTS.get(battle.getBattleId());
        if (fight == null || battle.getEnded() || battle.getPlayers().size() != 1) return null;
        var event = CraterRescueData.get(fight.world).active.get(fight.site);
        if (event == null || !event.howl() || event.howlState != CraterRescueEvent.HowlState.ANSWERED
                || !event.responder().id().equals(issued.getUuid()) || !event.active(issued.getUuid())
                || fight.pokemon.contains(issued.getUuid()) || !fight.player.equals(event.trainer)
                || !listener.canContinue(battle.getPlayers().getFirst(), event.site)) return null;
        var site = UmbrafallSiteData.get(fight.world).sites.get(fight.site);
        var home = event.responder().home();
        if (!CraterRescueSites.actorsLoaded(fight.world, home)) return null;
        return CraterRescueActors.ensure(fight.world, home, issued, feet -> CraterRescueSites.insideEncounter(fight.world, site, feet));
    }

    /** Called after Asymmetric Battles installs the actor and SOS Encounters binds its entity to this battle. */
    public static void recordSosJoin(PokemonBattle battle, PokemonEntity entity) {
        Fight fight = FIGHTS.get(battle.getBattleId());
        if (fight == null || entity.getBattle() != battle || !fight.site.equals(CraterRescueActors.site(entity.getPokemon()))) return;
        var event = CraterRescueData.get(fight.world).active.get(fight.site);
        if (event != null && event.howl() && event.responder().id().equals(entity.getUUID()) && event.active(entity.getUUID()))
            fight.pokemon.add(entity.getUUID());
    }

    private static void settleFaints(PokemonBattle battle) {
        DISPERSING.remove(battle.getBattleId());
        Fight fight = FIGHTS.remove(battle.getBattleId());
        if (fight == null) return;
        for (var actor : battle.getActors()) {
            if (actor.getType() != ActorType.WILD) continue;
            for (var member : actor.getPokemonList()) {
                UUID id = member.getUuid();
                // Cobblemon heals wild leaders in end(). Only the interpreter's actual faint is evidence,
                // including earlier knockouts when the player catches the final horde member or retreats.
                if (fight.pokemon.contains(id) && member.getFaintedAt() != null && active(fight.world, fight.site, id)) {
                    listener.resolved(fight.world, fight.site, id, fight.player);
                    if (!active(fight.world, fight.site, id)) CraterRescueActors.discard(fight.world, id);
                }
            }
        }
    }

    /** The faint notification runs inside Cobblemon's animated dispatch. Let the current turn and
     * any accepted throw finish before ending the surviving escorts' battle. */
    private static void finishDispersals() {
        if (DISPERSING.isEmpty()) return;
        for (UUID battleId : Set.copyOf(DISPERSING)) {
            PokemonBattle battle = BattleRegistry.getBattle(battleId);
            Fight fight = FIGHTS.get(battleId);
            if (battle == null || fight == null || battle.getEnded()) {
                DISPERSING.remove(battleId);
            } else if (battle.getDispatches().isEmpty() && battle.getDispatchResult().canProceed()
                    && battle.getCaptureActions().isEmpty() && !pendingThrow(fight.world, fight.site)) {
                battle.stop();
            }
        }
    }

    private static void guardCapture(ThrownPokeballHitEvent event) {
        PokemonEntity target = event.getPokemon();
        Pokemon pokemon = target.getPokemon();
        UUID site = CraterRescueActors.site(pokemon);
        if (site == null) return;
        PokemonBattle battle = target.getBattle();
        Fight fight = battle == null ? null : FIGHTS.get(battle.getBattleId());
        if (CraterRescueActors.victim(pokemon) || !(event.getPokeBall().getOwner() instanceof ServerPlayer player)
                || fight == null || !fight.player.equals(player.getUUID()) || !fight.site.equals(site)
                || !fight.pokemon.contains(pokemon.getUuid()) || !active(fight.world, site, pokemon.getUuid())
                || !listener.canContinue(player, site)) {
            event.cancel();
        } else if (!event.isCanceled()) {
            THROWS.put(event.getPokeBall(), new Throw(fight, pokemon.getUuid()));
        }
    }

    private static void captured(PokemonCapturedEvent event) {
        Throw attempt = THROWS.remove(event.getPokeBallEntity());
        if (attempt == null) return;
        Pokemon pokemon = event.getPokemon();
        ServerPlayer player = event.getPlayer();
        if (attempt.pokemon.equals(pokemon.getUuid()) && attempt.fight.site.equals(CraterRescueActors.site(pokemon))
                && attempt.fight.player.equals(player.getUUID()) && player.getUUID().equals(pokemon.getOwnerUUID())
                && !event.getPokeBallEntity().getCaptureFuture().isCompletedExceptionally()
                && Boolean.TRUE.equals(event.getPokeBallEntity().getCaptureFuture().getNow(false))
                && active(attempt.fight.world, attempt.fight.site, attempt.pokemon)) {
            CraterRescueActors.clearForAdoption(pokemon);
            listener.resolved(attempt.fight.world, attempt.fight.site, attempt.pokemon, player.getUUID());
        }
    }

    public static boolean busy(UUID id) {
        if (FIGHTS.values().stream().anyMatch(fight -> fight.pokemon.contains(id))) return true;
        return THROWS.entrySet().stream().anyMatch(entry -> entry.getValue().pokemon.equals(id)
                && !entry.getKey().isRemoved() && !entry.getKey().getCaptureFuture().isDone());
    }

    public static boolean inProgress(ServerLevel world, UUID site) {
        return FIGHTS.values().stream().anyMatch(fight -> fight.world == world && fight.site.equals(site))
                || pendingThrow(world, site);
    }

    private static boolean pendingThrow(ServerLevel world, UUID site) {
        return THROWS.entrySet().stream().anyMatch(entry -> entry.getValue().fight.world == world
                && entry.getValue().fight.site.equals(site) && !entry.getKey().isRemoved()
                && !entry.getKey().getCaptureFuture().isDone());
    }

    /** Expire only the old claimant's live operations; a later native callback cannot credit the successor. */
    public static void releaseClaim(ServerLevel level, UUID site) {
        for (var entry : new HashMap<>(FIGHTS).entrySet()) {
            Fight fight = entry.getValue();
            if (fight.world != level || !site.equals(fight.site)) continue;
            FIGHTS.remove(entry.getKey());
            PokemonBattle battle = BattleRegistry.getBattle(entry.getKey());
            if (battle != null && !battle.getEnded()) battle.stop();
        }
        for (Throw attempt : new ArrayList<>(THROWS.values())) {
            if (attempt.fight.world == level && site.equals(attempt.fight.site)) cancelCapture(attempt.pokemon);
        }
    }

    /** Runs before the ball's native scheduler commits to party storage. */
    public static void recheckCapture(EmptyPokeBallEntity ball) {
        Throw attempt = THROWS.get(ball);
        if (attempt == null) return;
        if (ball.getCaptureFuture().isDone()) {
            THROWS.remove(ball);
        } else if (!active(attempt.fight.world, attempt.fight.site, attempt.pokemon)
                || !(ball.getOwner() instanceof ServerPlayer player) || !attempt.fight.player.equals(player.getUUID())
                || !listener.canContinue(player, attempt.fight.site)) {
            cancelCapture(attempt.pokemon);
        }
    }

    static void cancelCapture(UUID pokemon) {
        for (var entry : new HashMap<>(THROWS).entrySet()) {
            if (!entry.getValue().pokemon.equals(pokemon)) continue;
            EmptyPokeBallEntity ball = entry.getKey();
            THROWS.remove(ball);
            if (!ball.getCaptureFuture().isDone()) {
                ball.getCaptureFuture().complete(false);
                PokemonEntity target = ball.getCapturingPokemon();
                if (target != null && pokemon.equals(target.getPokemon().getUuid()) && !target.getPokemon().isPlayerOwned()) target.discard();
                ball.discard();
            }
        }
    }

    private static final class Fight {
        final ServerLevel world;
        final UUID site;
        final UUID player;
        final Set<UUID> pokemon;
        int warnedOn;
        boolean nativeSos;

        Fight(ServerLevel world, UUID site, UUID player, Set<UUID> pokemon) {
            this.world = world;
            this.site = site;
            this.player = player;
            this.pokemon = pokemon;
        }
    }
    private record Throw(Fight fight, UUID pokemon) {}
}
