package com.jayemceekay.shadowedhearts.world.gen;

import com.jayemceekay.shadowedhearts.config.IWorldAlterationConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallGeometry;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallShapeBuilder;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

public class MeteoroidStructure extends Structure {
    public static final MapCodec<MeteoroidStructure> CODEC = simpleCodec(MeteoroidStructure::new);

    public MeteoroidStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        IWorldAlterationConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();
        if (!config.meteoroidWorldGenEnabled()) {
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();

        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = context.biomeSource().getNoiseBiome(x >> 2, context.chunkGenerator().getMinY() >> 2, z >> 2, context.randomState().sampler());
        if (!ImpactLocationSelector.isBiomeAllowed(biome, config.meteoroidBiomeWhitelist(), config.meteoroidBiomeBlacklist())) {
            return Optional.empty();
        }

        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());

        // These excavation sites need dry, sufficiently deep terrain. Their generated
        // bounds must fit vertically, rather than truncating a buried core at world limits.
        if (y < context.chunkGenerator().getSeaLevel()) return Optional.empty();

        int roll = context.random().nextInt(100);
        UmbrafallTier tier = roll < 15 ? UmbrafallTier.FAINT : roll < 75 ? UmbrafallTier.WEAK
                : roll < 95 ? UmbrafallTier.MODERATE : roll < 99 ? UmbrafallTier.STRONG : UmbrafallTier.RESONANT;
        long seed = context.random().nextLong();
        UmbrafallGeometry geometry = UmbrafallGeometry.CURRENT;
        UmbrafallShapeBuilder shape = new UmbrafallShapeBuilder(tier, seed, geometry);
        if (y + shape.bounds().minY() <= context.heightAccessor().getMinBuildHeight()
                || y + shape.bounds().maxY() >= context.heightAccessor().getMaxBuildHeight()) {
            return Optional.empty();
        }

        // Check every chunk-width strip through the footprint before choosing a site.
        // A conservative height spread prevents rims suspended over ravines or mountains.
        for (int dx = -shape.radius(); dx <= shape.radius(); dx += 8) {
            for (int dz = -shape.radius(); dz <= shape.radius(); dz += 8) {
                int terrainY = context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz,
                        Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                if (terrainY < context.chunkGenerator().getSeaLevel() || Math.abs(terrainY - y) > 2) {
                    return Optional.empty();
                }
            }
        }

        BlockPos pos = new BlockPos(x, y, z);

        return Optional.of(new Structure.GenerationStub(pos, (builder) -> {
            builder.addPiece(new MeteoroidStructurePiece(pos, tier, seed, geometry));
        }));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.METEOROID.get();
    }
}
