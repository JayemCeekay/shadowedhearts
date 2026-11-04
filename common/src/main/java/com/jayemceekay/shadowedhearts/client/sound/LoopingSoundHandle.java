package com.jayemceekay.shadowedhearts.client.sound;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Owns a loop independently of the sound engine, which can discard playback without stopping its instance. */
public final class LoopingSoundHandle<T> {
    private static final long RETRY_TICKS = 20;
    private T sound;
    private long attemptedAt = Long.MIN_VALUE;

    public T sound() { return sound; }

    public void update(long tick, boolean audible, Supplier<T> create, Predicate<T> playing,
                       Consumer<T> play, Consumer<T> dispose) {
        if (!audible) {
            stop(dispose);
            return;
        }
        // Give asynchronous playback time to acquire a channel and bound retries for unavailable audio.
        if (attemptedAt != Long.MIN_VALUE && tick >= attemptedAt && tick - attemptedAt < RETRY_TICKS) return;
        if (sound != null && playing.test(sound)) return;
        stop(dispose);
        attemptedAt = tick;
        sound = create.get();
        play.accept(sound);
    }

    public void stop(Consumer<T> dispose) {
        T previous = sound;
        sound = null;
        attemptedAt = Long.MIN_VALUE;
        if (previous != null) dispose.accept(previous);
    }
}
