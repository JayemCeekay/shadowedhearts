package com.jayemceekay.shadowedhearts.config;

public interface IClientConfig extends IModConfig {
    default boolean enableShadowAura() { return true; }
    default boolean useXdAura() { return false; }
}
