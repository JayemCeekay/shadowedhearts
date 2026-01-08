package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

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
    }
}
