package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class CraterRescueReward {
    public enum Result { JOINED, BALL_MISSING, STORAGE_FULL }

    private CraterRescueReward() {}

    /** Prefer a standard ball. A rescue offer always names the exact stack the trainer would spend. */
    public static ItemStack offeredBall(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack alternative = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!eligible(stack)) continue;
            if (stack.is(CobblemonItems.POKE_BALL)) return stack.copyWithCount(1);
            if (alternative.isEmpty()) alternative = stack;
        }
        return alternative.isEmpty() ? ItemStack.EMPTY : alternative.copyWithCount(1);
    }

    private static boolean eligible(ItemStack stack) {
        // A Dark Ball corrupts its occupant and cannot deliver the ordinary rescue Pokémon.
        return !stack.isEmpty() && stack.getItem() instanceof PokeBallItem && !stack.is(ModItems.DARK_BALL.get());
    }

    /** The caller passes the detached saved victim and retires its event entity after a successful handoff. */
    public static Result welcome(ServerPlayer player, Pokemon victim, ItemStack offered) {
        var storage = Cobblemon.INSTANCE.getStorage();
        var party = storage.getParty(player);
        if (party.get(victim.getUuid()) != null || storage.getPC(player).get(victim.getUuid()) != null) {
            return Result.JOINED;
        }
        if (!eligible(offered)) return Result.BALL_MISSING;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, offered)) continue;

            CraterRescueActors.clearForAdoption(victim);
            victim.setCaughtBall(((PokeBallItem) stack.getItem()).getPokeBall());
            // PlayerPartyStore sets the original trainer and automatically tries the owner's PC.
            boolean added;
            try {
                added = party.add(victim);
            } catch (RuntimeException exception) {
                // A third-party Pokémon-gained listener may throw after native storage already accepted it.
                if (party.get(victim.getUuid()) == null && storage.getPC(player).get(victim.getUuid()) == null) {
                    throw exception;
                }
                added = true;
            }
            if (!added) return Result.STORAGE_FULL;
            stack.shrink(1);
            inventory.setChanged();
            return Result.JOINED;
        }
        return Result.BALL_MISSING;
    }

    public static boolean berries(ServerPlayer player) {
        return drop(player, new ItemStack(CobblemonItems.ORAN_BERRY, 2 + player.getRandom().nextInt(2)));
    }

    static boolean outbreakDust(ServerPlayer player, int count) {
        return count == 0 || drop(player, new ItemStack(ModItems.SHADOW_DUST.get(), count));
    }

    static boolean howlScent(ServerPlayer player, int count) {
        return count == 0 || drop(player, new ItemStack(ModItems.JOY_SCENT.get(), count));
    }

    private static boolean drop(ServerPlayer player, ItemStack stack) {
        var level = player.serverLevel();
        var gift = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack);
        gift.setNoPickUpDelay();
        gift.setTarget(player.getUUID());
        return level.addFreshEntity(gift);
    }
}
