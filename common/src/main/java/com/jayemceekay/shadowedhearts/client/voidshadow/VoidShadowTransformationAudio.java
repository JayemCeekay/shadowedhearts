package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.voidshadow.VoidShadowTransformPacket;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** One confirmed appearance change starts both the visual clock and its preloaded one-shot. */
public final class VoidShadowTransformationAudio {
    private static final VoidShadowTransformationQueue QUEUE = new VoidShadowTransformationQueue();

    private VoidShadowTransformationAudio() { }

    public static void accept(VoidShadowTransformPacket event, Minecraft minecraft) {
        syncSession(minecraft);
        if (minecraft.level == null || minecraft.player == null
                || !event.getDimension().equals(minecraft.level.dimension().location())) return;
        QUEUE.accept(event, minecraft.level.getGameTime());
        tick(minecraft);
    }

    /** Called even for offscreen entities; render passes may also resolve newly arrived metadata. */
    public static void tick(Minecraft minecraft) {
        syncSession(minecraft);
        var level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) return;
        long tick = level.getGameTime();
        var ready = QUEUE.ready(tick, event -> {
            var entity = level.getEntity(event.getEntityId());
            if (entity == null) return VoidShadowTransformationQueue.Readiness.WAIT;
            if (!(entity instanceof PokemonEntity pokemon) || entity.isRemoved()
                    || !entity.getUUID().equals(event.getEntityUuid())) return VoidShadowTransformationQueue.Readiness.DROP;
            return event.getExpectedSpecies().equals(pokemon.getPokemon().getSpecies().getResourceIdentifier())
                    && event.getExpectedAspects().equals(pokemon.getAspects())
                    ? VoidShadowTransformationQueue.Readiness.READY : VoidShadowTransformationQueue.Readiness.WAIT;
        });
        for (var event : ready) {
            PokemonEntity entity = (PokemonEntity) level.getEntity(event.getEntityId());
            VoidShadowTransitions.beginTransformation(entity, tick);
            float volume = ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().voidShadowTransformVolume();
            if (volume > 0 && minecraft.player.distanceToSqr(entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5, entity.getZ()) <= 32 * 32) {
                minecraft.getSoundManager().play(new VoidShadowTransformationSoundInstance(entity, minecraft, volume));
            }
        }
    }

    static boolean isPending(UUID entity) { return QUEUE.isPending(entity); }

    private static void syncSession(Minecraft minecraft) {
        QUEUE.syncSession(minecraft.level, minecraft.player,
                minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime());
    }
}
