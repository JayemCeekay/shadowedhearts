#version 150

uniform sampler2D PreviousDensitySampler;
uniform sampler2D ShapeSampler;

uniform vec3 VolumeSize;
uniform vec3 OutletLocal;
uniform vec3 SiphonP0;
uniform vec3 SiphonP1;
uniform float BodyRadius;
uniform float VoxelSize;
uniform float DeltaTime;
uniform float PreviousSiphonProgress;
uniform float SiphonProgress;
uniform float PreviousFinalCollapse;
uniform float FinalCollapse;

in vec2 texCoord0;
out vec4 fragColor;

const int GRID_X = 96;
const int GRID_Y = 64;
const int GRID_Z = 64;

vec3 safeNormalize(vec3 value, vec3 fallback) {
    float lengthSquared = dot(value, value);
    if (lengthSquared > 0.0000001) {
        return value * inversesqrt(lengthSquared);
    }
    float fallbackLengthSquared = dot(fallback, fallback);
    if (fallbackLengthSquared > 0.0000001) {
        return fallback * inversesqrt(fallbackLengthSquared);
    }
    return vec3(1.0, 0.0, 0.0);
}

bool cellInBounds(ivec3 cell) {
    return cell.x >= 0 && cell.x < GRID_X
        && cell.y >= 0 && cell.y < GRID_Y
        && cell.z >= 0 && cell.z < GRID_Z;
}

vec4 shapeTexel(ivec3 cell) {
    if (!cellInBounds(cell)) {
        return vec4(VolumeSize.y * 4.0, 0.0, 1.0, 0.0);
    }
    return texelFetch(ShapeSampler, ivec2(cell.z * GRID_X + cell.x, cell.y), 0);
}

float densityTexel(ivec3 cell) {
    if (!cellInBounds(cell)) {
        return 0.0;
    }
    return texelFetch(PreviousDensitySampler,
            ivec2(cell.z * GRID_X + cell.x, cell.y), 0).r;
}

vec3 localToGrid(vec3 localPos) {
    return vec3(
        localPos.x / max(VolumeSize.x, 0.0001) * float(GRID_X) - 0.5,
        (localPos.y / max(VolumeSize.y, 0.0001) * 0.5 + 0.5) * float(GRID_Y) - 0.5,
        (localPos.z / max(VolumeSize.z, 0.0001) * 0.5 + 0.5) * float(GRID_Z) - 0.5
    );
}

vec3 gridToLocal(vec3 grid) {
    return vec3(
        (grid.x + 0.5) / float(GRID_X) * VolumeSize.x,
        ((grid.y + 0.5) / float(GRID_Y) * 2.0 - 1.0) * VolumeSize.y,
        ((grid.z + 0.5) / float(GRID_Z) * 2.0 - 1.0) * VolumeSize.z
    );
}

vec3 gridSpacing() {
    return vec3(
        VolumeSize.x / float(GRID_X),
        VolumeSize.y * 2.0 / float(GRID_Y),
        VolumeSize.z * 2.0 / float(GRID_Z)
    );
}

vec4 sampleShapeLocal(vec3 localPos) {
    vec3 grid = localToGrid(localPos);
    if (grid.x < -1.0 || grid.x > float(GRID_X)
            || grid.y < -1.0 || grid.y > float(GRID_Y)
            || grid.z < -1.0 || grid.z > float(GRID_Z)) {
        return vec4(VolumeSize.y * 4.0, 0.0, 1.0, 0.0);
    }
    ivec3 base = ivec3(floor(grid));
    vec3 f = fract(grid);
    vec4 c000 = shapeTexel(base);
    vec4 c100 = shapeTexel(base + ivec3(1, 0, 0));
    vec4 c010 = shapeTexel(base + ivec3(0, 1, 0));
    vec4 c110 = shapeTexel(base + ivec3(1, 1, 0));
    vec4 c001 = shapeTexel(base + ivec3(0, 0, 1));
    vec4 c101 = shapeTexel(base + ivec3(1, 0, 1));
    vec4 c011 = shapeTexel(base + ivec3(0, 1, 1));
    vec4 c111 = shapeTexel(base + ivec3(1, 1, 1));
    return mix(mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y),
               mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y), f.z);
}

float sampleDensityLocal(vec3 localPos) {
    vec3 grid = localToGrid(localPos);
    if (grid.x < -1.0 || grid.x > float(GRID_X)
            || grid.y < -1.0 || grid.y > float(GRID_Y)
            || grid.z < -1.0 || grid.z > float(GRID_Z)) {
        return 0.0;
    }
    ivec3 base = ivec3(floor(grid));
    vec3 f = fract(grid);
    float c000 = densityTexel(base);
    float c100 = densityTexel(base + ivec3(1, 0, 0));
    float c010 = densityTexel(base + ivec3(0, 1, 0));
    float c110 = densityTexel(base + ivec3(1, 1, 0));
    float c001 = densityTexel(base + ivec3(0, 0, 1));
    float c101 = densityTexel(base + ivec3(1, 0, 1));
    float c011 = densityTexel(base + ivec3(0, 1, 1));
    float c111 = densityTexel(base + ivec3(1, 1, 1));
    return mix(mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y),
               mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y), f.z);
}

