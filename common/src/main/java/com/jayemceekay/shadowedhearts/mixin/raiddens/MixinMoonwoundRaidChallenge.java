package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.jayemceekay.shadowedhearts.common.moonwound.raid.RaidDensMoonwoundAdapter;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Upstream enforces a den roster only inside its raid dimension; our portal remains in the overworld. */
@Pseudo
@Mixin(targets = "com.necro.raid.dens.common.network.packets.RaidChallengePacket", remap = false)
public abstract class MixinMoonwoundRaidChallenge {
    @Shadow public abstract int targetedEntityId();

    @WrapMethod(method = "handleServer", remap = false)
    private void shadowedhearts$party(ServerPlayer player, Operation<Void> original) {
        var target = player.level().getEntity(targetedEntityId());
        if (!RaidDensMoonwoundAdapter.allowChallenge(player, target)) return;
        RaidDensMoonwoundAdapter.nativeChallenge(player, target, () -> original.call(player));
    }
}
