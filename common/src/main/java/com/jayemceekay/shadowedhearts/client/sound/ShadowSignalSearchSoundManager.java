package com.jayemceekay.shadowedhearts.client.sound;

import com.jayemceekay.shadowedhearts.client.aura.AuraReaderSearchClientState;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.content.items.ShadowSignalDataItem;
import com.jayemceekay.shadowedhearts.content.umbrafall.AuraReaderSearchProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** One local processing loop, owned by the server's Signal Data job rather than its HUD card. */
public final class ShadowSignalSearchSoundManager {
    private static final LoopingSoundHandle<ShadowSignalSearchSoundInstance> LOOP = new LoopingSoundHandle<>();
    private static ClientLevel level;
    private static LocalPlayer player;

    private ShadowSignalSearchSoundManager() { }

    public static void accept(Minecraft minecraft, long sequence, AuraReaderSearchProgress progress) {
        syncSession(minecraft);
        if (level == null || player == null) return;
        if (AuraReaderSearchClientState.apply(sequence, progress, player.tickCount)) onClientTick(minecraft);
    }

    public static void onClientTick(Minecraft minecraft) {
        syncSession(minecraft);
        if (level == null || player == null || !player.isAlive()) {
            LOOP.stop(ShadowSignalSearchSoundManager::dispose);
            return;
        }
        if (minecraft.isPaused()) return;
        var progress = AuraReaderSearchClientState.getProgress(player.tickCount);
        if (!isProcessingForPlayer(progress, player)) {
            var sound = LOOP.sound();
            if (sound != null) {
                sound.setProcessing(false);
                // A discarded engine channel has nothing to fade. Release ownership immediately.
                if (sound.isStopped() || !minecraft.getSoundManager().isActive(sound)) {
                    LOOP.stop(ShadowSignalSearchSoundManager::dispose);
                }
            }
            return;
        }
        boolean audible = minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.PLAYERS) > 0
                && ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().shadowSignalSearchVolume() > 0;
        // A short wait can finish inside the engine's retry window. Resuming work must not wait for it.
        if (LOOP.sound() != null && LOOP.sound().isStopped()) LOOP.stop(ShadowSignalSearchSoundManager::dispose);
        LOOP.update(player.tickCount, audible, () -> new ShadowSignalSearchSoundInstance(level, player),
                sound -> !sound.isStopped() && minecraft.getSoundManager().isActive(sound),
                minecraft.getSoundManager()::play, ShadowSignalSearchSoundManager::dispose);
        if (LOOP.sound() != null) LOOP.sound().setProcessing(true);
    }

    private static void syncSession(Minecraft minecraft) {
        if (level != minecraft.level || player != minecraft.player) {
            LOOP.stop(ShadowSignalSearchSoundManager::dispose);
            AuraReaderSearchClientState.clear();
            level = minecraft.level;
            player = minecraft.player;
        }
    }

    static boolean isProcessing(AuraReaderSearchProgress progress, boolean holdingMatchingData) {
        if (progress == null) return false;
        return switch (progress.stage()) {
            case SEARCHING, SURVEYING, CHECKING -> holdingMatchingData;
            // Reservation consumes the item; preparation remains an active server job afterward.
            case ESTABLISHING -> true;
            default -> false;
        };
    }

    static boolean isProcessingForPlayer(AuraReaderSearchProgress progress, LocalPlayer player) {
        return progress != null && player != null && isProcessing(progress,
                !progress.stage().preflight() || holdsMatchingData(progress, player));
    }

    static boolean holdsMatchingData(AuraReaderSearchProgress progress, LocalPlayer player) {
        return progress != null && player != null
                && (matches(progress, player.getMainHandItem()) || matches(progress, player.getOffhandItem()));
    }

    private static boolean matches(AuraReaderSearchProgress progress, ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ShadowSignalDataItem signal
                && signal.tier() == progress.tier();
    }

    private static void dispose(ShadowSignalSearchSoundInstance sound) {
        sound.stopSound();
        Minecraft.getInstance().getSoundManager().stop(sound);
    }
}
