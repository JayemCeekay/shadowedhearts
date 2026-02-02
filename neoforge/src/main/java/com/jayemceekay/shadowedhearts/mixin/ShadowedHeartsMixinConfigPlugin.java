package com.jayemceekay.shadowedhearts.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
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
            return FMLLoader.getLoadingModList().getModFileById("tim_core") != null;
        }

        return switch (mixinClassName) {
            case "com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras.CobblemonBattleExtras_1_7_24_MoveTileTooltipMixin" -> {
                var mod = FMLLoader.getLoadingModList().getModFileById("cobblemon_battle_extras");
                if (mod == null) yield false;
                try {
                    yield VersionRange.createFromVersionSpec("(,1.7.24]")
                            .containsVersion(new DefaultArtifactVersion(mod.versionString()));
                } catch (Exception e) {
                    yield false;
                }
            }
            case "com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras.CobblemonBattleExtrasNewMoveTileTooltipMixin" -> {
                var mod = FMLLoader.getLoadingModList().getModFileById("cobblemon_battle_extras");
                if (mod == null) yield false;
                try {
                    yield VersionRange.createFromVersionSpec("[1.7.25,)")
                            .containsVersion(new DefaultArtifactVersion(mod.versionString()));
                } catch (Exception e) {
                    yield false;
                }
            }
            case "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasCustomTooltipRenderer",
                 "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasNatureTooltipBuilder",
                 "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasMoveTooltipBuilder",
                 "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasMoveTooltipHelper",
                 "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasSummaryUIMixin" ->
                    FMLLoader.getLoadingModList().getModFileById("cobblemon_party_extras") != null;
            case "com.jayemceekay.shadowedhearts.mixin.simpletms.MixinPokemonSelectingItemNonBattle" ->
                    FMLLoader.getLoadingModList().getModFileById("simpletms") != null;
            case "com.jayemceekay.shadowedhearts.mixin.rctmod.MixinRCTModMakeBattle" ->
                    FMLLoader.getLoadingModList().getModFileById("rctmod") != null;
            case "com.jayemceekay.shadowedhearts.mixin.MixinIrisRenderingPipeline",
                 "com.jayemceekay.shadowedhearts.mixin.IrisRenderingPipelineAccessor" ->
                    FMLLoader.getLoadingModList().getModFileById("iris") != null;
            case "com.jayemceekay.shadowedhearts.mixin.MixinShowdownMoveset" ->
                    FMLLoader.getLoadingModList().getModFileById("mega_showdown") != null;
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