float combinedAllowedSdf(vec3 localPos) {
    return sampleShapeLocal(localPos).r;
}

float releaseRemaining(float releaseOrder, float siphonProgress, float finalCollapse) {
    if (siphonProgress <= 0.001) {
        return 1.0;
    }
    float front = clamp(siphonProgress * 1.16 - 0.045 + finalCollapse * 0.16,
            0.0, 1.30);
    float width = 0.130 + (1.0 - min(siphonProgress, 1.0)) * 0.045;
    return smoothstep(front - width, front + width, releaseOrder);
}

float baseBodyDensity(vec4 shape) {
    float inside = smoothstep(-VoxelSize * 0.75, VoxelSize * 1.45, -shape.r);
    float absorption = 1.0 - exp(-max(shape.g, VoxelSize)
            / max(BodyRadius * 0.34, 0.001));
    return inside * mix(0.72, 1.12, absorption);
}

vec3 releaseGradientAtCell(ivec3 cell) {
    vec4 center = shapeTexel(cell);
    if (center.a < 0.5) {
        return vec3(0.0);
    }

    vec3 spacing = gridSpacing();
    vec4 minusX = shapeTexel(cell - ivec3(1, 0, 0));
    vec4 plusX = shapeTexel(cell + ivec3(1, 0, 0));
    vec4 minusY = shapeTexel(cell - ivec3(0, 1, 0));
    vec4 plusY = shapeTexel(cell + ivec3(0, 1, 0));
    vec4 minusZ = shapeTexel(cell - ivec3(0, 0, 1));
    vec4 plusZ = shapeTexel(cell + ivec3(0, 0, 1));

    float gx = 0.0;
    if (minusX.a >= 0.5 && plusX.a >= 0.5) {
        gx = (plusX.b - minusX.b) / max(spacing.x * 2.0, 0.0001);
    } else if (plusX.a >= 0.5) {
        gx = (plusX.b - center.b) / max(spacing.x, 0.0001);
    } else if (minusX.a >= 0.5) {
        gx = (center.b - minusX.b) / max(spacing.x, 0.0001);
    }

    float gy = 0.0;
    if (minusY.a >= 0.5 && plusY.a >= 0.5) {
        gy = (plusY.b - minusY.b) / max(spacing.y * 2.0, 0.0001);
    } else if (plusY.a >= 0.5) {
        gy = (plusY.b - center.b) / max(spacing.y, 0.0001);
    } else if (minusY.a >= 0.5) {
        gy = (center.b - minusY.b) / max(spacing.y, 0.0001);
    }

    float gz = 0.0;
    if (minusZ.a >= 0.5 && plusZ.a >= 0.5) {
        gz = (plusZ.b - minusZ.b) / max(spacing.z * 2.0, 0.0001);
    } else if (plusZ.a >= 0.5) {
        gz = (plusZ.b - center.b) / max(spacing.z, 0.0001);
    } else if (minusZ.a >= 0.5) {
        gz = (center.b - minusZ.b) / max(spacing.z, 0.0001);
    }
    return vec3(gx, gy, gz);
}

vec3 bodySdfGradientAtCell(ivec3 cell) {
    vec3 spacing = gridSpacing();
    float gx = (shapeTexel(cell + ivec3(1, 0, 0)).r
            - shapeTexel(cell - ivec3(1, 0, 0)).r) / max(spacing.x * 2.0, 0.0001);
    float gy = (shapeTexel(cell + ivec3(0, 1, 0)).r
            - shapeTexel(cell - ivec3(0, 1, 0)).r) / max(spacing.y * 2.0, 0.0001);
    float gz = (shapeTexel(cell + ivec3(0, 0, 1)).r
            - shapeTexel(cell - ivec3(0, 0, 1)).r) / max(spacing.z * 2.0, 0.0001);
    return vec3(gx, gy, gz);
}

