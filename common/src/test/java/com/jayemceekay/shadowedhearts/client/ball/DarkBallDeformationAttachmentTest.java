package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallDeformationAttachmentTest {
    private static final float EPSILON = 0.000001f;

    @Test
    void turbulenceRetractsBeforeItsSourceMaterialIsDepleted() {
        assertEquals(1.0f,
                DarkBallAdvectedDensityField.deformationAttachment(
                        0.0f, 0.0f, 0.0f),
                EPSILON);

        float siphon = 0.50f;
        float front = siphon * 1.16f - 0.045f;
        assertEquals(0.0f,
                DarkBallAdvectedDensityField.deformationAttachment(
                        front, siphon, 0.0f),
                EPSILON);
        assertEquals(1.0f,
                DarkBallAdvectedDensityField.deformationAttachment(
                        1.0f, siphon, 0.0f),
                EPSILON);

        float previous = 1.0f;
        for (int step = 0; step <= 20; step++) {
            float progress = step / 20.0f;
            float attachment =
                    DarkBallAdvectedDensityField.deformationAttachment(
                            0.72f, progress, 0.0f);
            assertTrue(attachment <= previous + EPSILON);
            previous = attachment;
        }
    }

}
