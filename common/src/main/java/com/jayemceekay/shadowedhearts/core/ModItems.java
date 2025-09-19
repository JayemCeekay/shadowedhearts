package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.signals.FragmentKind;
import com.jayemceekay.shadowedhearts.signals.FragmentRarity;
import com.jayemceekay.shadowedhearts.signals.MissionSignalItem;
import com.jayemceekay.shadowedhearts.signals.SignalFragmentItem;
import com.jayemceekay.shadowedhearts.snag.SnagBallItem;
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

    // Mission Signal (key used by the Gateway)
    public static final RegistrySupplier<Item> MISSION_SIGNAL = ITEMS.register(
            "mission_signal",
            () -> new MissionSignalItem(new Item.Properties().stacksTo(1)));

    // Snag Machine Gauntlet and Snag Ball
    public static final RegistrySupplier<Item> SNAG_MACHINE = ITEMS.register(
            "snag_machine",
            () -> new SnagMachineItem(new Item.Properties().stacksTo(1)));
    public static final RegistrySupplier<Item> SNAG_BALL = ITEMS.register(
            "snag_ball",
            () -> new SnagBallItem(new Item.Properties()));

    // BlockItems for blocks so we can obtain/place them
    public static final RegistrySupplier<Item> SIGNAL_LOCATOR_ITEM = ITEMS.register(
            "signal_locator",
            () -> new BlockItem(ModBlocks.SIGNAL_LOCATOR.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> MISSION_GATEWAY_ITEM = ITEMS.register(
            "mission_gateway",
            () -> new BlockItem(ModBlocks.MISSION_GATEWAY.get(), new Item.Properties()));

    // PokeTOSS
    public static final RegistrySupplier<Item> PASTURE_BLOCK_ITEM = ITEMS.register(
            "pasture_block",
            () -> new BlockItem(ModBlocks.PASTURE_BLOCK.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> TRAINERS_WHISTLE = ITEMS.register(
            "trainers_whistle",
            () -> new com.jayemceekay.shadowedhearts.poketoss.TrainersWhistleItem(new Item.Properties().stacksTo(1)));

    // --- Theme fragments (examples) ---
    public static final RegistrySupplier<Item> FRAGMENT_THEME_GRAVE = ITEMS.register(
            "fragment_theme_grave",
            () -> new SignalFragmentItem(new Item.Properties(), FragmentKind.THEME, "grave", FragmentRarity.UNCOMMON,
                    "A cold whisper traces these shards."));

    public static final RegistrySupplier<Item> FRAGMENT_THEME_JUNGLE = ITEMS.register(
            "fragment_theme_jungle",
            () -> new SignalFragmentItem(new Item.Properties(), FragmentKind.THEME, "jungle", FragmentRarity.COMMON,
                    "Verdant shards pulsing with distant drums."));

    // --- Tier fragments ---
    public static final RegistrySupplier<Item> FRAGMENT_TIER_1 = ITEMS.register(
            "fragment_tier_1",
            () -> new SignalFragmentItem(new Item.Properties(), FragmentKind.TIER, "1", FragmentRarity.COMMON,
                    "Tier I"));
    public static final RegistrySupplier<Item> FRAGMENT_TIER_2 = ITEMS.register(
            "fragment_tier_2",
            () -> new SignalFragmentItem(new Item.Properties(), FragmentKind.TIER, "2", FragmentRarity.UNCOMMON,
                    "Tier II"));
    public static final RegistrySupplier<Item> FRAGMENT_TIER_3 = ITEMS.register(
            "fragment_tier_3",
            () -> new SignalFragmentItem(new Item.Properties(), FragmentKind.TIER, "3", FragmentRarity.RARE,
                    "Tier III"));

    // --- Affix fragments (example) ---
    public static final RegistrySupplier<Item> FRAGMENT_AFFIX_HAZE = ITEMS.register(
            "fragment_affix_haze",
            () -> new SignalFragmentItem(new Item.Properties(), FragmentKind.AFFIX, "haze", FragmentRarity.RARE,
                    "Your vision dims slightly as you hold it."));

    // --- Puzzle fragments (example) ---
    public static final RegistrySupplier<Item> FRAGMENT_PUZZLE_RUNES = ITEMS.register(
            "fragment_puzzle_runes",
            () -> new SignalFragmentItem(new Item.Properties(), FragmentKind.PUZZLE, "runes", FragmentRarity.UNCOMMON,
                    "Ancient runes hum softly."));

    public static void init() {
        ITEMS.register();
    }
}
