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
        if (mixinClassName.equals("com.jayemceekay.shadowedhearts.mixin.MixinCancelThrownPokeballInBattleHelper") ||
                mixinClassName.equals("com.jayemceekay.shadowedhearts.mixin.MixinPokeballHitReserved")) {
            return Platform.isModLoaded("tim_core");
        }

        return switch (mixinClassName) {
            case "com.jayemceekay.shadowedhearts.mixin.simpletms.MixinPokemonSelectingItemNonBattle",
                 "com.jayemceekay.shadowedhearts.mixin.simpletms.MixinMoveLearnItem" ->
                    Platform.isModLoaded("simpletms");
            case "com.jayemceekay.shadowedhearts.mixin.rctmod.MixinRCTModMakeBattle" ->
                   Platform.isModLoaded("rctmod");
            case "com.jayemceekay.shadowedhearts.mixin.MixinIrisRenderingPipeline",
                 "com.jayemceekay.shadowedhearts.mixin.IrisRenderingPipelineAccessor" ->
                    Platform.isModLoaded("iris");
            case "com.jayemceekay.shadowedhearts.mixin.MixinShowdownMoveset",
                 "com.jayemceekay.shadowedhearts.mixin.MixinAspectUtils",
                 "com.jayemceekay.shadowedhearts.mixin.megashowdown.MixinMegaEvoHandler",
                 "com.jayemceekay.shadowedhearts.mixin.megashowdown.MixinMegaGimmick",
                 "com.jayemceekay.shadowedhearts.mixin.megashowdown.MixinUltraBurstHandler",
                 "com.jayemceekay.shadowedhearts.mixin.megashowdown.MixinUltraGimmick" ->
                    Platform.isModLoaded("mega_showdown");
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