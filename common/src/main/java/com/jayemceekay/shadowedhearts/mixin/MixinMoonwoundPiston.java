package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class MixinMoonwoundPiston {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$protect(BlockState state, Level level, BlockPos pos, Direction direction,
                                               boolean destroy, Direction pistonDirection,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (MoonwoundService.protectedBlock(level, pos)) cir.setReturnValue(false);
    }
}
