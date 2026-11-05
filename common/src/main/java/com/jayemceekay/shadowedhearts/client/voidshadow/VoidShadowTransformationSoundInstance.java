package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;

/** A single live transformation cue follows its entity for this client session only. */
final class VoidShadowTransformationSoundInstance extends EntityBoundSoundInstance {
    private final PokemonEntity entity;
    private final Minecraft minecraft;
    private final ClientLevel level;
    private final LocalPlayer player;

    VoidShadowTransformationSoundInstance(PokemonEntity entity, Minecraft minecraft, float volume) {
        super(ModSounds.VOID_SHADOW_TRANSFORM.get(), SoundSource.NEUTRAL, volume, 1f,
                entity, entity.level().random.nextLong());
        this.entity = entity;
        this.minecraft = minecraft;
        this.level = minecraft.level;
        this.player = minecraft.player;
        updatePosition();
    }

    @Override
    public boolean canPlaySound() {
        // This server-authored event is independent of Cobblemon's temporary cry/send-out mute.
        return true;
    }

    @Override
    public void tick() {
        if (level == null || minecraft.level != level || minecraft.player != player
                || entity.isRemoved() || entity.level() != level || level.getEntity(entity.getId()) != entity) {
            stop();
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        x = entity.getX();
        y = entity.getY() + entity.getBbHeight() * 0.5;
        z = entity.getZ();
    }
}
