package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.datafixer.fix.TypeGemGrowthTypeFix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/** Includes saved Shadow clusters in Cobblemon's 1.8.2 growth-state migration. */
@Mixin(value = TypeGemGrowthTypeFix.class, remap = false)
public abstract class MixinTypeGemGrowthTypeFix {
    @WrapOperation(method = "fixPaletteEntry",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"),
            require = 1, allow = 1)
    private boolean shadowedhearts$includeShadowCluster(Set<?> clusters, Object blockId,
                                                       Operation<Boolean> original) {
        // Datafixers can initialize before the block registry is available.
        return original.call(clusters, blockId) || "shadowedhearts:shadow_gem_cluster".equals(blockId);
    }
}
