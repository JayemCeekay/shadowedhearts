package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinMoonwoundMining {
    @Shadow @Final protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean shadowedhearts$mine(BlockPos pos, Operation<Boolean> original) {
        return MoonwoundService.mine(player, pos, () -> original.call(pos));
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$open(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                    BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND && MoonwoundService.interact(player, hit.getBlockPos(), stack)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
