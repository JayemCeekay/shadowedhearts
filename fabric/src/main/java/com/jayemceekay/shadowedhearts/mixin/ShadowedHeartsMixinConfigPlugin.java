package com.jayemceekay.shadowedhearts.mixin;

import dev.architectury.platform.Platform;
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
                    Platform.isModLoaded("cobblemon_battle_extras");
            case "com.jayemceekay.shadowedhearts.mixin.simpletms.MixinPokemonSelectingItemNonBattle" ->
                    Platform.isModLoaded("simpletms");
            case "com.jayemceekay.shadowedhearts.mixin.rctmod.MixinRCTModMakeBattle" ->
                   Platform.isModLoaded("rctmod");
            default -> true;
        };
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}