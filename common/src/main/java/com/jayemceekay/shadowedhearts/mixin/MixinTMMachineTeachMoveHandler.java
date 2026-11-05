package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.net.messages.server.block.TMMachineTeachMovePacket;
import com.cobblemon.mod.common.net.serverhandling.block.TMMachineTeachMoveHandler;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowMoveTeachingPolicy;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.tm.ShadowTMTeaching;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TMMachineTeachMoveHandler.class, remap = false)
public abstract class MixinTMMachineTeachMoveHandler {
    @Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/server/block/TMMachineTeachMovePacket;"
            + "Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$guardTMMachine(TMMachineTeachMovePacket packet, MinecraftServer server,
                                             ServerPlayer player, CallbackInfo ci) {
        if (ShadowMoveUtil.isShadowMove(packet.getMoveTemplate())) {
            ShadowTMTeaching.beginMachine(player, packet);
            ci.cancel();
            return;
        }
        var store = packet.isParty() ? Cobblemon.INSTANCE.getStorage().getParty(player)
                : Cobblemon.INSTANCE.getStorage().getPC(player);
        var pokemon = store.get(packet.getUuid());
        if (ShadowMoveTeachingPolicy.rejectNativeTM(player, pokemon, packet.getMoveTemplate())) {
            ci.cancel();
        }
    }
}
