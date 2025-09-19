package com.jayemceekay.shadowedhearts.poketoss.ai;

import com.jayemceekay.shadowedhearts.poketoss.PokeToss;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrder;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Very first pass at wiring PokeTOSS orders to vanilla AI via a Goal.
 *
 * Minimal behaviors implemented:
 * - ATTACK_TARGET: sets the mob's target and steers toward it.
 * - GUARD_TARGET: keeps within radius of the guard target by following it.
 * - MOVE_TO / HOLD_POSITION: steers to the designated position.
 *
 * Other order types are currently ignored (no-op) and will be expanded later.
 */
public class TossOrderGoal extends Goal {
    private final Mob mob;
    private LivingEntity cachedTarget; // resolved from order UUID for efficiency

    public TossOrderGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // Only run on server and only if we have an order
        if (!(mob.level() instanceof ServerLevel)) return false;
        TacticalOrder order = PokeToss.getOrder(mob);
        return order != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel)) return false;
        TacticalOrder order = PokeToss.getOrder(mob);
        if (order == null) return false;
        // For ATTACK/GUARD we can continue while target (or guard anchor) still valid
        return switch (order.type) {
            case ATTACK_TARGET -> resolveAttackTarget((ServerLevel) mob.level(), order) != null;
            case GUARD_TARGET -> resolveGuardTarget((ServerLevel) mob.level(), order) != null;
            case MOVE_TO, HOLD_POSITION -> order.targetPos.isPresent();
            default -> false;
        };
    }

    @Override
    public void start() {
        cachedTarget = null;
    }

    @Override
    public void stop() {
        // Do not clear the order here; higher-level systems/networking control lifecycle.
        // Just stop navigation and clear soft targeting if our target vanished.
        mob.getNavigation().stop();
        // Don't forcibly clear combat target so existing AI can disengage naturally.
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) return;
        TacticalOrder order = PokeToss.getOrder(mob);
        if (order == null) return;

        switch (order.type) {
            case ATTACK_TARGET -> tickAttack(level, order);
            case GUARD_TARGET -> tickGuard(level, order);
            case MOVE_TO -> tickMoveTo(level, order);
            case HOLD_POSITION -> tickHold(level, order);
            default -> {
                // Not yet implemented
            }
        }
    }

    private void tickAttack(ServerLevel level, TacticalOrder order) {
        LivingEntity target = resolveAttackTarget(level, order);
        if (target == null) return;
        // Ensure the mob is focusing/targeting this entity
        if (mob.getTarget() != target) {
            mob.setTarget(target);
        }
        // Look at and path toward the target if too far
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        double dist2 = mob.distanceToSqr(target);
        double desiredDist = 2.0; // basic melee range fallback; Cobblemon may have its own attack ranges
        if (dist2 > desiredDist * desiredDist) {
            mob.getNavigation().moveTo(target, 1.2);
        }
        // If target is dead, let canContinueToUse() drop us next tick
    }

    private void tickGuard(ServerLevel level, TacticalOrder order) {
        LivingEntity anchor = resolveGuardTarget(level, order);
        if (anchor == null) return;
        float radius = Math.max(1.0f, order.radius);
        double dist2 = mob.distanceToSqr(anchor);
        if (dist2 > (double) (radius * radius * 0.9f)) {
            // Too far from guard target: follow/close in
            mob.getNavigation().moveTo(anchor, 1.1);
        } else {
            // Close enough: loiter and face same direction
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(anchor, 20.0f, 20.0f);
        }
    }

    private void tickMoveTo(ServerLevel level, TacticalOrder order) {
        order.targetPos.ifPresent(pos -> moveToward(pos, 1.15));
    }

    private void tickHold(ServerLevel level, TacticalOrder order) {
        order.targetPos.ifPresent(pos -> {
            // If we drift beyond radius, move back toward the hold position
            float r = Math.max(0.5f, order.radius);
            double dist2 = mob.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (dist2 > (double) r * r) {
                moveToward(pos, 1.0);
            } else {
                mob.getNavigation().stop();
            }
        });
    }

    private LivingEntity resolveAttackTarget(ServerLevel level, TacticalOrder order) {
        if (order.targetEntity.isEmpty()) return null;
        UUID id = order.targetEntity.get();
        LivingEntity tgt = this.cachedTarget;
        if (tgt == null || !tgt.isAlive() || !tgt.getUUID().equals(id)) {
            var e = level.getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive()) {
                tgt = le;
                this.cachedTarget = le;
            } else {
                tgt = null;
                this.cachedTarget = null;
            }
        }
        return tgt;
    }

    private LivingEntity resolveGuardTarget(ServerLevel level, TacticalOrder order) {
        // Same resolution as attack, but we don't set mob target
        if (order.targetEntity.isEmpty()) return null;
        UUID id = order.targetEntity.get();
        LivingEntity tgt = this.cachedTarget;
        if (tgt == null || !tgt.isAlive() || !tgt.getUUID().equals(id)) {
            var e = level.getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive()) {
                tgt = le;
                this.cachedTarget = le;
            } else {
                tgt = null;
                this.cachedTarget = null;
            }
        }
        return tgt;
    }

    private void moveToward(BlockPos pos, double speed) {
        mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
        mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
