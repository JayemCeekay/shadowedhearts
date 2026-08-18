package com.jayemceekay.shadowedhearts.client.ball;

/**
 * Deterministic scalar foundation for transporting topology-independent Dark
 * Ball surface splats toward the siphon inlet.
 *
 * <p>Section coordinate {@code 0} is the first, farthest released section and
 * coordinate {@code 1} is the inlet throat. The transported coordinate may
 * extend slightly beyond one so ownership can transfer through a finite
 * terminal collar instead of retiring the body at the inlet plane.</p>
 *
 * <p>The longitudinal formulas deliberately use only clamp, mix, and cubic
 * smoothstep so the eventual GLSL implementation can mirror this class
 * without accumulated simulation state. The optional near-inlet curl is a
 * bounded planar rotation and therefore cannot change a splat's radial
 * distance from its direct inlet route.</p>
 *
 * <pre>
 * activation = smoothstep(s - w, s + w, front)
 * travelEase = smoothstep(s + w, s + w + travelSpan, front)
 * frontQ     = clamp(max(front, s), s, terminalQ)
 * q          = mix(s, frontQ, travelEase)
 * path       = clamp((q - s) / (terminalQ - s), 0, 1)
 * radius     = mix(1, minimumRadius, path)
 * retirement = smoothstep(1 - retireWidth, 1, path)
 * collar     = retirement
 *              * (1 - smoothstep(collarExitQ - exitWidth,
 *                                collarExitQ, q))
 * </pre>
 *
 * <p>This renderer-independent reference mirrors the shader transport
 * contract and also supplies the CPU-side final-section retirement gate.</p>
 */
final class DarkBallSplatTransportMath {
    static final float CURL_START_PROGRESS = 0.72f;
    static final float MAX_CURL_ANGLE_RADIANS = 0.14f;
    static final float KELVINLET_PINCH_START_PROGRESS = 0.18f;
    static final float KELVINLET_PINCH_FULL_PROGRESS = 0.88f;
    static final float MAX_KELVINLET_PINCH = 0.18f;
    static final float HANDOFF_START_PROGRESS = 0.62f;
    static final float HANDOFF_FULL_PROGRESS = 0.94f;

    static final Parameters DEFAULT_PARAMETERS = new Parameters(
            0.085f,
            0.20f,
            0.18f,
            1.0f,
            1.10f,
            0.018f,
            1.08f,
            0.018f);

    private DarkBallSplatTransportMath() {
    }

    static Sample sample(float sectionCoordinate, float releaseFront) {
        return sample(
                sectionCoordinate,
                releaseFront,
                DEFAULT_PARAMETERS);
    }

    static Sample sample(
            float sectionCoordinate,
            float releaseFront,
            Parameters parameters) {
        requireFinite("sectionCoordinate", sectionCoordinate);
        requireFinite("releaseFront", releaseFront);
        Parameters checked = requireParameters(parameters);
        float section = clampUnit(sectionCoordinate);
        float activation = sectionActivation(
                section,
                releaseFront,
                checked);
        float travel = travelProgress(
                section,
                releaseFront,
                checked);
        float coordinate = transportedCoordinate(
                section,
                releaseFront,
                travel,
                checked);
        float normalizedPathProgress = normalizedPathProgress(
                section,
                coordinate,
                checked);
        float crossSectionScale = crossSectionScale(
                normalizedPathProgress,
                checked);
        float retirement = routeRetirementProgress(
                normalizedPathProgress,
                checked);
        float collarRetirement = retirementProgress(
                coordinate,
                checked);
        return new Sample(
                activation,
                travel,
                coordinate,
                normalizedPathProgress,
                crossSectionScale,
                1.0f - retirement,
                terminalCollarOwnership(
                        coordinate,
                        collarRetirement,
                        checked));
    }

    static float sectionActivation(
            float sectionCoordinate,
            float releaseFront,
            Parameters parameters) {
        requireFinite("sectionCoordinate", sectionCoordinate);
        requireFinite("releaseFront", releaseFront);
        Parameters checked = requireParameters(parameters);
        float section = clampUnit(sectionCoordinate);
        return smoothstep(
                section - checked.activationHalfWidth(),
                section + checked.activationHalfWidth(),
                releaseFront);
    }

