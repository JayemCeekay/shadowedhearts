package com.jayemceekay.shadowedhearts.common.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Represents a directional-input calibration sequence for the Aura Reader.
 * <p>
 * When a node requires calibration, the HUD enters a special mode and displays
 * a sequence of directional prompts. The player inputs them in order to re-lock
 * the signal and reveal the next trail segment.
 * <p>
 * Directions map thematically to scanner operations:
 * <ul>
 *   <li>UP    = amplify</li>
 *   <li>DOWN  = dampen</li>
 *   <li>LEFT  = phase shift</li>
 *   <li>RIGHT = frequency align</li>
 * </ul>
 */
public class CalibrationSequence {

    /**
     * The four directional inputs used in calibration.
     */
    public enum Direction {
        UP, DOWN, LEFT, RIGHT;

        public int toId() { return ordinal(); }

        public static Direction fromId(int id) {
            Direction[] values = values();
            if (id < 0 || id >= values.length) return UP;
            return values[id];
        }

        /** Generate a random direction. */
        public static Direction random(Random rng) {
            return values()[rng.nextInt(values().length)];
        }
    }

    /**
     * Calibration event variants that modify presentation and difficulty.
     */
    public enum Variant {
        /** Standard directional sequence. */
        HARMONIC_LOCK,
        /** Prompts wobble or shift, requiring closer attention. */
        SIGNAL_DRIFT,
        /** A false prompt flashes briefly and must be ignored. */
        SHADOW_ECHO,
        /** One portion of the sequence is inverted (left↔right, up↔down). */
        REVERSE_INTERFERENCE,
        /** Two short sequences entered back-to-back. */
        DUAL_BAND,
        /** Input must complete before a rising instability meter maxes out. */
        OVERLOAD_RECOVERY;

        public int toId() { return ordinal(); }

        public static Variant fromId(int id) {
            Variant[] values = values();
            if (id < 0 || id >= values.length) return HARMONIC_LOCK;
            return values[id];
        }
    }

    /**
     * Performance grades for calibration completion.
     */
    public enum Grade {
        /** No mistakes, fast completion. */
        PERFECT,
        /** Completed with minor hesitation. */
        STANDARD,
        /** Completed with errors or slow input. */
        SLOPPY,
        /** Failed to complete the sequence. */
        FAILED;

        public int toId() { return ordinal(); }

        public static Grade fromId(int id) {
            Grade[] values = values();
            if (id < 0 || id >= values.length) return FAILED;
            return values[id];
        }
    }

    private final List<Direction> sequence;
    private final Variant variant;
    private final int timeLimitTicks;
    private int currentInputIndex;
    private int mistakes;
    private int elapsedTicks;
    private boolean completed;
    private boolean failed;

    public CalibrationSequence(List<Direction> sequence, Variant variant, int timeLimitTicks) {
        this.sequence = Collections.unmodifiableList(new ArrayList<>(sequence));
        this.variant = variant;
        this.timeLimitTicks = timeLimitTicks;
        this.currentInputIndex = 0;
        this.mistakes = 0;
        this.elapsedTicks = 0;
        this.completed = false;
        this.failed = false;
    }

    /**
     * Generate a calibration sequence appropriate for the given tier and tension.
     */
    public static CalibrationSequence generate(ShadowSignalTier tier, float tension, Random rng) {
        int min = tier.getMinCalibrationInputs();
        int max = tier.getMaxCalibrationInputs();
        int length = min + rng.nextInt(Math.max(1, max - min + 1));

        // Tension can add 0-1 extra inputs
        if (tension > 0.6f && rng.nextFloat() < tension * 0.5f) {
            length = Math.min(length + 1, max + 1);
        }

        List<Direction> dirs = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            dirs.add(Direction.random(rng));
        }

        // Pick variant based on tier and tension
        Variant variant = pickVariant(tier, tension, rng);

        // Time limit: base 5 seconds + 1 second per input, reduced by tension
        int baseTicks = 100 + length * 20;
        int tensionReduction = (int) (tension * 40);
        int timeLimitTicks = Math.max(60, baseTicks - tensionReduction);

