package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;

/**
 * Synced data accessors attached to PokemonEntity via mixin.
 * Provides runtime flags for whether an entity is shadowed and its corruption value.
 */
public class ShadowPokemonData implements ShadowFlag {
    /** Synced boolean: whether the PokemonEntity is a shadow. */
    public static final EntityDataAccessor<Boolean> SHADOW = SynchedEntityData.defineId(PokemonEntity.class, EntityDataSerializers.BOOLEAN);
    /** Synced float [0..1]: corruption intensity for visual/behavioral effects. */
    public static final EntityDataAccessor<Float> HEART_GAUGE = SynchedEntityData.defineId(PokemonEntity.class, EntityDataSerializers.FLOAT);

    /** Define default values into the entity's data container. Called from mixin. */
    public static void define(SynchedEntityData.Builder builder) {
        builder.define(SHADOW, false);
        builder.define(HEART_GAUGE, 0f);
    }

    /** Update both flags on a live PokemonEntity (values are clamped appropriately). */
    public static void set(PokemonEntity e, boolean shadow, float corruption) {
        e.getEntityData().set(SHADOW, shadow);
        // corruption is expected as 0..1 scalar for visuals
        e.getEntityData().set(HEART_GAUGE, Mth.clamp(corruption, 0f, 1f));
        PokemonAspectUtil.syncAspects(e.getPokemon());
        PokemonAspectUtil.syncBenchedMoves(e.getPokemon());
        PokemonAspectUtil.syncMoveSet(e.getPokemon());
    }

    public static void bootstrap() { /* no-op */ }

    public static boolean isShadow(PokemonEntity e) { return e.getEntityData().get(SHADOW); }
    public static float getHeartGauge(PokemonEntity e) { return e.getEntityData().get(HEART_GAUGE); }

    @Override
    public boolean shadowedHearts$isShadow() {
        return isShadow((PokemonEntity) (Object) this);
    }

    @Override
    public float shadowedHearts$getCorruption() {
        return getHeartGauge((PokemonEntity) (Object) this);
    }
}