    /**
     * Starts longitudinal travel only after the local activation feather has
     * completed. Progress is monotonic in the advancing release front.
     */
    static float travelProgress(
            float sectionCoordinate,
            float releaseFront,
            Parameters parameters) {
        requireFinite("sectionCoordinate", sectionCoordinate);
        requireFinite("releaseFront", releaseFront);
        Parameters checked = requireParameters(parameters);
        float section = clampUnit(sectionCoordinate);
        float travelStart =
                section + checked.activationHalfWidth();
        return smoothstep(
                travelStart,
                travelStart + checked.travelFrontSpan(),
                releaseFront);
    }

    /**
     * Returns the furthest coordinate this section may currently target. The
     * advancing release front, rather than the final terminal coordinate,
     * owns this bound. Consequently an activated section cannot leap through
     * unreleased sweep frames.
     */
    static float frontTargetCoordinate(
            float sectionCoordinate,
            float releaseFront,
            Parameters parameters) {
        requireFinite("sectionCoordinate", sectionCoordinate);
        requireFinite("releaseFront", releaseFront);
        Parameters checked = requireParameters(parameters);
        float section = clampUnit(sectionCoordinate);
        return clamp(
                Math.max(releaseFront, section),
                section,
                checked.terminalCoordinate());
    }

    static float transportedCoordinate(
            float sectionCoordinate,
            float releaseFront,
            float travelProgress,
            Parameters parameters) {
        requireFinite("sectionCoordinate", sectionCoordinate);
        requireFinite("releaseFront", releaseFront);
        requireFinite("travelProgress", travelProgress);
        Parameters checked = requireParameters(parameters);
        float section = clampUnit(sectionCoordinate);
        float frontTarget = frontTargetCoordinate(
                section,
                releaseFront,
                checked);
        return mix(
                section,
                frontTarget,
                clampUnit(travelProgress));
    }

    /**
     * Measures how much of this section's complete route to the terminal has
     * actually been covered. This is deliberately independent of the local
     * easing schedule: a section that has finished easing but whose release
     * front has advanced only a short distance must remain broad.
     */
    static float normalizedPathProgress(
            float sectionCoordinate,
            float transportedCoordinate,
            Parameters parameters) {
        requireFinite("sectionCoordinate", sectionCoordinate);
        requireFinite(
                "transportedCoordinate",
                transportedCoordinate);
        Parameters checked = requireParameters(parameters);
        float section = clampUnit(sectionCoordinate);
        float routeLength =
                checked.terminalCoordinate() - section;
        return clampUnit(
                (transportedCoordinate - section) / routeLength);
    }

    static float crossSectionScale(
            float normalizedPathProgress,
            Parameters parameters) {
        requireFinite(
                "normalizedPathProgress",
                normalizedPathProgress);
        Parameters checked = requireParameters(parameters);
        return mix(
                1.0f,
                checked.minimumCrossSectionScale(),
                clampUnit(normalizedPathProgress));
    }

    /**
     * Returns the splat's remaining direct distance to the inlet. Applying the
     * already-monotonic normalized path progress to each splat's own rest
     * distance avoids the shared-spine expansion and reversal failure mode.
     */
    static float remainingInletDistance(
            float restInletDistance,
            float normalizedPathProgress) {
        requireFinite("restInletDistance", restInletDistance);
        requireFinite(
                "normalizedPathProgress",
                normalizedPathProgress);
        if (restInletDistance < 0.0f) {
            throw new IllegalArgumentException(
                    "restInletDistance must not be negative");
        }
        return restInletDistance
                * (1.0f - clampUnit(normalizedPathProgress));
    }

    /**
     * Cumulative transverse constriction used by the shader's bounded
     * Kelvinlet-style inlet pinch. The scale can only decrease as a local
     * section advances, so it cannot create the old expansion/reversal hook.
     */
    static float kelvinletPinchScale(
            float normalizedPathProgress,
            float neighborhoodCoherence) {
        requireFinite(
                "normalizedPathProgress",
                normalizedPathProgress);
        requireFinite(
                "neighborhoodCoherence",
                neighborhoodCoherence);
        float activation = smoothstep(
                KELVINLET_PINCH_START_PROGRESS,
                KELVINLET_PINCH_FULL_PROGRESS,
                clampUnit(normalizedPathProgress));
        float strength = MAX_KELVINLET_PINCH
                * activation
                * mix(0.76f, 1.0f,
                clampUnit(neighborhoodCoherence));
        return 1.0f - strength;
    }

