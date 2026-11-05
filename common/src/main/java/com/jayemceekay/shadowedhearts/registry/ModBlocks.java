// common/src/main/java/com/jayemceekay/shadowedhearts/core/ModBlocks.java
package com.jayemceekay.shadowedhearts.registry;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.block.TypeGemClusterBlock;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.content.blocks.PurificationChamberBlock;
import com.jayemceekay.shadowedhearts.content.blocks.RelicStoneBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.resources.ResourceLocation;
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

    public static final RegistrySupplier<Block> SHADOW_GEM_BLOCK = BLOCKS.register(
            "shadow_gem_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 6.0F)
                    .sound(CobblemonSounds.TYPE_GEM_BLOCK_SOUNDS)));

    // Cobblemon represents the small, medium, large, and mature forms with stage=0..3.
    public static final RegistrySupplier<Block> SHADOW_GEM_CLUSTER = BLOCKS.register(
            "shadow_gem_cluster", () -> {
                TypeGemClusterBlock cluster = new TypeGemClusterBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 3.0F)
                        .sound(CobblemonSounds.TYPE_GEM_CLUSTER_SOUNDS), SHADOW_GEM_BLOCK.get(),
                        ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_gem"));
                TypeGemClusterBlock.Companion.getGemToClusterMap().put(SHADOW_GEM_BLOCK.get(), cluster);
                return cluster;
            });

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
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE)
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
