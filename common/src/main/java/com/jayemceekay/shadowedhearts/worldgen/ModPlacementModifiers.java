package com.jayemceekay.shadowedhearts.worldgen;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public class ModPlacementModifiers {
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.PLACEMENT_MODIFIER_TYPE);

    public static void init() {
        PLACEMENT.register();
    }
}
