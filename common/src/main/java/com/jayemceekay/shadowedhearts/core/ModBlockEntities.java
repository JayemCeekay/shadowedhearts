package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.blocks.entity.PurificationChamberBlockEntity;
import com.jayemceekay.shadowedhearts.blocks.entity.RelicStoneBlockEntity;
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

    public static final RegistrySupplier<BlockEntityType<RelicStoneBlockEntity>> RELIC_STONE_BE = BLOCK_ENTITIES.register(
            "relic_stone",
            () -> BlockEntityType.Builder.of(RelicStoneBlockEntity::new, ModBlocks.RELIC_STONE.get()).build(null)
    );

    public static final RegistrySupplier<BlockEntityType<PurificationChamberBlockEntity>> PURIFICATION_CHAMBER_BE = BLOCK_ENTITIES.register(
            "purification_pc",
            () -> BlockEntityType.Builder.of(PurificationChamberBlockEntity::new, ModBlocks.PURIFICATION_PC.get()).build(null)
    );

    /** Call once from common init on both loaders. */
    public static void init() {
        BLOCK_ENTITIES.register();
    }
}
