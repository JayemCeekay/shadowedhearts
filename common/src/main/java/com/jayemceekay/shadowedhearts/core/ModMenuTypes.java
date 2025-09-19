// common/src/main/java/com/jayemceekay/shadowedhearts/core/ModMenuTypes.java
package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.blocks.menu.SignalLocatorMenu;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Cross-platform MenuType registry (Architectury). */
public final class ModMenuTypes {
    private ModMenuTypes() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.MENU);

    /** Your factory doesn’t read the buffer, so a plain MenuSupplier works. */
    public static final RegistrySupplier<MenuType<SignalLocatorMenu>> SIGNAL_LOCATOR_MENU =
            MENUS.register("signal_locator",
                    () -> new MenuType<>(SignalLocatorMenu::new, FeatureFlags.VANILLA_SET));

    /** Call once during common setup on both loaders. */
    public static void init() {
        MENUS.register();
    }

    /** Client-only: bind screens in your platform client init. */
    public static void registerClientScreens() {
        // Example: Menu -> Screen hookup (implement SignalLocatorScreen)
        // MenuRegistry.registerScreenFactory(SIGNAL_LOCATOR_MENU.get(), SignalLocatorScreen::new);
    }
}
