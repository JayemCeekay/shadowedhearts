package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.block.TypeGemCoreBlock;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/** Lets native crystal cores propagate an attached Shadow gem through native growth rules. */
@Mixin(value = TypeGemCoreBlock.Companion.class, remap = false)
public abstract class MixinTypeGemCoreBlock {
    @Unique
    private static final ResourceLocation shadowedhearts$gemBlockId =
            ResourceLocation.parse("shadowedhearts:shadow_gem_block");
    @Unique
    private static final ResourceLocation shadowedhearts$gemClusterId =
            ResourceLocation.parse("shadowedhearts:shadow_gem_cluster");

    // Addons can query and extend this map before our deferred blocks are registered.
    // Keep the shared native map so their later registrations remain visible to growth.
    @ModifyReturnValue(method = "getBLOCK_TO_CLUSTER", at = @At("RETURN"), require = 1)
    private Map<ResourceLocation, Block> shadowedhearts$resolveShadowGemCluster(Map<ResourceLocation, Block> original) {
        Block cluster = BuiltInRegistries.BLOCK.getOptional(shadowedhearts$gemClusterId).orElse(null);
        if (cluster != null) {
            original.putIfAbsent(shadowedhearts$gemBlockId, cluster);
        }
        return original;
    }
}
