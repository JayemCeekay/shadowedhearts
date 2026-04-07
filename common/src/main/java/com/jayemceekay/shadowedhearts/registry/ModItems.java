package com.jayemceekay.shadowedhearts.registry;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalTier;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.content.items.*;
import com.jayemceekay.shadowedhearts.integration.mega_showdown.MegaShowdownBridgeHolder;
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
            () -> new SnagMachineItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1),
                    ShadowedHeartsConfigs.getInstance().getSnagConfig()::prototypeCapacity));

    public static final RegistrySupplier<Item> SNAG_MACHINE_ADVANCED = ITEMS.register(
            "snag_machine_advanced",
            () -> new SnagMachineItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1),
                    ShadowedHeartsConfigs.getInstance().getSnagConfig()::advancedCapacity));

    public static final RegistrySupplier<Item> PURIFICATION_PC_ITEM = ITEMS.register(
            "purification_pc",
            () -> new BlockItem(ModBlocks.PURIFICATION_PC.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> RELIC_STONE_ITEM = ITEMS.register(
            "relic_stone",
            () -> new BlockItem(ModBlocks.RELIC_STONE.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> SHADOWFALL_METEOROID_ITEM = ITEMS.register(
            "shadowfall_meteoroid",
            () -> new BlockItem(ModBlocks.SHADOWFALL_METEOROID.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    // Scents
    public static final RegistrySupplier<Item> JOY_SCENT = ITEMS.register(
            "joy_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 1, "soothing", 0xFFA9D1)); // Soft Pink
    public static final RegistrySupplier<Item> TRANQUIL_SCENT = ITEMS.register(
            "tranquil_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 2, "soothing", 0xAFEEEE)); // Pale Blue
    public static final RegistrySupplier<Item> MEADOW_SCENT = ITEMS.register(
            "meadow_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 3, "soothing", 0x90EE90)); // Light Green

    // Stimulating
    public static final RegistrySupplier<Item> SPARK_SCENT = ITEMS.register(
            "spark_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 1, "stimulating", 0xFFFF00)); // Vibrant Yellow
    public static final RegistrySupplier<Item> EXCITE_SCENT = ITEMS.register(
            "excite_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 2, "stimulating", 0xFFD700)); // Bright Gold
    public static final RegistrySupplier<Item> FOCUS_SCENT = ITEMS.register(
            "focus_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 3, "stimulating", 0xFF8C00)); // Sharp Orange

    // Affectionate
    public static final RegistrySupplier<Item> FAMILIAR_SCENT = ITEMS.register(
            "familiar_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 1, "affectionate", 0xFF00FF)); // Magenta
    public static final RegistrySupplier<Item> COMFORT_SCENT = ITEMS.register(
            "comfort_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 2, "affectionate", 0x8B0000)); // Deep Red
    public static final RegistrySupplier<Item> HEARTH_SCENT = ITEMS.register(
            "hearth_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 3, "affectionate", 0xFFBF00)); // Warm Amber

    // Clarifying
    public static final RegistrySupplier<Item> INSIGHT_SCENT = ITEMS.register(
            "insight_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 1, "clarifying", 0xEE82EE)); // Soft Violet
    public static final RegistrySupplier<Item> LUCID_SCENT = ITEMS.register(
            "lucid_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 2, "clarifying", 0x007FFF)); // Azure
    public static final RegistrySupplier<Item> VIVID_SCENT = ITEMS.register(
            "vivid_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 3, "clarifying", 0x4B0082)); // Deep Indigo

    // Resolute
    public static final RegistrySupplier<Item> GROUNDING_SCENT = ITEMS.register(
            "grounding_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 1, "resolute", 0x808000)); // Olive Green
    public static final RegistrySupplier<Item> STEADY_SCENT = ITEMS.register(
            "steady_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 2, "resolute", 0x708090)); // Slate Gray
    public static final RegistrySupplier<Item> RESOLVE_SCENT = ITEMS.register(
            "resolve_scent",
            () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), 3, "resolute", 0x964B00)); // Tawny Brown

    public static final RegistrySupplier<Item> SHADOW_SHARD = ITEMS.register(
            "shadow_shard",
            () -> new Item(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> PURIFIED_GEM = ITEMS.register(
            "purified_gem",
            () -> new PurifiedGemItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> SHADOWIUM_Z = ITEMS.register(
            "shadowium_z",
            () -> MegaShowdownBridgeHolder.INSTANCE.createShadowiumZ(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1)));

    public static final RegistrySupplier<Item> AURA_READER = ITEMS.register(
            "aura_reader",
            () -> new AuraReaderItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1)));

    public static final RegistrySupplier<Item> DIRECTION_ARROW = ITEMS.register(
            "direction_arrow",
            () -> new Item(new Item.Properties()));

   /* public static final RegistrySupplier<Item> POKEDEX_INTEGRATOR = ITEMS.register(
            "pokedex_integrator",
            () -> new com.jayemceekay.shadowedhearts.content.items.PokedexIntegratorItem(new net.minecraft.world.item.Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1))
    );*/

    // Shadow Signal Data — tiered hunt-initiating items (tiers 1–5)
    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_FAINT = ITEMS.register(
            "shadow_signal_data_faint",
            () -> new ShadowSignalDataItem(
                    new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(16)
                            .rarity(ShadowSignalDataItem.tierToRarity(ShadowSignalTier.FAINT)),
                    ShadowSignalTier.FAINT));

    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_WEAK = ITEMS.register(
            "shadow_signal_data_weak",
            () -> new ShadowSignalDataItem(
                    new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(16)
                            .rarity(ShadowSignalDataItem.tierToRarity(ShadowSignalTier.WEAK)),
                    ShadowSignalTier.WEAK));

    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_MODERATE = ITEMS.register(
            "shadow_signal_data_moderate",
            () -> new ShadowSignalDataItem(
                    new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(16)
                            .rarity(ShadowSignalDataItem.tierToRarity(ShadowSignalTier.MODERATE)),
                    ShadowSignalTier.MODERATE));

    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_STRONG = ITEMS.register(
            "shadow_signal_data_strong",
            () -> new ShadowSignalDataItem(
                    new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(16)
                            .rarity(ShadowSignalDataItem.tierToRarity(ShadowSignalTier.STRONG)),
                    ShadowSignalTier.STRONG));

    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_RESONANT = ITEMS.register(
            "shadow_signal_data_resonant",
            () -> new ShadowSignalDataItem(
                    new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(16)
                            .rarity(ShadowSignalDataItem.tierToRarity(ShadowSignalTier.RESONANT)),
                    ShadowSignalTier.RESONANT));

    /*public static final RegistrySupplier<Item> DARK_BALL = ITEMS.register(
            "dark_ball",
            () -> {
                var id = ResourceLocation.fromNamespaceAndPath("cobblemon", "dark_ball");
                var pb = PokeBalls.getPokeBall(id);
                if (pb == null) {
                    throw new IllegalStateException("PokeBall 'cobblemon:dark_ball' was not registered (mixin failed?).");
                }
                PokeBallItem item = new PokeBallItem(pb);
                pb.item = item;
                return item;
            });*/

    public static void init() {
        ITEMS.register();
    }
}
