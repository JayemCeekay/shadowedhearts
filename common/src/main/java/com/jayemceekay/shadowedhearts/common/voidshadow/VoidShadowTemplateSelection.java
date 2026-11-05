package com.jayemceekay.shadowedhearts.common.voidshadow;

/** Health preservation shared by disguise application and canonical restoration. */
public final class VoidShadowTemplateSelection {
    private VoidShadowTemplateSelection() { }

    /** Keep living survivors alive while rounding a fractional HP value down conservatively. */
    public static int healthAtFraction(int maxHealth, double fraction) {
        if (maxHealth <= 0 || !Double.isFinite(fraction) || fraction <= 0) return 0;
        return Math.max(1, (int) Math.floor(maxHealth * Math.min(1, fraction)));
    }
}
