package com.jayemceekay.shadowedhearts.content.infusion;

/** Server-side charge accounting. Charge is spent only when a complete job commits. */
public final class InfusionProgress {
    public static final int CAPACITY = 64;
    /** Serialization/recipe ceiling; actual capacity comes from server policy and connected tanks. */
    public static final int MAX_CAPACITY = 1024 * 9;
    private int capacity;
    private int charge;
    private int progress;
    private String job = "";
    private int cost;
    private int duration;

    public InfusionProgress() { this(CAPACITY); }

    public InfusionProgress(int capacity) { setCapacity(capacity); }

    public int capacity() { return capacity; }
    public int charge() { return charge; }
    public int progress() { return progress; }
    public String job() { return job; }
    public int cost() { return cost; }
    public int duration() { return duration; }

    /** Updates the accounting view; physical energy remains in the individual machine/tank stores. */
    public void setCapacity(int capacity) {
        if (capacity < 1 || capacity > MAX_CAPACITY) throw new IllegalArgumentException("Invalid infusion capacity");
        this.capacity = capacity;
        charge = Math.min(charge, capacity);
        if (cost > capacity) cancel();
    }

    public boolean addCharge(int amount) {
        if (amount <= 0 || amount > capacity - charge) return false;
        charge += amount;
        return true;
    }

    public void cancel() {
        progress = 0;
        job = "";
        cost = 0;
        duration = 0;
    }

    public void restore(int charge, int progress, String job, int cost, int duration) {
        this.charge = Math.clamp(charge, 0, capacity);
        cancel();
        if (valid(cost, duration) && job != null && !job.isEmpty()) {
            this.job = job;
            this.cost = cost;
            this.duration = duration;
            this.progress = Math.clamp(progress, 0, duration - 1);
        }
    }

    public void restore(int capacity, int charge, int progress, String job, int cost, int duration) {
        setCapacity(capacity);
        restore(charge, progress, job, cost, duration);
    }

    /** Returns true once, on the tick that pays for a finished operation. */
    public boolean tick(boolean assembled, String job, int cost, int duration) {
        if (!assembled || job == null || job.isEmpty() || !valid(cost, duration) || charge < cost) {
            cancel();
            return false;
        }
        if (!this.job.equals(job) || this.cost != cost || this.duration != duration) {
            cancel();
            this.job = job;
            this.cost = cost;
            this.duration = duration;
        }
        if (++progress < duration) return false;
        charge -= cost;
        cancel();
        return true;
    }

    private boolean valid(int cost, int duration) {
        return cost >= 1 && cost <= capacity && duration >= 1 && duration <= 72000;
    }
}
