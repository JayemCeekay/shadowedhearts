package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = com.cobblemon.mod.common.battles.ShowdownActionRequest.class, remap = false)
public abstract class MixinShowdownActionRequest {

    @Shadow
    private @NotNull List<@NotNull Boolean> forceSwitch;

    @Shadow
    public abstract @NotNull List<@NotNull Boolean> getForceSwitch();

    @Inject(method = "loadFromBuffer", at = @At("HEAD"))
    private void shadowedhearts$printBufferOnLoad(RegistryFriendlyByteBuf buffer, CallbackInfoReturnable<ShowdownActionRequest> cir) {
        int readerIndex = buffer.readerIndex();
        try {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(readerIndex, bytes);
            StringBuilder sb = new StringBuilder("ShowdownActionRequest buffer contents: ");
            for (byte b : bytes) {
                sb.append(String.format("%02X ", b));
            }
           // System.out.println(sb.toString());
        } catch (Exception e) {
            System.err.println("Failed to print ShowdownActionRequest buffer: " + e.getMessage());
        }
    }

}