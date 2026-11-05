#version 150

// Packed columns: center and conservative distance scale, then three inverse
// ellipsoid rows. The distance scale makes each field at most 1-Lipschitz.
uniform mat4 EnergyPrimitives[24];
uniform mat4 EnergyNotches[4];
uniform int EnergyPrimitiveCount;
uniform int EnergyNotchCount;
uniform float EnergySmoothness;
uniform float EnergyCoverage;
uniform float EnergyTraceScale;
uniform vec3 EnergyMinimum;
uniform vec3 EnergyMaximum;
uniform mat4 EnergyLocalToCamera;
uniform mat4 EnergyLocalToClip;
uniform mat4 EnergyClipToLocal;
uniform sampler2D EnergyRestSampler;
uniform int EnergyRestAvailable;
uniform vec3 EnergyRestGridMinimum;
uniform vec3 EnergyRestGridMaximum;
uniform vec3 EnergyRestGridSize;
uniform vec3 EnergyRestMinimum;
uniform vec3 EnergyRestMaximum;
uniform vec3 EnergyRestCenter;
uniform vec3 EnergyRestDirection;
uniform vec3 EnergyRestSide;
uniform vec3 EnergyRestUp;
uniform vec3 EnergyRestTarget;
uniform vec3 EnergyRestBend;
uniform vec4 EnergyRestShape;
uniform vec4 EnergyRestClock;
uniform vec4 EnergyRestShearPhases;
uniform vec2 EnergyRestSqueeze;
uniform vec4 EnergyRestWarp;
uniform vec4 EnergyRestTransport;
uniform float EnergyRestAxialMinimum;
uniform float EnergyRestDistanceLipschitz;
uniform vec2 EnergyRestDistanceBand;
uniform vec3 EnergyRestOuterBands;
uniform vec3 EnergyRestOuterBandGradients;
uniform int EnergyTailAvailable;
uniform vec3 EnergyTailTip;
uniform vec3 EnergyTailDirection;
uniform vec4 EnergyTailShape;
in vec2 energyUv;
out vec4 fragColor;
// Zero keeps standalone field/parity probes on the global spatial bound.
// main replaces it with one constant directional bound for the complete ray.
float energyRestRayDivisor = 0.0;
bool energyTubeIntervalsReady = false;
vec3 energyTraceOrigin;
vec3 energyTraceDirection;
vec2 energyTubeIntervals[48];
int energyTubeIntervalCount = 0;
vec2 energyTubeNotchIntervals[4];

bool energyFinite(float value) {
    return !isnan(value) && !isinf(value);
}

float energyEllipsoid(mat4 primitive, vec3 point) {
    vec3 offset = point - primitive[0].xyz;
    vec3 unit = vec3(dot(primitive[1].xyz, offset),
            dot(primitive[2].xyz, offset), dot(primitive[3].xyz, offset));
    return (length(unit) - 1.0) * primitive[0].w;
}

float energySmoothUnion(float a, float b) {
    if (EnergySmoothness <= 0.0) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / EnergySmoothness, 0.0, 1.0);
    return mix(b, a, h) - EnergySmoothness * h * (1.0 - h);
}

float energyTailField(vec3 point) {
    vec3 axis = normalize(EnergyTailDirection);
    vec3 offset = point - EnergyTailTip;
    float z = dot(offset, axis);
    float radial = length(offset - axis * z);
    float slope = EnergyTailShape.x;
    float cone = (radial - slope * z) * inversesqrt(1.0 + slope * slope);
    float flareSlope = EnergyTailShape.z;
    float flare = (radial - flareSlope * (z - EnergyTailShape.w))
            * inversesqrt(1.0 + flareSlope * flareSlope);
    return max(-z, min(min(cone, flare), EnergyTailShape.y - z));
}

float energyRestSmooth(float x) { return x * x * (3.0 - 2.0 * x); }

float energyRestLocal(float axial) {
    float order = clamp((axial - EnergyRestShape.y) / EnergyRestShape.z, 0.0, 1.0);
    float lag = 1.0 - order;
    return clamp((EnergyRestClock.x - .46 * lag) / (.52 + .02 * lag), 0.0, 1.0);
}

float energyRestAxial(float axial) {
    float travel = energyRestSmooth(energyRestLocal(axial)) * EnergyRestWarp.y;
    float target = dot(EnergyRestTarget - EnergyRestCenter, EnergyRestDirection);
    return mix(axial, target, travel);
}

