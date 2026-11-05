package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.cobblemon.mod.common.block.ApricornBlock;
import com.cobblemon.mod.common.block.BerryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Material classification, not a claim that a block was generated rather than player-placed. */
public final class UmbrafallTerrainMaterials {
    private static final TagKey<Block> REPLACEABLE = tag("umbrafall_replaceable");
    private static final TagKey<Block> PROTECTED = tag("umbrafall_protected");
    private static final TagKey<Block> VEGETATION = tag("umbrafall_vegetation");

    private static final Set<String> MANUFACTURED = Set.of(
            "brick", "bricks", "plank", "planks", "stairs", "stair", "slab", "slabs", "wall", "walls",
            "fence", "fences", "gate", "gates", "door", "doors", "trapdoor", "trapdoors", "glass", "pane", "panes",
            "button", "buttons", "pressure", "plate", "plates", "rail", "rails", "sign", "signs", "carpet",
            "concrete", "glazed", "polished", "chiseled", "carved", "cut", "smooth", "paved", "paving",
            "tile", "tiles", "tiled", "cobbled", "cobblestone", "pillar", "column", "ornate", "decorated",
            "compressed", "reinforced", "encased", "framed", "painted", "waxed", "illuminated",
            "machine", "pasture", "pastures", "healer", "healing", "computer", "monitor", "pc", "terminal",
            "resurrection", "tank", "generator", "engine", "motor", "cable", "wire", "pipe", "tube",
            "battery", "capacitor", "connector", "controller", "interface", "processor", "repeater", "comparator",
            "detector", "sensor", "observer", "lever", "piston", "dispenser", "dropper", "hopper", "crafter",
            "chest", "barrel", "crate", "storage", "furnace", "smoker", "kiln", "oven", "stove", "campfire",
            "torch", "lantern", "lamp", "light", "bulb", "beacon", "conduit", "altar", "pedestal", "shrine",
            "table", "bench", "workbench", "seat", "chair", "shelf", "bookshelf", "cabinet", "bed", "bedroll",
            "anvil", "grindstone", "stonecutter", "loom", "composter", "cauldron", "keg", "pot", "rack",
            "scaffolding", "ladder", "chain", "bell", "target", "anchor", "tnt", "portal", "frame", "spawner");
    private static final Set<String> RAW_ROCKS = Set.of(
            "stone", "deepslate", "granite", "diorite", "andesite", "basalt", "blackstone", "tuff", "calcite",
            "sandstone", "limestone", "marble", "slate", "shale", "schist", "gneiss", "rhyolite", "gabbro",
            "dolomite", "chalk", "chert", "quartzite", "conglomerate", "breccia", "soapstone", "travertine",
            "argillite", "siltstone", "mudstone", "phyllite", "pumice", "scoria", "peridotite", "jasper",
            "obsidian", "netherrack", "endstone", "dripstone");
    private static final Set<String> RAW_SOILS = Set.of(
            "dirt", "grass", "soil", "sand", "gravel", "clay", "mud", "moss", "peat", "silt", "ash",
            "loam", "humus", "podzol", "mycelium", "permafrost", "snow", "ice", "terracotta");
    private static final Set<String> TREE_PARTS = Set.of("log", "logs", "wood", "leaves", "leaf", "roots", "root");
    // Registry identities do not change on datapack reload; tag membership intentionally is not cached.
    private static final Map<Block, IdentifierPolicy> IDENTIFIERS = new ConcurrentHashMap<>();

    private UmbrafallTerrainMaterials() { }

    public static boolean replaceable(BlockState state) {
        Block block = state.getBlock();
        // Cobblemon berries store plant growth/yield data, not a player inventory or machine state.
        if ((state.hasBlockEntity() && !(block instanceof BerryBlock)) || !state.getFluidState().isEmpty()
                || state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0 || state.is(PROTECTED)) return false;
        IdentifierPolicy identifier = IDENTIFIERS.computeIfAbsent(block, UmbrafallTerrainMaterials::identifier);
        if (identifier.protectedMaterial || buildingShape(block) || state.isSignalSource()) return false;
        return state.isAir() || state.is(REPLACEABLE) || naturalClass(block) || identifier.rawMaterial;
    }

