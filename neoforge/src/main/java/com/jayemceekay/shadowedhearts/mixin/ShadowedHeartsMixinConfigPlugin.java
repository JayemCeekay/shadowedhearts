package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.moonwound.raid.MoonwoundRaidCompatibility;
import com.jayemceekay.shadowedhearts.integration.yacl.YaclCompatibility;
import com.jayemceekay.shadowedhearts.integration.CraterSosCompatibility;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ShadowedHeartsMixinConfigPlugin implements IMixinConfigPlugin {


    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("com.jayemceekay.shadowedhearts.mixin.sos.")) {
            return FMLLoader.getLoadingModList().getMods().stream().anyMatch(mod -> mod.getModId().equals(CraterSosCompatibility.MOD_ID)
                    && (mixinClassName.endsWith(".MixinCraterSosEligibility") || CraterSosCompatibility.supports(mod.getVersion().toString())));
        }
        if (mixinClassName.startsWith("com.jayemceekay.shadowedhearts.mixin.horde.")) {
            return FMLLoader.getLoadingModList().getModFileById("hordeencounters") != null
                    && FMLLoader.getLoadingModList().getModFileById("asymmetricbattles") != null;
        }
        if (mixinClassName.startsWith("com.jayemceekay.shadowedhearts.mixin.yacl.")) {
            return FMLLoader.getLoadingModList().getMods().stream().anyMatch(mod -> mod.getModId().equals(YaclCompatibility.MOD_ID)
                    && YaclCompatibility.supportsHeadingLayout(mod.getVersion().toString()));
        }
        if (mixinClassName.startsWith("com.jayemceekay.shadowedhearts.mixin.emi.")) {
            return FMLLoader.getLoadingModList().getModFileById("emi") != null;
        }
        if (mixinClassName.startsWith("com.jayemceekay.shadowedhearts.mixin.raiddens.")) {
            return FMLLoader.getLoadingModList().getMods().stream()
                    .anyMatch(mod -> mod.getModId().equals(MoonwoundRaidCompatibility.MOD_ID)
                            && MoonwoundRaidCompatibility.supportsMixin(mixinClassName, mod.getVersion().toString()));
        }

        if (mixinClassName.equals("com.jayemceekay.shadowedhearts.mixin.MixinCancelThrownPokeballInBattleHelper") ||
                mixinClassName.equals("com.jayemceekay.shadowedhearts.mixin.MixinPokeballHitReserved")) {
            return FMLLoader.getLoadingModList().getModFileById("tim_core") != null;
        }

        return switch (mixinClassName) {
            case "com.jayemceekay.shadowedhearts.mixin.simpletms.MixinPokemonSelectingItemNonBattle",
                 "com.jayemceekay.shadowedhearts.mixin.simpletms.MixinMoveLearnItem" ->
                    FMLLoader.getLoadingModList().getModFileById("simpletms") != null;
            case "com.jayemceekay.shadowedhearts.mixin.MixinIrisRenderingPipeline",
                 "com.jayemceekay.shadowedhearts.mixin.IrisRenderingPipelineAccessor" ->
                    FMLLoader.getLoadingModList().getModFileById("iris") != null;
            case "com.jayemceekay.shadowedhearts.mixin.MixinShowdownMoveset",
                 "com.jayemceekay.shadowedhearts.mixin.MixinAspectUtils",
                 "com.jayemceekay.shadowedhearts.mixin.megashowdown.MixinMegaEvoHandler",
                 "com.jayemceekay.shadowedhearts.mixin.megashowdown.MixinMegaGimmick",
                 "com.jayemceekay.shadowedhearts.mixin.megashowdown.MixinUltraBurstHandler",
                 "com.jayemceekay.shadowedhearts.mixin.megashowdown.MixinUltraGimmick" ->
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
