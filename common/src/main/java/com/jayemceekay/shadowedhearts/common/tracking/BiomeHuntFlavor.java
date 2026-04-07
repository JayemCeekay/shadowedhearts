package com.jayemceekay.shadowedhearts.common.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Random;

/**
 * Provides biome-specific theming for hunt events.
 * Maps biome categories to contextual clue descriptions, environmental themes,
 * and flavor text that make each hunt feel embedded in its environment.
 * <p>
 * Biome categories:
 * <ul>
 *   <li><b>FOREST</b> — broken branches, clawed bark, rustling movement</li>
 *   <li><b>DESERT</b> — scorched sand, heat haze, buried clue fragments</li>
 *   <li><b>SNOWY</b> — frozen traces, delayed scanner response</li>
 *   <li><b>SWAMP</b> — residue pools, toxic mist, unstable footing</li>
 *   <li><b>MOUNTAIN</b> — claw marks on stone, echoing signals, unstable rock</li>
 *   <li><b>OCEAN/BEACH</b> — washed-up traces, salt-corroded signals</li>
 *   <li><b>CAVE</b> — deep residue, mineral interference, faint echoes</li>
 *   <li><b>PLAINS</b> — trampled grass, open-field disturbances</li>
 * </ul>
 */
public final class BiomeHuntFlavor {

    public enum BiomeCategory {
        FOREST,
        DESERT,
        SNOWY,
        SWAMP,
        MOUNTAIN,
        OCEAN,
        CAVE,
        PLAINS;

        /**
         * Get a list of evidence clue descriptions for this biome.
         * Used by Evidence Interpretation events to provide biome-contextual clue text.
         */
        public List<String> getClueDescriptions() {
            return switch (this) {
                case FOREST -> List.of(
                        "Broken branches with dark residue",
                        "Deep claw marks on bark",
                        "Disturbed underbrush with shadow traces",
                        "Cracked tree trunk oozing dark energy",
                        "Scattered leaves with a corrupted shimmer",
                        "A hollow log emanating faint shadow pulses"
                );
                case DESERT -> List.of(
                        "Scorched sand with glass-like patterns",
                        "Heat-distorted shadow residue",
                        "Buried fragment pulsing with dark energy",
                        "Wind-swept dune with corrupted crystals",
                        "Cracked earth emitting shadow vapor",
                        "Sand-covered tracks with an unnatural glow"
                );
                case SNOWY -> List.of(
                        "Frozen shadow traces in the ice",
                        "Frost-covered footprints with dark aura",
                        "Ice crystals vibrating with shadow energy",
                        "A snow drift concealing dark residue",
                        "Frozen bark with shadowy claw marks",
                        "Icicles tinted with corrupted energy"
                );
                case SWAMP -> List.of(
                        "Dark residue pooling on murky water",
                        "Toxic mist clinging to shadow traces",
                        "Rotting wood seeping with dark energy",
                        "Bubbling mud with corrupted undertones",
                        "Vine-covered stone with shadow stains",
                        "Disturbed wetland with an eerie glow"
                );
                case MOUNTAIN -> List.of(
                        "Deep claw marks scored into stone",
                        "Rock face cracked by shadow energy",
                        "Mineral deposits resonating with dark pulses",
                        "Loose gravel disturbed by a heavy presence",
                        "Cave entrance emanating shadow interference",
                        "Stone altar with corrupted energy traces"
                );
                case OCEAN -> List.of(
                        "Washed-up traces of shadow residue",
                        "Salt-corroded signal fragments",
                        "Coral formations pulsing with dark energy",
                        "Tide-smoothed stones with shadow patterns",
                        "Driftwood marked with corrupted claw traces",
                        "Shoreline sand swirled with dark energy"
                );
                case CAVE -> List.of(
                        "Deep mineral veins corrupted by shadow",
                        "Stalactite dripping with dark residue",
                        "Ancient stone wall scarred by shadow claws",
                        "Phosphorescent moss tainted by dark energy",
                        "Underground pool reflecting shadow distortion",
                        "Collapsed tunnel with concentrated shadow traces"
                );
                case PLAINS -> List.of(
                        "Trampled grass with shadow residue",
                        "Disturbed soil with a dark energy signature",
                        "Wildflowers wilted by corrupted aura",
                        "A clear patch of scorched earth",
                        "Scattered stones arranged in an unnatural pattern",
                        "Tall grass bent by an invisible force"
                );
            };
        }

        /**
         * Get a biome-specific environmental search hint.
         */
        public String getSearchHint() {
            return switch (this) {
                case FOREST -> "Check behind trees and under fallen logs";
                case DESERT -> "Search around rock formations and dunes";
                case SNOWY -> "Look beneath snowdrifts and frozen surfaces";
                case SWAMP -> "Probe the edges of pools and rotting vegetation";
                case MOUNTAIN -> "Scan cliff faces and rocky outcrops";
                case OCEAN -> "Search along the shoreline and tide pools";
                case CAVE -> "Investigate mineral deposits and alcoves";
                case PLAINS -> "Scan open ground disturbances and tall grass";
            };
        }
    }

    private BiomeHuntFlavor() {}

    /**
     * Determine the biome category for a given position in a level.
     */
    public static BiomeCategory categorize(ServerLevel level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);

        // Check tags for categorization
        if (biomeHolder.is(BiomeTags.IS_FOREST) || biomeHolder.is(BiomeTags.IS_JUNGLE)) {
            return BiomeCategory.FOREST;
        }
        if (biomeHolder.is(BiomeTags.IS_BADLANDS)) {
            return BiomeCategory.DESERT;
        }
        // Check for snowy biomes
        if (biomeHolder.value().coldEnoughToSnow(pos)) {
            return BiomeCategory.SNOWY;
        }
        if (biomeHolder.is(BiomeTags.IS_OCEAN) || biomeHolder.is(BiomeTags.IS_BEACH) || biomeHolder.is(BiomeTags.IS_RIVER)) {
            return BiomeCategory.OCEAN;
        }
        if (biomeHolder.is(BiomeTags.IS_MOUNTAIN) || biomeHolder.is(BiomeTags.IS_HILL)) {
            return BiomeCategory.MOUNTAIN;
        }
        // Check if underground
        if (pos.getY() < level.getSeaLevel() - 10) {
            return BiomeCategory.CAVE;
        }
        // Check biome name for swamp hints
        var biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isPresent()) {
            String biomeName = biomeKey.get().location().getPath().toLowerCase();
            if (biomeName.contains("swamp") || biomeName.contains("marsh") || biomeName.contains("mangrove")) {
                return BiomeCategory.SWAMP;
            }
            if (biomeName.contains("desert") || biomeName.contains("mesa") || biomeName.contains("badland")) {
                return BiomeCategory.DESERT;
            }
        }

        return BiomeCategory.PLAINS;
    }

    /**
     * Pick a random clue description for the given biome category.
     */
    public static String randomClueDescription(BiomeCategory category, Random rng) {
        List<String> descriptions = category.getClueDescriptions();
        return descriptions.get(rng.nextInt(descriptions.size()));
    }

    /**
     * Pick multiple unique clue descriptions (for evidence interpretation with multiple options).
     */
    public static List<String> randomClueDescriptions(BiomeCategory category, int count, Random rng) {
        List<String> all = category.getClueDescriptions();
        java.util.List<String> shuffled = new java.util.ArrayList<>(all);
        java.util.Collections.shuffle(shuffled, rng);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
}
