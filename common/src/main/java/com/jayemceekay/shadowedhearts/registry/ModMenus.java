package com.jayemceekay.shadowedhearts.registry;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserMenu;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<MenuType<UmbraInfuserMenu>> UMBRA_INFUSER =
            MENUS.register("umbra_infuser", () -> new MenuType<>(UmbraInfuserMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}

    public static void init() {
        MENUS.register();
    }
}