    /** Tree trunks/canopies and plants do not define the ground elevation for a crater. */
    public static boolean vegetation(BlockState state) {
        if (!replaceable(state)) return false;
        return state.is(VEGETATION) || vegetationClass(state.getBlock())
                || IDENTIFIERS.computeIfAbsent(state.getBlock(), UmbrafallTerrainMaterials::identifier).treePart;
    }

    private static boolean buildingShape(Block block) {
        return block instanceof StairBlock || block instanceof SlabBlock || block instanceof WallBlock
                || block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof DoorBlock
                || block instanceof TrapDoorBlock || block instanceof IronBarsBlock || block instanceof TransparentBlock
                || block instanceof BasePressurePlateBlock || block instanceof ButtonBlock || block instanceof BaseRailBlock
                || block instanceof DiodeBlock || block instanceof RedStoneWireBlock || block instanceof LeverBlock
                || block instanceof BaseTorchBlock || block instanceof LanternBlock || block instanceof GlazedTerracottaBlock
                || block instanceof ConcretePowderBlock || block instanceof WoolCarpetBlock || block instanceof FarmBlock;
    }

    private static boolean naturalClass(Block block) {
        return vegetationClass(block) || block instanceof DropExperienceBlock || block instanceof RedStoneOreBlock
                || block instanceof SnowyDirtBlock || block instanceof RootedDirtBlock || block instanceof MudBlock
                || block instanceof MossBlock || block instanceof IceBlock || block instanceof SnowLayerBlock
                || block instanceof PowderSnowBlock || block instanceof NetherrackBlock || block instanceof NyliumBlock
                || block instanceof SoulSandBlock || block instanceof MagmaBlock || block instanceof PointedDripstoneBlock
                || block instanceof AmethystBlock || block instanceof AmethystClusterBlock || block instanceof SculkBlock;
    }

    private static boolean vegetationClass(Block block) {
        return block instanceof BerryBlock || block instanceof ApricornBlock
                || block instanceof LeavesBlock || block instanceof BushBlock || block instanceof GrowingPlantBlock
                || block instanceof HugeMushroomBlock || block instanceof MangroveRootsBlock || block instanceof VineBlock
                || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof BambooStalkBlock
                || block instanceof GlowLichenBlock || block instanceof HangingRootsBlock || block instanceof SporeBlossomBlock;
    }

    private static IdentifierPolicy identifier(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String path = id.getPath();
        boolean own = id.getNamespace().equals("shadowedhearts")
                && (path.contains("umbrafall") || path.equals("umbral_lattice") || path.equals("shadow_residue")
                || path.equals("shadowfall_meteoroid"));
        boolean manufactured = own || manufacturedIdentifier(path);
        boolean modded = !id.getNamespace().equals("minecraft");
        return new IdentifierPolicy(manufactured, modded && naturalIdentifier(path), modded && treeIdentifier(path));
    }

    /** Conservative compatibility fallback for raw blocks whose mods omit conventional material tags. */
    static boolean naturalIdentifier(String path) {
        if (manufacturedIdentifier(path)) return false;
        String[] parts = path.split("[/_]");
        String last = parts[parts.length - 1];
        if (last.equals("block") && parts.length > 1) last = parts[parts.length - 2];
        return RAW_ROCKS.contains(last) || RAW_SOILS.contains(last) || TREE_PARTS.contains(last)
                || last.equals("ore") || last.equals("ores") || path.endsWith("moss_carpet");
    }

    private static boolean treeIdentifier(String path) {
        if (manufacturedIdentifier(path)) return false;
        String[] parts = path.split("[/_]");
        return TREE_PARTS.contains(parts[parts.length - 1]);
    }

    static boolean manufacturedIdentifier(String path) {
        // Smooth basalt generates naturally around geodes; other smooth variants are manufactured.
        if (path.equals("smooth_basalt")) return false;
        // Do not mistake natural color/plant names for lamps, carpets, piping, or building walls.
        String qualifiers = path.replace("light_blue_", "blue_").replace("light_gray_", "gray_")
                .replace("moss_carpet", "moss").replace("tube_coral", "coral")
                .replace("coral_wall_fan", "coral_fan");
        return Arrays.stream(qualifiers.split("[/_]")).anyMatch(MANUFACTURED::contains);
    }

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("shadowedhearts", name));
    }

    private record IdentifierPolicy(boolean protectedMaterial, boolean rawMaterial, boolean treePart) { }
}
