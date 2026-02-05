package com.jayemceekay.shadowedhearts.mixin;

import dev.architectury.platform.Platform;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
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
            case "com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras.CobblemonBattleExtras_1_7_24_MoveTileTooltipMixin" -> {
                if( !Platform.isModLoaded("cobblemon-battle-extras"))
                    yield false;
                var mod = Platform.getMod("cobblemon-battle-extras");
                if (mod == null) yield false;
                try {
                    yield VersionPredicate.parse("<=1.7.24").test(Version.parse(mod.getVersion()));
                } catch (VersionParsingException e) {
                    yield false;
                }
            }
            case "com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras.CobblemonBattleExtrasNewMoveTileTooltipMixin" -> {
                if( !Platform.isModLoaded("cobblemon-battle-extras"))
                    yield false;
                var mod = Platform.getMod("cobblemon-battle-extras");
                if (mod == null) yield false;
                try {
                    yield VersionPredicate.parse(">=1.7.25").test(Version.parse(mod.getVersion()));
                } catch (VersionParsingException e) {
                    yield false;
                }
            }
            case "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasCustomTooltipRenderer",
                 "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasNatureTooltipBuilder",
                 "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasMoveTooltipBuilder",
                 "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasMoveTooltipHelper",
                 "com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras.MixinCobblemonPartyExtrasSummaryUIMixin" ->
                    Platform.isModLoaded("cobblemon_party_extras");
            case "com.jayemceekay.shadowedhearts.mixin.simpletms.MixinPokemonSelectingItemNonBattle" ->
                    Platform.isModLoaded("simpletms");
            case "com.jayemceekay.shadowedhearts.mixin.rctmod.MixinRCTModMakeBattle" ->
                   Platform.isModLoaded("rctmod");
            case "com.jayemceekay.shadowedhearts.mixin.MixinIrisRenderingPipeline",
                 "com.jayemceekay.shadowedhearts.mixin.IrisRenderingPipelineAccessor" ->
                    Platform.isModLoaded("iris");
            case "com.jayemceekay.shadowedhearts.mixin.MixinShowdownMoveset" ->
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