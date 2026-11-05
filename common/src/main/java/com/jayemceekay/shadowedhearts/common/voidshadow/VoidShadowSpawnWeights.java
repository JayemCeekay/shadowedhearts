package com.jayemceekay.shadowedhearts.common.voidshadow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/** Action-free sampling of already evaluated, per-context distinct Cobblemon spawn entries. */
public final class VoidShadowSpawnWeights {
    private VoidShadowSpawnWeights() { }

    /** Weight is the highest native position-adjusted weight; percentage -1 is native "unset". */
    public record Candidate<T>(T value, double weight, double percentage) { }
    public record Context<T>(double weight, List<Candidate<T>> candidates) {
        public Context { candidates = candidates == null ? List.of() : List.copyOf(candidates); }
    }
    public record Bucket<T>(double weight, List<Context<T>> contexts) {
        public Bucket { contexts = contexts == null ? List.of() : List.copyOf(contexts); }
    }

    /**
     * Returns null when no eligible entry exists or a supplied random value is invalid.
     * Buckets and position types are chosen before the native percentage/weight passes.
     * No world state, spawn action, Pokémon, or entity is created or changed here.
     */
    public static <T> T select(List<Bucket<T>> buckets, DoubleSupplier random) {
        if (buckets == null || random == null) return null;
        List<ReadyBucket<T>> eligible = new ArrayList<>();
        for (Bucket<T> bucket : buckets) {
            if (bucket == null || !positiveFinite(bucket.weight())) continue;
            List<ReadyContext<T>> contexts = new ArrayList<>();
            for (Context<T> context : bucket.contexts()) {
                ReadyContext<T> ready = prepare(context);
                if (ready != null) contexts.add(ready);
            }
            if (!contexts.isEmpty()) eligible.add(new ReadyBucket<>(bucket.weight(), contexts));
        }
        ReadyBucket<T> bucket = weighted(eligible, ReadyBucket::weight, ignored -> 1, random);
        if (bucket == null) return null;
        // Count eligible distinct details, not positions or the sum of candidate weights.
        ReadyContext<T> context = weighted(bucket.contexts(), ReadyContext::weight,
                value -> value.candidates().size(), random);
        if (context == null) return null;
        if (context.percentSum() > 0) {
            double roll = random.getAsDouble();
            if (!validRoll(roll)) return null;
            double selectedPercentage = 100 - roll * 100;
            double cumulative = 0;
            for (Candidate<T> candidate : context.candidates()) {
                if (candidate.percentage() <= 0) continue;
                cumulative += candidate.percentage();
                if (cumulative >= selectedPercentage) return candidate.value();
            }
        }
        // Native fallback includes percentage-bearing entries that also have positive weight.
        Candidate<T> selected = weighted(context.candidates(), Candidate::weight, ignored -> 1, random);
        return selected == null ? null : selected.value();
    }

    private static <T> ReadyContext<T> prepare(Context<T> context) {
        if (context == null || !positiveFinite(context.weight())) return null;
        List<Candidate<T>> candidates = new ArrayList<>();
        double percentSum = 0;
        for (Candidate<T> candidate : context.candidates()) {
            double percentage = candidate.percentage();
            if (!Double.isFinite(percentage) || percentage > 100 || (percentage < 0 && percentage != -1)) return null;
            if (candidate.value() == null || !Double.isFinite(candidate.weight())) continue;
            if (candidate.weight() <= 0 && percentage <= 0) continue;
            double weight = Math.max(0, candidate.weight());
            percentage = Math.max(0, percentage);
            percentSum += percentage;
            if (percentSum > 100) return null;
            candidates.add(new Candidate<>(candidate.value(), weight, percentage));
        }
        return candidates.isEmpty() ? null : new ReadyContext<>(context.weight(), candidates, percentSum);
    }

    private static <T> T weighted(List<T> items, ToDoubleFunction<T> weights,
                                  ToIntFunction<T> multiplicity, DoubleSupplier random) {
        double maxWeight = 0;
        for (T item : items) if (positiveFinite(weights.applyAsDouble(item))) {
            maxWeight = Math.max(maxWeight, weights.applyAsDouble(item));
        }
        if (maxWeight == 0) return null;
        double total = 0;
        for (T item : items) {
            double weight = weights.applyAsDouble(item);
            if (positiveFinite(weight)) total += (weight / maxWeight) * multiplicity.applyAsInt(item);
        }
        double roll = random.getAsDouble();
        if (!validRoll(roll)) return null;
        double target = roll * total, cumulative = 0;
        T last = null;
        for (T item : items) {
            double weight = weights.applyAsDouble(item);
            if (!positiveFinite(weight)) continue;
            double scaled = (weight / maxWeight) * multiplicity.applyAsInt(item);
            if (scaled <= 0) continue;
            last = item;
            cumulative += scaled;
            // Match Cobblemon's inclusive cumulative boundary, including a zero roll.
            if (cumulative >= target) return item;
        }
        return last;
    }

    private static boolean positiveFinite(double value) { return Double.isFinite(value) && value > 0; }
    private static boolean validRoll(double value) { return Double.isFinite(value) && value >= 0 && value < 1; }
    private record ReadyBucket<T>(double weight, List<ReadyContext<T>> contexts) { }
    private record ReadyContext<T>(double weight, List<Candidate<T>> candidates, double percentSum) { }
}
