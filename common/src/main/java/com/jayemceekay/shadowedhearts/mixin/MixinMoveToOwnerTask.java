package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.ai.tasks.MoveToOwnerTask;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MoveToOwnerTask.class, remap = false)
public abstract class MixinMoveToOwnerTask {

    @WrapOperation(
            method = "create$lambda$0$0$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V",
                    ordinal = 0
            )
    )
    private static <U> void shadowedhearts$skipOwnerLookTarget(
            Brain<?> brain,
            MemoryModuleType<U> memoryType,
            U value,
            Operation<Void> original
    ) {
        if (memoryType != MemoryModuleType.LOOK_TARGET) {
            original.call(brain, memoryType, value);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(
            method = "create$lambda$0$0$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V",
                    ordinal = 1
            )
    )
    private static void shadowedhearts$avoidOwnerEntityWalkTarget(
            Brain<?> brain,
            MemoryModuleType memoryType,
            Object value,
            Operation<Void> original
    ) {
        if (memoryType == MemoryModuleType.WALK_TARGET && value instanceof WalkTarget walkTarget) {
            original.call(
                    brain,
                    memoryType,
                    new WalkTarget(
                            walkTarget.getTarget().currentPosition(),
                            walkTarget.getSpeedModifier(),
                            walkTarget.getCloseEnoughDist()
                    )
            );
            return;
        }

        original.call(brain, memoryType, value);
    }
}
