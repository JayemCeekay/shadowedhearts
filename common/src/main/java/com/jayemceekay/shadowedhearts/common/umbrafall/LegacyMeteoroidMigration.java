package com.jayemceekay.shadowedhearts.common.umbrafall;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.function.UnaryOperator;

/**
 * A mod ID migration at Minecraft's typed serialization boundaries. Vanilla DFU only
 * advances Minecraft DataVersions, so it cannot migrate worlds already on 1.21.1.
 * These adapters also apply to same-version saves without altering their DataVersion.
 */
public final class LegacyMeteoroidMigration {
    public static final String LEGACY_ID = "shadowedhearts:shadowfall_meteoroid";
    public static final String SHELL_ID = "shadowedhearts:umbrafall_shell";
    public static final String LEGACY_SHELL_FRAGMENT_ID = "shadowedhearts:umbrafall_shell_fragment";
    public static final String SHADOW_SHARD_ID = "shadowedhearts:shadow_shard";
    public static final String LEGACY_CORE_HUSK_ID = "shadowedhearts:dormant_umbrafall_core_husk";
    public static final String LEGACY_CORE_FRAGMENT_ID = "shadowedhearts:umbrafall_core_fragment";
    public static final String LEGACY_INTACT_CORE_ID = "shadowedhearts:intact_umbrafall_core";
    public static final String CORE_SAMPLE_ID = "shadowedhearts:core_sample";
    public static final String LEGACY_STABILIZER_ID = "shadowedhearts:pure_stabilizer_tool";
    public static final String LEGACY_CONTAINMENT_CAPSULE_ID = "shadowedhearts:core_containment_capsule";
    public static final String METEORIC_ALLOY_ID = "shadowedhearts:meteoric_alloy";
    public static final String LEGACY_CLEAR_PHIAL_ID = "shadowedhearts:clear_phial";
    public static final String AGATE_DEW_ID = "shadowedhearts:agate_dew";

    private LegacyMeteoroidMigration() {
    }

    public static String canonicalId(String id) {
        return LEGACY_ID.equals(id) || LEGACY_CORE_HUSK_ID.equals(id) ? SHELL_ID : id;
    }

    public static String canonicalItemId(String id) {
        if (LEGACY_CLEAR_PHIAL_ID.equals(id)) return AGATE_DEW_ID;
        if (LEGACY_SHELL_FRAGMENT_ID.equals(id)) return SHADOW_SHARD_ID;
        if (LEGACY_CORE_FRAGMENT_ID.equals(id) || LEGACY_INTACT_CORE_ID.equals(id)) return CORE_SAMPLE_ID;
        // Salvage retired equipment without invalidating containers holding old stacks.
        if (LEGACY_STABILIZER_ID.equals(id) || LEGACY_CONTAINMENT_CAPSULE_ID.equals(id)) return METEORIC_ALLOY_ID;
        return canonicalId(id);
    }

    /** Wrap only an item/block registry ID codec, never arbitrary strings or mod NBT. */
    public static <A> Codec<A> canonicalIds(Codec<A> delegate) {
        return canonicalIds(delegate, LegacyMeteoroidMigration::canonicalId);
    }

    /** Item-only replacements must not affect block-state or chunk palette IDs. */
    public static <A> Codec<A> canonicalItemIds(Codec<A> delegate) {
        return canonicalIds(delegate, LegacyMeteoroidMigration::canonicalItemId);
    }

    private static <A> Codec<A> canonicalIds(Codec<A> delegate, UnaryOperator<String> canonicalId) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return delegate.decode(ops, canonicalValue(ops, input, canonicalId));
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                // Also retire IDs originating from a legacy /give or third-party registry lookup.
                return delegate.encode(input, ops, prefix).map(value -> canonicalValue(ops, value, canonicalId));
            }

            @Override
            public String toString() {
                return "UmbrafallLegacyId[" + delegate + "]";
            }
        };
    }

    private static <T> T canonicalValue(DynamicOps<T> ops, T value, UnaryOperator<String> canonicalId) {
        return ops.getStringValue(value).result().map(id -> {
            String canonical = canonicalId.apply(id);
            return id.equals(canonical) ? value : ops.createString(canonical);
        }).orElse(value);
    }

    /**
     * Retains palette ordering, packed indices, properties and all unrelated tags. The
     * chunk reader uses the result to mark a changed chunk for its next ordinary save.
     * Called after vanilla's older-version upgrades have produced the current layout.
     */
    public static boolean migrateChunkPalettes(CompoundTag chunk) {
        boolean changed = false;
        ListTag sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            ListTag palette = section.getCompound("block_states").getList("palette", Tag.TAG_COMPOUND);
            for (int stateIndex = 0; stateIndex < palette.size(); stateIndex++) {
                CompoundTag state = palette.getCompound(stateIndex);
                String id = state.getString("Name");
                String canonical = canonicalId(id);
                if (!id.equals(canonical)) {
                    state.putString("Name", canonical);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