float energyRestVoxel(ivec3 p) {
    return texelFetch(EnergyRestSampler, ivec2(p.x + p.z * int(EnergyRestGridSize.x), p.y), 0).r;
}

float energyRestVolume(vec3 point) {
    vec3 bounded = clamp(point, EnergyRestGridMinimum, EnergyRestGridMaximum);
    vec3 grid = (bounded - EnergyRestGridMinimum) / (EnergyRestGridMaximum - EnergyRestGridMinimum)
            * (EnergyRestGridSize - vec3(1.0));
    ivec3 low = ivec3(floor(grid));
    ivec3 high = min(low + ivec3(1), ivec3(EnergyRestGridSize) - ivec3(1));
    vec3 weight = grid - vec3(low);
    float near = mix(mix(energyRestVoxel(ivec3(low.x, low.y, low.z)), energyRestVoxel(ivec3(high.x, low.y, low.z)), weight.x),
            mix(energyRestVoxel(ivec3(low.x, high.y, low.z)), energyRestVoxel(ivec3(high.x, high.y, low.z)), weight.x), weight.y);
    float far = mix(mix(energyRestVoxel(ivec3(low.x, low.y, high.z)), energyRestVoxel(ivec3(high.x, low.y, high.z)), weight.x),
            mix(energyRestVoxel(ivec3(low.x, high.y, high.z)), energyRestVoxel(ivec3(high.x, high.y, high.z)), weight.x), weight.y);
    return mix(near, far, weight.z) + length(point - bounded) / EnergyRestDistanceLipschitz;
}

vec3 energyRestInverseElastic(vec3 point) {
    vec3 relative = point - EnergyRestCenter;
    float axial = dot(relative, EnergyRestDirection);
    float u = dot(relative, EnergyRestSide), v = dot(relative, EnergyRestUp);
    float radius = EnergyRestShape.x, strength = EnergyRestClock.y;
    float angle = EnergyRestSqueeze.x, strain = EnergyRestSqueeze.y;
    float c = cos(angle), s = sin(angle);
    float x = (u * c + v * s) * exp(-strain), y = (-u * s + v * c) * exp(strain);
    u = x * c - y * s;
    v = x * s + y * c;
    u += radius * strength * (.18 * sin(4.0 * axial / radius + EnergyRestShearPhases.x)
            + .10 * sin(3.0 * v / radius + EnergyRestShearPhases.y));
    v += radius * strength * (.18 * sin(3.8 * axial / radius + EnergyRestShearPhases.z)
            + .08 * sin(3.0 * u / radius + EnergyRestShearPhases.w));
    return EnergyRestCenter + EnergyRestDirection * axial + EnergyRestSide * u + EnergyRestUp * v;
}

float energyRestDirectionalBound(vec3 direction) {
    float aRay = abs(dot(direction, EnergyRestDirection));
    float uRay = abs(dot(direction, EnergyRestSide));
    float vRay = abs(dot(direction, EnergyRestUp));
    float strength = EnergyRestClock.y, strain = EnergyRestSqueeze.y;
    float c = cos(EnergyRestSqueeze.x), s = sin(EnergyRestSqueeze.x);
    float forward = exp(strain), inverse = exp(-strain);
    float s00 = c * c * inverse + s * s * forward;
    float s11 = s * s * inverse + c * c * forward;
    float s01 = c * s * (inverse - forward);
    // Clamping only removes components from this complete derivative chain.
    float a = aRay / EnergyRestTransport.x;
    float u = (uRay + EnergyRestTransport.z * aRay) / EnergyRestTransport.y;
    float v = (vRay + EnergyRestTransport.w * aRay) / EnergyRestTransport.y;
    float squeezedU = abs(s00) * u + abs(s01) * v;
    float squeezedV = abs(s01) * u + abs(s11) * v;
    float shearedU = squeezedU + strength * (.72 * a + .30 * squeezedV);
    float shearedV = squeezedV + strength * (.684 * a + .24 * shearedU);
    return min(EnergyRestShape.w, length(vec3(a, shearedU, shearedV)) * 1.0001);
}

