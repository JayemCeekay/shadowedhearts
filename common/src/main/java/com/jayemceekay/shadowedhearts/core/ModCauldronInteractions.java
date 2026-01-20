package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LayeredCauldronBlock;

public class ModCauldronInteractions {

    public static void register() {
        if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().expandedScentSystemEnabled()) {
            registerScentRecipe(Items.PINK_PETALS, ModItems.JOY_SCENT.get());
            registerScentRecipe(Items.GLOW_BERRIES, ModItems.EXCITE_SCENT.get());
            registerScentRecipe(Items.PHANTOM_MEMBRANE, ModItems.VIVID_SCENT.get());
        }
    }

    private static void registerScentRecipe(Item ingredient, Item result) {
        CauldronInteraction.WATER.map().put(ingredient, (state, level, pos, player, hand, stack) -> {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            ItemStack resultStack = new ItemStack(result);
            if (stack.isEmpty()) {
                player.setItemInHand(hand, resultStack);
            } else {
                if (!player.getInventory().add(resultStack)) {
                    player.drop(resultStack, false);
                }
            }

            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(ingredient));
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        });
    }
}
