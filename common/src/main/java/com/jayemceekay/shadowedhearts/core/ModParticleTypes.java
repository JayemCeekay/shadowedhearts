package com.jayemceekay.shadowedhearts.core;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

/**
 * Standard particle registry for Shadowed Hearts.
 */
public final class ModParticleTypes {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.PARTICLE_TYPE);

    public static final RegistrySupplier<SimpleParticleType> LUMINOUS_MOTE =
            PARTICLE_TYPES.register("luminous_mote", () -> new SimpleParticleType(false) {});

    public static void register() {
        PARTICLE_TYPES.register();
    }

    private ModParticleTypes() {}
}
