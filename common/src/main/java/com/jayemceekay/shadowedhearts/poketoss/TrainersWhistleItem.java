package com.jayemceekay.shadowedhearts.poketoss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Minimal Trainer's Whistle implementation.
 * Right click: attempts to select the Pokemon the player is looking at within ~10 blocks,
 * and issues a basic order:
 * - Sneaking: REGROUP to the player
 * - Otherwise: FOLLOW within small radius
 */
public class TrainersWhistleItem extends Item {
    public TrainersWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000; // allow holding RMB
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // Begin client-side brush/quick selection flow
            player.startUsingItem(hand);
            com.jayemceekay.shadowedhearts.poketoss.client.WhistleSelectionClient.begin();
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        // Server: no immediate order; selection will be sent from client on release
        return InteractionResultHolder.consume(stack);
    }

    private static LivingEntity findLookTarget(ServerLevel level, Player player, double maxDist, double minDot) {
        Vec3 eye = player.getEyePosition(1f);
        Vec3 look = player.getLookAngle().normalize();
        AABB box = new AABB(eye.add(look.scale(0.5)), eye.add(look.scale(maxDist))).inflate(1.5);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())
                .stream()
                .filter(e -> isRoughlyInFront(eye, look, e.position(), minDot))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(eye.x, eye.y, eye.z)))
                .orElse(null);
    }

    private static boolean isRoughlyInFront(Vec3 eye, Vec3 look, Vec3 targetPos, double minDot) {
        Vec3 to = targetPos.subtract(eye).normalize();
        double dot = to.dot(look);
        return dot >= minDot;
    }
}
