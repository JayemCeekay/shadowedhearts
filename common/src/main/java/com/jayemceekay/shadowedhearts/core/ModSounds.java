package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> SHADOW_SPAWN = SOUNDS.register(
            "shadow_spawn",
            () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_spawn"), 64.0f)
    );

    public static void init() {
        SOUNDS.register();
    }
}
