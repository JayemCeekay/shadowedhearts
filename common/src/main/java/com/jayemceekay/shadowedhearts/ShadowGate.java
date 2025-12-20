package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.Locale;
import java.util.Set;

public final class ShadowGate {
    private static final Set<String> SHADOW_MOVE_IDS = Set.of(
            "shadowblast",
            "shadowblitz",
            "shadowbolt",
            "shadowbreak",
            "shadowchill",
            "shadowdown",
            "shadowend",
            "shadowfire",
            "shadowhalf",
            "shadowhold",
            "shadowmist",
            "shadowpanic",
            "shadowrave",
            "shadowrush",
            "shadowshed",
            "shadowsky",
            "shadowstorm",
            "shadowwave"
    );

    public static boolean isShadowMoveId(String id) {
        if (id == null) return false;
        return SHADOW_MOVE_IDS.contains(id.toLowerCase(Locale.ROOT));
        // If you add a Shadow elemental type and multiple Shadow moves, you can also
        //   look up MoveTemplate by id to test its type == shadow.
    }

    // Server-side Pokemon (storage model)
    public static boolean isShadowLocked(Pokemon pkmn) {
        // Prefer a persistent flag on the Pokemon itself (recommended):
        //   ShadowService.isShadow(pkmn) && ShadowService.getCorruption(pkmn) >= 1.0F
        // Fallback when in battle and you rely on the entity synched data:
        return pkmn.getAspects().contains(SHAspects.SHADOW);
    }

    // Client-side Pokemon (Summary UI) – same logic but via your client-accessible service/data
    public static boolean isShadowLockedClient(com.cobblemon.mod.common.pokemon.Pokemon pkmn) {
        // Mirror of the server logic for UX only.
        return isShadowLocked(pkmn);
    }
}