float energyRestField(vec3 point) {
    float outputAxial = dot(point - EnergyRestCenter, EnergyRestDirection);
    float low = EnergyRestAxialMinimum, high = EnergyRestWarp.w;
    for (int i = 0; i < 24; i++) {
        float middle = (low + high) * .5;
        if (energyRestAxial(middle) < outputAxial) low = middle;
        else high = middle;
    }
    float axial = (low + high) * .5;
    float travel = energyRestSmooth(energyRestLocal(axial)) * EnergyRestWarp.y;
    float width = (1.0 - travel) * (1.0 + 1.2 * travel);
    vec3 curve = mix(EnergyRestCenter + EnergyRestDirection * axial, EnergyRestTarget, travel)
            + EnergyRestBend * (4.0 * travel * (1.0 - travel));
    vec3 difference = point - curve;
    float u = clamp(dot(difference, EnergyRestSide) / width, -EnergyRestWarp.z, EnergyRestWarp.z);
    float v = clamp(dot(difference, EnergyRestUp) / width, -EnergyRestWarp.z, EnergyRestWarp.z);
    vec3 rest = energyRestInverseElastic(EnergyRestCenter + EnergyRestDirection * axial
            + EnergyRestSide * u + EnergyRestUp * v);
    float divisor = energyRestRayDivisor > 0.0 ? energyRestRayDivisor : EnergyRestShape.w;
    float sourceField = energyRestVolume(rest);
    // Preserve the interpolated zero exactly. The certified saturated band
    // strengthens shallow surface distances; the original field wins farther
    // away, preventing a constant-distance plateau through empty gaps.
    float bandField = min(abs(sourceField), EnergyRestDistanceBand.x) / EnergyRestDistanceBand.y;
    vec3 outerFields = min(vec3(abs(sourceField)), EnergyRestOuterBands) / EnergyRestOuterBandGradients;
    bandField = max(bandField, max(outerFields.x, max(outerFields.y, outerFields.z)));
    sourceField = sign(sourceField) * max(abs(sourceField), bandField);
    float field = (sourceField - EnergyRestWarp.x) / divisor;
    // The padded sides and back enclose all occupied source support. Their
    // signed chart constraints preserve that zero set while the front cuts the
    // releasing region. Each term has its own proven distance normalization.
    float sideBoundary = (abs(dot(difference, EnergyRestSide)) - EnergyRestWarp.z * width)
            / sqrt(1.0 + EnergyRestTransport.z * EnergyRestTransport.z);
    float upBoundary = (abs(dot(difference, EnergyRestUp)) - EnergyRestWarp.z * width)
            / sqrt(1.0 + EnergyRestTransport.w * EnergyRestTransport.w);
    field = max(field, max(sideBoundary, upBoundary));
    field = max(field, max(energyRestAxial(EnergyRestAxialMinimum) - outputAxial,
            outputAxial - energyRestAxial(EnergyRestWarp.w)));
    // The true surface is enclosed by this bounded proxy. Its ordinary signed
    // distance accelerates empty space without changing the source silhouette.
    vec3 q = abs(point - (EnergyRestMinimum + EnergyRestMaximum) * .5)
            - (EnergyRestMaximum - EnergyRestMinimum) * .5;
    float box = length(max(q, vec3(0.0))) + min(max(q.x, max(q.y, q.z)), 0.0);
    return max(field, box);
}

float energyField(vec3 point) {
    float distance = 1.0e30;
    if (energyTubeIntervalsReady) {
        // During source release the tube is a hard ellipsoid union. Its exact
        // ray intervals give a tighter 1D distance than a highly elongated
        // ellipsoid's spatial lower bound. Negative intervals remain exact cuts.
        float t = dot(point - energyTraceOrigin, energyTraceDirection);
        distance = 1.0e30;
        for (int i = 0; i < 48; i++) {
            if (i >= energyTubeIntervalCount) break;
            distance = min(distance, max(energyTubeIntervals[i].x - t, t - energyTubeIntervals[i].y));
        }
        for (int i = 0; i < 4; i++) {
            if (i >= EnergyNotchCount) break;
            float notch = max(energyTubeNotchIntervals[i].x - t, t - energyTubeNotchIntervals[i].y);
            distance = max(distance, -notch);
        }
    } else {
        if (EnergyPrimitiveCount > 0) distance = energyEllipsoid(EnergyPrimitives[0], point);
        for (int i = 1; i < 24; i++) {
            if (i >= EnergyPrimitiveCount) break;
            distance = energySmoothUnion(distance, energyEllipsoid(EnergyPrimitives[i], point));
        }
        for (int i = 0; i < 4; i++) {
            if (i >= EnergyNotchCount) break;
            // A true subtraction: no captured source core can refill these notches.
            distance = max(distance, -energyEllipsoid(EnergyNotches[i], point));
        }
    }
    if (!energyTubeIntervalsReady && EnergyTailAvailable != 0)
        distance = max(distance, energyTailField(point));
    return EnergyRestAvailable != 0 ? min(distance, energyRestField(point)) : distance;
}

