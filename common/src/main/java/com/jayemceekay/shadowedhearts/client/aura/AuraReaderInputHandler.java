package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderLockType;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraLockC2SPacket;
import com.jayemceekay.shadowedhearts.network.aura.AuraPulsePacket;
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderModeC2SPacket;
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

        while (ModKeybinds.consumeAuraScannerPress()) {
            boolean active = !AuraReaderClientState.isActive();
            AuraReaderClientState.setActive(active);
            ShadowedHeartsNetwork.sendToServer(new AuraScannerC2SPacket(active));
        }

        while (ModKeybinds.consumeAuraModeSelectorPress()) {
            AuraReaderMode mode = AuraReaderClientState.cycleMode();
            ShadowedHeartsNetwork.sendToServer(new AuraReaderModeC2SPacket(mode));
        }

        while (ModKeybinds.consumeAuraPulsePress()) {
            ShadowedHeartsNetwork.sendToServer(new AuraPulsePacket(-1));
        }

        while (ModKeybinds.consumeAuraLockPress()) {
            AuraLockC2SPacket.Action action = AuraReaderClientState.getLockType() == AuraReaderLockType.NONE
                    ? AuraLockC2SPacket.Action.LOCK_FOCUSED
                    : AuraLockC2SPacket.Action.CLEAR;
            ShadowedHeartsNetwork.sendToServer(new AuraLockC2SPacket(action));
        }

        while (ModKeybinds.consumeNextSignal()) {
            ShadowedHeartsNetwork.sendToServer(new AuraReaderSelectSignalC2SPacket(AuraReaderSelectSignalC2SPacket.Direction.NEXT));
        }

        while (ModKeybinds.consumePrevSignal()) {
            ShadowedHeartsNetwork.sendToServer(new AuraReaderSelectSignalC2SPacket(AuraReaderSelectSignalC2SPacket.Direction.PREVIOUS));
        }
    }
}
