// common/src/main/java/com/jayemceekay/shadowedhearts/core/ModBlocks.java
package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.blocks.PurificationChamberBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

// If you have block entities, keep them here too (see section below).
// import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlocks {
    private ModBlocks() {}

    /** Architectury deferred register bound to BLOCK registry. */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.BLOCK);

    // Purification Chamber (MVP UI container block)
    public static final RegistrySupplier<Block> PURIFICATION_PC = BLOCKS.register(
            "purification_pc",
            () -> new PurificationChamberBlock(BlockBehaviour.Properties
                    .of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    /** Call once during common init on both loaders. */
    public static void init() {
        BLOCKS.register();
    }
}