    static float remainingPinchedInletDistance(
            float restAxialDistance,
            float restTransverseDistance,
            float normalizedPathProgress,
            float neighborhoodCoherence) {
        requireFinite(
                "restAxialDistance",
                restAxialDistance);
        requireFinite(
                "restTransverseDistance",
                restTransverseDistance);
        if (restTransverseDistance < 0.0f) {
            throw new IllegalArgumentException(
                    "restTransverseDistance must not be negative");
        }
        float pinchScale = kelvinletPinchScale(
                normalizedPathProgress,
                neighborhoodCoherence);
        float pinchedDistance = (float) Math.hypot(
                restAxialDistance,
                restTransverseDistance * pinchScale);
        return remainingInletDistance(
                pinchedDistance,
                normalizedPathProgress);
    }

    static float siphonHandoffBlend(float normalizedPathProgress) {
        requireFinite(
                "normalizedPathProgress",
                normalizedPathProgress);
        return smoothstep(
                HANDOFF_START_PROGRESS,
                HANDOFF_FULL_PROGRESS,
                clampUnit(normalizedPathProgress));
    }

    /**
     * Supplies a small signed curl angle only near the inlet. The bell-shaped
     * envelope is exactly zero both before the curl window and at the terminal,
     * preventing a persistent sideways hook or terminal orbit.
     */
    static float nearInletCurlAngle(
            float normalizedPathProgress,
            float signedCurlStrength) {
        requireFinite(
                "normalizedPathProgress",
                normalizedPathProgress);
        requireFinite("signedCurlStrength", signedCurlStrength);
        float windowProgress = smoothstep(
                CURL_START_PROGRESS,
                1.0f,
                clampUnit(normalizedPathProgress));
        float endpointZeroEnvelope =
                4.0f * windowProgress * (1.0f - windowProgress);
        return clamp(
                signedCurlStrength,
                -1.0f,
                1.0f)
                * MAX_CURL_ANGLE_RADIANS
                * endpointZeroEnvelope;
    }

    /**
     * Rotates a direct-route radial offset by the bounded near-inlet curl.
     * This is a pure rotation: no curl term may enlarge or shrink the offset.
     */
    static CurlOffset applyNearInletCurl(
            float radialX,
            float radialY,
            float normalizedPathProgress,
            float signedCurlStrength) {
        requireFinite("radialX", radialX);
        requireFinite("radialY", radialY);
        float angle = nearInletCurlAngle(
                normalizedPathProgress,
                signedCurlStrength);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        return new CurlOffset(
                radialX * cosine - radialY * sine,
                radialX * sine + radialY * cosine,
                angle);
    }

    static float retirementProgress(
            float transportedCoordinate,
            Parameters parameters) {
        requireFinite(
                "transportedCoordinate",
                transportedCoordinate);
        Parameters checked = requireParameters(parameters);
        return smoothstep(
                checked.throatCoordinate(),
                checked.throatCoordinate()
                        + checked.retirementWidth(),
                transportedCoordinate);
    }

    static float bodyOwnership(
            float transportedCoordinate,
            Parameters parameters) {
        return 1.0f - retirementProgress(
                transportedCoordinate,
                parameters);
    }

    /**
     * Retires a surfel only at the end of its complete anatomical route. The
     * legacy throat-coordinate ownership helpers remain available for the
     * independent siphon collar, but no longer hide body splats mid-route.
     */
    static float routeRetirementProgress(
            float normalizedPathProgress,
            Parameters parameters) {
        requireFinite(
                "normalizedPathProgress",
                normalizedPathProgress);
        Parameters checked = requireParameters(parameters);
        float width = clamp(
                checked.retirementWidth(),
                0.0001f,
                0.25f);
        return smoothstep(
                1.0f - width,
                1.0f,
                clampUnit(normalizedPathProgress));
    }

