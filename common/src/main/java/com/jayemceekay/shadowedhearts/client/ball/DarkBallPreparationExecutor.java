package com.jayemceekay.shadowedhearts.client.ball;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Bounded preparation jobs whose cancellation also removes queued work. */
final class DarkBallPreparationExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;

    DarkBallPreparationExecutor(int workers, int queuedJobs) {
        AtomicInteger sequence = new AtomicInteger();
        executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queuedJobs), task -> {
                    Thread thread = new Thread(task,
                            "ShadowedHearts-DarkBall-Prep-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    <T> CompletableFuture<T> submit(AtomicBoolean cancellation, Supplier<T> work) {
        Job<T> job = new Job<>(Objects.requireNonNull(cancellation),
                Objects.requireNonNull(work));
        try {
            executor.execute(job);
        } catch (RejectedExecutionException rejected) {
            job.work = null;
            job.completeExceptionally(rejected);
        }
        return job;
    }

    int queuedJobCount() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        for (Runnable queued : executor.shutdownNow()) {
            if (queued instanceof Job<?> job) job.cancel(false);
        }
    }

    private final class Job<T> extends CompletableFuture<T> implements Runnable {
        private final AtomicBoolean cancellation;
        private volatile Supplier<T> work;

        private Job(AtomicBoolean cancellation, Supplier<T> work) {
            this.cancellation = cancellation;
            this.work = work;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancellation.set(true);
            boolean cancelled = super.cancel(false);
            if (executor.remove(this)) work = null;
            return cancelled;
        }

        @Override
        public void run() {
            try {
                Supplier<T> supplier = work;
                if (isDone() || cancellation.get() || supplier == null) {
                    cancel(false);
                    return;
                }
                // Native resources must be opened inside this supplier. A queued
                // cancellation never opens them; running cancellation lets the
                // supplier's try-with-resources finish before release.
                T result = supplier.get();
                if (cancellation.get()) cancel(false);
                else complete(result);
            } catch (Throwable failure) {
                completeExceptionally(failure);
            } finally {
                work = null;
            }
        }
    }
}
