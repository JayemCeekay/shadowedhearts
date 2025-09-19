package com.jayemceekay.shadowedhearts.blocks.entity;

import com.jayemceekay.shadowedhearts.core.ModBlockEntities;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.signals.MissionSignalItem;
import com.jayemceekay.shadowedhearts.signals.SignalFragmentItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * BlockEntity for the Signal Locator: holds a 3x3 grid of fragments and a 1-slot output preview.
 * MVP: auto-generate a Mission Signal when valid inputs are present; consume inputs when the output is taken.
 */
public class SignalLocatorBlockEntity extends BlockEntity implements MenuProvider, Clearable {
    public static final int GRID_SIZE = 9;

    private final SimpleContainer grid = new SimpleContainer(GRID_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            SignalLocatorBlockEntity.this.recomputePreview();
        }
    };
    private final SimpleContainer out = new SimpleContainer(1);

    // Cache what was used to produce the current preview so we can consume accurately on take
    private @Nullable CachedRecipe cached;

    public SignalLocatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIGNAL_LOCATOR_BE.get(), pos, state);
    }

    public SimpleContainer grid() { return grid; }
    public SimpleContainer output() { return out; }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new com.jayemceekay.shadowedhearts.blocks.menu.SignalLocatorMenu(id, inv, this);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("container.shadowedhearts.signal_locator");
    }


    public void onTakeOutput(Player player) {
        // Consume the cached inputs if the output matches
        ItemStack current = out.getItem(0);
        if (current.isEmpty() || cached == null) return;
        if (!Objects.equals(MissionSignalItem.getTheme(current), cached.theme) || MissionSignalItem.getTier(current) != cached.tier) {
            return;
        }
        // Consume one from the slots listed
        for (int slot : cached.themeSlots) consumeOne(slot);
        for (int slot : cached.tierSlots) consumeOne(slot);
        for (int slot : cached.affixSlots) consumeOne(slot);
        out.setItem(0, ItemStack.EMPTY);
        setChanged();
    }

    private void consumeOne(int slot) {
        ItemStack st = grid.getItem(slot);
        if (!st.isEmpty()) {
            st.shrink(1);
            grid.setItem(slot, st);
        }
    }

    private void recomputePreview() {
        if (level == null || level.isClientSide) return;
        // Parse fragments
        ResourceLocation theme = null;
        int themeSlot = -1;
        int tier = 0;
        int tierSlot = -1;
        List<ResourceLocation> affixes = new ArrayList<>();
        List<Integer> affixSlots = new ArrayList<>();
        Set<String> affixSeen = new HashSet<>();

        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack st = grid.getItem(i);
            if (st.isEmpty() || !(st.getItem() instanceof SignalFragmentItem frag)) continue;
            switch (frag.getKind()) {
                case THEME -> {
                    if (theme == null) { theme = ResourceLocation.fromNamespaceAndPath("shadowedhearts", frag.getValue()); themeSlot = i; }
                }
                case TIER -> {
                    if (tier == 0) { try { tier = Integer.parseInt(frag.getValue()); tierSlot = i; } catch (NumberFormatException ignored) {} }
                }
                case AFFIX -> {
                    if (affixes.size() < 3) {
                        String key = frag.getValue().toLowerCase(Locale.ROOT);
                        if (affixSeen.add(key)) {
                            affixes.add(ResourceLocation.fromNamespaceAndPath("shadowedhearts", key));
                            affixSlots.add(i);
                        }
                    }
                }
                default -> {}
            }
        }

        if (theme != null && tier > 0) {
            long seed = computeSeed(theme, tier, affixes, level instanceof ServerLevel sl ? sl.getSeed() : 0L);
            ItemStack result = MissionSignalItem.create(ModItems.MISSION_SIGNAL.get(), theme, tier, affixes, seed);
            out.setItem(0, result);
            this.cached = new CachedRecipe(theme, tier, new int[]{themeSlot}, new int[]{tierSlot}, affixSlots.stream().mapToInt(Integer::intValue).toArray());
        } else {
            out.setItem(0, ItemStack.EMPTY);
            this.cached = null;
        }
        setChanged();
    }

    private long computeSeed(ResourceLocation theme, int tier, List<ResourceLocation> affixes, long salt) {
        long hash = 0xcbf29ce484222325L;
        hash ^= theme.toString().hashCode(); hash *= 0x100000001b3L;
        hash ^= tier; hash *= 0x100000001b3L;
        for (ResourceLocation rl : affixes) { hash ^= rl.toString().hashCode(); hash *= 0x100000001b3L; }
        hash ^= salt; hash *= 0x100000001b3L;
        return hash;
    }

    private record CachedRecipe(ResourceLocation theme, int tier, int[] themeSlots, int[] tierSlots, int[] affixSlots) {}

    @Override
    public void clearContent() {
        grid.clearContent();
        out.clearContent();
        cached = null;
    }
}
