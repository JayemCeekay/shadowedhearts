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
    public static final EntityDataAccessor<Float>   CORRUPTION = SynchedEntityData.defineId(PokemonEntity.class, EntityDataSerializers.FLOAT);

    /** Define default values into the entity's data container. Called from mixin. */
    public static void define(SynchedEntityData.Builder builder) {
        builder.define(SHADOW, false);
        builder.define(CORRUPTION, 0f);
    }

    /** Update both flags on a live PokemonEntity (values are clamped appropriately). */
    public static void set(PokemonEntity e, boolean shadow, float corruption) {
        e.getEntityData().set(SHADOW, shadow);
        e.getEntityData().set(CORRUPTION, Mth.clamp(corruption, 0f, 1f));
    }

    public static void bootstrap() { /* no-op */ }

    public static boolean isShadow(PokemonEntity e) { return e.getEntityData().get(SHADOW); }
    public static float getCorruption(PokemonEntity e) { return e.getEntityData().get(CORRUPTION); }

    @Override
    public boolean shadowedHearts$isShadow() {
        return isShadow((PokemonEntity) (Object) this);
    }

    @Override
    public float shadowedHearts$getCorruption() {
        return getCorruption((PokemonEntity) (Object) this);
    }
}
