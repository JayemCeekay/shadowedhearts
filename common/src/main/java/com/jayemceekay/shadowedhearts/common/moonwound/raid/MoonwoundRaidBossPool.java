package com.jayemceekay.shadowedhearts.common.moonwound.raid;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/** Uses the loaded species registry, including implemented species supplied by datapacks. */
public final class MoonwoundRaidBossPool {
    private static final double EVOLVED_CHANCE = 0.85;
    private static final int SINGLE_STAGE_MIN_STATS = 450;

    private MoonwoundRaidBossPool() { }

    public static Species select(RandomSource random) {
        List<Species> evolved = new ArrayList<>();
        List<Species> singleStage = new ArrayList<>();
        for (Species species : PokemonSpecies.getSpecies()) {
            var form = species.getStandardForm();
            Group group = classify(species.getResourceIdentifier().toString(), species.getImplemented(),
                    form.getLabels(), form.getPreEvolution() != null, !form.getEvolutions().isEmpty(),
                    form.getBaseStats().values().stream().mapToInt(Integer::intValue).sum());
            if (group == Group.EVOLVED) evolved.add(species);
            else if (group == Group.SINGLE_STAGE) singleStage.add(species);
        }
        return select(evolved, singleStage, random::nextDouble, random::nextInt);
    }

    enum Group { EXCLUDED, EVOLVED, SINGLE_STAGE }

    static Group classify(String id, boolean implemented, Set<String> labels,
                          boolean hasPreEvolution, boolean hasEvolution, int baseStats) {
        if (!implemented || "shadowedhearts:void_shadow".equals(id)
                || labels.stream().anyMatch(label -> "legendary".equalsIgnoreCase(label)
                || "mythical".equalsIgnoreCase(label)) || hasEvolution) return Group.EXCLUDED;
        if (hasPreEvolution) return Group.EVOLVED;
        return baseStats >= SINGLE_STAGE_MIN_STATS ? Group.SINGLE_STAGE : Group.EXCLUDED;
    }

    /** Category odds stay fixed even when addons supply many species in one category. */
    static <T> T select(List<T> evolved, List<T> singleStage, DoubleSupplier chance, IntUnaryOperator index) {
        if (evolved.isEmpty() && singleStage.isEmpty()) {
            throw new IllegalStateException("No eligible implemented Moonwound raid boss species are loaded");
        }
        List<T> pool = singleStage.isEmpty() || (!evolved.isEmpty() && chance.getAsDouble() < EVOLVED_CHANCE)
                ? evolved : singleStage;
        return pool.get(index.applyAsInt(pool.size()));
    }
}