    static float routeBodyOwnership(
            float normalizedPathProgress,
            Parameters parameters) {
        return 1.0f - routeRetirementProgress(
                normalizedPathProgress,
                parameters);
    }

    static float terminalCollarOwnership(
            float transportedCoordinate,
            Parameters parameters) {
        return terminalCollarOwnership(
                transportedCoordinate,
                retirementProgress(
                        transportedCoordinate,
                        parameters),
                requireParameters(parameters));
    }

    private static float terminalCollarOwnership(
            float transportedCoordinate,
            float retirementProgress,
            Parameters parameters) {
        float collarExit = smoothstep(
                parameters.collarExitCoordinate()
                        - parameters.collarExitWidth(),
                parameters.collarExitCoordinate(),
                transportedCoordinate);
        return clampUnit(
                retirementProgress * (1.0f - collarExit));
    }

    private static Parameters requireParameters(Parameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException(
                    "transport parameters must not be null");
        }
        return parameters;
    }

    private static void requireFinite(String name, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " must be finite");
        }
    }

    private static float smoothstep(
            float edge0,
            float edge1,
            float value) {
        float t = clampUnit(
                (value - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float mix(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static float clampUnit(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    record Parameters(
            float activationHalfWidth,
            float travelFrontSpan,
            float minimumCrossSectionScale,
            float throatCoordinate,
            float terminalCoordinate,
            float retirementWidth,
            float collarExitCoordinate,
            float collarExitWidth) {
        Parameters {
            requireFinite(
                    "activationHalfWidth",
                    activationHalfWidth);
            requireFinite("travelFrontSpan", travelFrontSpan);
            requireFinite(
                    "minimumCrossSectionScale",
                    minimumCrossSectionScale);
            requireFinite(
                    "throatCoordinate",
                    throatCoordinate);
            requireFinite(
                    "terminalCoordinate",
                    terminalCoordinate);
            requireFinite("retirementWidth", retirementWidth);
            requireFinite(
                    "collarExitCoordinate",
                    collarExitCoordinate);
            requireFinite("collarExitWidth", collarExitWidth);

            if (!(activationHalfWidth > 0.0f)) {
                throw new IllegalArgumentException(
                        "activationHalfWidth must be positive");
            }
            if (!(travelFrontSpan > 0.0f)) {
                throw new IllegalArgumentException(
                        "travelFrontSpan must be positive");
            }
            if (!(minimumCrossSectionScale > 0.0f)
                    || minimumCrossSectionScale > 1.0f) {
                throw new IllegalArgumentException(
                        "minimumCrossSectionScale must be in (0, 1]");
            }
            if (throatCoordinate < 1.0f) {
                throw new IllegalArgumentException(
                        "throatCoordinate must preserve inlet coordinate 1");
            }
            if (!(terminalCoordinate > throatCoordinate)) {
                throw new IllegalArgumentException(
                        "terminalCoordinate must follow the throat");
            }
            if (!(retirementWidth > 0.0f)) {
                throw new IllegalArgumentException(
                        "retirementWidth must be positive");
            }
            if (!(collarExitWidth > 0.0f)) {
                throw new IllegalArgumentException(
                        "collarExitWidth must be positive");
            }
            if (!(collarExitCoordinate
                    > throatCoordinate + retirementWidth)) {
                throw new IllegalArgumentException(
                        "collarExitCoordinate must leave a collar window");
            }
            if (collarExitCoordinate > terminalCoordinate) {
                throw new IllegalArgumentException(
                        "collarExitCoordinate must not follow terminalCoordinate");
            }
            if (collarExitCoordinate - collarExitWidth
                    < throatCoordinate + retirementWidth) {
                throw new IllegalArgumentException(
                        "collar exit feather must follow body retirement");
            }
        }
    }

    record Sample(
            float activation,
            float travelProgress,
            float transportedCoordinate,
            float normalizedPathProgress,
            float crossSectionScale,
            float bodyOwnership,
            float terminalCollarOwnership) {
    }

    record CurlOffset(float x, float y, float angleRadians) {
    }
}
