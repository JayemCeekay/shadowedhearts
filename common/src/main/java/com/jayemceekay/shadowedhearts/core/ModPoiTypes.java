package com.jayemceekay.shadowedhearts.core;

import com.google.common.collect.ImmutableSet;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class ModPoiTypes {
    private ModPoiTypes() {}

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.POINT_OF_INTEREST_TYPE);

    public static final RegistrySupplier<PoiType> SHADOWFALL_METEOROID = POI_TYPES.register(
            "shadowfall_meteoroid",
            () -> new PoiType(getBlockStates(ModBlocks.SHADOWFALL_METEOROID.get()), 0, 1)
    );

    private static Set<BlockState> getBlockStates(Block block) {
        return ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates());
    }

    public static void init() {
        POI_TYPES.register();
    }
}
