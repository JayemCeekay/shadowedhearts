package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.items.ScentItem;
import com.jayemceekay.shadowedhearts.snag.SnagMachineItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/**
 * Item registry for Shadowed Hearts. Adds Signal Fragment items (MVP subset) and Mission Signal.
 */
public final class ModItems {
    private ModItems() {
    }

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.ITEM);

    // Snag Machines: Prototype and Advanced
    public static final RegistrySupplier<Item> SNAG_MACHINE_PROTOTYPE = ITEMS.register(
            "snag_machine_prototype",
            () -> new SnagMachineItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1), 50, 10));

    public static final RegistrySupplier<Item> SNAG_MACHINE_ADVANCED = ITEMS.register(
            "snag_machine_advanced",
            () -> new SnagMachineItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1), 100, 5));

    public static final RegistrySupplier<Item> PURIFICATION_PC_ITEM = ITEMS.register(
            "purification_pc",
            () -> new BlockItem(ModBlocks.PURIFICATION_PC.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    // Scents
    public static final RegistrySupplier<Item> JOY_SCENT = ITEMS.register(
            "joy_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 1));
    public static final RegistrySupplier<Item> EXCITE_SCENT = ITEMS.register(
            "excite_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 2));
    public static final RegistrySupplier<Item> VIVID_SCENT = ITEMS.register(
            "vivid_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 3));

    public static void init() {
        ITEMS.register();
    }
}
