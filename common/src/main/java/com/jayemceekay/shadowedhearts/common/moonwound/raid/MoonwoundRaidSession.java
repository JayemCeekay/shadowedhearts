package com.jayemceekay.shadowedhearts.common.moonwound.raid;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundClaimProtection;
import com.jayemceekay.shadowedhearts.mixin.raiddens.MoonwoundRaidHealthAccessor;
import com.necro.raid.dens.common.data.raid.RaidBattleFormat;
import com.necro.raid.dens.common.network.RaidDenNetworkMessages;
import com.necro.raid.dens.common.party.RaidStoreFactory;
import com.necro.raid.dens.common.raids.RaidInstance;
import com.necro.raid.dens.common.raids.RaidSession;
import com.necro.raid.dens.common.raids.RaidState;
import com.necro.raid.dens.common.raids.helpers.RaidHelper;
import com.necro.raid.dens.common.raids.helpers.RaidJoinHelper;
import com.necro.raid.dens.common.raids.scripts.RaidTriggerType;
import com.necro.raid.dens.common.showdown.events.AbstractEvent;
import com.necro.raid.dens.common.showdown.events.RaidContext;
import com.necro.raid.dens.common.util.IRaidBattle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** A single overworld encounter whose boss and roster are owned by the Moonwound event. */
public final class MoonwoundRaidSession extends RaidSession {
    private final RaidDensMoonwoundAdapter.Attempt attempt;
    private final Set<UUID> joined = new HashSet<>();
    private final Encounter encounter;
    private boolean resolving;
    private boolean closed;
    private UUID withdrawing;
    private @Nullable RaidContext sharedHealthContext;

    MoonwoundRaidSession(RaidDensMoonwoundAdapter.Attempt attempt) {
        super(null, attempt.event.raidId(), attempt.definition, attempt.origin, attempt.level);
        this.attempt = attempt;
        this.encounter = new Encounter();
        setActiveRaid(encounter);
    }

    @Override public MinecraftServer getServer() {
        return attempt.level.getServer();
    }

    @Override public void advanceToNextNode(@Nullable MinecraftServer server) {
        // The controller already spawned the frozen boss. Starting an adventure node would spawn a second one.
        if (getActive() == null && !isFinished()) stopRaid(true);
    }

    @Override public boolean isFinished() {
        return closed || super.isFinished();
    }

    @Override public boolean isPlayerIn(ServerPlayer player) {
        return !isFinished() && attempt.event.isActiveMember(player.getUUID()) && super.isPlayerIn(player);
    }

    @Override public void addPlayer(ServerPlayer player) {
        if (!RaidDensMoonwoundAdapter.eligible(player, attempt.entity) || hasFailed(player)) return;
        if (!RaidJoinHelper.isParticipating(player, getRaid())
                && !RaidJoinHelper.addParticipant(player, getRaid(), false, false)) return;
        if (activePlayers().contains(player)) return;
        if (joined.add(player.getUUID())) {
            super.addPlayer(player);
        } else {
            // Reconnection must retain the prior damage, remaining lives, and shared health fraction.
            activePlayers().removeIf(previous -> {
                if (!previous.getUUID().equals(player.getUUID())) return false;
                encounter.removeFromBossEvent(previous);
                return true;
            });
            activePlayers().add(player);
            encounter.addToBossEvent(player);
        }
    }

    @Override public void removePlayer(@Nullable ServerPlayer player, @Nullable PokemonBattle battle, boolean ignoreLives) {
        if (closed || resolving || battle == null || !encounter.getBattles().contains(battle)) return;
        // The whole-battle defeat callback has no player. ABA can replace actors after Cobblemon caches getPlayers().
        List<ServerPlayer> players = player == null
                ? activePlayers().stream().filter(member -> battle.getActor(member.getUUID()) != null).toList()
                : List.of(player);
        if (battle.getEnded()) {
            encounter.getBattles().remove(battle);
            ((IRaidBattle) battle).crd_setRaidSession(null);
        }
        for (ServerPlayer member : players) {
            if (closed || !activePlayers().contains(member)) continue;
            if (ignoreLives || member.hasDisconnected()) withdraw(member.getUUID());
            else encounter.removePlayer(member, battle.getEnded() ? null : battle, false);
        }
    }

