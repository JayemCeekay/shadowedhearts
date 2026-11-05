package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.integration.ftbchunks.RelicStoneProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinRelicStoneMining {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$authorizeRelicRemoval(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!RelicStoneProtection.canPlayerRemove(level, player, pos)) cir.setReturnValue(false);
    }
}
