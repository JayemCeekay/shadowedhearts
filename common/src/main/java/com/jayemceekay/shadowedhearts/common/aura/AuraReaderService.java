package com.jayemceekay.shadowedhearts.common.aura;

import com.jayemceekay.shadowedhearts.content.items.AuraReaderItem;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderStateS2CPacket;
import com.jayemceekay.shadowedhearts.registry.util.ModItemComponents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
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
    private static final int PULSE_CHARGE_COST = 100;
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
        session.setMode(AuraReaderPlayerState.getMode(player));
        session.setActive(AuraReaderPlayerState.isActive(player));
        session.setSelectedReadingId(AuraReaderPlayerState.getSelectedSignal(player));
        return session;
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

    public static void setActive(ServerPlayer player, ItemStack auraReader, boolean active) {
        boolean allowed = !auraReader.isEmpty();
        boolean nextActive = active && allowed;
        if (nextActive && !player.isCreative()) {
            AuraReaderCharge.ensureInitialized(auraReader, AuraReaderItem.MAX_CHARGE);
            nextActive = AuraReaderCharge.get(auraReader) > 0;
        }

        AuraReaderPlayerState.setActive(player, nextActive);
        session(player).setActive(nextActive);

        if (!auraReader.isEmpty()) {
            auraReader.set(ModItemComponents.AURA_SCANNER_ACTIVE.get(), nextActive);
        }
        sendState(player, auraReader);
    }

    public static void setTracking(ServerPlayer player, ItemStack auraReader, boolean tracking) {
        boolean nextTracking = tracking && !auraReader.isEmpty();
        AuraReaderPlayerState.setTracking(player, nextTracking);
        if (!auraReader.isEmpty()) {
            auraReader.set(ModItemComponents.AURA_SCANNER_TRACKING.get(), nextTracking);
        }
        sendState(player, auraReader);
    }

    public static void setMode(ServerPlayer player, AuraReaderMode mode) {
        setMode(player, mode, ItemStack.EMPTY);
    }

    public static void setMode(ServerPlayer player, AuraReaderMode mode, ItemStack auraReader) {
        AuraReaderPlayerState.setMode(player, mode);
        session(player).setMode(mode);
        sendState(player, auraReader);
    }

    public static void syncActiveFromStack(ServerPlayer player, ItemStack auraReader) {
        boolean active = false;
        if (!auraReader.isEmpty()) {
            active = Boolean.TRUE.equals(auraReader.get(ModItemComponents.AURA_SCANNER_ACTIVE.get()));
        }

        AuraReaderPlayerState.setActive(player, active);
        session(player).setActive(active);
    }

    public static boolean consumeActiveCharge(ServerPlayer player, ItemStack auraReader) {
        if (auraReader.isEmpty()) {
            setActive(player, auraReader, false);
            return false;
        }

        if (player.isCreative()) {
            syncActiveFromStack(player, auraReader);
            return true;
        }

        boolean hasCharge = AuraReaderCharge.consume(auraReader, 1, AuraReaderItem.MAX_CHARGE);
        if (!hasCharge) {
            setActive(player, auraReader, false);
        }
        return hasCharge;
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
            boolean selected = r.id().equals(selectedSignal);
            if (locked || selected) {
                return new AuraReading(
                        r.id(), r.type(), r.source(), r.label(), r.status(), r.dimension(),
                        r.directionX(), r.directionY(), r.directionZ(), r.bearingUncertainty(),
                        r.distanceBand(), r.verticalHint(), r.strength(), r.confidence(),
                        r.interference(), r.priority(), r.expiryTick(), selected, locked,
                        r.outOfDimension(), r.exactPositionAllowed()
                );
            }
            return r;
        }).toList();
    }

    public static List<AuraReading> targetedScan(ServerPlayer player, ItemStack auraReader) {
        if (auraReader.isEmpty() || !AuraReaderPlayerState.isActive(player)) {
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
        session(player).replaceCachedReadings(readings);
        return readings;
    }

    public static AuraReaderPulseResult pulse(ServerPlayer player, ItemStack auraReader) {
        if (auraReader.isEmpty() || !AuraReaderPlayerState.isActive(player)) {
            sendState(player, auraReader);
            return AuraReaderPulseResult.rejected();
        }

        long gameTime = player.serverLevel().getGameTime();
        AuraReaderSession session = session(player);
        if (session.getPulseCooldownEndTick() > gameTime) {
            sendState(player, auraReader);
            return AuraReaderPulseResult.rejected();
        }

        if (!player.isCreative() && !AuraReaderCharge.consume(auraReader, PULSE_CHARGE_COST, AuraReaderItem.MAX_CHARGE)) {
            setActive(player, auraReader, false);
            return AuraReaderPulseResult.rejected();
        }

        int cooldownTicks = Math.max(0, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraReaderPulseCooldownTicks());
        session.setLastPulseTick(gameTime);
        session.setPulseCooldownEndTick(gameTime + cooldownTicks);

        AuraScanContext context = new AuraScanContext(
                player,
                player.serverLevel(),
                auraReader,
                AuraReaderMode.PULSE_SCAN,
                gameTime,
                true
        );
        List<AuraReading> readings = pulse(context);
        session.replaceCachedReadings(readings);
        sendState(player, auraReader);
        return AuraReaderPulseResult.accepted(readings);
    }

    public static boolean lockFocusedTarget(ServerPlayer player, ItemStack auraReader) {
        if (auraReader.isEmpty() || !AuraReaderPlayerState.isActive(player)) {
            AuraReaderPlayerState.clearLock(player);
            sendState(player, auraReader);
            return false;
        }

        List<AuraReading> readings = targetedScan(player, auraReader);
        AuraReading reading = readings.isEmpty() ? null : readings.get(0);
        if (reading == null || reading.type() != AuraReadingType.SHADOW_POKEMON) {
            AuraReaderPlayerState.clearLock(player);
            sendState(player, auraReader);
            return false;
        }

        UUID entityUuid;
        try {
            entityUuid = UUID.fromString(reading.id());
        } catch (IllegalArgumentException ignored) {
            AuraReaderPlayerState.clearLock(player);
            sendState(player, auraReader);
            return false;
        }

        Entity entity = player.serverLevel().getEntity(entityUuid);
        if (!(entity instanceof PokemonEntity pokemonEntity) || !isLockablePokemon(player, pokemonEntity)) {
            AuraReaderPlayerState.clearLock(player);
            sendState(player, auraReader);
            return false;
        }

        int durationTicks = Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraLockMaxSeconds() * 20);
        AuraLockManager.INSTANCE.applyLock(pokemonEntity, player.serverLevel().getGameTime(), durationTicks);
        AuraReaderPlayerState.lockEntity(player, pokemonEntity.getUUID(), player.serverLevel().dimension().location());
        session(player).setSelectedReadingId(reading.id());
        sendState(player, auraReader);
        return true;
    }

    public static void clearLock(ServerPlayer player, ItemStack auraReader) {
        AuraReaderPlayerState.clearLock(player);
        session(player).setSelectedReadingId("");
        sendState(player, auraReader);
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
                || !isLockablePokemon(player, pokemonEntity)
                || !AuraLockManager.INSTANCE.isLocked(entity, player.serverLevel().getGameTime())) {
            if (entity != null) {
                AuraLockManager.INSTANCE.clear(entity);
            }
            AuraReaderPlayerState.clearLock(player);
            session(player).setSelectedReadingId("");
            sendState(player, auraReader);
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
        if (player.distanceTo(entity) > ShadowedHeartsConfigs.getInstance().getShadowConfig().auraLockRange()) {
            return false;
        }
        return entity.getPokemon().getOwnerUUID() == null && !entity.getPokemon().isNPCOwned();
    }

    public static void sendState(ServerPlayer player, ItemStack auraReader) {
        boolean active = AuraReaderPlayerState.isActive(player);
        int charge = 0;
        int maxCharge = AuraReaderItem.MAX_CHARGE;
        if (!auraReader.isEmpty()) {
            AuraReaderCharge.ensureInitialized(auraReader, maxCharge);
            charge = AuraReaderCharge.get(auraReader);
        }

        AuraReaderSession session = session(player);
        long gameTime = player.serverLevel().getGameTime();
        int pulseCooldownRemainingTicks = (int) Math.max(0L, session.getPulseCooldownEndTick() - gameTime);
        int pulseCooldownMaxTicks = Math.max(0, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraReaderPulseCooldownTicks());

        ShadowedHeartsNetwork.sendToPlayer(player, new AuraReaderStateS2CPacket(
                active,
                AuraReaderPlayerState.getMode(player),
                charge,
                maxCharge,
                AuraReaderPlayerState.isTracking(player),
                AuraReaderPlayerState.getSelectedSignal(player),
                AuraReaderPlayerState.getLockType(player),
                pulseCooldownRemainingTicks,
                pulseCooldownMaxTicks
        ));
    }
}
