package com.jayemceekay.shadowedhearts.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class RelicStoneSoundManager {
    private static final Map<BlockPos, RelicStoneSoundInstance> ACTIVE_SOUNDS = new HashMap<>();

    public static void updateSound(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!ACTIVE_SOUNDS.containsKey(pos) || ACTIVE_SOUNDS.get(pos).isStopped()) {
            RelicStoneSoundInstance sound = new RelicStoneSoundInstance(pos, mc.player);
            ACTIVE_SOUNDS.put(pos, sound);
            mc.getSoundManager().play(sound);
        }
    }

    public static void tick() {
        ACTIVE_SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
    }
}
