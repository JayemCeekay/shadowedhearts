package com.jayemceekay.shadowedhearts.poketoss.ai;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData;
import com.cobblemon.mod.common.entity.ai.SwapActivityTask;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.common.collect.ImmutableList;
import com.jayemceekay.shadowedhearts.poketoss.PokeToss;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.UUID;

/**
 * Brain/Behavior-driven replacement for the old TossOrderGoal.
 * <p>
 * This installs a single Behavior into the CORE activity of a Mob's Brain.
 * The behavior mirrors the minimal functionality previously implemented in
 * TossOrderGoal: ATTACK_TARGET, GUARD_TARGET, MOVE_TO, HOLD_POSITION.
 */
public final class TossOrderActivity {
    private TossOrderActivity() {
    }

    /**
     * Optional custom activity if we ever want a dedicated activity. Currently unused.
     */
    public static final Activity TOSS_ORDER = new Activity("shadowedhearts_toss_order");

    /**
     * Install the TossOrderBehavior into the entity's Brain under CORE activity.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void install(Mob mob) {
        Brain brain = mob.getBrain();
        // Merge our behavior into the existing CORE activity list without wiping Cobblemon's defaults.
        @SuppressWarnings("unchecked")
        var accessor = (com.jayemceekay.shadowedhearts.mixin.AccessorBrain<Mob>) (Object) brain;
        var map = accessor.shadowedhearts$getAvailableBehaviorsByPriority();
        java.util.List<Pair<Integer, net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Mob>>> existing = java.util.List.of();
        // Avoid TreeMap.get on Activity keys (Activity is not Comparable). Iterate to find CORE safely.
        for (var entry : map.entrySet()) {
            if (entry.getKey() == Activity.CORE) {
                existing = entry.getValue();
                break;
            }
        }
        // Idempotency: if our TossOrderTask is already the first CORE behavior, do nothing.
        if (!existing.isEmpty() && existing.get(0).getSecond() instanceof TossOrderTask) {
            return;
        }
        java.util.ArrayList<Pair<Integer, net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Mob>>> merged = new java.util.ArrayList<>(existing.size() + 1);
        // Prepend our task with priority 0 so orders take precedence.
        for (Pair<?, ?> behavior : existing) {
            System.out.println(behavior.getSecond().toString());
        }
        merged.add(Pair.of(0, new TossOrderTask()));
        // Then keep everything Cobblemon already configured.
        merged.add(Pair.of(0, SwapActivityTask.INSTANCE.possessing(
                MemoryModuleType.ATTACK_TARGET,
                Activity.FIGHT
        )));
        for (var p : existing) {
            @SuppressWarnings("unchecked")
            Pair<Integer, net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Mob>> cast = (Pair<Integer, net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Mob>>) (Pair<?, ?>) p;
            merged.add(cast);
        }
        brain.addActivity(Activity.CORE, ImmutableList.copyOf(merged));
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
            if (order == null) {
                return false;
            }

            return switch (order.type) {
                case ENGAGE_TARGET -> resolveAttackTarget(level, mob, order) != null;
                case GUARD_TARGET -> resolveGuardTarget(level, mob, order) != null;
                case MOVE_TO, HOLD_POSITION -> order.targetPos.isPresent();
                case DISENGAGE -> true; // allow one tick to cleanup and yield back to Cobblemon
                default -> true; // unknown orders: run once to clean up and yield
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
            brain.eraseMemory(CobblemonMemories.ATTACK_TARGET_DATA);
            mob.getNavigation().stop();
        }

        @Override
        protected void tick(ServerLevel level, Mob mob, long gameTime) {
            TacticalOrder order = PokeToss.getOrder(mob);
            if (order == null) return;

            switch (order.type) {
                case ENGAGE_TARGET -> tickAttack(level, mob, order);
                case GUARD_TARGET -> tickGuard(level, mob, order);
                case MOVE_TO -> tickMoveTo(mob, order);
                case HOLD_POSITION -> tickHold(mob, order);
                case DISENGAGE -> {
                    // Clear our steering memories and yield to stock Cobblemon AI
                    Brain<?> brain = mob.getBrain();
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                    brain.eraseMemory(CobblemonMemories.ATTACK_TARGET_DATA);
                    mob.getNavigation().stop();
                    // Clear the order so CORE controller stops running next tick
                    com.jayemceekay.shadowedhearts.poketoss.PokeToss.clearOrder(mob);
                }
                default -> {
                    // Unknown/neutral order: perform same cleanup and yield back to Cobblemon;
                   /* System.out.println("Unknown order: " + order.type);
                    Brain<?> brain = mob.getBrain();
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                    brain.eraseMemory(CobblemonMemories.INSTANCE.getATTACK_TARGET_DATA());
                    mob.getNavigation().stop();
                    com.jayemceekay.shadowedhearts.poketoss.PokeToss.clearOrder(mob);*/
                }
            }
        }

        private void tickAttack(ServerLevel level, Mob mob, TacticalOrder order) {
            if (mob instanceof PokemonEntity pokemonEntity) {
                LivingEntity target = resolveAttackTarget(level, mob, order);
                if (target == null) {
                    mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    mob.getBrain().eraseMemory(CobblemonMemories.ATTACK_TARGET_DATA);
                    return;
                }

                //pokemonEntity.getConfig().setDirectly("melee_range", MoValue.of(2.5f));

                // Hand off to Cobblemon's fight tasks by setting ATTACK_TARGET memory
                Brain<?> brain = mob.getBrain();

                brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
                // Provide Cobblemon-specific context for the fight tasks
                brain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, new CobblemonAttackTargetData());
            }
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
            if (mob instanceof PokemonEntity pokemonEntity) {
                order.targetPos.ifPresent(pos -> mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 1.0f, 1)));
            }
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
    }
}
