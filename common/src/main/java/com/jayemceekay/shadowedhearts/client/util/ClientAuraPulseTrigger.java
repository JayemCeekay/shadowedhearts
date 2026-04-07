package com.jayemceekay.shadowedhearts.client.util;

import com.jayemceekay.shadowedhearts.client.gui.AuraReaderManager;
import net.minecraft.client.Minecraft;

public class ClientAuraPulseTrigger {
    public static void trigger(int slotIndex) {
        if (AuraReaderManager.HUD_STATE.isActive) {
            if (AuraReaderManager.HUD_STATE.pulseCooldown <= 0) {
                AuraReaderManager.HUD_STATE.triggerLocalPulse(Minecraft.getInstance());
            }
        }
    }

    public static boolean isAuraReaderActive() {
        return AuraReaderManager.HUD_STATE.isActive;
    }
}
