package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Data components used by ShadowedHearts items.
 * Currently provides an integer energy store for Snag Machines.
 *
 * Uses Architectury DeferredRegister (same pattern as ModBlocks) for cross-loader correctness.
 */
public final class ModItemComponents {
    private ModItemComponents() {}

    /** Architectury deferred register bound to DATA_COMPONENT_TYPE registry. */
    public static final DeferredRegister<DataComponentType<?>> COMPONENT_TYPES =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.DATA_COMPONENT_TYPE);

    public static final RegistrySupplier<DataComponentType<Integer>> SNAG_ENERGY = COMPONENT_TYPES.register(
            "snag_energy",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()
    );

    /** Call once during common init on both loaders. */
    public static void init() {
        COMPONENT_TYPES.register();
    }
}
