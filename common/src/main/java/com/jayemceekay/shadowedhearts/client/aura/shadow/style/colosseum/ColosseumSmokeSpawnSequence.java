package com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Per-source footprint coverage with fresh positions within each shuffled cell. */
public final class ColosseumSmokeSpawnSequence {
    private final Random random;
    private final int[] cells = new int[8];
    private int cursor = cells.length;
    private int previousCell = -1;

    public ColosseumSmokeSpawnSequence(long seed) {
        random = new Random(seed);
    }

    public Vec3 next(ColosseumAuraVolume volume) {
        if (cursor == cells.length) {
            for (int i = 0; i < cells.length; i++) cells[i] = i;
            for (int i = cells.length - 1; i > 0; i--) {
                int other = random.nextInt(i + 1), swap = cells[i];
                cells[i] = cells[other]; cells[other] = swap;
            }
            if (cells[0] == previousCell) {
                int swap = cells[0]; cells[0] = cells[1]; cells[1] = swap;
            }
            cursor = 0;
        }
        int cell = cells[cursor++];
        previousCell = cell;
        double heightRoll = random.nextDouble();
        double azimuthRoll = ((cell & 3) + random.nextDouble()) * .25;
        // sampleSmokeBase takes a squared radius roll. Splitting that roll
        // evenly gives equal-area inner/outer bands, not a thin outer ring.
        double radialRoll = ((cell >>> 2) + random.nextDouble()) * .5;
        return volume.sampleSmokeBase(heightRoll, azimuthRoll, radialRoll);
    }
}
