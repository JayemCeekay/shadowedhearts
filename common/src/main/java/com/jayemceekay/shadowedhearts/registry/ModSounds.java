package com.jayemceekay.shadowedhearts.registry;

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

    public static final RegistrySupplier<SoundEvent> MOONWOUND_AMBIENT_LOOP = SOUNDS.register(
            "moonwound_ambient_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "moonwound_ambient_loop"))
    );

    public static final RegistrySupplier<SoundEvent> MOONWOUND_OPEN = SOUNDS.register(
            "moonwound_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "moonwound_open"))
    );

    public static final RegistrySupplier<SoundEvent> VOID_SHADOW_TRANSFORM = SOUNDS.register(
            "void_shadow_transform",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "void_shadow_transform"))
    );

    public static final RegistrySupplier<SoundEvent> SHADOW_SIGNAL_SEARCH = SOUNDS.register(
            "shadow_signal_search",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_signal_search"))
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
