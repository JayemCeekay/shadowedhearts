package com.jayemceekay.shadowedhearts.poketoss.ai;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData;
import com.jayemceekay.shadowedhearts.poketoss.PokeToss;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrder;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import com.mojang.datafixers.util.Pair;
import com.google.common.collect.ImmutableList;

import java.util.Map;
import java.util.UUID;

/**
 * Brain/Behavior-driven replacement for the old TossOrderGoal.
 *
 * This installs a single Behavior into the CORE activity of a Mob's Brain.
 * The behavior mirrors the minimal functionality previously implemented in
 * TossOrderGoal: ATTACK_TARGET, GUARD_TARGET, MOVE_TO, HOLD_POSITION.
 */
public final class TossOrderActivity {
    private TossOrderActivity() {}

    /** Optional custom activity if we ever want a dedicated activity. Currently unused. */
    public static final Activity TOSS_ORDER = new Activity("shadowedhearts_toss_order");

    /** Install the TossOrderBehavior into the entity's Brain under CORE activity. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void install(Mob mob) {
        Brain brain = mob.getBrain();
        // Add our behavior at high priority inside CORE. This keeps it simple and always available.
        brain.addActivity(Activity.CORE, ImmutableList.of(Pair.of(0, new TossOrderTask())));
    }

    /**
     * Behavior that runs when the entity has an active PokeToss order.
     */
    private static final class TossOrderTask extends Behavior<Mob> {
        TossOrderTask() {
            super(Map.of()); // no required memories
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Mob mob) {
            return PokeToss.getOrder(mob) != null;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Mob mob, long gameTime) {
            TacticalOrder order = PokeToss.getOrder(mob);
            if (order == null) return false;
            return switch (order.type) {
                case ATTACK_TARGET -> resolveAttackTarget(level, mob, order) != null;
                case GUARD_TARGET -> resolveGuardTarget(level, mob, order) != null;
                case MOVE_TO, HOLD_POSITION -> order.targetPos.isPresent();
                default -> false;
            };
        }

        @Override
        protected void start(ServerLevel level, Mob mob, long gameTime) {
            // no-op; we resolve targets on demand
        }

        @Override
        protected void stop(ServerLevel level, Mob mob, long gameTime) {
            Brain<?> brain = mob.getBrain();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            // Also clear Cobblemon's extra attack metadata
            brain.eraseMemory(CobblemonMemories.INSTANCE.getATTACK_TARGET_DATA());
            mob.getNavigation().stop();
        }

        @Override
        protected void tick(ServerLevel level, Mob mob, long gameTime) {
            TacticalOrder order = PokeToss.getOrder(mob);
            if (order == null) return;

            // Keep PokemonBrain in a coherent state by clearing conflicting memories first
            Brain<?> brain = mob.getBrain();
            if (order.type == TacticalOrderType.ATTACK_TARGET) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                // Also clear Cobblemon's extra attack metadata when no longer attacking
                brain.eraseMemory(CobblemonMemories.INSTANCE.getATTACK_TARGET_DATA());
            }

            switch (order.type) {
                case ATTACK_TARGET -> tickAttack(level, mob, order);
                case GUARD_TARGET -> tickGuard(level, mob, order);
                case MOVE_TO -> tickMoveTo(mob, order);
                case HOLD_POSITION -> tickHold(mob, order);
                default -> {
                    // not implemented
                }
            }
        }

        private void tickAttack(ServerLevel level, Mob mob, TacticalOrder order) {
            LivingEntity target = resolveAttackTarget(level, mob, order);
            if (target == null) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                return;
            }
            // Hand off to Cobblemon's fight tasks by setting ATTACK_TARGET memory
            Brain<?> brain = mob.getBrain();
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
            // Provide Cobblemon-specific context for the fight tasks
            brain.setMemory(CobblemonMemories.INSTANCE.getATTACK_TARGET_DATA(), new CobblemonAttackTargetData());
            brain.setActiveActivityIfPossible(Activity.FIGHT);
            System.out.println("TICKING ATTACK");
        }

        private void tickGuard(ServerLevel level, Mob mob, TacticalOrder order) {
            LivingEntity anchor = resolveGuardTarget(level, mob, order);
            if (anchor == null) {
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                return;
            }
            float radius = Math.max(1.0f, order.radius);
            // Use PokemonBrain's movement by writing WALK/LOOK memories
            BehaviorUtils.setWalkAndLookTargetMemories(mob, anchor, 1.1f, Math.max(1, Math.round(radius)));
        }

        private void tickMoveTo(Mob mob, TacticalOrder order) {
            order.targetPos.ifPresent(pos -> BehaviorUtils.setWalkAndLookTargetMemories(mob, pos, 1.15f, 1));
        }

        private void tickHold(Mob mob, TacticalOrder order) {
            order.targetPos.ifPresent(pos -> {
                int r = Math.max(1, Math.round(order.radius));
                BehaviorUtils.setWalkAndLookTargetMemories(mob, pos, 1.0f, r);
            });
        }

        private LivingEntity resolveAttackTarget(ServerLevel level, Mob mob, TacticalOrder order) {
            if (order.targetEntity.isEmpty()) return null;
            UUID id = order.targetEntity.get();
            var e = level.getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive()) {
                return le;
            }
            return null;
        }

        private LivingEntity resolveGuardTarget(ServerLevel level, Mob mob, TacticalOrder order) {
            if (order.targetEntity.isEmpty()) return null;
            UUID id = order.targetEntity.get();
            var e = level.getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive()) {
                return le;
            }
            return null;
        }

        private void moveToward(Mob mob, BlockPos pos, double speed) {
            mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
            mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
    }
}
