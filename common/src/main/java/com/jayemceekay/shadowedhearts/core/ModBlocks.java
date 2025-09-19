// common/src/main/java/com/jayemceekay/shadowedhearts/core/ModBlocks.java
package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;

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

    // ===== Examples — replace with your actual blocks =====
    public static final RegistrySupplier<Block> SIGNAL_LOCATOR = BLOCKS.register(
            "signal_locator",
            () -> new Block(BlockBehaviour.Properties
                    .of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion())  // if your model has holes/cutouts
    );

    public static final RegistrySupplier<Block> MISSION_GATEWAY = BLOCKS.register(
            "mission_gateway",
            () -> new Block(BlockBehaviour.Properties
                    .of().mapColor(MapColor.COLOR_BLACK)
                    .strength(3.5F)
                    .sound(SoundType.STONE))
    );

    /** Call once during common init on both loaders. */
    public static void init() {
        BLOCKS.register();
    }
}
