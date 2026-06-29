package com.jayemceekay.shadowedhearts.mixin.megashowdown;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.networking.server.handler.UltraBurstHandler;
import com.github.yajatkaul.mega_showdown.networking.server.packet.UltraBurstPacket;
import com.github.yajatkaul.mega_showdown.utils.PlayerUtils;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UltraBurstHandler.class, remap = false)
public class MixinUltraBurstHandler {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$blockShadowUltraBurst(UltraBurstPacket packet, NetworkManager.PacketContext context, CallbackInfo ci) {
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowCanUltraBurst()) return;
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        Pokemon pokemon = PlayerUtils.getPartyPokemonFromUUID(player, packet.pokemonId());
        if (pokemon != null && ShadowAspectUtil.hasShadowAspect(pokemon)) {
            ci.cancel();
        }
    }
}
