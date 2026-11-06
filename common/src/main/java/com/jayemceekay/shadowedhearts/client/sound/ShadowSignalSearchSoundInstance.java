package com.jayemceekay.shadowedhearts.client.sound;

import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderSearchClientState;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The supplied stereo search loop follows the local player without world attenuation. */
public class ShadowSignalSearchSoundInstance extends AbstractTickableSoundInstance {
    private final ClientLevel level;
    private final LocalPlayer player;
    private final ShadowSignalSearchAudioEnvelope envelope = new ShadowSignalSearchAudioEnvelope();

    public ShadowSignalSearchSoundInstance(ClientLevel level, LocalPlayer player) {
        super(ModSounds.SHADOW_SIGNAL_SEARCH.get(), SoundSource.PLAYERS, RandomSource.create());
        this.level = level;
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0;
    }

    @Override public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (level == null || minecraft.level != level || minecraft.player != player
                || player == null || !player.isAlive()) {
            stop();
            return;
        }
        var progress = AuraReaderSearchClientState.getProgress(player.tickCount);
        envelope.setProcessing(ShadowSignalSearchSoundManager.isProcessingForPlayer(progress, player));
        this.volume = envelope.tick()
                * ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().shadowSignalSearchVolume();
        if (envelope.finished()) {
            stop();
        }
    }

    public void setProcessing(boolean processing) {
        envelope.setProcessing(processing);
        // A job that finishes before its first audible tick has no tail to release.
        if (envelope.finished()) stop();
    }

    public void stopSound() { stop(); }
}