    /** The native detach/party reset also handles ABA's shared battle actor slots. Skip its faint penalty on leave. */
    public boolean isWithdrawing(ServerPlayer player) {
        return player.getUUID().equals(withdrawing);
    }

    public boolean isUncreditedHealthChange() {
        return sharedHealthContext != null && sharedHealthContext.player() == null;
    }

    public void syncSharedDamage(PokemonBattle battle, @Nullable BattlePokemon source, float damage) {
        if (source != null && source.getActor() instanceof PlayerBattleActor actor) {
            ServerPlayer player = actor.getEntity();
            if (player != null && !player.hasDisconnected() && activePlayers().contains(player)
                    && battle.getActor(player) == actor && RaidDensMoonwoundAdapter.eligible(player, attempt.entity)) {
                encounter.syncHealth(player, battle, damage);
                return;
            }
        }
        encounter.syncUncreditedHealth(battle, damage);
    }

    void withdraw(UUID id) {
        for (ServerPlayer player : List.copyOf(activePlayers())) {
            if (!player.getUUID().equals(id)) continue;
            activePlayers().remove(player);
            // A member can rejoin after a native leave. Abandoning the Moonwound itself forfeits this attempt.
            withdrawing = attempt.event.isActiveMember(id) ? id : null;
            try {
                for (PokemonBattle battle : List.copyOf(encounter.getBattles())) {
                    if (battle.getActor(id) != null) encounter.removePlayer(player, battle, true);
                }
                encounter.removeFromBossEvent(player);
            } finally {
                withdrawing = null;
                if (RaidJoinHelper.isParticipating(player, getRaid())) {
                    RaidJoinHelper.removeParticipant(player);
                    RaidStoreFactory.removePlayerParty(player);
                }
            }
        }
    }

    void refreshPlayers() {
        for (ServerPlayer player : List.copyOf(activePlayers())) {
            ServerPlayer online = getServer().getPlayerList().getPlayer(player.getUUID());
            if (online != player || !attempt.event.isActiveMember(player.getUUID()) || player.level() != attempt.level
                    || !MoonwoundClaimProtection.canInteractEncounter(player, attempt.event.eventId(), attempt.entity.getBoundingBox())) {
                withdraw(player.getUUID());
            }
        }
    }

    @Override public void stopRaid(boolean success) {
        if (!closed) encounter.stopInstance(success);
    }

    @Override public void closeRaid(MinecraftServer server, boolean cancelled) {
        if (closed) return;
        closed = true;
        try {
            List<Integer> entityIds = new ArrayList<>();
            for (PokemonBattle battle : encounter.getBattles()) {
                for (var actor : battle.getActors()) for (var pokemon : actor.getPokemonList()) {
                    if (pokemon.getEntity() != null) entityIds.add(pokemon.getEntity().getId());
                }
            }
            activePlayers().forEach(player -> RaidDenNetworkMessages.RAID_HEALTH_BAR.accept(player, entityIds, false));
            for (PokemonBattle battle : List.copyOf(encounter.getBattles())) {
                ((IRaidBattle) battle).crd_setRaidSession(null);
                if (!battle.getEnded()) battle.stop();
            }
            encounter.getBattles().clear();
            encounter.closeInstance();
            // Native cleanup removes parties and participant bindings by UUID. Only give it our own.
            activePlayers().removeIf(player -> !RaidJoinHelper.isParticipating(player, getRaid()));
            if (RaidHelper.ACTIVE_RAIDS.get(getRaid()) == this) super.closeRaid(server, cancelled);
        } finally {
            activePlayers().clear();
            RaidDensMoonwoundAdapter.closed(attempt);
        }
    }

    private final class Encounter extends RaidInstance {
        Encounter() {
            super(MoonwoundRaidSession.this, attempt.entity, true, true);
        }

