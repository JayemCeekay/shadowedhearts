package com.jayemceekay.shadowedhearts.worldgen;

import com.jayemceekay.shadowedhearts.config.IWorldAlterationConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

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

        BlockPos pos = new BlockPos(x, y, z);

        return Optional.of(new Structure.GenerationStub(pos, (builder) -> {
            generatePieces(builder, context, pos);
        }));
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context, BlockPos pos) {
        builder.addPiece(new MeteoroidStructurePiece(pos));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.METEOROID.get();
    }
}
