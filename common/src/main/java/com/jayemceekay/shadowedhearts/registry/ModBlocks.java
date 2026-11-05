// common/src/main/java/com/jayemceekay/shadowedhearts/core/ModBlocks.java
package com.jayemceekay.shadowedhearts.registry;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.block.TypeGemClusterBlock;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.content.blocks.PurificationChamberBlock;
import com.jayemceekay.shadowedhearts.content.blocks.RelicStoneBlock;
import com.jayemceekay.shadowedhearts.content.blocks.UmbraInfuserBlock;
import com.jayemceekay.shadowedhearts.content.blocks.UmbraInfuserTankBlock;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallCoreBlock;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallDebrisBlock;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallMaterialBlock;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallShellBlock;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbralLatticeBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    private ModBlocks() {
    }

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

    public static final RegistrySupplier<Block> UMBRA_INFUSER = BLOCKS.register(
            "umbra_infuser", () -> new UmbraInfuserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion()));

    public static final RegistrySupplier<Block> UMBRA_INFUSER_TANK = BLOCKS.register(
            "umbra_infuser_tank", () -> new UmbraInfuserTankBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion()));

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
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)
                    .forceSolidOn())
    );

    public static final RegistrySupplier<Block> SHADOWFALL_METEOROID = BLOCKS.register(
            "shadowfall_meteoroid",
            () -> new Block(BlockBehaviour.Properties
                    .ofFullCopy(Blocks.IRON_ORE).requiresCorrectToolForDrops().mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.DEEPSLATE))
    );

    public static final RegistrySupplier<Block> UMBRAFALL_SHELL = BLOCKS.register(
            "umbrafall_shell", () -> new UmbrafallShellBlock(meteorProperties(8.0F, 24.0F)));

    public static final RegistrySupplier<Block> CRACKED_UMBRAFALL_SHELL = BLOCKS.register(
            "cracked_umbrafall_shell", () -> new UmbrafallShellBlock(meteorProperties(8.0F, 24.0F)));

    public static final RegistrySupplier<Block> UMBRAL_LATTICE = BLOCKS.register(
            "umbral_lattice", () -> new UmbralLatticeBlock(meteorProperties(6.0F, 18.0F)));

    public static final RegistrySupplier<Block> UMBRAFALL_CORE_CASING = BLOCKS.register(
            "umbrafall_core_casing", () -> new UmbrafallShellBlock(meteorProperties(12.0F, 36.0F)));

    public static final RegistrySupplier<Block> UMBRAFALL_VEIN = BLOCKS.register(
            "umbrafall_vein", () -> new UmbrafallMaterialBlock(meteorProperties(10.0F, 24.0F)));

    public static final RegistrySupplier<Block> UMBRAFALL_CRYSTAL = BLOCKS.register(
            "umbrafall_crystal", () -> new UmbrafallMaterialBlock(meteorProperties(12.0F, 30.0F)
                    .sound(SoundType.AMETHYST).lightLevel(state -> 4)));

    public static final RegistrySupplier<Block> UMBRAFALL_CORE = BLOCKS.register(
            "umbrafall_core", () -> new UmbrafallCoreBlock(meteorProperties(15.0F, 60.0F)
                    .lightLevel(state -> 7).pushReaction(PushReaction.BLOCK)));

    public static final RegistrySupplier<Block> SHADOW_RESIDUE = BLOCKS.register(
            "shadow_residue", () -> new UmbrafallMaterialBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(0.2F).sound(SoundType.SAND)
                    .noOcclusion().pushReaction(PushReaction.DESTROY), 2.0));

    public static final RegistrySupplier<Block> UMBRAFALL_DEBRIS = BLOCKS.register(
            "umbrafall_debris", () -> new UmbrafallDebrisBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(1.5F, 6.0F).sound(SoundType.DEEPSLATE)
                    .noOcclusion()));

    private static BlockBehaviour.Properties meteorProperties(float hardness, float resistance) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                .strength(hardness, resistance).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE);
    }

    /**
     * Call once during common init on both loaders.
     */
    public static void init() {
        BLOCKS.register();
    }
}
