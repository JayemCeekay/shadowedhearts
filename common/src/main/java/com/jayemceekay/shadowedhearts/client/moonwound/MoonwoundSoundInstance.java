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

/** One preloaded, natively looping ambience at the rendered portal's center. */
public class MoonwoundSoundInstance extends AbstractTickableSoundInstance {
    public static final double RANGE = 64;
    private final ClientLevel level;
    private final LocalPlayer player;
    private final MoonwoundSnapshot snapshot;
    private final MoonwoundAudioTimeline.AmbienceEnvelope envelope;
    private boolean openingPlaying;

    public MoonwoundSoundInstance(MoonwoundSnapshot snapshot, ClientLevel level, LocalPlayer player) {
        this(snapshot, level, player, false);
    }

    public MoonwoundSoundInstance(MoonwoundSnapshot snapshot, ClientLevel level, LocalPlayer player, boolean openingPlaying) {
        super(ModSounds.MOONWOUND_AMBIENT_LOOP.get(), SoundSource.AMBIENT, RandomSource.create());
        this.level = level;
        this.player = player;
        this.snapshot = snapshot;
        this.openingPlaying = openingPlaying;
        this.envelope = new MoonwoundAudioTimeline.AmbienceEnvelope(openingPlaying);
        this.x = snapshot.center().getX() + 0.5;
        this.y = snapshot.portalY();
        this.z = snapshot.center().getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        // Distance is applied here once, so raising the mod volume never enlarges the audible radius.
        this.attenuation = Attenuation.NONE;
        this.volume = 0;
    }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (level == null || minecraft.level != level || minecraft.player != player
                || player == null || !player.isAlive()) {
            stop();
            return;
        }
        double distance = player.getEyePosition().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
        if (distance >= RANGE) {
            stop();
            return;
        }
        float fade = envelope.tick(MoonwoundClient.portalOpeningAge(snapshot, 0), openingPlaying);
        this.volume = fade * (float) (1 - distance / RANGE)
                * ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().moonwoundAmbientLoopVolume();
    }

    public void openingPlaying(boolean playing) { this.openingPlaying = playing; }

    public void stopSound() { stop(); }
}
