package com.jayemceekay.shadowedhearts.config;

public interface IClientConfig extends IModConfig {
    default boolean enableShadowAura() { return true; }
    default boolean auraScannerEnabled() { return true; }
    default float auraReaderYOffset() { return -0.15f; }
    ISoundConfig soundConfig();
    /**
     * If true, temperatures in HUD are displayed in Fahrenheit instead of Celsius.
     */
    default boolean useFahrenheitDisplay() { return false; }
}