vec3 velocityAt(vec3 localPos, ivec3 cell, vec4 shape) {
    vec3 gradient = releaseGradientAtCell(cell);
    vec3 bodyDirection = -gradient;
    if (dot(bodyDirection, bodyDirection) < 0.000001) {
        bodyDirection = OutletLocal - localPos;
    }
    bodyDirection = safeNormalize(bodyDirection, OutletLocal - localPos);
    vec3 toOutlet = OutletLocal - localPos;
    float outletDistance = length(toOutlet);
    if (outletDistance > 0.0001) {
        toOutlet /= outletDistance;
    } else {
        toOutlet = safeNormalize(SiphonP1 - SiphonP0, vec3(1.0, 0.0, 0.0));
    }
    float nearOutlet = 1.0 - smoothstep(BodyRadius * 0.24,
            BodyRadius * 1.15, outletDistance);
    bodyDirection = safeNormalize(mix(bodyDirection, toOutlet, nearOutlet * 0.78),
            toOutlet);
    float bodySpeed = max(BodyRadius * 0.78, VolumeSize.x * 0.12)
            * (0.38 + SiphonProgress * 1.28 + FinalCollapse * 0.55);
    vec3 bodyVelocity = bodyDirection * bodySpeed;
    vec3 sdfGradient = bodySdfGradientAtCell(cell);
    float sdfGradientLengthSquared = dot(sdfGradient, sdfGradient);
    if (sdfGradientLengthSquared > 0.000001) {
        vec3 outwardNormal = sdfGradient * inversesqrt(sdfGradientLengthSquared);
        float nearBodyBoundary = smoothstep(-VoxelSize * 2.0,
                -VoxelSize * 0.20, shape.r);
        float outwardSpeed = max(dot(bodyVelocity, outwardNormal), 0.0);
        bodyVelocity -= outwardNormal * outwardSpeed * nearBodyBoundary;
    }

    return bodyVelocity;
}

void main() {
    int atlasX = int(floor(gl_FragCoord.x));
    int y = int(floor(gl_FragCoord.y));
    int z = atlasX / GRID_X;
    int x = atlasX - z * GRID_X;
    ivec3 cell = ivec3(x, y, z);
    if (!cellInBounds(cell)) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 localPos = gridToLocal(vec3(cell));
    vec4 shape = shapeTexel(cell);
    float allowedDistance = shape.r;
    if (allowedDistance > 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 velocity = velocityAt(localPos, cell, shape);
    vec3 maximumDisplacement = gridSpacing() * 1.15;
    vec3 displacement = clamp(velocity * DeltaTime,
            -maximumDisplacement, maximumDisplacement);
    vec3 backtrace = localPos - displacement;
    float backtraceDistance = combinedAllowedSdf(backtrace);
    if (backtraceDistance > 0.0) {
        vec3 outsidePoint = backtrace;
        vec3 insidePoint = localPos;
        for (int i = 0; i < 5; i++) {
            vec3 midpoint = (outsidePoint + insidePoint) * 0.5;
            if (combinedAllowedSdf(midpoint) > 0.0) {
                outsidePoint = midpoint;
            } else {
                insidePoint = midpoint;
            }
        }
        backtrace = insidePoint;
    }

    float mobile = sampleDensityLocal(backtrace);
    float previousRemaining = releaseRemaining(shape.b,
            PreviousSiphonProgress, PreviousFinalCollapse);
    float currentRemaining = releaseRemaining(shape.b,
            SiphonProgress, FinalCollapse);
    float released = baseBodyDensity(shape)
            * max(0.0, previousRemaining - currentRemaining)
            * step(0.5, shape.a);
    mobile += released;

    // R stores remaining body-mobile density. G stores the exact density
    // removed at the P0 transfer boundary this step; the split siphon pass
    // consumes that flux once rather than repeatedly cloning body density.
    float transferRadius = max(BodyRadius * 0.180, VoxelSize * 2.55);
    vec3 rootTangent = safeNormalize(SiphonP1 - SiphonP0,
            OutletLocal - SiphonP0);
    vec3 rootOffset = localPos - SiphonP0;
    float rootAxial = abs(dot(rootOffset, rootTangent));
    float rootRadial = length(rootOffset - rootTangent * dot(rootOffset, rootTangent));
    float axialCellWidth = max(dot(abs(rootTangent), gridSpacing()), VoxelSize);
    float radialGate = 1.0 - smoothstep(transferRadius * 0.68,
            transferRadius * 1.16, rootRadial);
    float axialGate = 1.0 - smoothstep(axialCellWidth * 0.35,
            axialCellWidth * 1.35, rootAxial);
    float transferRegion = radialGate * axialGate;
    float transferRate = smoothstep(0.002, 0.055, SiphonProgress)
            * (14.0 + 10.0 * SiphonProgress + 16.0 * FinalCollapse);
    float beforeTransfer = mobile;
    mobile *= exp(-DeltaTime * transferRate * transferRegion);
    float transferredToSiphon = max(0.0, beforeTransfer - mobile);

    fragColor = vec4(clamp(mobile, 0.0, 2.5),
            clamp(transferredToSiphon, 0.0, 2.5), 0.0, 1.0);
}
