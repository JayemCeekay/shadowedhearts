package com.jayemceekay.shadowedhearts.common.aura;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.platform.events.PlatformEvents;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderPulseResultS2CPacket;
import com.jayemceekay.shadowedhearts.registry.util.ModItemComponents;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.EventResult;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class AuraReaderEvents {
    private AuraReaderEvents() {}

    public static void init() {
        TickEvent.PLAYER_POST.register(AuraReaderEvents::onPlayerPostTick);
        // Cleanup reads saved navigation state. Run before Cobblemon's NORMAL-priority
        // logout handler saves and evicts it; Architectury quit can run after eviction.
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(Priority.HIGH, event -> {
            AuraReaderService.removeSession(event.getPlayer());
            return Unit.INSTANCE;
        });
        PlayerEvent.CHANGE_DIMENSION.register((player, from, to) -> AuraReaderService.invalidateContacts(player));
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) AuraReaderService.removeSession(player);
            return EventResult.pass();
        });
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, reason) -> AuraReaderService.removeSession(player));
        LifecycleEvent.SERVER_STOPPED.register(server -> AuraReaderService.resetSessions());
    }

    private static void onPlayerPostTick(Player player) {
        if (player == null || player.level().isClientSide) return;
        if (!player.isAlive()) {
            if (player instanceof ServerPlayer sp) AuraReaderService.removeSession(sp);
            return;
        }

        // Equipment remains authoritative for the toggle and live scan updates.
        ItemStack auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player);

        if (!auraReader.isEmpty()) {
            tickAuraReader(player, auraReader);
            if (player instanceof ServerPlayer sp && sp.tickCount % 10 == 0) {
                AuraReaderService.validateLock(sp, auraReader);
                if (AuraReaderService.isActiveForActions(sp)) {
                    ShadowedHeartsNetwork.sendToPlayer(
                            sp,
                            AuraReaderPulseResultS2CPacket.fromReadings(
                                    AuraReaderService.targetedScan(sp, auraReader), false, true,
                                    Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange()))
                    );
                }
                AuraReaderService.sendState(sp);
            }
        } else if (player instanceof ServerPlayer sp) {
            if (AuraReaderPlayerState.isActive(sp)) {
                AuraReaderService.setActive(sp, ItemStack.EMPTY, false);
            }
        }
    }

    private static void tickAuraReader(Player player, ItemStack stack) {
        Boolean active = stack.get(ModItemComponents.AURA_SCANNER_ACTIVE.get());
        boolean isActive = active != null && active;
        if (player instanceof ServerPlayer sp && AuraReaderPlayerState.isActive(sp) != isActive) {
            AuraReaderService.syncActiveFromStack(sp, stack);
        }
    }
}
