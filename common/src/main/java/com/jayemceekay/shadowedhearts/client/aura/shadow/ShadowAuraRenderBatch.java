package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyle;

import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;

import java.util.ArrayList;
import java.util.List;

/** Keeps ordinary and Hyper/Reverse palettes separate when density fields overlap. */
record ShadowAuraRenderBatch(ShadowAuraStyle style, boolean modeTint) {
    private static final List<ShadowAuraRenderBatch> ALL = createBatches();

    ShadowAuraRenderBatch {
        if (style == null) style = ShadowAuraStyle.DEFAULT;
    }

    static ShadowAuraRenderBatch of(ShadowAuraStyle style, boolean modeTint) {
        ShadowAuraStyle safeStyle = style == null ? ShadowAuraStyle.DEFAULT : style;
        return ALL.get(safeStyle.ordinal() * 2 + (modeTint ? 1 : 0));
    }

    static List<ShadowAuraRenderBatch> all() {
        return ALL;
    }

    private static List<ShadowAuraRenderBatch> createBatches() {
        List<ShadowAuraRenderBatch> batches = new ArrayList<>();
        for (ShadowAuraStyle style : ShadowAuraStyle.values()) {
            batches.add(new ShadowAuraRenderBatch(style, false));
            batches.add(new ShadowAuraRenderBatch(style, true));
        }
        return List.copyOf(batches);
    }

    static boolean hasModeTint(Iterable<String> aspects) {
        if (aspects != null) {
            for (String aspect : aspects) {
                if (SHAspects.HYPER_MODE.equals(aspect) || SHAspects.REVERSE_MODE.equals(aspect)) {
                    return true;
                }
            }
        }
        return false;
    }
}
