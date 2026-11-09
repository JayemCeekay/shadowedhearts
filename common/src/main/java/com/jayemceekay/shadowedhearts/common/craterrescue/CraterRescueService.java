package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ServerConfig;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.craterrescue.CraterRescueOfferPacket;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/** One field encounter per site, independent of mining, signal ownership and Moonwound. */
public final class CraterRescueService {
    private static final int APPROACH = 28;
    private static final int PARTICIPATION_RANGE = 64;
    private static final int AWAY_GRACE_TICKS = 20 * 120;
    private record Offer(ServerLevel level, UUID site, ItemStack ball) {}
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();

    private CraterRescueService() {}

    public static void init() {
        CraterRescueBattles.init(new CraterRescueBattles.Listener() {
            @Override public boolean active(ServerLevel level, UUID site, UUID pokemon) {
                var event = CraterRescueData.get(level).active.get(site);
                return event != null && event.active(pokemon);
            }
            @Override public boolean canEngage(ServerPlayer player, UUID site) {
                var event = CraterRescueData.get(player.serverLevel()).active.get(site);
                return event != null && !event.reconcileStorage && (event.assembled || player.getUUID().equals(event.trainer)) && event.mayClaim(player.getUUID())
                        && nearby(player, event.center, PARTICIPATION_RANGE);
            }
            @Override public boolean canContinue(ServerPlayer player, UUID site) {
                var event = CraterRescueData.get(player.serverLevel()).active.get(site);
                return event != null && player.getUUID().equals(event.trainer) && nearby(player, event.center, PARTICIPATION_RANGE);
            }
            @Override public boolean isolated(ServerLevel level, UUID site, UUID pokemon) {
                var event = CraterRescueData.get(level).active.get(site);
                return event != null && (event.howl() || event.hasDeparted(pokemon));
            }
            @Override public boolean carrier(ServerLevel level, UUID site, UUID pokemon) {
                var event = CraterRescueData.get(level).active.get(site);
                return event != null && event.outbreak() && event.carrier().id().equals(pokemon);
            }
            @Override public boolean completedOutbreak(ServerLevel level, UUID site) {
                var event = CraterRescueData.get(level).active.get(site);
                return event != null && event.outbreak() && event.objectiveComplete();
            }
            @Override public void engaged(ServerPlayer player, UUID site, Set<UUID> opponents) {
                var data = CraterRescueData.get(player.serverLevel());
                var event = data.active.get(site);
                if (event != null && event.engage(player.getUUID())) {
                    for (UUID id : opponents) {
                        var entity = CraterRescueActors.find(player.serverLevel(), id);
                        if (entity != null) entity.clearRestriction();
                    }
                    data.setDirty();
                }
            }
            @Override public void resolved(ServerLevel level, UUID site, UUID pokemon, UUID trainer) {
                var data = CraterRescueData.get(level);
                var event = data.active.get(site);
                if (event == null || !event.resolve(trainer, pokemon)) return;
                data.setDirty();
                var player = level.getServer().getPlayerList().getPlayer(trainer);
                if (event.rescue() && event.remaining() > 0 && player != null) tell(player, "remaining", event.remaining());
                if (event.howl() && pokemon.equals(event.caller().id())) event.notified.remove(trainer);
            }
        });
        TickEvent.SERVER_POST.register(CraterRescueService::tick);
        PlayerEvent.PLAYER_QUIT.register(player -> OFFERS.remove(player.getUUID()));
        PlayerEvent.CHANGE_DIMENSION.register((player, from, to) -> dismiss(player));
        LifecycleEvent.SERVER_STOPPED.register(server -> OFFERS.clear());
    }

    /** Called by loaded, ready cores. Newly encountered sites are decided exactly once. */
    public static void discover(ServerLevel level, UmbrafallSiteData.Site site) {
        if (!CraterRescueSites.eligible(site)) return;
        var data = CraterRescueData.get(level);
        if (data.closed.contains(site.id) || data.active.containsKey(site.id)) return;
        var discoverer = level.players().stream().filter(player -> nearby(player, site.center, APPROACH))
                .min(Comparator.comparingDouble(player -> player.blockPosition().distSqr(site.center))).orElse(null);
        if (discoverer == null || !CraterRescueSites.matchesGeneratedCore(site) || !CraterRescueSites.rimLoaded(level, site)) return;
        var config = ServerConfig.INSTANCE;
        var kind = CraterEventKind.roll(level.random, config.craterRescueChance(),
                config.craterRescueWeight(), config.craterOutbreakWeight(), config.craterHowlWeight());
        var event = kind == null ? null : create(level, site, kind, config, discoverer);
        if (event == null) {
            data.close(site.id);
            return;
        }
        data.active.put(site.id, event);
        data.setDirty();
    }

