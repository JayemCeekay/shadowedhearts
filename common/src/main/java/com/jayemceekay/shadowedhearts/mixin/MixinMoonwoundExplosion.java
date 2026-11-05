package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BiConsumer;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinMoonwoundExplosion {
    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$protect(Level level, BlockPos pos, Explosion explosion,
                                        BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        // Cancel before loot is generated, not merely the final air replacement.
        if (MoonwoundService.protectedBlock(level, pos)) ci.cancel();
    }
}
