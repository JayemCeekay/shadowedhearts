package com.jayemceekay.shadowedhearts.content.items;

import com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalTier;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraPulsePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;

/**
 * Shadow Signal Data — a consumable clue item that initiates a Shadow Pokémon hunt.
 * <p>
 * The player obtains this item, travels to the appropriate region, and right-clicks
 * while the Aura Reader HUD is active to send a pulse and start the tracking minigame.
 * <p>
 * Each item has a {@link ShadowSignalTier} that determines:
 * <ul>
 *   <li>Number and complexity of hunt nodes</li>
 *   <li>Calibration sequence difficulty</li>
 *   <li>Encounter quality (species rarity pool, level range, IV quality)</li>
 * </ul>
 * <p>
 * Items can optionally carry a seed for deterministic hunt generation, and an
 * origin region tag to enforce location-based activation.
 */
public class ShadowSignalDataItem extends Item {

    private static final String TAG_ROOT = "ShadowSignal";
    private static final String TAG_SEED = "Seed";
    private static final String TAG_ORIGIN = "Origin";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_RADIUS = "Radius";

    private final ShadowSignalTier tier;

    public ShadowSignalDataItem(Properties properties, ShadowSignalTier tier) {
        super(properties);
        this.tier = tier;
    }

    public ShadowSignalTier getTier() {
        return tier;
    }

    // ── NBT helpers ──

    /**
     * Write a hunt seed into the item's custom data.
     * If no seed is set, one will be generated on activation.
     */
    public static void setSeed(ItemStack stack, long seed) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = existing.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        signal.putLong(TAG_SEED, seed);
        root.put(TAG_ROOT, signal);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    /**
     * Read the hunt seed from the item. Returns 0 if not set.
     */
    public static long getSeed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag root = data.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        return signal.getLong(TAG_SEED);
    }

    /**
     * Whether this item has a pre-set seed.
     */
    public static boolean hasSeed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag root = data.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        return signal.contains(TAG_SEED);
    }

    /**
     * Write an origin region identifier (e.g. biome or dimension key).
     */
    public static void setOrigin(ItemStack stack, String origin) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = existing.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        signal.putString(TAG_ORIGIN, origin);
        root.put(TAG_ROOT, signal);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    /**
     * Read the origin region from the item. Returns empty string if not set.
     */
    public static String getOrigin(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag root = data.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        return signal.getString(TAG_ORIGIN);
    }

    public static void setCoords(ItemStack stack, int x, int y, int z, double radius) {
        CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = existing.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        signal.putInt(TAG_X, x);
        signal.putInt(TAG_Y, y);
        signal.putInt(TAG_Z, z);
        signal.putDouble(TAG_RADIUS, radius);
        root.put(TAG_ROOT, signal);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static boolean hasCoords(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag root = data.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        return signal.contains(TAG_X) && signal.contains(TAG_Y) && signal.contains(TAG_Z);
    }

    public static int getX(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag root = data.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        return signal.getInt(TAG_X);
    }

    public static int getY(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag root = data.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        return signal.getInt(TAG_Y);
    }

    public static int getZ(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag root = data.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        return signal.getInt(TAG_Z);
    }

    public static double getRadius(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0.0;
        CompoundTag root = data.copyTag();
        CompoundTag signal = root.getCompound(TAG_ROOT);
        return signal.getDouble(TAG_RADIUS);
    }

    /**
     * Get the effective seed for hunt generation.
     * Uses the stored seed if present, otherwise generates one.
     */
    public static long getEffectiveSeed(ItemStack stack) {
        if (hasSeed(stack)) return getSeed(stack);
        return new Random().nextLong();
    }

    /**
     * Get the tier from any ShadowSignalDataItem stack.
     */
    public static ShadowSignalTier getTierFromStack(ItemStack stack) {
        if (stack.getItem() instanceof ShadowSignalDataItem signalItem) {
            return signalItem.getTier();
        }
        return ShadowSignalTier.FAINT;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int slotIndex = (hand == InteractionHand.MAIN_HAND) ? player.getInventory().selected : 40;

        if (level.isClientSide) {
            if (com.jayemceekay.shadowedhearts.client.util.ClientAuraPulseTrigger.isAuraReaderActive()) {
                com.jayemceekay.shadowedhearts.client.util.ClientAuraPulseTrigger.trigger(slotIndex);
                ShadowedHeartsNetwork.sendToServer(new AuraPulsePacket(slotIndex));
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    // ── Tooltip ──

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Tier label
        MutableComponent tierLabel = Component.translatable("item.shadowedhearts.shadow_signal_data.tier", tier.getTier());
        tooltip.add(tierLabel.withStyle(tierColor()));

        // Signal strength description
        tooltip.add(Component.translatable("item.shadowedhearts.shadow_signal_data.tier." + tier.name().toLowerCase())
                .withStyle(ChatFormatting.GRAY));

        // Hunt info
        tooltip.add(Component.translatable("item.shadowedhearts.shadow_signal_data.nodes",
                        tier.getMinNodes(), tier.getMaxNodes())
                .withStyle(ChatFormatting.DARK_GRAY));

        tooltip.add(Component.translatable("item.shadowedhearts.shadow_signal_data.level_range",
                        tier.getMinEncounterLevel(), tier.getMaxEncounterLevel())
                .withStyle(ChatFormatting.DARK_GRAY));

        // Origin region if set
        String origin = getOrigin(stack);
        if (!origin.isEmpty()) {
            tooltip.add(Component.translatable("item.shadowedhearts.shadow_signal_data.origin", origin)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }

        // Coordinates if set
        if (hasCoords(stack)) {
            tooltip.add(Component.translatable("item.shadowedhearts.shadow_signal_data.coords",
                            getX(stack), getY(stack), getZ(stack), getRadius(stack))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }

        // Usage hint
        tooltip.add(Component.translatable("item.shadowedhearts.shadow_signal_data.hint")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));

        super.appendHoverText(stack, context, tooltip, flag);
    }

    private ChatFormatting tierColor() {
        return switch (tier) {
            case FAINT -> ChatFormatting.GRAY;
            case WEAK -> ChatFormatting.GREEN;
            case MODERATE -> ChatFormatting.BLUE;
            case STRONG -> ChatFormatting.GOLD;
            case RESONANT -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    /**
     * Map tier to item rarity for display purposes.
     */
    public static Rarity tierToRarity(ShadowSignalTier tier) {
        return switch (tier) {
            case FAINT, WEAK -> Rarity.COMMON;
            case MODERATE -> Rarity.UNCOMMON;
            case STRONG -> Rarity.RARE;
            case RESONANT -> Rarity.EPIC;
        };
    }
}
