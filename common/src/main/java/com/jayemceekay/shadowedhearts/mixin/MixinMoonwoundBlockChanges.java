package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep non-player destruction from skipping the party's excavation and battle gates. */
@Mixin(Level.class)
public abstract class MixinMoonwoundBlockChanges {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$set(BlockPos pos, BlockState state, int flags, int recursion,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (MoonwoundService.preventChange((Level) (Object) this, pos, state)) cir.setReturnValue(false);
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$destroy(BlockPos pos, boolean drops, Entity entity, int recursion,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (MoonwoundService.protectedBlock((Level) (Object) this, pos)) cir.setReturnValue(false);
    }
}
