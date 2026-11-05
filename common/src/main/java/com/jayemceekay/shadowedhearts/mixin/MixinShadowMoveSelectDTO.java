package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.callback.MoveSelectDTO;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native callback packets use signed bytes, but boosted 100-PP Shadow moves can reach 160 PP. */
@Mixin(value = MoveSelectDTO.class, remap = false)
public abstract class MixinShadowMoveSelectDTO {
    @Shadow @Final
    private MoveTemplate moveTemplate;

    @Shadow @Final @Mutable
    private int pp;

    @Shadow @Final @Mutable
    private int ppMax;

    @Inject(method = "<init>(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V", at = @At("RETURN"))
    private void shadowedhearts$readFullShadowPP(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        if (!ShadowMoveUtil.isShadowMove(moveTemplate)) return;
        // Preserve the native -1 sentinel and the wire format; reinterpret only overflowed values.
        if (pp < -1) pp += 256;
        if (ppMax < -1) ppMax += 256;
    }
}
