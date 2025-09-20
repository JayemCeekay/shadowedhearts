package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.poketoss.PokeToss;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public final class PokeTossCommands {
    private PokeTossCommands() {}

    public static int follow(CommandContext<CommandSourceStack> ctx, @Nullable Entity explicitTarget) {
        return applySimple(ctx, explicitTarget, TacticalOrder.follow(5f, true));
    }

    public static int regroup(CommandContext<CommandSourceStack> ctx, @Nullable Entity explicitTarget) {
        try {
            ServerPlayer sp = ctx.getSource().getPlayerOrException();
            ServerLevel level = sp.serverLevel();
            LivingEntity target = resolveTarget(ctx.getSource(), explicitTarget);
            if (target == null) {
                sp.displayClientMessage(Component.literal("PokeTOSS: No valid Pokémon target in range."), false);
                return 0;
            }
            // Regroup: order the Pokémon to move to the player's current position with a small radius
            var pos = sp.blockPosition();
            TacticalOrder order = TacticalOrder.moveTo(pos, 2.0f);
            boolean ok = PokeToss.issueOrder(level, target, order, sp);
            return ok ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("PokeTOSS: Command failed."));
            return 0;
        }
    }

    public static int clear(CommandContext<CommandSourceStack> ctx, Entity explicitTarget) {
        try {
            ServerPlayer sp = ctx.getSource().getPlayerOrException();
            LivingEntity target = resolveTarget(ctx.getSource(), explicitTarget);
            if (target == null) {
                sp.displayClientMessage(Component.literal("PokeTOSS: No valid Pokémon target in range."), false);
                return 0;
            }
            PokeToss.clearOrder(target);
            sp.displayClientMessage(Component.literal("[TOSS] Cleared order for " + target.getName().getString()), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("PokeTOSS: Command failed."));
            return 0;
        }
    }

    private static int applySimple(CommandContext<CommandSourceStack> ctx, @Nullable Entity explicitTarget, TacticalOrder order) {
        try {
            ServerPlayer sp = ctx.getSource().getPlayerOrException();
            ServerLevel level = sp.serverLevel();
            LivingEntity target = resolveTarget(ctx.getSource(), explicitTarget);
            if (target == null) {
                sp.displayClientMessage(Component.literal("PokeTOSS: No valid Pokémon target in range."), false);
                return 0;
            }
            boolean ok = PokeToss.issueOrder(level, target, order, sp);
            return ok ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("PokeTOSS: Command failed."));
            return 0;
        }
    }

    private static @Nullable LivingEntity resolveTarget(CommandSourceStack src, @Nullable Entity explicitTarget) {
        if (explicitTarget instanceof LivingEntity le && le instanceof PokemonEntity) return le;
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return null;
        AABB box = sp.getBoundingBox().inflate(12);
        return sp.level().getEntitiesOfClass(PokemonEntity.class, box, e -> e.isAlive())
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(sp)))
                .orElse(null);
    }
}
