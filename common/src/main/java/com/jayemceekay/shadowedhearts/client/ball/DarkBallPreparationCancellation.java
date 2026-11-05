package com.jayemceekay.shadowedhearts.client.ball;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Shares one capture's cancellation token through synchronous preparation helpers. */
final class DarkBallPreparationCancellation {
    private static final ThreadLocal<BooleanSupplier> CURRENT = new ThreadLocal<>();

    private DarkBallPreparationCancellation() {
    }

    static <T> T run(BooleanSupplier cancellationRequested, Supplier<T> work) {
        BooleanSupplier previous = CURRENT.get();
        if (cancellationRequested != null) {
            CURRENT.set(previous == null ? cancellationRequested
                    : () -> previous.getAsBoolean() || cancellationRequested.getAsBoolean());
        }
        try {
            check();
            T result = work.get();
            check();
            return result;
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    static void check() {
        BooleanSupplier cancellationRequested = CURRENT.get();
        if (Thread.currentThread().isInterrupted()
                || cancellationRequested != null && cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Dark Ball preparation cancelled");
        }
    }

    /** Amortizes token reads in linear loops while bounding discarded work. */
    static void checkEvery(int iteration) {
        if ((iteration & 255) == 0) {
            check();
        }
    }
}
