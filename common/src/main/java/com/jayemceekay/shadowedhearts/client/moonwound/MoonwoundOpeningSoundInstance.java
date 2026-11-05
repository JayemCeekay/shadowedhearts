package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** The opening's one-shot tail remains owned by its event, including early cancellation. */
public class MoonwoundOpeningSoundInstance extends AbstractTickableSoundInstance {
    private final ClientLevel level;
    private final LocalPlayer player;
    private final MoonwoundSnapshot snapshot;

    public MoonwoundOpeningSoundInstance(MoonwoundSnapshot snapshot, ClientLevel level, LocalPlayer player) {
        super(ModSounds.MOONWOUND_OPEN.get(), SoundSource.AMBIENT, RandomSource.create());
        this.level = level;
        this.player = player;
        this.snapshot = snapshot;
        this.x = snapshot.center().getX() + 0.5;
        this.y = snapshot.portalY();
        this.z = snapshot.center().getZ() + 0.5;
        this.looping = false;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        updateVolume();
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (level == null || minecraft.level != level || minecraft.player != player
                || player == null || !player.isAlive()) {
            stop();
            return;
        }
        updateVolume();
        if (volume <= 0) stop();
    }

    private void updateVolume() {
        double distance = player.getEyePosition().distanceTo(new Vec3(x, y, z));
        this.volume = (float) Math.max(0, 1 - distance / MoonwoundSoundInstance.RANGE)
                * MoonwoundAudioTimeline.openingGain(MoonwoundClient.portalOpeningAge(snapshot, 0))
                * ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().moonwoundOpeningVolume();
    }

    public void stopSound() { stop(); }
}
