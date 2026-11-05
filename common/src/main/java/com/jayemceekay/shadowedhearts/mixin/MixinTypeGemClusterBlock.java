package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.block.TypeGemClusterBlock;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends native gem growth without adding Shadow gems to natural world generation. */
@Mixin(value = TypeGemClusterBlock.class, remap = false)
public abstract class MixinTypeGemClusterBlock {
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = true)
    private void shadowedhearts$preserveLegacyStackGrowth(
            BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null || !state.is(ModBlocks.SHADOW_GEM_CLUSTER.get())) return;

        var component = context.getItemInHand().get(DataComponents.BLOCK_STATE);
        if (component == null) return;
        var properties = component.properties();
        if (properties.containsKey("growth_type")
                || (!properties.containsKey("should_grow") && !properties.containsKey("stunted"))) return;

        // The native datafixer handles chunk palettes, but inventory components can
        // still carry the old flags. BlockItem later applies stage/facing and ignores
        // these removed properties, leaving this translated growth type intact.
        boolean stunted = "true".equals(properties.getOrDefault("stunted", "false"));
        boolean shouldGrow = "true".equals(properties.getOrDefault("should_grow", "true"));
        var growthType = stunted ? TypeGemClusterBlock.GrowthType.STUNTED
                : shouldGrow ? TypeGemClusterBlock.GrowthType.FULL : TypeGemClusterBlock.GrowthType.DECORATION;
        cir.setReturnValue(state.setValue(TypeGemClusterBlock.Companion.getGROWTH_TYPE(), growthType));
    }

    @Inject(method = "gemClusterCanGrow", at = @At("RETURN"), cancellable = true)
    private void shadowedhearts$allowShadowGemSupport(
            BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        var growthType = state.getValue(TypeGemClusterBlock.Companion.getGROWTH_TYPE());
        if (cir.getReturnValueZ()
                || growthType == TypeGemClusterBlock.GrowthType.DECORATION
                || (growthType == TypeGemClusterBlock.GrowthType.STUNTED
                    && state.getValue(TypeGemClusterBlock.Companion.getSTAGE()) == 3)) {
            return;
        }

        var supportPos = pos.relative(state.getValue(TypeGemClusterBlock.Companion.getFACING()).getOpposite());
        if (level.getBlockState(supportPos).is(ModBlocks.SHADOW_GEM_BLOCK.get())) {
            cir.setReturnValue(true);
        }
    }
}
