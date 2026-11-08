package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.moonwound.raid.RaidDensMoonwoundAdapter;
import com.jayemceekay.shadowedhearts.common.moonwound.raid.MoonwoundRaidRewardDefinitionAccess;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.necro.raid.dens.common.raids.rewards.RewardHandler;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Restores the issued Moonwound definition and the item pouch earned by pre-0.14 rewards. */
@Pseudo
@Mixin(targets = "com.necro.raid.dens.common.raids.rewards.RewardHandler", remap = false)
public abstract class MixinMoonwoundRaidRewardPersistence implements MoonwoundRaidRewardDefinitionAccess {
    @Shadow @Final private ResourceLocation raidBossId;
    @Shadow @Final private CompoundTag serializedPokemonReward;
    @Shadow private Pokemon pokemonReward;
    @Shadow private RaidBoss raidBoss;
    @Unique private byte[] shadowedhearts$frozenDefinition;

    @WrapMethod(method = "deserialize")
    private static RewardHandler shadowedhearts$readReward(CompoundTag tag, HolderLookup.Provider registries,
                                                          Operation<RewardHandler> original) {
        if (!RaidDensMoonwoundAdapter.BOSS.toString().equals(tag.getString("raid_boss"))) return original.call(tag, registries);
        if (!tag.contains("item_reward")) {
            tag = tag.copy();
            tag.putBoolean("item_reward", true);
        }
        RewardHandler reward = original.call(tag, registries);
        if (tag.contains("shadowedhearts_raid_definition", Tag.TAG_BYTE_ARRAY)) {
            ((MoonwoundRaidRewardDefinitionAccess) reward).shadowedhearts$restoreMoonwoundDefinition(
                    tag.getByteArray("shadowedhearts_raid_definition"));
        }
        return reward;
    }

    @Override
    public void shadowedhearts$restoreMoonwoundDefinition(byte[] definition) {
        shadowedhearts$frozenDefinition = definition.clone();
        try {
            raidBoss = RaidDensMoonwoundAdapter.decodeSavedDefinition(RaidDensMoonwoundAdapter.BOSS, definition);
        } catch (RuntimeException error) {
            // Keep both the Pokemon NBT and definition bytes, even if an addon/data change prevents decoding.
            Shadowedhearts.LOGGER.error("Could not restore queued Moonwound raid definition; saved Pokemon retained", error);
        }
    }

    @Inject(method = "serialize", at = @At("RETURN"))
    private void shadowedhearts$saveDefinition(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        if (!RaidDensMoonwoundAdapter.BOSS.equals(raidBossId)) return;
        byte[] definition = shadowedhearts$frozenDefinition;
        if (definition == null && raidBoss != null) definition = raidBoss.encode();
        if (definition != null) cir.getReturnValue().putByteArray("shadowedhearts_raid_definition", definition.clone());
    }

    @Inject(method = "givePokemonToPlayer", at = @At("HEAD"))
    private void shadowedhearts$decodeReward(ServerPlayer player, CallbackInfoReturnable<CompletableFuture<Boolean>> cir) {
        if (!RaidDensMoonwoundAdapter.BOSS.equals(raidBossId) || pokemonReward != null || serializedPokemonReward == null) return;
        pokemonReward = Pokemon.Companion.loadFromNBT(player.registryAccess(), serializedPokemonReward);
        new StringSpeciesFeature("raid", "reward").apply(pokemonReward);
        pokemonReward.setAlpha(false);
        pokemonReward.getPersistentData().remove(RaidDensMoonwoundAdapter.KEY);
        ShadowService.fullyCorrupt(pokemonReward, null, false);
        ShadowMoveUtil.assignShadowMoves(pokemonReward);
    }
}
