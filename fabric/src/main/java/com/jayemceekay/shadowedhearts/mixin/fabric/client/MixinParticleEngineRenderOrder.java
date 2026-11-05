package com.jayemceekay.shadowedhearts.mixin.fabric.client;

import com.jayemceekay.shadowedhearts.client.particle.ModParticleRenderTypes;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Keep our custom particle layers reachable in Fabric's fixed render order. */
@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngineRenderOrder {
    @Shadow @Final @Mutable
    private static List<ParticleRenderType> RENDER_ORDER;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void shadowedhearts$appendParticleRenderTypes(CallbackInfo ci) {
        RENDER_ORDER = ModParticleRenderTypes.appendMissingRenderTypes(RENDER_ORDER);
    }
}
