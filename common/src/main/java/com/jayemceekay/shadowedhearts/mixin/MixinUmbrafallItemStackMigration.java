package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.umbrafall.LegacyMeteoroidMigration;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class MixinUmbrafallItemStackMigration {
    // All native stack codecs share this ID codec, including nested component inventories.
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/DefaultedRegistry;holderByNameCodec()Lcom/mojang/serialization/Codec;"))
    private static Codec<Holder<Item>> shadowedhearts$migrateLegacyItemId(Codec<Holder<Item>> original) {
        return LegacyMeteoroidMigration.canonicalItemIds(original);
    }
}
