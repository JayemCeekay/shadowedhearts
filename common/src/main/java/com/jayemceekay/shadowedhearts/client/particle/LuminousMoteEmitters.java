package com.jayemceekay.shadowedhearts.client.particle;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.core.ModParticleTypes;
import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns short-lived “luminous motes” around Shadow Pokémon when they are sent out.
 * Client-side only. Particles are attached to the entity for 5 seconds and emit
 * small lavender sparkles that shoot outward/upward like the reference GIF.
 */
public final class LuminousMoteEmitters {
    private LuminousMoteEmitters() {}

    private static final Map<Integer, EmitterGroup> ACTIVE = new ConcurrentHashMap<>();

    /** Lifetime of an emitter after send-out, in ticks (5s @ 20 TPS). */
    private static final long EMIT_TICKS = 60L;

    /** Call from client init to subscribe to Cobblemon sent-out events. */
    public static void init() {
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL, (PokemonSentEvent.Post e) -> {
            PokemonEntity pe = e.getPokemonEntity();
            if (pe == null) return Unit.INSTANCE;
            if (!PokemonAspectUtil.hasShadowAspect(pe.getPokemon())) return Unit.INSTANCE;
            var mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return Unit.INSTANCE;
            long now = mc.level.getGameTime();
            ACTIVE.put(pe.getId(), EmitterGroup.create(pe, now + EMIT_TICKS));
            return Unit.INSTANCE;
        });
    }

    /** Invoke once per frame from a late render stage (e.g., AFTER_WEATHER). */
    public static void onRender(float partialTicks) {
        var mc = Minecraft.getInstance();
        ClientLevel level = mc != null ? mc.level : null;
        if (level == null) return;

        // Update & emit, remove dead/expired.
        long now = level.getGameTime();
        Iterator<Map.Entry<Integer, EmitterGroup>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            EmitterGroup group = en.getValue();
            if (group == null || group.isExpired(now)) { it.remove(); continue; }
            if (!group.tickAndEmit(level, partialTicks)) {
                it.remove();
            }
        }
    }

    // Group of emitters around the entity, count scaled by bbox size and spaced via Poisson sampling
    private static final class EmitterGroup {
        private final WeakReference<Entity> entityRef;
        private final long endTick;
        private final Emitter[] emitters;

        private EmitterGroup(Entity e, long endTick) {
            this.entityRef = new WeakReference<>(e);
            this.endTick = endTick;

            // Compute number of emitters based on bounding box size.
            // Map size ~1 -> 3 emitters, ~2.5 -> 6, up to a cap.
            double size = Math.max(0.5, e.getBoundingBox().getSize());
            int minEmitters = 3;
            int maxEmitters = 16;
            int count = (int) Math.round(minEmitters + Math.max(0, (size - 1.0) * 2.0));
            count = Math.min(maxEmitters, Math.max(minEmitters, count));

            this.emitters = new Emitter[count];

            // Generate Poisson-disc-like angles around the entity to avoid clumping.
            long seed = (((long) e.getId()) << 32) ^ endTick;
            double[] angles = poissonAngles(count, seed);

            // Keep the overall particle budget roughly constant by splitting base rate among emitters.
            double totalRatePerSec = 15.0; // approximately what we had before: 3 * 10/sec
            double perEmitterRate = totalRatePerSec / Math.max(1, count);

            // Alternate inside/outside bias so motes spawn just inside and just outside the AABB.
            for (int i = 0; i < count; i++) {
                boolean outside = (i % 2 == 0);
                emitters[i] = new Emitter(this.entityRef, endTick, angles[i], perEmitterRate, outside);
            }
        }

        private static double[] poissonAngles(int n, long seed) {
            double[] result = new double[n];
            if (n <= 0) return result;
            Random rnd = new Random(seed);
            double twoPi = Math.PI * 2.0;
            double minSep = (twoPi / n) * 0.9; // at least ~90% of equal spacing

            int placed = 0;
            int attempts = 0;
            int maxAttempts = Math.max(200, n * 200);
            while (placed < n && attempts < maxAttempts) {
                double a = rnd.nextDouble() * twoPi;
                boolean ok = true;
                for (int j = 0; j < placed; j++) {
                    double d = Math.abs(a - result[j]);
                    d = d % twoPi;
                    double circ = Math.min(d, twoPi - d);
                    if (circ < minSep) { ok = false; break; }
                }
                if (ok) {
                    result[placed++] = a;
                }
                attempts++;
            }
            if (placed < n) {
                // Fallback: equal spacing from a random base
                double base = rnd.nextDouble() * twoPi;
                for (int i = 0; i < n; i++) {
                    result[i] = base + i * (twoPi / n);
                }
            }
            return result;
        }

        static EmitterGroup create(Entity e, long endTick) {
            return new EmitterGroup(e, endTick);
        }

        boolean isExpired(long now) {
            Entity ent = entityRef.get();
            return ent == null || !ent.isAlive() || now >= endTick;
        }

        // Returns false if group should be removed
        boolean tickAndEmit(ClientLevel level, float partialTicks) {
            Entity ent = entityRef.get();
            if (ent == null || !ent.isAlive()) return false;
            for (Emitter em : emitters) {
                if (em != null) em.tickAndEmit(level, partialTicks);
            }
            return true;
        }
    }

    // Single attached emitter instance for one lobe
    private static final class Emitter {
        private final WeakReference<Entity> entityRef;
        private final long endTick;
        private final double baseAngleRad;
        private final boolean outsideBias; // true = outside edge, false = just inside edge
        private double ratePerSec; // average particles/sec for this emitter (group-scaled)
        private double carry = 0.0;       // fractional carry between frames

        Emitter(WeakReference<Entity> entityRef, long endTick, double baseAngleRad, double ratePerSec, boolean outsideBias) {
            this.entityRef = entityRef;
            this.endTick = endTick;
            this.baseAngleRad = baseAngleRad;
            this.ratePerSec = ratePerSec;
            this.outsideBias = outsideBias;
        }

        boolean isExpired(long now) {
            Entity e = entityRef.get();
            return e == null || !e.isAlive() || now >= endTick;
        }

        // Returns false if emitter should be removed immediately
        boolean tickAndEmit(ClientLevel level, float partialTicks) {
            Entity e = entityRef.get();
            if (e == null || !e.isAlive()) return false;

            // Center around the torso: a bit above mid-height
            double h = Math.max(0.2, e.getBbHeight());
            double w = Math.max(0.2, e.getBbWidth());
            double cx = e.getX();
            double cy = e.getY() + h * 0.10;
            double cz = e.getZ();

            // Derive a simple scale factor from the entity's bounding box size (blocks/meters)
            float scale = (float) entityRef.get().getBoundingBox().getSize();

            // Spawn count this frame (approximate real-time rate), scaled by bbox
            double dtSec = Math.max(0.0, partialTicks / 20.0);
            double emitRate = ratePerSec * (0.015 + 1.0 * scale);
            double want = emitRate * dtSec + carry;
            int count = (int) Math.floor(want);
            carry = want - count;

            // Slight initial burst for the first 0.6 seconds (optional)
            long now = level.getGameTime();
            double timeLeft = Math.max(0.0, (endTick - now) / 20.0);
            if (timeLeft > 4.4) {
                // count += 1;
            }

            // Emit
            for (int i = 0; i < count; i++) {
                // Lobe-biased emission around the entity with upward bias
                double spread = Math.PI / 6.0; // +/-30 degrees around base angle
                double ang = this.baseAngleRad + (level.random.nextDouble() * 2.0 - 1.0) * spread;

                // Compute distance to the horizontal AABB edge in direction ang, then offset
                // slightly to land either just outside or just inside the edge.
                double cosA = Math.cos(ang);
                double sinA = Math.sin(ang);
                double hw = Math.max(0.1, w * 0.5);
                double edge = hw / Math.max(Math.abs(cosA), Math.abs(sinA));
                double margin = Math.max(0.03, 0.06 * w);

                double r;
                if (outsideBias) {
                    double jitter = level.random.nextDouble() * (0.02 * Math.max(1.0, w));
                    r = edge + margin + jitter; // just outside
                } else {
                    double jitterIn = level.random.nextDouble() * (0.02 * Math.max(1.0, w));
                    r = Math.max(0.6 * edge, edge - margin - jitterIn); // just inside, but not too close to center
                }

                double sx = cx + cosA * r;
                double sz = cz + sinA * r;
                double sy = cy + (-0.05 + level.random.nextDouble() * 0.10) * Math.max(0.6, h);

                // Give each emitter a spawn area radius (disc in XZ) scaled to entity size
                double areaR = Math.max(0.08, 0.12 * w);
                // Local basis in XZ: outward normal (n) and tangential (t)
                double nx = cosA;
                double nz = sinA;
                double tx = -sinA;
                double tz = cosA;
                // Sample uniform-in-disc offset
                double phi2 = level.random.nextDouble() * (Math.PI * 2.0);
                double rr = Math.sqrt(level.random.nextDouble()) * areaR;
                // Slight bias outward when outside-biased; slight inward when inside-biased
                double bias = (outsideBias ? 0.25 : -0.10) * areaR;
                double offN = rr * Math.sin(phi2) + bias;
                double offT = rr * Math.cos(phi2);
                sx += nx * offN + tx * offT;
                sz += nz * offN + tz * offT;

                // Shoot outward from center with some upward velocity.
                Vec3 dir = new Vec3(sx - cx, 0.0, sz - cz).normalize().scale(0.5);
                // Scale initial speeds by bbox as well so larger entities get slightly more energetic motes
                // Added a floor (0.2) to prevent particles from being stationary on very small entities.
                double speedScale = Math.max(0.2, 0.75 * scale);
                double speedH = (0.008 + level.random.nextDouble() * 0.1) * speedScale; // horizontal speed
                double vy = (0.04 + level.random.nextDouble() * 0.10) * speedScale;     // upward speed
                double vx = dir.x * speedH;
                double vz = dir.z * speedH;

                // Add the particle; we pass the velocity vector via the speed parameters.
                level.addParticle(
                        ModParticleTypes.LUMINOUS_MOTE.get(),
                        sx, sy, sz,
                        vx, vy, vz
                );
            }
            return true;
        }
    }
}
