package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.integration.ftbchunks.RelicStoneProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinRelicStoneExplosion {
    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$authorizeRelicExplosion(Level level, BlockPos pos, Explosion explosion,
                                                       BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        // Cancel before loot is generated and before onRemove can cascade into another claim.
        if (level instanceof ServerLevel serverLevel && !RelicStoneProtection.canExplosionRemove(
                serverLevel, pos, (BlockState) (Object) this, explosion)) ci.cancel();
    }
}
