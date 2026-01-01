package com.jayemceekay.shadowedhearts.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ShadowedHeartsMixinConfigPlugin implements IMixinConfigPlugin {


    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return switch (mixinClassName) {
            case "com.jayemceekay.shadowedhearts.mixin.CobblemonBattleExtrasMoveTileTooltipMixin" ->
                    FMLLoader.getLoadingModList().getModFileById("cobblemon_battle_extras") != null;
            case "com.jayemceekay.shadowedhearts.mixin.simpletms.MixinPokemonSelectingItemNonBattle" ->
                    FMLLoader.getLoadingModList().getModFileById("simpletms") != null;
            case "com.jayemceekay.shadowedhearts.mixin.rctmod.MixinRCTModMakeBattle" ->
                    FMLLoader.getLoadingModList().getModFileById("rctmod") != null;
            default -> true;
        };
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}