bool energyBounds(vec3 origin, vec3 direction, inout float entry, inout float exit) {
    for (int axis = 0; axis < 3; axis++) {
        if (abs(direction[axis]) < 0.0000001) {
            if (origin[axis] < EnergyMinimum[axis] || origin[axis] > EnergyMaximum[axis]) return false;
        } else {
            float a = (EnergyMinimum[axis] - origin[axis]) / direction[axis];
            float b = (EnergyMaximum[axis] - origin[axis]) / direction[axis];
            entry = max(entry, min(a, b));
            exit = min(exit, max(a, b));
        }
    }
    return exit >= entry;
}

bool energyRayInterval(mat4 primitive, vec3 origin, vec3 direction, out vec2 interval) {
    // Move the quadratic origin near the primitive before applying its inverse.
    // This avoids subtracting two enormous squared values for a tiny distant
    // ellipse, especially in the final thin bridges approaching the ball.
    float shift = dot(primitive[0].xyz - origin, direction);
    vec3 offset = origin + direction * shift - primitive[0].xyz;
    vec3 q = vec3(dot(primitive[1].xyz, offset), dot(primitive[2].xyz, offset),
            dot(primitive[3].xyz, offset));
    vec3 d = vec3(dot(primitive[1].xyz, direction), dot(primitive[2].xyz, direction),
            dot(primitive[3].xyz, direction));
    float squaredLength = dot(d, d);
    if (!energyFinite(squaredLength) || squaredLength <= 0.0) return false;
    float inverseLength = inversesqrt(squaredLength);
    vec3 unit = d * inverseLength;
    float along = dot(q, unit);
    vec3 closest = q - unit * along;
    float remaining = 1.0 - dot(closest, closest);
    if (!energyFinite(remaining) || remaining < 0.0) return false;
    float middle = shift - along * inverseLength;
    float halfLength = sqrt(remaining) * inverseLength;
    interval = vec2(middle - halfLength, middle + halfLength);
    return energyFinite(interval.x) && energyFinite(interval.y) && interval.y > interval.x;
}

vec2 energyIntervalIntersection(vec2 a, vec2 b) {
    vec2 result = vec2(max(a.x, b.x), min(a.y, b.y));
    return result.y > result.x ? result : vec2(1.0e30, -1.0e30);
}

// Each positive capped cone is convex and contributes one exact ray interval.
vec2 energyTailConeInterval(vec3 origin, vec3 direction, float slope, float axialOffset) {
    vec2 cone = vec2(-1.0e30, 1.0e30);
    vec3 axis = normalize(EnergyTailDirection);
    float shift = dot(EnergyTailTip - origin, direction);
    vec3 q = origin + direction * shift - EnergyTailTip;
    float sourceZ = dot(q, axis), z = sourceZ - axialOffset, dz = dot(direction, axis);
    if (abs(dz) < 1.0e-10) {
        if (z < 0.0 || sourceZ > EnergyTailShape.y) cone = vec2(1.0e30, -1.0e30);
    } else {
        float rear = shift - z / dz;
        // Both cones and the forward halfspace must share identical arithmetic
        // for this boundary; subtracting the flare offset twice can leave a
        // one-ULP interval gap which looks like a false exit from inside.
        float front = shift + (EnergyTailShape.y - sourceZ) / dz;
        cone = vec2(min(rear, front), max(rear, front));
    }
    if (cone.y <= cone.x) return cone;
    float k2 = slope * slope;
    vec3 radialQ = q - axis * sourceZ, radialD = direction - axis * dz;
    float a = dot(radialD, radialD) - k2 * dz * dz;
    float b = dot(radialQ, radialD) - k2 * z * dz;
    float c = dot(radialQ, radialQ) - k2 * z * z;
    vec2 quadratic = vec2(-1.0e30, 1.0e30);
    if (a == 0.0) {
        if (b == 0.0) {
            if (c > 0.0) quadratic = vec2(1.0e30, -1.0e30);
        } else {
            float root = shift - c / (2.0 * b);
            quadratic = b > 0.0 ? vec2(-1.0e30, root) : vec2(root, 1.0e30);
        }
    } else {
        float discriminant = b * b - a * c;
        if (discriminant < 0.0) {
            if (a > 0.0) quadratic = vec2(1.0e30, -1.0e30);
        } else {
            float rootPart = sqrt(discriminant);
            float stable = -b - (b >= 0.0 ? rootPart : -rootPart);
            float first = stable / a;
            float second = stable != 0.0 ? c / stable : -b / a;
            float low = shift + min(first, second), high = shift + max(first, second);
            if (a > 0.0) quadratic = vec2(low, high);
            else quadratic = dz > 0.0 ? vec2(high, 1.0e30) : vec2(-1.0e30, low);
        }
    }
    return energyIntervalIntersection(cone, quadratic);
}