        return new CalibrationSequence(dirs, variant, timeLimitTicks);
    }

    private static Variant pickVariant(ShadowSignalTier tier, float tension, Random rng) {
        if (tier.getTier() <= 1) return Variant.HARMONIC_LOCK;
        if (tier.getTier() == 2) {
            return rng.nextFloat() < 0.3f ? Variant.SIGNAL_DRIFT : Variant.HARMONIC_LOCK;
        }

        // Higher tiers have access to more variants
        float roll = rng.nextFloat();
        if (tier.getTier() >= 5 && tension > 0.7f && roll < 0.15f) return Variant.OVERLOAD_RECOVERY;
        if (tier.getTier() >= 4 && roll < 0.25f) return Variant.DUAL_BAND;
        if (tier.getTier() >= 4 && roll < 0.35f) return Variant.REVERSE_INTERFERENCE;
        if (tier.getTier() >= 3 && roll < 0.5f) return Variant.SHADOW_ECHO;
        if (roll < 0.65f) return Variant.SIGNAL_DRIFT;
        return Variant.HARMONIC_LOCK;
    }

    // ── Query ──

    public List<Direction> getSequence() { return sequence; }
    public Variant getVariant() { return variant; }
    public int getTimeLimitTicks() { return timeLimitTicks; }
    public int getCurrentInputIndex() { return currentInputIndex; }
    public int getMistakes() { return mistakes; }
    public int getElapsedTicks() { return elapsedTicks; }
    public boolean isCompleted() { return completed; }
    public boolean isFailed() { return failed; }
    public int getLength() { return sequence.size(); }

    /** How far through the sequence the player is, as 0.0 – 1.0. */
    public float getProgress() {
        if (sequence.isEmpty()) return 1.0f;
        return (float) currentInputIndex / sequence.size();
    }

    /** Remaining time as 0.0 – 1.0 (1.0 = full time remaining). */
    public float getTimeRemainingNormalized() {
        if (timeLimitTicks <= 0) return 1.0f;
        return Math.max(0.0f, 1.0f - (float) elapsedTicks / timeLimitTicks);
    }

    // ── Mutation ──

    /**
     * Called each server/client tick while calibration is active.
     */
    public void tick() {
        if (completed || failed) return;
        elapsedTicks++;
        if (timeLimitTicks > 0 && elapsedTicks >= timeLimitTicks) {
            failed = true;
        }
    }

    /**
     * Submit a directional input. Returns true if the input was correct.
     */
    public boolean submitInput(Direction input) {
        if (completed || failed) return false;
        if (currentInputIndex >= sequence.size()) return false;

        Direction expected = sequence.get(currentInputIndex);

        // Handle REVERSE_INTERFERENCE variant: invert the expected direction
        if (variant == Variant.REVERSE_INTERFERENCE && shouldInvert(currentInputIndex)) {
            expected = invert(expected);
        }

        if (input == expected) {
            currentInputIndex++;
            if (currentInputIndex >= sequence.size()) {
                completed = true;
            }
            return true;
        } else {
            mistakes++;
            // On 3+ mistakes, fail the sequence
            if (mistakes >= 3) {
                failed = true;
            }
            return false;
        }
    }

    /**
     * Evaluate the performance grade based on completion state.
     */
    public Grade evaluate() {
        if (failed || !completed) return Grade.FAILED;
        if (mistakes == 0 && getTimeRemainingNormalized() > 0.5f) return Grade.PERFECT;
        if (mistakes <= 1) return Grade.STANDARD;
        return Grade.SLOPPY;
    }

    // ── Variant helpers ──

    /**
     * For REVERSE_INTERFERENCE: determines which inputs in the sequence are inverted.
     * Inverts the middle portion of the sequence.
     */
    private boolean shouldInvert(int index) {
        int len = sequence.size();
        if (len <= 2) return index == len - 1;
        int start = len / 3;
        int end = (len * 2) / 3;
        return index >= start && index <= end;
    }

    private static Direction invert(Direction d) {
        return switch (d) {
            case UP -> Direction.DOWN;
            case DOWN -> Direction.UP;
            case LEFT -> Direction.RIGHT;
            case RIGHT -> Direction.LEFT;
        };
    }
}
