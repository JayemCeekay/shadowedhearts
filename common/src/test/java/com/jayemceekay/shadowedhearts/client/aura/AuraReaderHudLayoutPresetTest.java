package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuraReaderHudLayoutPresetTest {
    @Test
    void fromNameIsCaseInsensitiveAndDefaultsSafely() {
        assertEquals(
                AuraReaderHudLayoutPreset.ULTRAWIDE,
                AuraReaderHudLayoutPreset.fromName("uLtRaWiDe"));
        assertEquals(
                AuraReaderHudLayoutPreset.STREAMER,
                AuraReaderHudLayoutPreset.fromName(" streamer "));
        assertEquals(
                AuraReaderHudLayoutPreset.DEFAULT,
                AuraReaderHudLayoutPreset.fromName(null));
        assertEquals(
                AuraReaderHudLayoutPreset.DEFAULT,
                AuraReaderHudLayoutPreset.fromName("not-a-preset"));
    }
}
