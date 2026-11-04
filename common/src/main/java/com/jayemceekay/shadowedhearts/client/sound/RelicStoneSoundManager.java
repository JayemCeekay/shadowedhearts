package com.jayemceekay.shadowedhearts.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;

import java.util.HashMap;
import java.util.Map;

public class RelicStoneSoundManager {
    private static final Map<BlockPos, LoopingSoundHandle<RelicStoneSoundInstance>> ACTIVE_SOUNDS = new HashMap<>();
    private static ClientLevel level;
    private static LocalPlayer player;

    public static void updateSound(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        syncWorld(mc);
        if (!inRange(pos)) return;
        updatePlayback(mc, pos, ACTIVE_SOUNDS.computeIfAbsent(pos.immutable(), ignored -> new LoopingSoundHandle<>()));
    }

    public static void stopSound(BlockPos pos) {
        var sound = ACTIVE_SOUNDS.remove(pos);
        if (sound != null) sound.stop(RelicStoneSoundManager::dispose);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        syncWorld(mc);
        if (mc.isPaused()) return;
        ACTIVE_SOUNDS.entrySet().removeIf(entry -> {
            if (level == null || player == null || !player.isAlive()
                    || !level.getBlockState(entry.getKey()).is(ModBlocks.RELIC_STONE.get())) {
                entry.getValue().stop(RelicStoneSoundManager::dispose);
                return true;
            }
            if (!inRange(entry.getKey())) {
                // Let a playing instance retain its existing distance fade, but never revive it out of range.
                var sound = entry.getValue().sound();
                if (sound == null || sound.isStopped() || !mc.getSoundManager().isActive(sound)) {
                    entry.getValue().stop(RelicStoneSoundManager::dispose);
                    return true;
                }
                return false;
            }
            updatePlayback(mc, entry.getKey(), entry.getValue());
            return false;
        });
    }

    private static void syncWorld(Minecraft mc) {
        if (mc.level != level || mc.player != player) {
            ACTIVE_SOUNDS.values().forEach(handle -> handle.stop(RelicStoneSoundManager::dispose));
            ACTIVE_SOUNDS.clear();
            level = mc.level;
            player = mc.player;
        }
    }

    private static boolean inRange(BlockPos pos) {
        return level != null && player != null && player.isAlive()
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 16 * 16;
    }

    private static void updatePlayback(Minecraft mc, BlockPos pos, LoopingSoundHandle<RelicStoneSoundInstance> handle) {
        boolean audible = mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && mc.options.getSoundSourceVolume(SoundSource.BLOCKS) > 0
                && ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().relicShrineLoopVolume() > 0;
        handle.update(level.getGameTime(), audible, () -> new RelicStoneSoundInstance(pos, player),
                sound -> !sound.isStopped() && mc.getSoundManager().isActive(sound),
                mc.getSoundManager()::play, RelicStoneSoundManager::dispose);
    }

    private static void dispose(RelicStoneSoundInstance sound) {
        sound.stopSound();
        Minecraft.getInstance().getSoundManager().stop(sound);
    }
}
