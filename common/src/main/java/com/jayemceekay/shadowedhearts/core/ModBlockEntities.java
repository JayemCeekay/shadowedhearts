package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.blocks.entity.SignalLocatorBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * BlockEntity registry for Shadowed Hearts.
 */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<SignalLocatorBlockEntity>> SIGNAL_LOCATOR_BE = BLOCK_ENTITIES.register(
            "signal_locator",
            () -> BlockEntityType.Builder.of(SignalLocatorBlockEntity::new, ModBlocks.SIGNAL_LOCATOR.get()).build(null)
    );

    /** Call once from common init on both loaders. */
    public static void init() {
        BLOCK_ENTITIES.register();
    }
}
