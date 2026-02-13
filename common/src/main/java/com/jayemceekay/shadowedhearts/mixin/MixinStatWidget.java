package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.client.gui.summary.featurerenderers.BarSummarySpeciesFeatureRenderer;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.stats.StatWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.client.gui.summary.widgets.screens.stats.features.HeartGaugeFeatureRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import kotlin.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = StatWidget.class)
public abstract class MixinStatWidget {

    @Shadow
    @Final
    @Mutable
    private List<BarSummarySpeciesFeatureRenderer> universalFeatures;
    @Shadow
    @Final
    private Pokemon pokemon;
    @Shadow
    @Final
    private List<String> statOptions;

    @Shadow
    private boolean statLabelsHovered(List<kotlin.Pair<Double, Double>> labelOffsets, int mouseX, int mouseY) {
        return false;
    }

    @Shadow
    public abstract int getStatTabIndex();

    private int shadowedhearts$lastMouseX;
    private int shadowedhearts$lastMouseY;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void shadowedhearts$captureMouse(GuiGraphics context, int pMouseX, int pMouseY, float pPartialTicks, CallbackInfo ci) {
        this.shadowedhearts$lastMouseX = pMouseX;
        this.shadowedhearts$lastMouseY = pMouseY;
    }

    @Inject(method = "<init>*", at = @At("TAIL"))
    private void shadowedhearts$appendHeartGauge(int pX, int pY, Pokemon pokemon, int tabIndex, CallbackInfo ci) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) {
            return;
        }
        List<BarSummarySpeciesFeatureRenderer> mutated = new ArrayList<>(this.universalFeatures);
        boolean exists = mutated.stream().anyMatch(r -> "heart_gauge".equals(r.getName()));
        if (!exists) {
            mutated.add(new HeartGaugeFeatureRenderer(pokemon));
        }
        this.universalFeatures = mutated;
    }

    @WrapOperation(
            method = "renderWidget",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/stats/StatWidget;statValuesAsText(Ljava/util/List;Z)Ljava/util/List;"
            )
    )
    private List<MutableComponent> shadowedhearts$maskEvTexts(StatWidget instance, List<Stat> stats, boolean asPercent, Operation<List<MutableComponent>> original) {
        List<MutableComponent> list = original.call(instance, stats, asPercent);
        try {
            String tab = statOptions.get(instance.getStatTabIndex());
            if (("evs".equals(tab) && ShadowAspectUtil.isEVHiddenByGauge(pokemon))
                    || ("ivs".equals(tab) && ShadowAspectUtil.isIVHiddenByGauge(pokemon))) {
                List<MutableComponent> masked = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++)
                    masked.add(Component.literal("??"));
                return masked;
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    @ModifyArg(method = "renderWidget", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/stats/StatWidget;renderPolygonLabels$default(Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/stats/StatWidget;Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Ljava/util/List;DZILjava/lang/Object;)V", ordinal = 2), index = 2)
    public List<MutableComponent> shadowedhearts$maskLabels(List<MutableComponent> par3, @Local(name = "labelsHovered") boolean labelsHovered) {
        if (labelsHovered && (statOptions.get(getStatTabIndex()).equalsIgnoreCase("ivs") || statOptions.get(getStatTabIndex()).equalsIgnoreCase("evs"))) {
            if (ShadowAspectUtil.isEVHiddenByGauge(pokemon) || ShadowAspectUtil.isIVHiddenByGauge(pokemon))
                return List.of(Component.literal("??/??"), Component.literal("??/??"), Component.literal("??/??"), Component.literal("??/??"), Component.literal("??/??"), Component.literal("??/??"));
        }

        return par3;
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/stats/StatWidget;drawStatPolygon(Ljava/util/List;Lorg/joml/Vector3f;)V", ordinal = 2))
    private void shadowedhearts$maskIVPolygon(StatWidget instance, List list, Vector3f vector3f, Operation<Void> original) {
        if (statOptions.get(getStatTabIndex()).equalsIgnoreCase("ivs") && ShadowAspectUtil.hasShadowAspect(pokemon) && ShadowAspectUtil.isIVHiddenByGauge(pokemon)) {
        return;
        }
        original.call(instance, list, vector3f);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/stats/StatWidget;drawStatPolygon(Ljava/util/List;Lorg/joml/Vector3f;)V", ordinal = 3))
    private void shadowedhearts$maskEVPolygon(StatWidget instance, List list, Vector3f vector3f, Operation<Void> original) {
        if (statOptions.get(getStatTabIndex()).equalsIgnoreCase("evs") && ShadowAspectUtil.hasShadowAspect(pokemon) && ShadowAspectUtil.isEVHiddenByGauge(pokemon) ) {
            return;
        }
        original.call(instance, list, vector3f);
    }

    @WrapMethod(method = "renderModifiedStatIcon")
    private void shadowedhearts$maskStatIcons(PoseStack pPoseStack, Stat stat, boolean increasedStat, Operation<Void> original) {
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) {
            return;
        }
        original.call(pPoseStack, stat, increasedStat);
    }

    @WrapMethod(method = "renderPolygonLabels")
    private void shadowedhearts$maskLabels(GuiGraphics context, List<? extends MutableComponent> labels, List<Pair<Double, Double>> verticesOffset, double offsetY, boolean enableColour, Operation<Void> original) {
        if (enableColour && ShadowAspectUtil.hasShadowAspect(pokemon) && statOptions.get(getStatTabIndex()).equalsIgnoreCase("stats")) {
            original.call(context, labels, verticesOffset, offsetY, false);
            return;
        }
        original.call(context, labels, verticesOffset, offsetY, enableColour);
    }
}
