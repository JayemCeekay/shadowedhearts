package com.jayemceekay.shadowedhearts.registry;

import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleSelection;
import com.jayemceekay.shadowedhearts.content.items.*;
import com.jayemceekay.shadowedhearts.integration.mega_showdown.MegaShowdownBridgeHolder;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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

    public static final RegistrySupplier<Item> UMBRA_INFUSER = ITEMS.register("umbra_infuser",
            () -> new BlockItem(ModBlocks.UMBRA_INFUSER.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> UMBRA_INFUSER_TANK = ITEMS.register("umbra_infuser_tank",
            () -> new BlockItem(ModBlocks.UMBRA_INFUSER_TANK.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> SHADOW_DUST = ITEMS.register("shadow_dust",
            () -> new Item(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> REFINED_SHADOW_SHARD = ITEMS.register("refined_shadow_shard",
            () -> new Item(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    // Snag Machines: Prototype and Advanced
    public static final RegistrySupplier<Item> SNAG_MACHINE_PROTOTYPE = ITEMS.register(
            "snag_machine_prototype",
            () -> new SnagMachineItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1)));

    public static final RegistrySupplier<Item> SNAG_MACHINE_ADVANCED = ITEMS.register(
            "snag_machine_advanced",
            () -> new SnagMachineItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1)));

    public static final RegistrySupplier<Item> PURIFICATION_PC_ITEM = ITEMS.register(
            "purification_pc",
            () -> new BlockItem(ModBlocks.PURIFICATION_PC.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> RELIC_STONE_ITEM = ITEMS.register(
            "relic_stone",
            () -> new BlockItem(ModBlocks.RELIC_STONE.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> SHADOWFALL_METEOROID_ITEM = ITEMS.register(
            "shadowfall_meteoroid",
            () -> new BlockItem(ModBlocks.UMBRAFALL_SHELL.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> UMBRAFALL_SHELL = registerMeteorBlock("umbrafall_shell", ModBlocks.UMBRAFALL_SHELL);

    public static final RegistrySupplier<Item> CRACKED_UMBRAFALL_SHELL = registerMeteorBlock("cracked_umbrafall_shell", ModBlocks.CRACKED_UMBRAFALL_SHELL);

    public static final RegistrySupplier<Item> UMBRAL_LATTICE = registerMeteorBlock("umbral_lattice", ModBlocks.UMBRAL_LATTICE);

    public static final RegistrySupplier<Item> UMBRAFALL_CORE_CASING = registerMeteorBlock("umbrafall_core_casing", ModBlocks.UMBRAFALL_CORE_CASING);

    public static final RegistrySupplier<Item> UMBRAFALL_VEIN = registerMeteorBlock("umbrafall_vein", ModBlocks.UMBRAFALL_VEIN);

    public static final RegistrySupplier<Item> UMBRAFALL_CRYSTAL = registerMeteorBlock("umbrafall_crystal", ModBlocks.UMBRAFALL_CRYSTAL);

    public static final RegistrySupplier<Item> UMBRAFALL_CORE = registerMeteorBlock("umbrafall_core", ModBlocks.UMBRAFALL_CORE);

    public static final RegistrySupplier<Item> SHADOW_RESIDUE = registerMeteorBlock("shadow_residue", ModBlocks.SHADOW_RESIDUE);

    public static final RegistrySupplier<Item> UMBRAFALL_DEBRIS = registerMeteorBlock("umbrafall_debris", ModBlocks.UMBRAFALL_DEBRIS);

    public static final RegistrySupplier<Item> METEORIC_ALLOY = registerMeteorMaterial("meteoric_alloy");

    public static final RegistrySupplier<Item> CONDENSED_SHADOW = registerMeteorMaterial("condensed_shadow");

    public static final RegistrySupplier<Item> CORE_SAMPLE = registerMeteorMaterial("core_sample");

    // A new ID: pure_stabilizer_tool remains a retired item migrated into alloy.
    public static final RegistrySupplier<Item> PURE_STABILIZER = registerDescribedItem("pure_stabilizer", 2);

    private static Item.Properties meteorItemProperties() {
        return new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB);
    }

    private static RegistrySupplier<Item> registerMeteorBlock(String name, RegistrySupplier<net.minecraft.world.level.block.Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), meteorItemProperties()));
    }

    private static RegistrySupplier<Item> registerMeteorMaterial(String name) {
        return ITEMS.register(name, () -> new Item(meteorItemProperties()));
    }

    // Scents
    public static final RegistrySupplier<Item> JOY_SCENT = registerScent("joy", 1, "soothing", 0xFFA9D1);
    public static final RegistrySupplier<Item> TRANQUIL_SCENT = registerScent("tranquil", 2, "soothing", 0xAFEEEE);
    public static final RegistrySupplier<Item> MEADOW_SCENT = registerScent("meadow", 3, "soothing", 0x90EE90);

    // Stimulating
    public static final RegistrySupplier<Item> SPARK_SCENT = registerScent("spark", 1, "stimulating", 0xFFFF00);
    public static final RegistrySupplier<Item> EXCITE_SCENT = registerScent("excite", 2, "stimulating", 0xFFD700);
    public static final RegistrySupplier<Item> FOCUS_SCENT = registerScent("focus", 3, "stimulating", 0xFF8C00);

    // Affectionate
    public static final RegistrySupplier<Item> FAMILIAR_SCENT = registerScent("familiar", 1, "affectionate", 0xFF00FF);
    public static final RegistrySupplier<Item> COMFORT_SCENT = registerScent("comfort", 2, "affectionate", 0x8B0000);
    public static final RegistrySupplier<Item> HEARTH_SCENT = registerScent("hearth", 3, "affectionate", 0xFFBF00);

    // Clarifying
    public static final RegistrySupplier<Item> INSIGHT_SCENT = registerScent("insight", 1, "clarifying", 0xEE82EE);
    public static final RegistrySupplier<Item> LUCID_SCENT = registerScent("lucid", 2, "clarifying", 0x007FFF);
    public static final RegistrySupplier<Item> VIVID_SCENT = registerScent("vivid", 3, "clarifying", 0x4B0082);

    // Resolute
    public static final RegistrySupplier<Item> GROUNDING_SCENT = registerScent("grounding", 1, "resolute", 0x808000);
    public static final RegistrySupplier<Item> STEADY_SCENT = registerScent("steady", 2, "resolute", 0x708090);
    public static final RegistrySupplier<Item> RESOLVE_SCENT = registerScent("resolve", 3, "resolute", 0x964B00);

    private static RegistrySupplier<Item> registerScent(String name, int tier, String type, int color) {
        return ITEMS.register(name + "_scent", () -> new ScentItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), tier, type, color));
    }

    public static final RegistrySupplier<Item> SHADOW_SHARD = ITEMS.register(
            "shadow_shard",
            () -> new Item(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> PURIFIED_GEM = ITEMS.register(
            "purified_gem",
            () -> new PurifiedGemItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> SHADOW_GEM = ITEMS.register(
            "shadow_gem",
            () -> new ShadowGemItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> PURIFIED_DUST = registerDescribedItem("purified_dust", 2);
    public static final RegistrySupplier<Item> NULL_GLASS = registerDescribedItem("null_glass", 2);
    public static final RegistrySupplier<Item> NULL_CHARM = ITEMS.register("null_charm",
            () -> new NullCharmItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));
    public static final RegistrySupplier<Item> RIVEN_ORB = registerDescribedItem("riven_orb", 2);
    public static final RegistrySupplier<Item> SHADOW_COLLAR = registerDescribedItem("shadow_collar", 2);
    public static final RegistrySupplier<Item> EM_WAVE_AMPLIFIER = registerDescribedItem("em_wave_amplifier", 2);
    public static final RegistrySupplier<Item> DISSONANCE_BAND = registerDescribedItem("dissonance_band", 3);
    public static final RegistrySupplier<Item> BREACH_LENS = registerDescribedItem("breach_lens", 3);
    public static final RegistrySupplier<Item> BITTER_REAWAKENING = ITEMS.register("bitter_reawakening",
            () -> new BitterReawakeningItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(16)));
    public static final RegistrySupplier<Item> CALMING_SACHET = registerDescribedItem("calming_sachet", 2);
    public static final RegistrySupplier<Item> HEART_SOOTHE_BELL = registerDescribedItem("heart_soothe_bell", 2);
    public static final RegistrySupplier<Item> RUIN_POLICY = registerDescribedItem("ruin_policy", 3);

    private static RegistrySupplier<Item> registerDescribedItem(String name, int tooltipLines) {
        return ITEMS.register(name, () -> new DescribedItem(
                new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), tooltipLines));
    }

    public static final RegistrySupplier<Item> SHADOW_GEM_BLOCK = ITEMS.register(
            "shadow_gem_block",
            () -> new BlockItem(ModBlocks.SHADOW_GEM_BLOCK.get(), new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB)));

    public static final RegistrySupplier<Item> SHADOW_GEM_CLUSTER = ITEMS.register(
            "shadow_gem_cluster",
            () -> new BlockItem(ModBlocks.SHADOW_GEM_CLUSTER.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> SHADOWIUM_Z = ITEMS.register(
            "shadowium_z",
            () -> MegaShowdownBridgeHolder.INSTANCE.createShadowiumZ(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1)));

    public static final RegistrySupplier<Item> SHADOW_SCALE = ITEMS.register(
            "shadow_scale",
            () -> new ShadowScale(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(16)));

    public static final RegistrySupplier<Item> AURA_READER = ITEMS.register(
            "aura_reader",
            () -> new AuraReaderItem(new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(1)));

    public static final RegistrySupplier<Item> COLOSSEUM_EMBER = registerAuraCatalyst("colosseum_ember", AuraStyleSelection.COLOSSEUM);
    public static final RegistrySupplier<Item> GALE_PHIAL = registerAuraCatalyst("gale_phial", AuraStyleSelection.XD_FAITHFUL);
    public static final RegistrySupplier<Item> UMBRAFALL_PRISM = registerAuraCatalyst("umbrafall_prism", AuraStyleSelection.SIGNATURE);
    public static final RegistrySupplier<Item> AGATE_DEW = registerAuraCatalyst("agate_dew", AuraStyleSelection.DEFAULT);

    private static RegistrySupplier<Item> registerAuraCatalyst(String name, AuraStyleSelection style) {
        return ITEMS.register(name, () -> new AuraCatalystItem(
                new Item.Properties().arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB).stacksTo(16), style));
    }

    public static final RegistrySupplier<Item> DIRECTION_ARROW = ITEMS.register(
            "direction_arrow",
            () -> new Item(new Item.Properties()));

    // Shadow Signal Data — tiered hunt-initiating items (tiers 1–5)
    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_FAINT = registerSignalData("faint", UmbrafallTier.FAINT);

    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_WEAK = registerSignalData("weak", UmbrafallTier.WEAK);

    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_MODERATE = registerSignalData("moderate", UmbrafallTier.MODERATE);

    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_STRONG = registerSignalData("strong", UmbrafallTier.STRONG);

    public static final RegistrySupplier<Item> SHADOW_SIGNAL_DATA_RESONANT = registerSignalData("resonant", UmbrafallTier.RESONANT);

    private static RegistrySupplier<Item> registerSignalData(String name, UmbrafallTier tier) {
        return ITEMS.register("shadow_signal_data_" + name,
                () -> new ShadowSignalDataItem(
                        meteorItemProperties().stacksTo(16),
                        tier));
    }

    public static final RegistrySupplier<Item> PENUMBRA_BALL = ITEMS.register(
            "penumbra_ball",
            () -> {
                var id = ResourceLocation.fromNamespaceAndPath("cobblemon", "penumbra_ball");
                var pb = PokeBalls.getPokeBall(id);
                if (pb == null) {
                    throw new IllegalStateException("PokeBall 'cobblemon:penumbra_ball' was not registered (mixin failed?).");
                }
                PokeBallItem item = new PokeBallItem(pb);
                pb.item = item;
                return item;
            });

    public static final RegistrySupplier<Item> ANTUMBRA_BALL = ITEMS.register(
            "antumbra_ball",
            () -> {
                var id = ResourceLocation.fromNamespaceAndPath("cobblemon", "antumbra_ball");
                var pb = PokeBalls.getPokeBall(id);
                if (pb == null) {
                    throw new IllegalStateException("PokeBall 'cobblemon:antumbra_ball' was not registered (mixin failed?).");
                }
                PokeBallItem item = new PokeBallItem(pb);
                pb.item = item;
                return item;
            });

    public static final RegistrySupplier<Item> UMBRA_BALL = ITEMS.register(
            "umbra_ball",
            () -> {
                var id = ResourceLocation.fromNamespaceAndPath("cobblemon", "umbra_ball");
                var pb = PokeBalls.getPokeBall(id);
                if (pb == null) {
                    throw new IllegalStateException("PokeBall 'cobblemon:umbra_ball' was not registered (mixin failed?).");
                }
                PokeBallItem item = new PokeBallItem(pb);
                pb.item = item;
                return item;
            });

    public static final RegistrySupplier<Item> DARK_BALL = ITEMS.register(
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
            });

    public static void init() {
        ITEMS.register();
        LifecycleEvent.SETUP.register(() -> {
            CobblemonHeldItemManager.INSTANCE.registerRemap(SHADOW_GEM.get(), "shadowgem");
            CobblemonHeldItemManager.INSTANCE.registerRemap(NULL_CHARM.get(), "nullcharm");
            CobblemonHeldItemManager.INSTANCE.registerRemap(RIVEN_ORB.get(), "rivenorb");
            CobblemonHeldItemManager.INSTANCE.registerRemap(SHADOW_COLLAR.get(), "shadowcollar");
            CobblemonHeldItemManager.INSTANCE.registerRemap(EM_WAVE_AMPLIFIER.get(), "emwaveamplifier");
            CobblemonHeldItemManager.INSTANCE.registerRemap(DISSONANCE_BAND.get(), "dissonanceband");
            CobblemonHeldItemManager.INSTANCE.registerRemap(BREACH_LENS.get(), "breachlens");
            CobblemonHeldItemManager.INSTANCE.registerRemap(CALMING_SACHET.get(), "calmingsachet");
            CobblemonHeldItemManager.INSTANCE.registerRemap(HEART_SOOTHE_BELL.get(), "heartsoothebell");
            CobblemonHeldItemManager.INSTANCE.registerRemap(RUIN_POLICY.get(), "ruinpolicy");
        });
    }

    /** Reverse mapping used by native held-item transfers such as Trick and Recycle. */
    public static Item heldItemForShowdownId(String showdownId) {
        return switch (showdownId) {
            case "shadowgem" -> SHADOW_GEM.get();
            case "nullcharm" -> NULL_CHARM.get();
            case "rivenorb" -> RIVEN_ORB.get();
            case "shadowcollar" -> SHADOW_COLLAR.get();
            case "emwaveamplifier" -> EM_WAVE_AMPLIFIER.get();
            case "dissonanceband" -> DISSONANCE_BAND.get();
            case "breachlens" -> BREACH_LENS.get();
            case "calmingsachet" -> CALMING_SACHET.get();
            case "heartsoothebell" -> HEART_SOOTHE_BELL.get();
            case "ruinpolicy" -> RUIN_POLICY.get();
            default -> null;
        };
    }
}
