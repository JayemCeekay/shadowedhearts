package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.umbrafall.LegacyMeteoroidMigration;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockState.class)
public abstract class MixinUmbrafallBlockStateMigration {
    // Covers chunk palettes and block states in entities/components using BlockState.CODEC.
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/DefaultedRegistry;byNameCodec()Lcom/mojang/serialization/Codec;"))
    private static Codec<Block> shadowedhearts$migrateLegacyBlockId(Codec<Block> original) {
        return LegacyMeteoroidMigration.canonicalIds(original);
    }
}
