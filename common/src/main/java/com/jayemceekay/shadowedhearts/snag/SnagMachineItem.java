package com.jayemceekay.shadowedhearts.snag;

import com.jayemceekay.shadowedhearts.network.payload.SnagArmedS2C;
import dev.architectury.networking.NetworkManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
/**
 * Unique wearable/held item that toggles an "armed" state for snagging.
 */
public class SnagMachineItem extends Item {
    public SnagMachineItem(Properties p) { super(p.stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(held);
        PlayerSnagData cap = SnagCaps.get(player);
        if (!cap.hasSnagMachine()) return InteractionResultHolder.fail(held);
        if (cap.cooldown() > 0) return InteractionResultHolder.fail(held);
        boolean newState = !cap.isArmed();
        cap.setArmed(newState);
        // brief cooldown to avoid spam toggling
        cap.setCooldown(20);
        NetworkManager.sendToPlayer((net.minecraft.server.level.ServerPlayer) player, new SnagArmedS2C(newState));
        return InteractionResultHolder.sidedSuccess(held, false);
    }
}
