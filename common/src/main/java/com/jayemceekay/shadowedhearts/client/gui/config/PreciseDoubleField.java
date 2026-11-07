package com.jayemceekay.shadowedhearts.client.gui.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.gui.controllers.string.number.DoubleFieldController;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;

/** Retain the full stored double when YACL reparses a numeric field on loss of focus. */
final class PreciseDoubleField extends DoubleFieldController {
    private final NumberFormat format;

    PreciseDoubleField(Option<Double> option, double min, double max) {
        super(option, min, max);
        // Match YACL's parser locale, including its decimal separator and localized digits.
        format = (NumberFormat) NUMBER_FORMAT.clone();
        format.setGroupingUsed(false);
        // Even the smallest subnormal fits in 340 fractional decimal places.
        format.setMaximumFractionDigits(340);
    }

    @Override
    public String getString() {
        return format.format(pendingValue());
    }

    @Override
    public Component formatValue() {
        return Component.literal(getString());
    }

    @Override
    public void setFromString(String value) {
        if (!value.equals(getString())) super.setFromString(value);
    }
}
