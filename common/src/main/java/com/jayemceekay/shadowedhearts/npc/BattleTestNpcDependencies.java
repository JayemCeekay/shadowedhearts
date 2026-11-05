package com.jayemceekay.shadowedhearts.npc;

/** Optional dependencies required by NPC classes in the manual testing datapack. */
public final class BattleTestNpcDependencies {
    private BattleTestNpcDependencies() {
    }

    public static boolean requiresMegaShowdown(String npcClassId) {
        return BattleTestNpcGimmicks.classIds().contains(npcClassId);
    }

    public static boolean isUnavailable(String npcClassId, boolean megaShowdownLoaded) {
        return !megaShowdownLoaded && requiresMegaShowdown(npcClassId);
    }
}
