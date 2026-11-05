package com.jayemceekay.shadowedhearts.common.moonwound.raid;

import net.minecraft.network.chat.Component;

/** Display-only compatibility for the two exact labels emitted by older built-in definitions. */
final class MoonwoundRaidLabel {
    private MoonwoundRaidLabel() { }

    static Component localizeLegacy(Component label, Component speciesName) {
        if (Component.literal("Moonwound • Shadow Raid Boss").equals(label)) {
            return Component.translatable("message.shadowedhearts.moonwound.raid_boss_generic");
        }
        if (Component.literal("Moonwound • Shadow ").append(speciesName).equals(label)) {
            return Component.translatable("message.shadowedhearts.moonwound.raid_boss_name", speciesName);
        }
        return label;
    }
}
