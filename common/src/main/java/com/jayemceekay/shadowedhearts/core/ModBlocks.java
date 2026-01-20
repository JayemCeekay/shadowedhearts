// common/src/main/java/com/jayemceekay/shadowedhearts/core/ModBlocks.java
package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.blocks.PurificationChamberBlock;
import com.jayemceekay.shadowedhearts.blocks.RelicStoneBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> PURIFICATION_PC = BLOCKS.register(
            "purification_pc",
            () -> new PurificationChamberBlock(BlockBehaviour.Properties
                    .of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final RegistrySupplier<Block> RELIC_STONE = BLOCKS.register(
            "relic_stone",
            () -> new RelicStoneBlock(BlockBehaviour.Properties
                    .of().mapColor(MapColor.STONE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.STONE)
                    .noLootTable()
                    .forceSolidOn())
    );

    public static final RegistrySupplier<Block> SHADOWFALL_METEOROID = BLOCKS.register(
            "shadowfall_meteoroid",
            () -> new Block(BlockBehaviour.Properties
                    .ofFullCopy(Blocks.IRON_ORE).requiresCorrectToolForDrops().mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.DEEPSLATE))
    );

    /** Call once during common init on both loaders. */
    public static void init() {
        BLOCKS.register();
    }
}