        @Override public void addPlayer(ServerPlayer player) {
            if (!activePlayers().contains(player)) return;
            float ratio = getCurrentHealth() / getMaxHealth();
            super.addPlayer(player);
            // Keep the first player at base HP and count each subsequent player once, including after departures.
            float multiplier = boss().getMultiplayerHealthMulti();
            if (multiplier > 1F) {
                MoonwoundRaidHealthAccessor health = (MoonwoundRaidHealthAccessor) (Object) this;
                float maximum = health.shadowedhearts$initialHealth() * (1F + (multiplier - 1F) * (joined.size() - 1));
                health.shadowedhearts$maxHealth(maximum);
                health.shadowedhearts$currentHealth(maximum * ratio);
            }
        }

        @Override public void addBattle(ServerPlayer player, PokemonBattle battle) {
            if (!activePlayers().contains(player) || !RaidDensMoonwoundAdapter.eligible(player, attempt.entity)
                    || !RaidJoinHelper.isParticipating(player, getRaid())) {
                if (!getBattles().contains(battle) && !battle.getEnded()) battle.stop();
                return;
            }
            super.addBattle(player, battle);
        }

        @Override public void syncHealth(ServerPlayer player, PokemonBattle battle, float damage) {
            if (damage < 0 && boss().getBattleFormat() == RaidBattleFormat.RAID) {
                syncUncreditedHealth(battle, damage);
                return;
            }
            if (!closed && getBattles().contains(battle) && activePlayers().contains(player)
                    && RaidDensMoonwoundAdapter.eligible(player, attempt.entity) && Float.isFinite(damage)) {
                if (boss().getBattleFormat() == RaidBattleFormat.RAID) syncSharedHealth(player, player, battle, damage);
                else super.syncHealth(player, battle, damage);
            }
        }

        private void syncUncreditedHealth(PokemonBattle battle, float damage) {
            if (closed || !getBattles().contains(battle) || !Float.isFinite(damage)) return;
            // Raid Dens requires a participant to synchronize the boss HP even for weather, recoil, and healing.
            for (ServerPlayer player : activePlayers()) {
                if (player.hasDisconnected() || battle.getActor(player) == null || !RaidDensMoonwoundAdapter.eligible(player, attempt.entity)) continue;
                syncSharedHealth(player, null, battle, damage);
                return;
            }
        }

        private void syncSharedHealth(ServerPlayer participant, @Nullable ServerPlayer source, PokemonBattle battle, float damage) {
            RaidContext previous = sharedHealthContext;
            // The native context caches the original host as the player for every damage-triggered event.
            sharedHealthContext = new RaidContext(this, battle) {
                @Override public @Nullable ServerPlayer player() { return source; }
            };
            try { super.syncHealth(participant, battle, damage); }
            finally { sharedHealthContext = previous; }
        }

        @Override public void sendEvent(AbstractEvent event, @Nullable PokemonBattle battle) {
            if (sharedHealthContext == null || sharedHealthContext.battle() != battle) super.sendEvent(event, battle);
            else if (!isFinished()) event.execute(sharedHealthContext);
        }

        @Override public <T> void runScripts(RaidTriggerType type, @Nullable PokemonBattle battle, Supplier<T> predicate) {
            if (isUncreditedHealthChange() && type == RaidTriggerType.DAMAGE) return;
            super.runScripts(type, battle, predicate);
        }

        @Override public void stopInstance(boolean success) {
            if (closed || resolving) return;
            resolving = true;
            try {
                if (!MoonwoundClaimProtection.canRun(attempt.level, attempt.site, attempt.event)
                        || !MoonwoundClaimProtection.canSpawn(attempt.level, attempt.event.eventId(),
                        attempt.entity.getBoundingBox().expandTowards(0, -1, 0))) return;
                refreshPlayers();
                if (activePlayers().isEmpty() || !RaidDensMoonwoundAdapter.active(attempt.event)) return;
                boolean won = success && getCurrentHealth() <= 0F;
                try {
                    super.stopInstance(won);
                } finally {
                    // Native success is published before rewards. A failing reward listener must not reopen the raid.
                    if (won && getRaidState() == RaidState.SUCCESS) attempt.event.completeRaid(getRaid(), true);
                    else RaidDensMoonwoundAdapter.notifyParty(attempt, Component.translatable("message.shadowedhearts.moonwound.raid_retry_available"));
                }
                finishSession(won);
            } finally {
                closeRaid(getServer(), true);
            }
        }
    }

    private void finishSession(boolean won) {
        super.stopRaid(won);
    }
}
