package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.CanceledError;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.jayemceekay.shadowedhearts.npc.BattleTestNpcDependencies;
import dev.architectury.platform.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = BattleBuilder.class, remap = false)
public abstract class MixinBattleBuilderTestNpcDependencies {
    @Inject(method = "pvn(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lcom/cobblemon/mod/common/entity/npc/NPCEntity;Ljava/util/UUID;"
            + "Lcom/cobblemon/mod/common/battles/BattleFormat;ZZ"
            + "Lcom/cobblemon/mod/common/api/storage/party/PartyStore;)"
            + "Lcom/cobblemon/mod/common/battles/BattleStartResult;",
            at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$checkTestNpcDependencies(
            ServerPlayer player, NPCEntity npcEntity, UUID leadingPokemon, BattleFormat battleFormat,
            boolean cloneParties, boolean healFirst, PartyStore party,
            CallbackInfoReturnable<BattleStartResult> cir) {
        if (!BattleTestNpcDependencies.isUnavailable(
                npcEntity.getNpc().getId().toString(), Platform.isModLoaded("mega_showdown"))) {
            return;
        }

        MutableComponent message = Component.translatable("shadowedhearts.battle_test.requires_mega_showdown");
        ErroredBattleStart errors = new ErroredBattleStart();
        errors.getGeneralErrors().add(new CanceledError(message));
        // Native NPC start_battle discards errors, so report this before returning one.
        player.sendSystemMessage(message);
        // Stop before dynamic parties resolve optional Mega Showdown held items.
        cir.setReturnValue(errors);
    }
}
