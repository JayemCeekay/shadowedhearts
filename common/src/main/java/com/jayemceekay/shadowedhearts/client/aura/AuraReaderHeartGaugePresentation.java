package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraHeartGaugeReadout;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Formats the complete Heart Gauge information approved for this viewer. */
public final class AuraReaderHeartGaugePresentation {
    private AuraReaderHeartGaugePresentation() {
    }

    public static String format(
            AuraHeartGaugeReadout readout, String label, String bandLabel, String hiddenLabel) {
        Objects.requireNonNull(readout, "readout");
        String value = switch (readout.disclosure()) {
            case HIDDEN -> hiddenLabel;
            case BANDED -> bandLabel;
            case EXACT -> Component.translatable("hud.shadowedhearts.aura_reader.heart_gauge.exact_value",
                    readout.current(), percentLabel(readout)).getString();
        };
        return Component.translatable("hud.shadowedhearts.aura_reader.heart_gauge.value", label, value).getString();
    }

    public static String percentLabel(AuraHeartGaugeReadout readout) {
        return readout.current() > 0 && readout.percent() == 0
                ? Component.translatable("hud.shadowedhearts.aura_reader.heart_gauge.below_one_percent").getString()
                : Component.translatable("hud.shadowedhearts.aura_reader.heart_gauge.percent", readout.percent()).getString();
    }
}
