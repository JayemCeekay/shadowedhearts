package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public record HowlCast(ResourceLocation caller, ResourceLocation responder) {
    private static final MapCodec<HowlCast> ROLES = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("caller").forGetter(HowlCast::caller),
            ResourceLocation.CODEC.fieldOf("responder").forGetter(HowlCast::responder)
    ).apply(instance, HowlCast::new));
    private static final CraterCastPool<HowlCast> POOL = new CraterCastPool<>("crater_howl_casts", ROLES,
            cast -> CraterCastPool.available(cast.caller()) && CraterCastPool.available(cast.responder()));

    public static @Nullable HowlCast select(ServerLevel level, BlockPos origin) {
        return POOL.select(level, origin);
    }
}
