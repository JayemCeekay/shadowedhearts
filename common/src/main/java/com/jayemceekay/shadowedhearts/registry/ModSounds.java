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

    public static RegistrySupplier<SoundEvent> AURA_READER_EQUIP = SOUNDS.register(
            "aura_reader_equip",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_equip"))
    );

    public static RegistrySupplier<SoundEvent> AURA_READER_UNEQUIP = SOUNDS.register(
            "aura_reader_unequip",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_unequip"))
    );

    // ── Shadow Hunt Audio ──

    /* TODO: Replace placeholder sound files in assets/shadowedhearts/sounds/hunt/ */
    public static final RegistrySupplier<SoundEvent> HUNT_TRAIL_REVEAL = SOUNDS.register(
            "hunt_trail_reveal",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_trail_reveal"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_CALIBRATION_PROMPT = SOUNDS.register(
            "hunt_calibration_prompt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_calibration_prompt"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_CALIBRATION_CORRECT = SOUNDS.register(
            "hunt_calibration_correct",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_calibration_correct"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_CALIBRATION_WRONG = SOUNDS.register(
            "hunt_calibration_wrong",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_calibration_wrong"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_CALIBRATION_COMPLETE = SOUNDS.register(
            "hunt_calibration_complete",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_calibration_complete"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_NODE_SCAN = SOUNDS.register(
            "hunt_node_scan",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_node_scan"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_EVIDENCE_CORRECT = SOUNDS.register(
            "hunt_evidence_correct",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_evidence_correct"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_EVIDENCE_WRONG = SOUNDS.register(
            "hunt_evidence_wrong",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_evidence_wrong"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_SEARCH_PING = SOUNDS.register(
            "hunt_search_ping",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_search_ping"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_WILD_AGGRO = SOUNDS.register(
            "hunt_wild_aggro",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_wild_aggro"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_PROVOCATION_BUILDUP = SOUNDS.register(
            "hunt_provocation_buildup",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_provocation_buildup"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_TENSION_AMBIENT = SOUNDS.register(
            "hunt_tension_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_tension_ambient"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_MANIFESTATION_BUILDUP = SOUNDS.register(
            "hunt_manifestation_buildup",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_manifestation_buildup"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_MANIFESTATION_REVEAL = SOUNDS.register(
            "hunt_manifestation_reveal",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_manifestation_reveal"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_GRADE_PERFECT = SOUNDS.register(
            "hunt_grade_perfect",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_grade_perfect"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_GRADE_SLOPPY = SOUNDS.register(
            "hunt_grade_sloppy",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_grade_sloppy"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_GRADE_FAILED = SOUNDS.register(
            "hunt_grade_failed",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_grade_failed"))
    );

    /* TODO: Replace placeholder sound file */
    public static final RegistrySupplier<SoundEvent> HUNT_SIGNAL_BLACKOUT = SOUNDS.register(
            "hunt_signal_blackout",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "hunt_signal_blackout"))
    );

    public static void init() {
        SOUNDS.register();
    }
}
