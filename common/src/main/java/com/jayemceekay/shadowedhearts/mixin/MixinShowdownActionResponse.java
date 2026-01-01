package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShowdownActionResponse.Companion.class, remap = false)
public abstract class MixinShowdownActionResponse {
    @Inject(method = "loadFromBuffer", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$loadCallFromBuffer(RegistryFriendlyByteBuf buffer, CallbackInfoReturnable<ShowdownActionResponse> cir) {
        int ordinal = buffer.readUnsignedByte();
        if (ordinal == 99) { // Our custom ordinal for CALL
            cir.setReturnValue(new com.jayemceekay.shadowedhearts.cobblemon.battles.CallActionResponse().loadFromBuffer(buffer));
        } else {
            buffer.readerIndex(buffer.readerIndex() - 1);
        }
    }
}