// The narrow rear, continuous flare and forward halfspace match the spatial
// envelope. Twelve stream bodies need at most 36 of the 48 bounded intervals.
void energyTailIntervals(vec3 origin, vec3 direction, out vec2 cone, out vec2 flare, out vec2 forward) {
    cone = vec2(-1.0e30, 1.0e30);
    flare = forward = vec2(1.0e30, -1.0e30);
    if (EnergyTailAvailable == 0) return;
    cone = energyTailConeInterval(origin, direction, EnergyTailShape.x, 0.0);
    flare = energyTailConeInterval(origin, direction, EnergyTailShape.z, EnergyTailShape.w);
    vec3 axis = normalize(EnergyTailDirection);
    float shift = dot(EnergyTailTip - origin, direction);
    float z = dot(origin + direction * shift - EnergyTailTip, axis), dz = dot(direction, axis);
    if (abs(dz) < 1.0e-10) {
        if (z >= EnergyTailShape.y) forward = vec2(-1.0e30, 1.0e30);
    } else {
        float front = shift + (EnergyTailShape.y - z) / dz;
        forward = dz > 0.0 ? vec2(front, 1.0e30) : vec2(-1.0e30, front);
    }
}

bool energyHardIntersection(vec3 origin, vec3 direction, float entry, float exit, out float hitDistance) {
    vec2 bodies[48];
    vec2 notches[4];
    vec2 cone, flare, forward;
    energyTailIntervals(origin, direction, cone, flare, forward);
    int bodyCount = EnergyPrimitiveCount * (EnergyTailAvailable != 0 ? 3 : 1);
    bool inside = false;
    for (int i = 0; i < 48; i++) {
        bodies[i] = vec2(1.0e30, -1.0e30);
        if (i < bodyCount) {
            vec2 interval;
            int primitive = EnergyTailAvailable != 0 ? i / 3 : i;
            if (energyRayInterval(EnergyPrimitives[primitive], origin, direction, interval)) {
                bodies[i] = energyIntervalIntersection(interval,
                        EnergyTailAvailable != 0 && i % 3 != 0 ? (i % 3 == 1 ? flare : forward) : cone);
                interval = bodies[i];
                inside = inside || (interval.x < entry && interval.y > entry);
            }
        }
    }
    for (int i = 0; i < 4; i++) {
        notches[i] = vec2(1.0e30, -1.0e30);
        if (i < EnergyNotchCount) {
            vec2 interval;
            if (energyRayInterval(EnergyNotches[i], origin, direction, interval)) {
                notches[i] = interval;
                if (interval.x <= entry && interval.y > entry) inside = false;
            }
        }
    }
    if (inside) {
        // Find the far end of the connected union containing the near plane.
        // Each pass extends across at least one of the finite clipped intervals.
        float unionExit = entry;
        for (int pass = 0; pass < 48; pass++) {
            float previous = unionExit;
            for (int i = 0; i < 48; i++) {
                if (i >= bodyCount) break;
                if (bodies[i].x <= unionExit && bodies[i].y > entry)
                    unionExit = max(unionExit, bodies[i].y);
            }
            if (unionExit == previous) break;
        }
        for (int i = 0; i < 4; i++) {
            if (i >= EnergyNotchCount) break;
            if (notches[i].x >= entry) unionExit = min(unionExit, notches[i].x);
        }
        hitDistance = unionExit;
        return hitDistance > entry && hitDistance <= exit;
    }

    float current = entry;
    // Every unsuccessful pass crosses a new negative interval. Four notches
    // therefore require at most five attempts, regardless of bridge thickness.
    for (int pass = 0; pass < 5; pass++) {
        float next = 1.0e30;
        for (int i = 0; i < 48; i++) {
            if (i >= bodyCount) break;
            if (bodies[i].y > current) next = min(next, max(current, bodies[i].x));
        }
        if (next > exit) return false;
        bool blocked = false;
        for (int i = 0; i < 4; i++) {
            if (i >= EnergyNotchCount) break;
            if (notches[i].x <= next && notches[i].y > next) {
                next = notches[i].y;
                blocked = true;
            }
        }
        if (!blocked) {
            hitDistance = next;
            return true;
        }
        current = next;
    }
    return false;
}

