package com.jayemceekay.shadowedhearts.client.gui;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ShadowMaskingUtil {
    public static final MutableComponent MASKED_NAME = Component.literal("????").copy().withStyle(s -> s.withBold(true));
    public static final MutableComponent MASKED_PP = Component.literal("??/??").copy().withStyle(s -> s.withBold(true));
    public static final MutableComponent MASKED_STAT = Component.literal("??").copy().withStyle(st -> st.withBold(true));
    public static final MutableComponent MASKED_NATURE = Component.literal("????").copy();
    public static final MutableComponent MASKED_DESC = Component.literal("?????").copy();
    public static final MutableComponent MASKED_VAL = Component.literal("???").copy();

    public static final float[] NEUTRAL_TINT = {0.12f, 0.12f, 0.12f, 1.0f};

    public static ElementalType getLockedType() {
        return ElementalTypes.INSTANCE.get("shadow-locked");
    }
}
