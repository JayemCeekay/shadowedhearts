package com.jayemceekay.shadowedhearts.client.moonwound;

import net.minecraft.network.chat.Component;

/** Resolve display strings on the client so the active language and its argument order are respected. */
final class MoonwoundText {
    private MoonwoundText() {}

    static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}
