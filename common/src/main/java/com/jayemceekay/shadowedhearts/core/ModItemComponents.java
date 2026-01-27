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

    public static final RegistrySupplier<DataComponentType<Boolean>> SNAG_ARMED = COMPONENT_TYPES.register(
            "snag_armed",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    public static final RegistrySupplier<DataComponentType<Integer>> SNAG_COOLDOWN = COMPONENT_TYPES.register(
            "snag_cooldown",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()
    );

    public static final RegistrySupplier<DataComponentType<Boolean>> SNAG_ELIGIBLE = COMPONENT_TYPES.register(
            "snag_eligible",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    public static final RegistrySupplier<DataComponentType<Integer>> SNAG_FAIL_ATTEMPTS = COMPONENT_TYPES.register(
            "snag_fail_attempts",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()
    );

    public static final RegistrySupplier<DataComponentType<Boolean>> AURA_SCANNER_ACTIVE = COMPONENT_TYPES.register(
            "aura_scanner_active",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    public static final RegistrySupplier<DataComponentType<Integer>> AURA_READER_CHARGE = COMPONENT_TYPES.register(
            "aura_reader_charge",
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
