package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public class ShadowAuraSoundInstance extends AbstractTickableSoundInstance {
    private final ShadowAuraEmitters.AuraInstance aura;
    private final net.minecraft.client.multiplayer.ClientLevel level;

    public ShadowAuraSoundInstance(ShadowAuraEmitters.AuraInstance aura) {
        super(ModSounds.SHADOW_AURA_LOOP.get(), SoundSource.NEUTRAL, net.minecraft.util.RandomSource.create());
        this.aura = aura;
        this.level = net.minecraft.client.Minecraft.getInstance().level;
        this.looping = true;
        this.delay = 0;
        this.volume = ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().shadowAuraLoopVolume();
        this.relative = false;
    }

    @Override
    public void tick() {
        if (level == null || net.minecraft.client.Minecraft.getInstance().level != level
                || this.aura.isExpired(level.getGameTime())) {
            this.stop();
        } else {
            Entity entity = this.aura.getEntity();
            if (entity != null && entity.isAlive()) {
                this.x = (double)((float)entity.getX());
                this.y = (double)((float)entity.getY());
                this.z = (double)((float)entity.getZ());
            } else {
                this.x = (double)((float)this.aura.x);
                this.y = (double)((float)this.aura.y);
                this.z = (double)((float)this.aura.z);
            }

            float fade = this.aura.fadeFactor(level.getGameTime());
            this.volume = fade * 0.5F * ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().shadowAuraLoopVolume(); // Scale volume if needed
        }
    }

    public void stopSound() {
        this.stop();
    }
}
