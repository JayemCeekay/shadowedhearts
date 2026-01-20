package com.jayemceekay.shadowedhearts.client.sound;

import com.jayemceekay.shadowedhearts.core.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class RelicStoneSoundInstance extends AbstractTickableSoundInstance {
    private final BlockPos pos;
    private final Player player;
    private int tickCount = 0;

    public RelicStoneSoundInstance(BlockPos pos, Player player) {
        super(ModSounds.RELIC_SHRINE_LOOP.get(), SoundSource.BLOCKS, net.minecraft.util.RandomSource.create());
        this.pos = pos;
        this.player = player;
        this.x = (double)pos.getX() + 0.5;
        this.y = (double)pos.getY() + 0.5;
        this.z = (double)pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F; // Start quiet and fade in
        this.relative = false;
    }

    @Override
    public void tick() {
        tickCount++;
        if (this.player != null && this.player.isAlive()) {
            double distSq = this.player.distanceToSqr(this.x, this.y, this.z);
            float maxDist = 16.0F;
            float maxDistSq = maxDist * maxDist;

            if (distSq > maxDistSq) {
                this.volume = Math.max(0.0F, this.volume - 0.05F);
                if (this.volume <= 0.0F && tickCount > 100) {
                    this.stop();
                }
            } else {
                float targetVolume = 1.0F - (float)(Math.sqrt(distSq) / (double)maxDist);
                this.volume = Mth.lerp(0.1F, this.volume, targetVolume);
            }
        } else {
            this.stop();
        }
    }

    public BlockPos getPos() {
        return pos;
    }
}
