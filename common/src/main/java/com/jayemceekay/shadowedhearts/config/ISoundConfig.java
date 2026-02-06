package com.jayemceekay.shadowedhearts.config;

public interface ISoundConfig {
    default float shadowAuraInitialBurstVolume() { return 3.0f; }
    default float shadowAuraLoopVolume() { return 1.0f; }
    default float auraScannerBeepVolume() { return 1.0f; }
    default float relicShrineLoopVolume() { return 1.0f; }
    default float auraReaderEquipVolume() { return 1.0f; }
    default float auraReaderUnequipVolume() { return 1.0f; }
}
