package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.umbrafall.LegacyMeteoroidMigration;
import net.minecraft.nbt.NbtUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NbtUtils.class)
public abstract class MixinUmbrafallNbtBlockStateMigration {
    // Structure templates, falling blocks and moving pistons use this direct lookup path.
    @ModifyArg(method = "readBlockState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/ResourceLocation;parse(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            index = 0)
    private static String shadowedhearts$migrateLegacyBlockName(String id) {
        return LegacyMeteoroidMigration.canonicalId(id);
    }
}
