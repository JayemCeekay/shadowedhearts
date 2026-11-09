package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public record CraterRescueCast(ResourceLocation attacker, ResourceLocation victim) {
    private static final MapCodec<CraterRescueCast> ROLES = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("attacker").forGetter(CraterRescueCast::attacker),
            ResourceLocation.CODEC.fieldOf("victim").forGetter(CraterRescueCast::victim)
    ).apply(instance, CraterRescueCast::new));
    private static final CraterCastPool<CraterRescueCast> POOL = new CraterCastPool<>("crater_rescue_casts", ROLES,
            cast -> CraterCastPool.available(cast.attacker()) && CraterCastPool.available(cast.victim()));

    @Nullable
    public static CraterRescueCast select(ServerLevel level, BlockPos rim) {
        return POOL.select(level, rim);
    }
}
