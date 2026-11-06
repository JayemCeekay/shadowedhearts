package com.jayemceekay.shadowedhearts.client.particle;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum.ColosseumAuraVolume;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Persistent shuffled volume coverage, with fresh directions and radii for every birth. */
final class ColosseumMoteSpawnSequence {
    private static final double OUTER_RADIUS_CUBED = .72 * .72 * .72;
    private final Random random;
    private final int[] cells = new int[8];
    private int cursor = 8;
    private int previousCell = -1;

    ColosseumMoteSpawnSequence(long seed) { random = new Random(seed); }

    Birth next(ColosseumAuraVolume volume, double scale) {
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
        double directionY = ((cell >>> 2) + random.nextDouble()) * .5;
        double azimuth = ((cell & 3) + random.nextDouble()) * .25;
        // Most accents live outside the opaque body; retain an inner population
        // so this remains a filled field rather than another narrow shell.
        double radial = random.nextDouble();
        if (random.nextDouble() < .8) radial = OUTER_RADIUS_CUBED + radial * (1 - OUTER_RADIUS_CUBED);
        Vec3 position = samplingVolume(volume).sample(directionY, azimuth, radial);
        Vec3 velocity = ColosseumMoteMotion.initialVelocity(scale, random.nextDouble(), random.nextDouble(), random.nextDouble());
        return new Birth(position, velocity, random.nextFloat(), random.nextFloat(), random.nextInt(15));
    }

    static ColosseumAuraVolume samplingVolume(ColosseumAuraVolume modelVolume) {
        // Start motes near the feet and let their upward drift carry them higher.
        // Keep the full horizontal footprint; floating models use their own underside.
        double bottom = modelVolume.modelBounds().minY;
        double top = bottom + Math.max(.15, modelVolume.modelBounds().getYsize()) * .35;
        return new ColosseumAuraVolume(modelVolume.modelBounds(),
                new Vec3(modelVolume.center().x, (bottom + top) * .5, modelVolume.center().z),
                modelVolume.radiusX(), (top - bottom) * .5, modelVolume.radiusZ(), modelVolume.puffScale());
    }

    record Birth(Vec3 position, Vec3 velocity, float paletteRoll, float sizeRoll, int lifetimeRoll) {}
}
