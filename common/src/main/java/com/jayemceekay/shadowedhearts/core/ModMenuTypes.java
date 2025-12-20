// common/src/main/java/com/jayemceekay/shadowedhearts/core/ModMenuTypes.java
package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

/** Cross-platform MenuType registry (Architectury). */
public final class ModMenuTypes {
    private ModMenuTypes() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.MENU);

    /** Call once during common setup on both loaders. */
    public static void init() {
        MENUS.register();
    }

}
