package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public interface IModConfig {
    void load();

    boolean isLoaded();

    ModConfigSpec getSpec();
}
