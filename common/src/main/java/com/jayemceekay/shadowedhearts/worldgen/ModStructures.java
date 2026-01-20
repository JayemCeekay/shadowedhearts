package com.jayemceekay.shadowedhearts.worldgen;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.STRUCTURE_TYPE);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.STRUCTURE_PIECE);

    public static final RegistrySupplier<StructureType<MeteoroidStructure>> METEOROID =
            STRUCTURE_TYPES.register("meteoroid", () -> () -> MeteoroidStructure.CODEC);

    public static final RegistrySupplier<StructurePieceType> METEOROID_PIECE =
            STRUCTURE_PIECE_TYPES.register("meteoroid_piece", () -> MeteoroidStructurePiece::new);

    public static void init() {
        STRUCTURE_TYPES.register();
        STRUCTURE_PIECE_TYPES.register();
    }
}
