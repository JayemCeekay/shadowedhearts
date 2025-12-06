package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.net.serverhandling.storage.RequestMoveSwapHandler;
import com.jayemceekay.shadowedhearts.ShadowGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RequestMoveSwapHandler.class, remap = false)
public abstract class MixinRequestMoveSwapHandler {

    @Inject(method = "handle*", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$guardSwap(
            com.cobblemon.mod.common.net.messages.server.RequestMoveSwapPacket packet,
            net.minecraft.server.MinecraftServer server,
            net.minecraft.server.level.ServerPlayer player,
            CallbackInfo ci
    ) {
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        var pokemon = party.get(packet.getSlot());
        if (pokemon != null && ShadowGate.isShadowLocked(pokemon)) {
            ci.cancel();
        }
    }
}