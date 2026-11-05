package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.client.sound.LoopingSoundHandle;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-thread ownership survives sound-engine reloads, but never outlives a fresh active portal. */
public final class MoonwoundSoundManager {
    private static final Map<UUID, LoopingSoundHandle<MoonwoundSoundInstance>> ACTIVE_SOUNDS = new HashMap<>();
    private static final Map<UUID, Opening> OPENING_SOUNDS = new HashMap<>();
    private static ClientLevel level;
    private static LocalPlayer player;

    private record Opening(MoonwoundOpeningSoundInstance sound, long startedAt) { }

    private MoonwoundSoundManager() { }

    public static void tick(Minecraft minecraft, List<MoonwoundSnapshot> portals, long tick) {
        syncWorld(minecraft);
        if (level == null || player == null || !player.isAlive()) {
            clear();
            return;
        }
        if (minecraft.isPaused()) return;

        boolean audible = minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.AMBIENT) > 0
                && ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().moonwoundAmbientLoopVolume() > 0;
        var activeEvents = new HashSet<UUID>();
        var nearby = portals.stream().filter(snapshot -> snapshot.portalActive() && snapshot.portalSize() > 0
                && snapshot.dimension().equals(level.dimension().location())
                && inRange(snapshot, player.getEyePosition())).toList();
        nearby.forEach(snapshot -> activeEvents.add(snapshot.eventId()));
        OPENING_SOUNDS.entrySet().removeIf(entry -> {
            var opening = entry.getValue();
            if (activeEvents.contains(entry.getKey()) && openingAudible(minecraft) && !opening.sound().isStopped()
                    && (tick - opening.startedAt() < 20 || minecraft.getSoundManager().isActive(opening.sound()))) return false;
            dispose(opening.sound());
            return true;
        });
        for (var snapshot : nearby) {
            boolean openingPlaying = OPENING_SOUNDS.containsKey(snapshot.eventId());
            var handle = ACTIVE_SOUNDS.computeIfAbsent(snapshot.eventId(), ignored -> new LoopingSoundHandle<>());
            handle.update(tick, audible, () -> new MoonwoundSoundInstance(snapshot, level, player, openingPlaying),
                    sound -> !sound.isStopped() && minecraft.getSoundManager().isActive(sound),
                    minecraft.getSoundManager()::play, MoonwoundSoundManager::dispose);
            if (handle.sound() != null) handle.sound().openingPlaying(openingPlaying);
        }
        ACTIVE_SOUNDS.entrySet().removeIf(entry -> {
            if (activeEvents.contains(entry.getKey())) return false;
            entry.getValue().stop(MoonwoundSoundManager::dispose);
            return true;
        });
    }

    /** Only the activation snapshot starts the cue; later broadcasts, reloads and arrivals never replay it. */
    public static void accept(Minecraft minecraft, MoonwoundSnapshot snapshot, MoonwoundPortalClock previous, long tick) {
        syncWorld(minecraft);
        if (level == null || player == null || !player.isAlive() || !opensNow(snapshot, previous)
                || !inRange(snapshot, player.getEyePosition()) || !openingAudible(minecraft)) return;
        var sound = new MoonwoundOpeningSoundInstance(snapshot, level, player);
        var replaced = OPENING_SOUNDS.put(snapshot.eventId(), new Opening(sound, tick));
        if (replaced != null) dispose(replaced.sound());
        minecraft.getSoundManager().play(sound);
        // Start the preloaded loop silently alongside the opening, before its gain rises at impact.
        if (minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.AMBIENT) > 0
                && ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().moonwoundAmbientLoopVolume() > 0) {
            var handle = ACTIVE_SOUNDS.computeIfAbsent(snapshot.eventId(), ignored -> new LoopingSoundHandle<>());
            handle.update(tick, true, () -> new MoonwoundSoundInstance(snapshot, level, player, true),
                    loop -> !loop.isStopped() && minecraft.getSoundManager().isActive(loop),
                    minecraft.getSoundManager()::play, MoonwoundSoundManager::dispose);
        }
    }

    static boolean opensNow(MoonwoundSnapshot snapshot, MoonwoundPortalClock previous) {
        return snapshot.portalActive() && snapshot.portalSize() > 0 && snapshot.portalAgeTicks() == 0
                && (previous == null || !previous.active() || !previous.eventId().equals(snapshot.eventId()));
    }

    private static boolean openingAudible(Minecraft minecraft) {
        return minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.AMBIENT) > 0
                && ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().moonwoundOpeningVolume() > 0;
    }

    private static void syncWorld(Minecraft minecraft) {
        if (level != minecraft.level || player != minecraft.player) {
            clear();
            level = minecraft.level;
            player = minecraft.player;
        }
    }

    static boolean inRange(MoonwoundSnapshot snapshot, Vec3 listener) {
        return listener.distanceToSqr(snapshot.center().getX() + 0.5, snapshot.portalY(),
                snapshot.center().getZ() + 0.5) < MoonwoundSoundInstance.RANGE * MoonwoundSoundInstance.RANGE;
    }

    public static void stop(UUID eventId) {
        var handle = ACTIVE_SOUNDS.remove(eventId);
        if (handle != null) handle.stop(MoonwoundSoundManager::dispose);
        var opening = OPENING_SOUNDS.remove(eventId);
        if (opening != null) dispose(opening.sound());
    }

    public static void clear() {
        ACTIVE_SOUNDS.values().forEach(handle -> handle.stop(MoonwoundSoundManager::dispose));
        ACTIVE_SOUNDS.clear();
        OPENING_SOUNDS.values().forEach(opening -> dispose(opening.sound()));
        OPENING_SOUNDS.clear();
        level = null;
        player = null;
    }

    private static void dispose(MoonwoundSoundInstance sound) {
        sound.stopSound();
        Minecraft.getInstance().getSoundManager().stop(sound);
    }

    private static void dispose(MoonwoundOpeningSoundInstance sound) {
        sound.stopSound();
        Minecraft.getInstance().getSoundManager().stop(sound);
    }
}
