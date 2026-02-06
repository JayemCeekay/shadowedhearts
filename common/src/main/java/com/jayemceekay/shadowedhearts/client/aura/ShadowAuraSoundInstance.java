package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public class ShadowAuraSoundInstance extends AbstractTickableSoundInstance {
    private final AuraEmitters.AuraInstance aura;

    public ShadowAuraSoundInstance(AuraEmitters.AuraInstance aura) {
        super(ModSounds.SHADOW_AURA_LOOP.get(), SoundSource.NEUTRAL, net.minecraft.util.RandomSource.create());
        this.aura = aura;
        this.looping = true;
        this.delay = 0;
        this.volume = ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().shadowAuraLoopVolume();
        this.relative = false;
    }

    @Override
    public void tick() {
        if (this.aura.isExpired(net.minecraft.client.Minecraft.getInstance().level.getGameTime())) {
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

            float fade = this.aura.fadeFactor(net.minecraft.client.Minecraft.getInstance().level.getGameTime());
            this.volume = fade * 0.5F * ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().shadowAuraLoopVolume(); // Scale volume if needed
        }
    }

    public void stopSound() {
        this.stop();
    }
}
