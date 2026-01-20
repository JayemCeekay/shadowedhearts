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

    public static final RegistrySupplier<SoundEvent> SHADOW_AURA_INITIAL_BURST = SOUNDS.register(
            "shadow_aura_initial_burst",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_aura_initial_burst"))
    );

    public static final RegistrySupplier<SoundEvent> SHADOW_AURA_LOOP = SOUNDS.register(
            "shadow_aura_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_aura_loop"))
    );

    public static final RegistrySupplier<SoundEvent> AURA_SCANNER_BEEP = SOUNDS.register(
            "aura_scanner_beep",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_scanner_beep"))
    );

    public static final RegistrySupplier<SoundEvent> RELIC_SHRINE_LOOP = SOUNDS.register(
            "relic_shrine_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "relic_shrine_loop"))
    );

    public static RegistrySupplier<SoundEvent> AURA_READER_EQUIP = SOUNDS.register(
            "aura_reader_equip",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_equip"))
    );

    public static RegistrySupplier<SoundEvent> AURA_READER_UNEQUIP = SOUNDS.register(
            "aura_reader_unequip",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_unequip"))
    );

    public static void init() {
        SOUNDS.register();
    }
}
