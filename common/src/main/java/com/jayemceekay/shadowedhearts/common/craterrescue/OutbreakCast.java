package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record OutbreakCast(ResourceLocation pack, ResourceLocation carrier) {
    private static final MapCodec<OutbreakCast> ROLES = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("pack").forGetter(OutbreakCast::pack),
            ResourceLocation.CODEC.optionalFieldOf("carrier").forGetter(cast -> Optional.of(cast.carrier()))
    ).apply(instance, (pack, carrier) -> new OutbreakCast(pack, carrier.orElse(pack))));
    private static final CraterCastPool<OutbreakCast> POOL = new CraterCastPool<>("crater_outbreak_casts", ROLES,
            cast -> CraterCastPool.available(cast.pack()) && CraterCastPool.available(cast.carrier()));

    @Nullable
    public static OutbreakCast select(ServerLevel level, BlockPos rim) {
        return POOL.select(level, rim);
    }
}
