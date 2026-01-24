package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(RandomSpreadStructurePlacement.class)
public abstract class MixinRandomSpreadStructurePlacement extends StructurePlacement {
    protected MixinRandomSpreadStructurePlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt, Optional<ExclusionZone> exclusionZone) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        spacing = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().meteoroidSpacing();
        separation = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().meteoroidSeparation();
    }

    @Shadow
    @Final
    @Mutable
    private int spacing;

    @Shadow
    @Final
    @Mutable
    private int separation;

    @Final
    @Shadow
    private RandomSpreadType spreadType;



    @Inject(method = "spacing", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$overrideSpacing(CallbackInfoReturnable<Integer> cir) {
        if (this.salt() == 14357621) {
            cir.setReturnValue(ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().meteoroidSpacing());
        }
    }

    @Inject(method = "separation", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$overrideSeparation(CallbackInfoReturnable<Integer> cir) {
        if (this.salt() == 14357621) {
            cir.setReturnValue(ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().meteoroidSeparation());
        }
    }

    @Inject(method = "getPotentialStructureChunk", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$overridePlacement(
            long seed, int x, int z,
            CallbackInfoReturnable<ChunkPos> cir
    ) {
        if (this.salt() != 14357621) return;

        int spacing = ShadowedHeartsConfigs.getInstance()
                .getShadowConfig().worldAlteration().meteoroidSpacing();
        int separation = ShadowedHeartsConfigs.getInstance()
                .getShadowConfig().worldAlteration().meteoroidSeparation();

        int k = Math.floorDiv(x, spacing);
        int m = Math.floorDiv(z, spacing);

        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, k, m, this.salt());

        int n = spacing - separation;
        int ox = this.spreadType.evaluate(random, n);
        int oz = this.spreadType.evaluate(random, n);

        cir.setReturnValue(new ChunkPos(k * spacing + ox, m * spacing + oz));
    }

}