    private static CraterRescueEvent create(ServerLevel level, UmbrafallSiteData.Site site, CraterEventKind kind,
                                            ServerConfig config, ServerPlayer discoverer) {
        int castSize = switch (kind) {
            case RESCUE -> config.craterRescueAttackers() + 1;
            case OUTBREAK -> config.craterOutbreakPackSize();
            case HOWL -> 2;
        };
        var homes = CraterRescueSites.homes(level, site, castSize);
        if (homes.isEmpty()) return null;
        var cast = CraterFieldCast.select(level, homes.getFirst(), discoverer, kind);
        if (cast == null) return null;
        int specialIndex = kind == CraterEventKind.OUTBREAK ? level.random.nextInt(castSize) : castSize - 1;
        int baseLevel = site.tier == UmbrafallTier.WEAK ? 8 : 16;
        var actors = new ArrayList<CraterRescueEvent.Actor>();
        for (int i = 0; i < homes.size(); i++) {
            UUID pokemonId = UUID.randomUUID();
            var specimen = cast.create(i == specialIndex, baseLevel + level.random.nextInt(4), level, discoverer);
            var pokemon = CraterRescueActors.create(site.id, pokemonId, specimen, kind == CraterEventKind.RESCUE && i == specialIndex);
            if (kind == CraterEventKind.HOWL) CraterRescueActors.markHowl(pokemon);
            actors.add(new CraterRescueEvent.Actor(pokemonId, homes.get(i),
                    pokemon.saveToNBT(level.registryAccess(), new CompoundTag())));
        }
        return switch (kind) {
            case RESCUE -> new CraterRescueEvent(site.id, site.center, actors, config.craterRescueInterventionSeconds());
            case OUTBREAK -> CraterRescueEvent.outbreak(site.id, site.center, actors, specialIndex,
                    config.craterOutbreakScatterDistance(), config.craterOutbreakDustReward());
            case HOWL -> CraterRescueEvent.howl(site.id, site.center, actors, config.craterHowlScentReward());
        };
    }

    static @Nullable CraterRescueEvent howlCaller(ServerPlayer player, UUID callerId) {
        var level = player.serverLevel();
        var entity = CraterRescueActors.find(level, callerId);
        if (entity == null) return null;
        var event = CraterRescueData.get(level).active.get(CraterRescueActors.site(entity.getPokemon()));
        return event != null && event.howl() && event.caller().id().equals(callerId) && event.active(callerId)
                && player.getUUID().equals(event.trainer) && nearby(player, event.center, PARTICIPATION_RANGE) ? event : null;
    }

    static boolean howlWarning(ServerPlayer player, CraterRescueEvent event) {
        if (howlCaller(player, event.caller().id()) != event || event.howlState != CraterRescueEvent.HowlState.OPEN) return false;
        var entity = CraterRescueActors.find(player.serverLevel(), event.caller().id());
        entity.cry();
        tellEvent(player, event, "warning", entity.getPokemon().getSpecies().getTranslatedName());
        return true;
    }

    static boolean howlQuiet(ServerPlayer player, CraterRescueEvent event) {
        if (howlCaller(player, event.caller().id()) != event
                || !event.finishCall(player.getUUID(), CraterRescueEvent.HowlState.SILENCED)) return false;
        CraterRescueData.get(player.serverLevel()).setDirty();
        tellEvent(player, event, "quiet", name(player.serverLevel(), event.caller()));
        return true;
    }

