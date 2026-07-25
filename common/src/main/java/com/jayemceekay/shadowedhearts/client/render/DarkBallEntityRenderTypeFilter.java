package com.jayemceekay.shadowedhearts.client.render;

import net.minecraft.client.renderer.RenderType;

import java.util.Locale;

/**
 * Keeps Dark Ball snapshot capture limited to actual entity model geometry.
 */
final class DarkBallEntityRenderTypeFilter {

    private DarkBallEntityRenderTypeFilter() {
    }

    static boolean accepts(RenderType renderType) {
        String normalized = renderType.toString().toLowerCase(Locale.ROOT);
        if (normalized.contains("entity_shadow")
                || normalized.contains("entity_leash")
                || normalized.contains("entity_beam")) {
            return false;
        }

        return normalized.contains("entity_solid")
                || normalized.contains("entity_cutout")
                || normalized.contains("entity_translucent")
                || normalized.contains("entity_smooth_cutout")
                || normalized.contains("entity_no_outline")
                || normalized.contains("entity_decal")
                || normalized.contains("entity_alpha")
                || normalized.contains("entity_glint")
                || normalized.contains("eyes")
                || normalized.contains("outline");
    }
}
