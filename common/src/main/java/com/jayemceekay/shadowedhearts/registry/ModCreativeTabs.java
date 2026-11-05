package com.jayemceekay.shadowedhearts.registry;

import com.cobblemon.mod.common.block.TypeGemClusterBlock;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> SHADOWED_HEARTS_TAB = TABS.register(
            "shadowed_hearts_tab",
            () -> CreativeTabRegistry.create(
                    Component.translatable("itemGroup.shadowedhearts.main"),
                    () -> new ItemStack(ModItems.SHADOW_SHARD.get())
            )
    );

    public static void init() {
        TABS.register();
        for (int stage : TypeGemClusterBlock.Companion.getSTAGE().getPossibleValues()) {
            CreativeTabRegistry.appendStack(SHADOWED_HEARTS_TAB, () -> {
                ItemStack stack = new ItemStack(ModItems.SHADOW_GEM_CLUSTER.get());
                stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
                        .with(TypeGemClusterBlock.Companion.getSTAGE(), stage)
                        .with(TypeGemClusterBlock.Companion.getGROWTH_TYPE(), TypeGemClusterBlock.GrowthType.DECORATION));
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(stage));
                return stack;
            });
        }
    }
}