    static boolean howlAnswer(ServerPlayer player, CraterRescueEvent event) {
        if (howlCaller(player, event.caller().id()) != event
                || !event.finishCall(player.getUUID(), CraterRescueEvent.HowlState.ANSWERED)) return false;
        CraterRescueData.get(player.serverLevel()).setDirty();
        var species = event.responder().restore(player.serverLevel()).getSpecies();
        var cry = species.getResourceIdentifier().withPath(path -> "pokemon." + path + ".cry");
        player.playNotifySound(SoundEvent.createVariableRangeEvent(cry), SoundSource.NEUTRAL, 0.8F, 1F);
        tellEvent(player, event, "answered", species.getTranslatedName());
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            var data = CraterRescueData.get(level);
            if (server.getTickCount() % 80 == 0) {
                for (ServerPlayer player : level.players()) {
                    if (!player.isSpectator() && !data.taught.contains(player.getUUID()) && CraterRescueSites.atFaintSite(player)) {
                        tell(player, "tutorial");
                        data.taught.add(player.getUUID());
                        data.setDirty();
                    }
                }
            }
            for (var event : List.copyOf(data.active.values())) {
                if (event.retryAt > level.getGameTime()) continue;
                try {
                    tickEvent(level, data, event);
                    event.retryAt = 0;
                } catch (RuntimeException error) {
                    if (event.retryAt == 0) Shadowedhearts.LOGGER.error("Crater encounter {} paused; its saved roster is retained", event.site, error);
                    event.retryAt = level.getGameTime() + 600;
                }
            }
        }
    }

    private static boolean nearby(ServerPlayer player, BlockPos center, int radius) {
        return player != null && player.isAlive() && !player.isSpectator() && center.closerToCenterThan(player.position(), radius);
    }

    private static void tickEvent(ServerLevel level, CraterRescueData data, CraterRescueEvent event) {
        if (event.reconcileStorage && event.trainer != null) {
            // Cobblemon storage and dimension saved data can be flushed at different moments.
            // Honor an already-delivered capture before recreating wild actors or releasing the claim.
            var storage = Cobblemon.INSTANCE.getStorage();
            var party = storage.getParty(event.trainer, level.registryAccess());
            var pc = storage.getPC(event.trainer, level.registryAccess());
            // The reserved responder cannot enter storage until its call has been answered.
            if (event.howl() && event.howlState == CraterRescueEvent.HowlState.OPEN
                    && (party.get(event.responder().id()) != null || pc.get(event.responder().id()) != null)) {
                event.howlState = CraterRescueEvent.HowlState.ANSWERED;
                data.setDirty();
            }
            for (var actor : event.actors) {
                if (party.get(actor.id()) == null && pc.get(actor.id()) == null) continue;
                if (event.rescue() && actor == event.victim() && event.objectiveComplete()) {
                    finish(level, data, event);
                    return;
                }
                if (event.resolve(event.trainer, actor.id())) data.setDirty();
            }
            event.reconcileStorage = false;
        }
        ServerPlayer trainer = event.trainer == null ? null : level.getServer().getPlayerList().getPlayer(event.trainer);
        boolean present = trainer != null && trainer.serverLevel() == level && nearby(trainer, event.center, PARTICIPATION_RANGE);
        if (event.trainer != null && !present) {
            if (trainer != null && offered(trainer, level, event.site)) dismiss(trainer);
            if (event.absentSince < 0) {
                event.absentSince = level.getGameTime();
                data.setDirty();
            }
            if (event.releaseIfAbsent(level.getGameTime(), AWAY_GRACE_TICKS)) {
                CraterRescueBattles.releaseClaim(level, event.site);
                event.notified.clear();
                level.players().stream().filter(p -> nearby(p, event.center, APPROACH)).forEach(p -> tellEvent(p, event, "available"));
                data.setDirty();
            }
        } else if (present && event.absentSince >= 0) {
            event.absentSince = -1;
            data.setDirty();
        }
        if (!event.rescue() && event.objectiveComplete()) {
            if (CraterRescueBattles.inProgress(level, event.site)) return;
            if (present && (event.howl() ? CraterRescueReward.howlScent(trainer, event.scentReward)
                    : CraterRescueReward.outbreakDust(trainer, event.dustReward))) {
                finish(level, data, event);
                tellEvent(trainer, event, event.howl() ? "completed" : "contained");
            } else {
                // The pack disperses even when its winner disconnects before receiving the reward.
                for (var actor : event.actors) {
                    if (CraterRescueActors.find(level, actor.id()) != null) CraterRescueActors.discard(level, actor.id());
                }
            }
            return;
        }
        if (!level.hasChunkAt(event.center) && (event.refuge == null || !level.hasChunkAt(event.refuge))) {
            event.notified.clear();
            return;
        }
        if (event.objectiveComplete() && !present) {
            // Retain the saved reward for its rescuer without leaving an invulnerable wild copy for other visitors.
            if (CraterRescueActors.find(level, event.victim().id()) != null) CraterRescueActors.discard(level, event.victim().id());
            return;
        }
        List<ServerPlayer> near = level.players().stream().filter(p -> nearby(p, event.center, PARTICIPATION_RANGE)).toList();
        event.notified.removeIf(id -> near.stream().noneMatch(p -> p.getUUID().equals(id)));
        if (near.isEmpty()) return;
        var site = UmbrafallSiteData.get(level).sites.get(event.site);
        if (!event.interiorHomes && event.refuge == null && site != null && site.hasValidTerrainProfile()
                && CraterRescueSites.rimLoaded(level, site) && !CraterRescueBattles.inProgress(level, event.site)
                && event.actors.stream().filter(a -> event.active(a.id())).allMatch(a -> CraterRescueSites.actorsLoaded(level, a.home()))) {
            // An older unsplit roster keeps the same Pokémon, credit and trainer when moving off the rim.
            var homes = CraterRescueSites.homes(level, site, event.actors.size());
            if (!homes.isEmpty()) {
                event.moveAssembly(homes);
                data.setDirty();
            }
        }
        event.assembled = assemble(level, event, site);
        if (event.objectiveComplete()) {
            if (present && BattleRegistry.getBattleByParticipatingPlayer(trainer) == null) offer(level, data, event, trainer);
            return;
        }
        if (!event.assembled) {
            // An oversized datapack species or an obstructed rim can prevent the full pack from standing
            // together. Retire an unstarted scene instead of leaving invulnerable, unchallengeable actors.
            if (event.trainer == null && event.refuge == null && event.remaining() == event.packSize()
                    && event.actors.stream().map(event::home).allMatch(home -> CraterRescueSites.actorsLoaded(level, home))
                    && ++event.failedAssemblies >= 15) finish(level, data, event);
            return;
        }
        event.failedAssemblies = 0;
        var watching = near.stream().filter(p -> nearby(p, event.center, APPROACH)).toList();
        for (ServerPlayer player : watching) {
            if (!event.notified.add(player.getUUID())) continue;
            if (event.trainer != null && !event.trainer.equals(player.getUUID())) tellEvent(player, event, "claimed");
            else if (event.outbreak()) {
                tellEvent(player, event, "found", event.packSize());
                if (event.refuge != null) tellEvent(player, event, "split");
            } else if (event.howl()) {
                if (event.active(event.caller().id())) tellEvent(player, event, "found", name(level, event.caller()));
                else if (!CraterRescueBattles.inProgress(level, event.site))
                    tellEvent(player, event, "waiting", name(level, event.responder()));
                else event.notified.remove(player.getUUID());
            } else if (event.remaining() < event.packSize()) tell(player, "remaining", event.remaining());
            else tell(player, "found", name(level, event.actors.getFirst()), name(level, event.victim()), event.packSize());
        }
        if (event.outbreak()) {
            if (event.refuge != null && (!watching.isEmpty() || present) && level.getServer().getTickCount() % 60 == 0)
                OutbreakTrail.showDeparture(level, event.carrier().home(), event.refuge);
            return;
        }
        if (event.rescue() && event.trainer == null && !watching.isEmpty() && event.interventionSeconds > 0) {
            event.interventionSeconds--;
            data.setDirty();
            if (event.interventionSeconds == 30) watching.forEach(p -> tell(p, "warning"));
            if (event.interventionSeconds == 0) {
                watching.forEach(p -> tell(p, "expired"));
                finish(level, data, event);
            }
        }
    }

    /** Called by loaded Pokémon, so a trainer crossing the approach distance can see the departure immediately. */
    public static void tickActor(PokemonEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        var pokemon = entity.getPokemon();
        var site = CraterRescueActors.site(pokemon);
        if (site == null || pokemon.isPlayerOwned() || !entity.getUUID().equals(pokemon.getUuid())) return;
        var data = CraterRescueData.get(level);
        var event = data.active.get(site);
        if (event == null || event.reconcileStorage || !event.active(pokemon.getUuid())) return;
        var actor = event.actor(pokemon.getUuid());
        if (entity.tickCount % 5 == 0) {
            if (event.travelling(actor.id())) {
                BlockPos settled = OutbreakTrail.continueTo(level, entity, event.home(actor));
                if (settled != null && event.center.closerThan(settled, PARTICIPATION_RANGE)) {
                    event.settle(actor, settled);
                    data.setDirty();
                }
            } else {
                CraterRescueActors.maintain(level, event.home(actor), entity);
            }
        }
        if (!event.outbreak() || event.refuge != null || !event.assembled || event.objectiveComplete()
                || level.getGameTime() < event.retryRefugeAt || entity.isBusy() || entity.isBattling()) return;
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && (event.trainer == null || player.getUUID().equals(event.trainer))
                    && entity.getBoundingBox().distanceToSqr(player.getEyePosition()) <= event.scatterDistance * event.scatterDistance
                    && player.hasLineOfSight(entity) && BattleRegistry.getBattleByParticipatingPlayer(player) == null) {
                beginScatter(level, data, event);
                return;
            }
        }
    }

    private static void beginScatter(ServerLevel level, CraterRescueData data, CraterRescueEvent event) {
        var issued = event.carrier();
        if (CraterRescueBattles.busy(issued.id())) return;
        var carrier = CraterRescueActors.find(level, issued.id());
        if (carrier == null) return;
        BlockPos refuge = OutbreakTrail.breakAway(level, event.center, issued.home(), carrier);
        if (refuge == null) {
            event.retryRefugeAt = level.getGameTime() + (carrier.tickCount < 20 ? 1 : 100);
            return;
        }
        event.beginTravel(issued, refuge);
        data.setDirty();
        List<BlockPos> reserved = new ArrayList<>();
        int ordinal = 0;
        for (var actor : event.actors) {
            if (actor == issued) continue;
            var escort = CraterRescueActors.find(level, actor.id());
            if (escort != null && event.active(actor.id())) {
                BlockPos landing = OutbreakTrail.scatterEscort(level, event.center, escort, ordinal,
                        event.packSize() - 1, issued.home(), refuge, reserved);
                if (landing != null) {
                    event.beginTravel(actor, landing);
                    reserved.add(landing);
                }
            }
            ordinal++;
        }
        OutbreakTrail.showDeparture(level, issued.home(), refuge);
        for (ServerPlayer player : level.players()) {
            if (nearby(player, event.center, PARTICIPATION_RANGE)
                    && (event.trainer == null || event.trainer.equals(player.getUUID()))) tellEvent(player, event, "split");
        }
    }

    private static boolean assemble(ServerLevel level, CraterRescueEvent event, UmbrafallSiteData.Site site) {
        boolean complete = true;
        for (var actor : event.actors) {
            if (CraterRescueBattles.busy(actor.id())) continue;
            // A native SOS ally belongs to its live fight. Outside that fight the saved ally
            // waits offstage until the caller has been caught or defeated and its battle has closed.
            if (!event.active(actor.id()) || (event.howl() && actor == event.responder()
                    && (event.active(event.caller().id()) || CraterRescueBattles.inProgress(level, event.site)))) {
                if (CraterRescueActors.find(level, actor.id()) != null) CraterRescueActors.discard(level, actor.id());
                continue;
            }
            BlockPos home = event.home(actor);
            var entity = CraterRescueActors.find(level, actor.id());
            if (entity != null) continue;
            // After a split the carrier is a standalone objective. An obstructed or unloaded
            // escort must not prevent a trainer at the refuge from spotting or challenging it.
            boolean required = event.refuge == null || event.isolated(actor.id());
            // Wait for persisted entities along a journey to load before creating another copy.
            if (!CraterRescueSites.actorsLoaded(level, home)
                    || (event.hasDeparted(actor.id()) && !routeLoaded(level, actor.home(), home))) {
                if (required) complete = false;
                continue;
            }
            boolean startingInside = !event.hasDeparted(actor.id()) || event.travelling(actor.id());
            Predicate<BlockPos> allowed = startingInside && event.interiorHomes
                    ? feet -> CraterRescueSites.insideEncounter(level, site, feet) : feet -> true;
            BlockPos spawnHome = event.travelling(actor.id()) ? actor.home() : home;
            if (!spawnNearHome(level, actor, spawnHome, allowed) && required) complete = false;
        }
        return complete;
    }

    private static boolean routeLoaded(ServerLevel level, BlockPos from, BlockPos to) {
        int minX = (Math.min(from.getX(), to.getX()) - 8) >> 4, maxX = (Math.max(from.getX(), to.getX()) + 8) >> 4;
        int minZ = (Math.min(from.getZ(), to.getZ()) - 8) >> 4, maxZ = (Math.max(from.getZ(), to.getZ()) + 8) >> 4;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x, z))) return false;
        return true;
    }

    private static boolean spawnNearHome(ServerLevel level, CraterRescueEvent.Actor actor, BlockPos home, Predicate<BlockPos> allowed) {
        Pokemon pokemon = null;
        for (BlockPos column : BlockPos.withinManhattan(home, 2, 0, 2)) {
            BlockPos feet = CraterRescueSites.safeSurface(level, column);
            if (feet == null || Math.abs(feet.getY() - home.getY()) > 8 || !allowed.test(feet)) continue;
            if (pokemon == null) pokemon = actor.restore(level);
            if (CraterRescueActors.ensure(level, feet, pokemon, allowed) != null) return true;
        }
        return false;
    }

    private static Component name(ServerLevel level, CraterRescueEvent.Actor actor) {
        return actor.restore(level).getSpecies().getTranslatedName();
    }

    private static void offer(ServerLevel level, CraterRescueData data, CraterRescueEvent event, ServerPlayer player) {
        if (OFFERS.containsKey(player.getUUID()) || event.nextOfferAt > level.getGameTime()) return;
        UUID id = event.victim().id();
        var storage = Cobblemon.INSTANCE.getStorage();
        if (storage.getParty(player).get(id) != null || storage.getPC(player).get(id) != null) {
            finish(level, data, event);
            return;
        }
        var ball = CraterRescueReward.offeredBall(player);
        if (ball.isEmpty()) {
            farewell(level, data, event, player);
            return;
        }
        OFFERS.put(player.getUUID(), new Offer(level, event.site, ball));
        ShadowedHeartsNetwork.sendToPlayer(player, new CraterRescueOfferPacket(event.site, name(level, event.victim()), ball));
    }

    public static void respond(ServerPlayer player, UUID site, boolean accept, ResourceLocation ballId) {
        var level = player.serverLevel();
        var data = CraterRescueData.get(level);
        var event = data.active.get(site);
        var offer = OFFERS.get(player.getUUID());
        if (event == null || !event.rescue() || !offered(player, level, site) || !player.getUUID().equals(event.trainer)
                || event.remaining() != 0 || !BuiltInRegistries.ITEM.getKey(offer.ball.getItem()).equals(ballId)) return;
        OFFERS.remove(player.getUUID());
        if (!nearby(player, event.center, PARTICIPATION_RANGE) || BattleRegistry.getBattleByParticipatingPlayer(player) != null) return;
        if (!accept) {
            farewell(level, data, event, player);
            return;
        }
        var victim = event.victim().restore(level);
        switch (CraterRescueReward.welcome(player, victim, offer.ball)) {
            case JOINED -> {
                finish(level, data, event);
                tell(player, "joined", victim.getSpecies().getTranslatedName());
            }
            case BALL_MISSING -> offer(level, data, event, player);
            case STORAGE_FULL -> {
                event.nextOfferAt = level.getGameTime() + 20 * 30;
                tell(player, "storage_full", victim.getSpecies().getTranslatedName());
            }
        }
    }

    private static boolean offered(ServerPlayer player, ServerLevel level, UUID site) {
        var offer = OFFERS.get(player.getUUID());
        return offer != null && offer.level == level && offer.site.equals(site);
    }

    private static void dismiss(ServerPlayer player) {
        var offer = OFFERS.remove(player.getUUID());
        if (offer != null) ShadowedHeartsNetwork.sendToPlayer(player, CraterRescueOfferPacket.dismiss(offer.site));
    }

    private static void farewell(ServerLevel level, CraterRescueData data, CraterRescueEvent event, ServerPlayer player) {
        if (!CraterRescueReward.berries(player)) return;
        finish(level, data, event);
        tell(player, "farewell", name(level, event.victim()));
    }

    private static void finish(ServerLevel level, CraterRescueData data, CraterRescueEvent event) {
        data.close(event.site);
        for (var actor : event.actors) CraterRescueActors.discard(level, actor.id());
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.shadowedhearts.crater_rescue." + key, args));
    }

    private static void tellEvent(ServerPlayer player, CraterRescueEvent event, String key, Object... args) {
        String prefix = switch (event.kind) {
            case OUTBREAK -> "message.shadowedhearts.crater_outbreak.";
            case RESCUE -> "message.shadowedhearts.crater_rescue.";
            case HOWL -> "message.shadowedhearts.crater_howl.";
        };
        player.sendSystemMessage(Component.translatable(prefix + key, args));
    }
}
