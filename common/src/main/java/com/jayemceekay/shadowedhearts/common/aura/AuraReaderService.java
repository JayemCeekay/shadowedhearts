package com.jayemceekay.shadowedhearts.common.aura;

import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderStateS2CPacket;
import com.jayemceekay.shadowedhearts.registry.util.ModItemComponents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AuraReaderService {
    private static final ConcurrentMap<UUID, AuraReaderSession> SESSIONS = new ConcurrentHashMap<>();
    private static final List<AuraReadingProvider> PROVIDERS = new ArrayList<>();
    private static boolean defaultProvidersRegistered;

    private AuraReaderService() {
    }

    public static void init() {
        if (defaultProvidersRegistered) {
            return;
        }
        defaultProvidersRegistered = true;
        registerProvider(new ShadowPokemonReadingProvider());
    }

    public static AuraReaderSession session(ServerPlayer player) {
        AuraReaderSession session = SESSIONS.computeIfAbsent(player.getUUID(), AuraReaderSession::new);
        AuraReaderPlayerState.setMode(player, AuraReaderMode.PASSIVE_AURA);
        session.setMode(AuraReaderMode.PASSIVE_AURA);
        session.setActive(AuraReaderPlayerState.isActive(player));
        session.setSelectedReadingId(AuraReaderPlayerState.getSelectedSignal(player));
        return session;
    }

    /** Drop transient discoveries, pins and cooldown without changing saved navigation preferences. */
    public static void removeSession(ServerPlayer player) {
        AuraReaderSession removed = SESSIONS.remove(player.getUUID());
        clearPokemonSelection(player, removed);
        AuraLockManager.INSTANCE.clearPlayer(player.getUUID());
        if (AuraReaderPlayerState.getLockType(player) == AuraReaderLockType.ENTITY) {
            AuraReaderPlayerState.clearLock(player);
        }
        if (removed != null) {
            AuraReaderPlayerState.setTracking(player, removed.isExplicitTracking()
                    || !AuraReaderPlayerState.getSelectedSignal(player).isBlank()
                    || AuraReaderPlayerState.getLockType(player) != AuraReaderLockType.NONE);
        }
    }

    /** Observe transitions even while inactive; site navigation and pulse cooldown are independent. */
    public static void invalidateContacts(ServerPlayer player) {
        AuraReaderSession session = SESSIONS.get(player.getUUID());
        clearPokemonSelection(player, session);
        AuraLockManager.INSTANCE.clearPlayer(player.getUUID());
        if (AuraReaderPlayerState.getLockType(player) == AuraReaderLockType.ENTITY) AuraReaderPlayerState.clearLock(player);
        if (session != null) {
            session.contacts().clear();
            session.replaceCachedReadings(List.of());
        }
        restoreTracking(player);
        sendState(player);
    }

    private static void clearPokemonSelection(ServerPlayer player, AuraReaderSession session) {
        String selected = AuraReaderPlayerState.getSelectedSignal(player);
        if ((session != null && session.isPokemonSelection(selected))
                || (!selected.isBlank() && AuraReaderPlayerState.getLockType(player) == AuraReaderLockType.ENTITY
                && selected.equals(AuraReaderPlayerState.getLockedTarget(player)))) {
            AuraReaderPlayerState.clearSelectedSignal(player);
            if (session != null) session.setSelectedReadingId("");
        }
    }

    public static void resetSessions() {
        SESSIONS.clear();
        AuraLockManager.INSTANCE.clearAll();
    }

    public static void registerProvider(AuraReadingProvider provider) {
        if (provider == null || PROVIDERS.contains(provider)) {
            return;
        }
        PROVIDERS.add(provider);
        PROVIDERS.sort(Comparator.comparingInt(AuraReadingProvider::priority).reversed());
    }

    public static List<AuraReadingProvider> providers() {
        return List.copyOf(PROVIDERS);
    }

    /** Battle detection permits actions without changing the equipped item's manual toggle. */
    public static boolean isActiveForActions(ServerPlayer player) {
        return AuraReaderPlayerState.isActive(player) || AuraReaderBattleDetection.isDetecting(player);
    }

    public static void setActive(ServerPlayer player, ItemStack auraReader, boolean active) {
        boolean allowed = !auraReader.isEmpty();
        boolean nextActive = active && allowed;

        AuraReaderPlayerState.setActive(player, nextActive);
        session(player).setActive(nextActive);

        if (!auraReader.isEmpty()) {
            auraReader.set(ModItemComponents.AURA_SCANNER_ACTIVE.get(), nextActive);
        }
        sendState(player);
    }

    public static void setTracking(ServerPlayer player, ItemStack auraReader, boolean tracking) {
        boolean nextTracking = tracking && !auraReader.isEmpty();
        session(player).setExplicitTracking(nextTracking);
        if (!nextTracking) {
            AuraLockManager.INSTANCE.clearPlayer(player.getUUID());
            session(player).contacts().clearPin();
        }
        AuraReaderPlayerState.setTracking(player, nextTracking);
        if (!auraReader.isEmpty()) {
            auraReader.set(ModItemComponents.AURA_SCANNER_TRACKING.get(), nextTracking);
        }
        sendState(player);
    }

    public static void setMode(ServerPlayer player, AuraReaderMode mode) {
        // Legacy clients may still send this packet. Modes no longer gate Reader mechanics.
        AuraReaderPlayerState.setMode(player, AuraReaderMode.PASSIVE_AURA);
        session(player).setMode(AuraReaderMode.PASSIVE_AURA);
        sendState(player);
    }

    public static void syncActiveFromStack(ServerPlayer player, ItemStack auraReader) {
        boolean active = false;
        if (!auraReader.isEmpty()) {
            active = Boolean.TRUE.equals(auraReader.get(ModItemComponents.AURA_SCANNER_ACTIVE.get()));
        }

        AuraReaderPlayerState.setActive(player, active);
        session(player).setActive(active);
    }

    public static List<AuraReading> pulse(AuraScanContext context) {
        List<AuraReading> readings = new ArrayList<>();
        for (AuraReadingProvider provider : PROVIDERS) {
            if (provider.supports(context)) {
                readings.addAll(provider.scan(context));
            }
        }
        return postProcess(context.player(), readings);
    }

    private static List<AuraReading> postProcess(ServerPlayer player, List<AuraReading> readings) {
        String lockedTarget = AuraReaderPlayerState.getLockedTarget(player);
        String selectedSignal = AuraReaderPlayerState.getSelectedSignal(player);

        return readings.stream().map(r -> {
            boolean locked = r.id().equals(lockedTarget);
            boolean selected = r.selected() || r.id().equals(selectedSignal);
            if (locked || selected) {
                return new AuraReading(
                        r.id(), r.type(), r.source(), r.labelText(), r.statusText(), r.dimension(),
                        r.directionX(), r.directionY(), r.directionZ(), r.bearingUncertainty(),
                        r.distanceBand(), r.verticalHint(), r.strength(), r.confidence(),
                        r.interference(), r.priority(), r.expiryTick(), selected, locked,
                        r.outOfDimension(), r.exactPositionAllowed(), r.heartGauge()
                );
            }
            return r;
        }).toList();
    }

    public static List<AuraReading> targetedScan(ServerPlayer player, ItemStack auraReader) {
        if (!player.isAlive() || auraReader.isEmpty() || !isActiveForActions(player)) {
            return List.of();
        }

        AuraScanContext context = new AuraScanContext(
                player,
                player.serverLevel(),
                auraReader,
                AuraReaderPlayerState.getMode(player),
                player.serverLevel().getGameTime(),
                false
        );
        List<AuraReading> readings = pulse(context);
        reconcileTracking(player, readings);
        session(player).replaceCachedReadings(readings);
        return readings;
    }

    public static AuraReaderPulseResult pulse(ServerPlayer player, ItemStack auraReader) {
        if (!player.isAlive() || auraReader.isEmpty() || !isActiveForActions(player)) {
            sendState(player);
            return AuraReaderPulseResult.rejected();
        }

        long gameTime = player.serverLevel().getGameTime();
        AuraReaderSession session = session(player);
        if (session.getPulseCooldownEndTick() > gameTime) {
            sendState(player);
            return AuraReaderPulseResult.rejected();
        }

        int cooldownTicks = Math.max(0, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraReaderPulseCooldownTicks());
        session.setLastPulseTick(gameTime);
        session.setPulseCooldownEndTick(gameTime + cooldownTicks);

        AuraScanContext context = new AuraScanContext(
                player,
                player.serverLevel(),
                auraReader,
                AuraReaderMode.PASSIVE_AURA,
                gameTime,
                true
        );
        List<AuraReading> readings = pulse(context);
        session.replaceCachedReadings(readings);
        ModCriteriaTriggers.triggerProgression(player, "reader_pulse");
        sendState(player);
        return AuraReaderPulseResult.accepted(readings);
    }

    public static boolean lockFocusedTarget(ServerPlayer player, ItemStack auraReader) {
        if (!player.isAlive() || auraReader.isEmpty() || !isActiveForActions(player)) {
            sendState(player);
            return false;
        }

        int range = Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange());
        Entity target = AuraReaderTargeting.pickEntity(player.serverLevel(), player, 1.0f, range);
        if (!(target instanceof PokemonEntity pokemonEntity)
                || !isUsableContact(player, pokemonEntity, range)) {
            sendState(player);
            return false;
        }

        AuraReaderContacts contacts = contacts(player);
        if (AuraReaderPlayerState.getLockType(player) == AuraReaderLockType.ENTITY) {
            AuraLockManager.INSTANCE.clearPlayer(player.getUUID());
            AuraReaderPlayerState.clearLock(player);
        }
        contacts.pinVisible(pokemonEntity.getUUID());
        // Pinning an owned/NPC contact never grants a wild gameplay lock.
        if (isLockablePokemon(player, pokemonEntity)) {
            int durationTicks = Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraLockMaxSeconds() * 20);
            AuraLockManager.INSTANCE.applyLock(pokemonEntity, player.getUUID(), player.serverLevel().getGameTime(), durationTicks);
            AuraReaderPlayerState.lockEntity(player, pokemonEntity.getUUID(), player.serverLevel().dimension().location());
            ModCriteriaTriggers.triggerProgression(player, "shadow_locked");
        }
        restoreTracking(player);
        sendState(player);
        return true;
    }

    public static boolean pinSelectedContact(ServerPlayer player, ItemStack auraReader) {
        if (!player.isAlive() || auraReader.isEmpty() || !isActiveForActions(player)) return false;
        // Resolve/prune only authorized contacts before accepting the selection.
        targetedScan(player, auraReader);
        UUID selected;
        try {
            selected = UUID.fromString(AuraReaderPlayerState.getSelectedSignal(player));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        int range = Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange());
        Entity entity = player.serverLevel().getEntity(selected);
        if (!(entity instanceof PokemonEntity pokemonEntity) || !isUsableContact(player, pokemonEntity, range)
                || !contacts(player).pinDiscovered(selected, player.serverLevel().getGameTime())) {
            return false;
        }
        restoreTracking(player);
        sendState(player);
        return true;
    }

    public static void clearPinnedContact(ServerPlayer player, ItemStack auraReader) {
        session(player).contacts().clearPin();
        restoreTracking(player);
        sendState(player);
    }

    public static void cycleSelectedSignal(ServerPlayer player, ItemStack auraReader, boolean previous) {
        if (!player.isAlive() || auraReader.isEmpty() || !isActiveForActions(player)) return;
        AuraReaderContacts contacts = contacts(player);
        long nowTick = player.serverLevel().getGameTime();
        List<String> ids = targetedScan(player, auraReader).stream()
                .filter(reading -> reading.type() == AuraReadingType.UMBRAFALL_SIGNAL
                        || reading.type() == AuraReadingType.SHADOW_POKEMON && authorizedContact(contacts, reading.id(), nowTick))
                .sorted(Comparator.comparingInt(AuraReading::priority).reversed().thenComparing(AuraReading::id))
                .map(AuraReading::id).toList();
        String selected = AuraReaderSignalSelection.cycle(ids, AuraReaderPlayerState.getSelectedSignal(player), previous);
        AuraReaderPlayerState.setSelectedSignal(player, selected);
        session(player).setSelectedReadingId(selected);
        restoreTracking(player);
        sendState(player);
    }

    private static boolean authorizedContact(AuraReaderContacts contacts, String id, long nowTick) {
        try {
            return contacts.isAuthorized(UUID.fromString(id), nowTick);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static AuraReaderContacts contacts(ServerPlayer player) {
        AuraReaderContacts contacts = session(player).contacts();
        contacts.observeDimension(player.serverLevel().dimension().location().toString());
        return contacts;
    }

    private static boolean isUsableContact(ServerPlayer player, PokemonEntity entity, int range) {
        return AuraReaderContacts.usable(entity.isAlive() && !entity.isRemoved(),
                ShadowAspectUtil.hasShadowAspect(entity.getPokemon()), AuraReaderTargeting.distanceToHitboxSqr(player, entity), range);
    }

    private static void restoreTracking(ServerPlayer player) {
        AuraReaderSession session = session(player);
        AuraReaderPlayerState.setTracking(player, session.isExplicitTracking() || session.contacts().pinnedId() != null
                || !AuraReaderPlayerState.getSelectedSignal(player).isBlank()
                || AuraReaderPlayerState.getLockType(player) != AuraReaderLockType.NONE);
    }

    private static void reconcileTracking(ServerPlayer player, List<AuraReading> completeReadings) {
        String selected = AuraReaderPlayerState.getSelectedSignal(player);
        if (!selected.isBlank() && completeReadings.stream().noneMatch(reading -> reading.id().equals(selected))) {
            AuraReaderPlayerState.clearSelectedSignal(player);
            session(player).setSelectedReadingId("");
        }
        restoreTracking(player);
    }

    public static void clearLock(ServerPlayer player, ItemStack auraReader) {
        AuraLockManager.INSTANCE.clearPlayer(player.getUUID());
        AuraReaderPlayerState.clearLock(player);
        session(player).contacts().clearPin();
        restoreTracking(player);
        sendState(player);
    }

    public static boolean validateLock(ServerPlayer player, ItemStack auraReader) {
        if (AuraReaderPlayerState.getLockType(player) == AuraReaderLockType.NONE) {
            return true;
        }

        if (AuraReaderPlayerState.getLockType(player) != AuraReaderLockType.ENTITY) {
            return true;
        }

        Entity entity = resolveLockedEntity(player);
        if (!(entity instanceof PokemonEntity pokemonEntity)
                || entity.isRemoved()
                || !entity.isAlive()
                || !ShadowAspectUtil.hasShadowAspect(pokemonEntity.getPokemon())
                || !isValidWildLockOwner(player, pokemonEntity)
                || !AuraLockManager.INSTANCE.isLocked(entity, player.getUUID(), player.serverLevel().getGameTime())) {
            AuraLockManager.INSTANCE.clearPlayer(player.getUUID());
            AuraReaderPlayerState.clearLock(player);
            // The independent tracking pin may outlive the timed wild lock.
            restoreTracking(player);
            sendState(player);
            return false;
        }

        return true;
    }

    private static Entity resolveLockedEntity(ServerPlayer player) {
        String dimension = AuraReaderPlayerState.getLockDimension(player);
        if (dimension == null || !dimension.equals(player.serverLevel().dimension().location().toString())) {
            return null;
        }

        String lockedTarget = AuraReaderPlayerState.getLockedTarget(player);
        if (lockedTarget == null || lockedTarget.isBlank()) {
            return null;
        }

        try {
            return player.serverLevel().getEntity(UUID.fromString(lockedTarget));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isLockablePokemon(ServerPlayer player, PokemonEntity entity) {
        int range = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraLockRange();
        if (!isUsableContact(player, entity, range)) {
            return false;
        }
        return AuraScanOwnership.resolve(player.getUUID(), entity) == AuraScanOwnership.WILD
                && entity.getPokemon().getOwnerUUID() == null && !entity.getPokemon().isNPCOwned();
    }

    /** Shared validity check for tick reconciliation and the native natural-despawn guard. */
    public static boolean isValidWildLockOwner(ServerPlayer player, PokemonEntity entity) {
        return player.isAlive() && !player.isRemoved() && player.serverLevel() == entity.level()
                && !SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player).isEmpty()
                && AuraReaderPlayerState.getLockType(player) == AuraReaderLockType.ENTITY
                && entity.getUUID().toString().equals(AuraReaderPlayerState.getLockedTarget(player))
                && player.serverLevel().dimension().location().toString().equals(AuraReaderPlayerState.getLockDimension(player))
                && isLockablePokemon(player, entity);
    }

    public static void sendState(ServerPlayer player) {
        boolean active = AuraReaderPlayerState.isActive(player);

        AuraReaderSession session = session(player);
        long gameTime = player.serverLevel().getGameTime();
        int pulseCooldownRemainingTicks = (int) Math.max(0L, session.getPulseCooldownEndTick() - gameTime);
        int pulseCooldownMaxTicks = Math.max(0, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraReaderPulseCooldownTicks());

        ShadowedHeartsNetwork.sendToPlayer(player, new AuraReaderStateS2CPacket(
                active,
                AuraReaderPlayerState.getMode(player),
                AuraReaderPlayerState.isTracking(player) || session.contacts().pinnedId() != null,
                AuraReaderPlayerState.getSelectedSignal(player),
                AuraReaderPlayerState.getLockType(player),
                pulseCooldownRemainingTicks,
                pulseCooldownMaxTicks
        ));
    }
}
