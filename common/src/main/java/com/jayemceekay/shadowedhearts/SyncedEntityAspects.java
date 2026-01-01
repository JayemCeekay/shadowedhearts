package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.api.net.serializers.StringSetDataSerializer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Registry for synced data accessors injected into vanilla entities.
 */
public final class SyncedEntityAspects {
    public static final EntityDataAccessor<Set<String>> ASPECTS = (EntityDataAccessor<Set<String>>) (Object) SynchedEntityData.defineId(LivingEntity.class, StringSetDataSerializer.INSTANCE);

    public static void define(SynchedEntityData.Builder builder) {
        builder.define(ASPECTS, new HashSet<>());
    }
}
