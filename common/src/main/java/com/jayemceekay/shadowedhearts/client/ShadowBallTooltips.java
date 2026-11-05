package com.jayemceekay.shadowedhearts.client;

import com.jayemceekay.shadowedhearts.common.capture.DarkBallClientState;
import com.jayemceekay.shadowedhearts.common.capture.DarkBallSettings;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.math.BigDecimal;
import java.util.List;

/** Describes the current server's ball settings, including custom configuration values. */
public final class ShadowBallTooltips {
    private ShadowBallTooltips() {
    }

    public static void append(ItemStack stack, List<Component> lines, Item.TooltipContext context, TooltipFlag flag) {
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        if (stack.is(ModItems.PENUMBRA_BALL.get())) {
            appendValues(lines, config.penumbraBallCatchMultiplier(), config.penumbraBallShadowCatchMultiplier(),
                    config.penumbraBallShadowConversionChance(), config.penumbraBallHeartGaugeMaxBonus());
        } else if (stack.is(ModItems.ANTUMBRA_BALL.get())) {
            appendValues(lines, config.antumbraBallCatchMultiplier(), config.antumbraBallShadowCatchMultiplier(),
                    config.antumbraBallShadowConversionChance(), config.antumbraBallHeartGaugeMaxBonus());
        } else if (stack.is(ModItems.UMBRA_BALL.get())) {
            appendValues(lines, config.umbraBallCatchMultiplier(), config.umbraBallShadowCatchMultiplier(),
                    config.umbraBallShadowConversionChance(), config.umbraBallHeartGaugeMaxBonus());
        } else if (stack.is(ModItems.DARK_BALL.get())) {
            appendDarkBall(lines);
        }
    }

    private static void appendDarkBall(List<Component> lines) {
        DarkBallSettings settings = DarkBallClientState.INSTANCE.currentSettings(DarkBallSettings::serverCurrent);
        if (settings == null) {
            lines.add(Component.translatable("tooltip.shadowedhearts.dark_ball.pending")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(Component.translatable(settings.corruptionEnabled()
                        ? "tooltip.shadowedhearts.dark_ball.corruption"
                        : "tooltip.shadowedhearts.dark_ball.corruption_disabled")
                .withStyle(ChatFormatting.GRAY));
        lines.add((settings.levelBoost() == 0
                ? Component.translatable("tooltip.shadowedhearts.dark_ball.level_boost_disabled")
                : Component.translatable("tooltip.shadowedhearts.dark_ball.level_boost", settings.levelBoost()))
                .withStyle(ChatFormatting.GRAY));
        if (settings.corruptionEnabled()) lines.add(Component.translatable("tooltip.shadowedhearts.dark_ball.maximum_gauge",
                        formatNumber(settings.heartGaugeMultiplier()))
                .withStyle(ChatFormatting.GRAY));
    }

    private static void appendValues(List<Component> lines, double ordinaryMultiplier, double shadowMultiplier,
                                     double conversionChance, double maximumGaugeBonus) {
        lines.add(Component.translatable("tooltip.shadowedhearts.shadow_ball.ordinary_catch", formatNumber(ordinaryMultiplier))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.shadowedhearts.shadow_ball.shadow_catch", formatNumber(shadowMultiplier))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.shadowedhearts.shadow_ball.conversion", formatPercent(conversionChance))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.shadowedhearts.shadow_ball.maximum_gauge", formatPercent(maximumGaugeBonus))
                .withStyle(ChatFormatting.GRAY));
    }

    static String formatNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String formatPercent(double value) {
        return BigDecimal.valueOf(value).movePointRight(2).stripTrailingZeros().toPlainString();
    }
}
