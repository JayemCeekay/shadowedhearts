package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.cobblemon.mod.common.net.serverhandling.storage.BenchMoveHandler.class, remap = false)
public abstract class MixinBenchMoveHandler {

    @Inject(method = "handle*", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$guardBench(
            com.cobblemon.mod.common.net.messages.server.BenchMovePacket packet,
            net.minecraft.server.MinecraftServer server,
            net.minecraft.server.level.ServerPlayer player,
            CallbackInfo ci
    ) {
        var store = packet.isParty() ? com.cobblemon.mod.common.util.PlayerExtensionsKt.party(player)
                : Cobblemon.INSTANCE.getStorage().getPC(player);
        var pokemon = store.get(packet.getUuid());
        if (ShadowRestrictionPolicy.blocksMoveReplacement(pokemon, packet.getOldMove())) {
            ci.cancel();
        }
    }
}
