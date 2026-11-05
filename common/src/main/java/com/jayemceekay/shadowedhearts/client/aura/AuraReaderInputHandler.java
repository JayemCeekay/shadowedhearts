package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderLockType;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraLockC2SPacket;
import com.jayemceekay.shadowedhearts.network.aura.AuraPulsePacket;
import com.jayemceekay.shadowedhearts.client.gui.AuraReaderTutorial;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderSelectSignalC2SPacket;
import com.jayemceekay.shadowedhearts.network.aura.AuraScannerC2SPacket;
import net.minecraft.client.Minecraft;

public final class AuraReaderInputHandler {
    private AuraReaderInputHandler() {
    }

    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        AuraReaderTutorial.tick(client);
        if (client.screen != null) {
            discardPendingInputs();
            return;
        }

        // Help always opens the tutorial, including while sneaking or searching.
        // Handle it first so simultaneous gameplay inputs cannot act behind it.
        while (ModKeybinds.consumeAuraReaderHelpPress()) {
            if (SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(client.player)) {
                AuraReaderTutorial.open(client);
            }
        }
        if (client.screen != null) {
            discardPendingInputs();
            return;
        }

        while (ModKeybinds.consumeAuraScannerPress()) {
            if (!SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(client.player)) {
                client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.shadowedhearts.aura_reader.equip_required"), true);
                continue;
            }
            boolean active = !AuraReaderClientState.isActive();
            AuraReaderClientState.setActive(active);
            ShadowedHeartsNetwork.sendToServer(new AuraScannerC2SPacket(active));
        }

        while (ModKeybinds.consumeAuraPulsePress()) {
            if (AuraReaderClientState.isActive()) ShadowedHeartsNetwork.sendToServer(new AuraPulsePacket(-1));
        }

        while (ModKeybinds.consumeAuraLockPress()) {
            if (!AuraReaderClientState.isActive()) continue;
            var focused = AuraReaderClientState.getFocusedReading();
            var selected = AuraReaderClientState.getPrimaryReading();
            boolean focusedPinned = focused != null && focused.source() == AuraReadingSource.TRACKED;
            boolean selectedPokemon = selected != null && selected.type() == AuraReadingType.SHADOW_POKEMON;
            boolean selectedPinned = selectedPokemon && selected.source() == AuraReadingSource.TRACKED;
            boolean hasPin = AuraReaderClientState.getReadings().stream().anyMatch(reading ->
                    reading.type() == AuraReadingType.SHADOW_POKEMON && reading.source() == AuraReadingSource.TRACKED);
            AuraLockC2SPacket.Action action;
            if (AuraReaderClientState.getLockType() != AuraReaderLockType.NONE) {
                action = AuraLockC2SPacket.Action.CLEAR;
            } else if (focusedPinned || (focused == null && (selectedPinned || (!selectedPokemon && hasPin)))) {
                action = AuraLockC2SPacket.Action.CLEAR_PIN;
            } else if (focused == null && selectedPokemon) {
                action = AuraLockC2SPacket.Action.PIN_SELECTED;
            } else {
                action = AuraLockC2SPacket.Action.LOCK_FOCUSED;
            }
            ShadowedHeartsNetwork.sendToServer(new AuraLockC2SPacket(action));
        }

        while (ModKeybinds.consumeNextSignal()) {
            if (AuraReaderClientState.isActive()) ShadowedHeartsNetwork.sendToServer(
                    new AuraReaderSelectSignalC2SPacket(AuraReaderSelectSignalC2SPacket.Direction.NEXT));
        }

        while (ModKeybinds.consumePrevSignal()) {
            if (AuraReaderClientState.isActive()) ShadowedHeartsNetwork.sendToServer(
                    new AuraReaderSelectSignalC2SPacket(AuraReaderSelectSignalC2SPacket.Direction.PREVIOUS));
        }
    }

    private static void discardPendingInputs() {
        // A click queued before a screen opened must not fire when it closes.
        while (ModKeybinds.consumeAuraReaderHelpPress()) { }
        while (ModKeybinds.consumeAuraScannerPress()) { }
        while (ModKeybinds.consumeAuraPulsePress()) { }
        while (ModKeybinds.consumeAuraLockPress()) { }
        while (ModKeybinds.consumeNextSignal()) { }
        while (ModKeybinds.consumePrevSignal()) { }
    }
}