void main() {
    if ((EnergyPrimitiveCount <= 0 && EnergyRestAvailable == 0) || EnergyCoverage <= 0.0 || EnergyTraceScale <= 0.0) discard;
    vec2 ndc = energyUv * 2.0 - 1.0;
    vec4 nearH = EnergyClipToLocal * vec4(ndc, -1.0, 1.0);
    vec4 farH = EnergyClipToLocal * vec4(ndc, 1.0, 1.0);
    if (abs(nearH.w) < 0.0000000001 || abs(farH.w) < 0.0000000001) discard;
    vec3 origin = nearH.xyz / nearH.w;
    vec3 ray = farH.xyz / farH.w - origin;
    float farDistance = length(ray);
    if (!energyFinite(farDistance) || farDistance <= 0.0) discard;
    vec3 direction = ray / farDistance;
    if (EnergyRestAvailable != 0) energyRestRayDivisor = energyRestDirectionalBound(direction);
    if (EnergyRestAvailable != 0 && EnergySmoothness <= 0.0) {
        energyTraceOrigin = origin;
        energyTraceDirection = direction;
        vec2 cone, flare, forward;
        energyTailIntervals(origin, direction, cone, flare, forward);
        energyTubeIntervalCount = EnergyPrimitiveCount * (EnergyTailAvailable != 0 ? 3 : 1);
        for (int i = 0; i < 48; i++) {
            energyTubeIntervals[i] = vec2(1.0e30, -1.0e30);
            if (i >= energyTubeIntervalCount) break;
            vec2 interval;
            int primitive = EnergyTailAvailable != 0 ? i / 3 : i;
            if (energyRayInterval(EnergyPrimitives[primitive], origin, direction, interval))
                energyTubeIntervals[i] = energyIntervalIntersection(interval,
                        EnergyTailAvailable != 0 && i % 3 != 0 ? (i % 3 == 1 ? flare : forward) : cone);
        }
        for (int i = 0; i < 4; i++) {
            energyTubeNotchIntervals[i] = vec2(1.0e30, -1.0e30);
            if (i >= EnergyNotchCount) break;
            vec2 interval;
            if (energyRayInterval(EnergyNotches[i], origin, direction, interval)) energyTubeNotchIntervals[i] = interval;
        }
        energyTubeIntervalsReady = true;
    }
    // Derivatives are evaluated before divergent marching, and influence only
    // convergence tolerance. The target remains an ordinary opaque surface.
    float pixelAngle = max(length(dFdx(direction)), length(dFdy(direction)));
    float entry = 0.0;
    float exit = farDistance;
    if (!energyBounds(origin, direction, entry, exit)) discard;
    float distance = entry;
    bool hit = false;
    if (EnergySmoothness <= 0.0 && EnergyRestAvailable == 0) {
        // Intake has an exact union-minus-notches representation after smoothing
        // retires. Solve its intervals directly: a conservative distance bound
        // on a long, thin ellipsoid need not converge within the ray-step cap.
        hit = energyHardIntersection(origin, direction, entry, exit, distance);
    } else {
        float previousDistance = entry;
        float previousField = energyField(origin + direction * entry);
        if (!energyFinite(previousField)) discard;
        float field = previousField;
        bool startedInside = previousField < 0.0;
        float relaxation = EnergyRestAvailable != 0 ? 1.8 : 1.6;
        // Flat, grazing distance bounds can converge too slowly with a fixed
        // fractional step. Verify each accelerated advance with overlapping
        // conservative zero-free ray intervals; retain the original step when it fails.
        for (int step = 0; step < 320; step++) {
            if (!energyFinite(field)) discard;
            float epsilon = max(EnergyTraceScale * 0.00035,
                    min(EnergyTraceScale * 0.003, distance * pixelAngle * 0.12));
            if (EnergyRestAvailable != 0) {
                epsilon /= max(1.0,
                        energyRestRayDivisor * EnergyRestDistanceLipschitz * EnergyRestDistanceBand.y);
                // Distant pixels or anisotropic grids can request a tolerance
                // wider than the narrow band. Its positive plateau must never
                // be mistaken for convergence to the actual source surface.
                epsilon = min(epsilon, .5 * EnergyRestDistanceBand.x
                        / (energyRestRayDivisor * EnergyRestDistanceBand.y));
            }
            bool crossed = startedInside ? field >= 0.0 : field <= 0.0;
            if (abs(field) <= epsilon || crossed) {
                float lower = previousDistance;
                float upper = distance;
                float lowerField = previousField;
                float upperField = field;
                // Conservative tracing normally converges just outside the zero.
                // Probe a short interval to bracket that surface, then refine depth.
                if (lowerField * upperField > 0.0 || lower == upper) {
                    lower = distance;
                    lowerField = field;
                    upper = min(exit, distance + 3.0 * epsilon);
                    upperField = energyField(origin + direction * upper);
                }
                if (energyFinite(upperField) && lowerField * upperField <= 0.0) {
                    for (int refine = 0; refine < 6; refine++) {
                        float midpoint = (lower + upper) * 0.5;
                        float middleField = energyField(origin + direction * midpoint);
                        if (lowerField * middleField <= 0.0) upper = midpoint;
                        else {
                            lower = midpoint;
                            lowerField = middleField;
                        }
                    }
                    distance = (lower + upper) * 0.5;
                }
                hit = true;
                break;
            }
            previousDistance = distance;
            previousField = field;
            // Absolute distance also traces a camera/near plane inside the body to
            // its exit surface without manufacturing an opaque near-plane sheet.
            float radius = abs(field);
            // The interpolated rest field carries an additional inverse-warp
            // bound, so its neighboring empty intervals commonly overlap farther.
            // This remains a proposal: the same proof below must accept it.
            float candidate = min(exit, distance + relaxation * radius);
            float candidateField = energyField(origin + direction * candidate);
            bool sameSide = (field < 0.0) == (candidateField < 0.0);
            // Along this ray each field is 1-Lipschitz. Same-sign zero-free
            // intervals covering the segment prove that no surface was skipped.
            // The extra margin keeps their overlap away from roundoff ties.
            bool safeAdvance = energyFinite(candidateField) && sameSide
                    && candidate - distance < 0.98 * (radius + abs(candidateField));
            if (safeAdvance) {
                distance = candidate;
                field = candidateField; // reuse the proof sample next iteration
            } else {
                distance += 0.8 * radius;
                if (distance > exit) break;
                field = energyField(origin + direction * distance);
            }
            if (EnergyRestAvailable != 0) {
                // A slope estimate proposes the next overlap interval only; it
                // never certifies a step. Adapt before repeatedly proposing an
                // overly long interval and paying for the .8 fallback instead.
                float decrease = clamp((abs(previousField) - abs(field))
                        / max(distance - previousDistance, 1.0e-8), 0.0, 1.0);
                relaxation = 1.9 / (1.0 + decrease);
            }
            if (distance >= exit) break;
        }
    }
    if (!hit) discard;
    vec3 hitLocal = origin + direction * distance;
    vec4 hitClip = EnergyLocalToClip * vec4(hitLocal, 1.0);
    if (hitClip.w <= 0.0 || !energyFinite(hitClip.w)) discard;
    float deviceDepth = hitClip.z / hitClip.w * 0.5 + 0.5;
    vec3 cameraRelative = (EnergyLocalToCamera * vec4(hitLocal, 1.0)).xyz;
    float cameraDistance = length(cameraRelative);
    if (!energyFinite(deviceDepth) || !energyFinite(cameraDistance)
            || deviceDepth < 0.0 || deviceDepth > 1.0 || cameraDistance <= 0.0) discard;
    float coverage = clamp(EnergyCoverage, 0.0, 1.0);
    gl_FragDepth = deviceDepth;
    // Occupancy and presentation alpha have different jobs during the source
    // handoff. R supplies one geometric union boundary even at a tiny weight;
    // B fades the material without seeding an outline through that union.
    // Negative A still marks moving geometry with its true first-hit depth.
    fragColor = vec4(1.0, 0.0, coverage, -(2.0 + cameraDistance));
}
