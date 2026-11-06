package com.jayemceekay.shadowedhearts.client.particle;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyle;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleResolver;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum.ColosseumAuraVolume;
import com.jayemceekay.shadowedhearts.client.aura.shadow.AuraPerformanceBudget;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.network.aura.LuminousMotePacket;
import com.jayemceekay.shadowedhearts.registry.util.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-only luminous accents: send-out bursts for Signature and observed,
 * continuously refreshed emission for Colosseum.
 */
public final class LuminousMoteEmitters {
    private LuminousMoteEmitters() {
    }

    private static final Map<Integer, EmitterGroup> ACTIVE = new ConcurrentHashMap<>();

    private static final LuminousMoteEmissionBudget COLOSSEUM_BUDGET = new LuminousMoteEmissionBudget();

    /**
     * Call from client init to subscribe to Cobblemon sent-out events.
     */
    public static void init() {
        // Signature starts from server packets; Colosseum follows world observation.
    }

    public static void receivePacket(LuminousMotePacket pkt) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        Entity ent = mc.level.getEntity(pkt.getEntityId());
        if (ent instanceof PokemonEntity pe) {
            register(pe);
        }
    }

    public static void register(PokemonEntity pe) {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()
                || com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() == 0) return;
        if (!ShadowAspectUtil.shouldHaveShadowAura(pe.getPokemon()) || !usesLuminousMotes(pe)) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        ShadowAuraStyle style = resolvedStyle(pe);
        if (style == ShadowAuraStyle.COLOSSEUM) {
            observe(pe);
        } else {
            ACTIVE.put(pe.getId(), EmitterGroup.create(pe, now, style));
        }
    }

    /** Refreshes Colosseum motes from the same visible source that emits its aura. */
    public static void observe(PokemonEntity pokemon) {
        observe(pokemon, null);
    }

    /** Posed bounds are world-space; ordinary observations never replace a fresh posed volume. */
    public static void observe(PokemonEntity pokemon, AABB worldModelBounds) {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()
                || com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() == 0) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || pokemon.level() != mc.level
                || !pokemon.isAlive() || !ShadowAspectUtil.shouldHaveShadowAura(pokemon.getPokemon())) return;
        ShadowAuraStyle style = resolvedStyle(pokemon);
        if (style != ShadowAuraStyle.COLOSSEUM) return;
        long now = mc.level.getGameTime();
        ACTIVE.compute(pokemon.getId(), (id, existing) -> {
            if (existing == null || existing.entityRef.get() != pokemon
                    || existing.lifecycle.expired(now, style)) {
                existing = EmitterGroup.create(pokemon, now, style);
            }
            existing.lifecycle.observe(now);
            if (worldModelBounds != null) existing.observeVolume(pokemon, worldModelBounds, now);
            return existing;
        });
    }

    private static ShadowAuraStyle resolvedStyle(PokemonEntity pokemon) {
        return ShadowAuraStyleResolver.resolve(pokemon);
    }

    private static boolean usesLuminousMotes(PokemonEntity pokemon) {
        return resolvedStyle(pokemon) != ShadowAuraStyle.XD_FAITHFUL;
    }

    static double colosseumRate(double sourceSize) {
        double size = Double.isFinite(sourceSize) ? Math.max(0.0, sourceSize) : 1.0;
        return Math.max(20.0, Math.min(48.0, 24.0 * Math.sqrt(size)));
    }

    static double colosseumBudgetScale(int activeGroups) {
        return Math.min(1.0, LuminousMoteEmissionBudget.RATE_PER_SECOND / (Math.max(1, activeGroups) * 48.0));
    }

    public static boolean hasColosseumParticles() {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()
                || com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() == 0) {
            clearColosseumParticles();
            return false;
        }
        return ColosseumMoteRenderer.hasParticles();
    }

    public static void clearColosseumParticles() {
        ColosseumMoteRenderer.clear();
    }

    public static void renderColosseumLate(Camera camera, float partialTicks,
                                            int sceneDepthTexture, int width, int height) {
        ColosseumMoteRenderer.render(camera, partialTicks, sceneDepthTexture, width, height);
    }

    /**
     * Invoke once per frame from a late render stage (e.g., AFTER_WEATHER).
     */
    public static void onRender(float partialTicks) {
        var mc = Minecraft.getInstance();
        ClientLevel level = mc != null ? mc.level : null;
        if (level == null || !com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()
                || com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity() == 0) {
            ACTIVE.clear();
            COLOSSEUM_BUDGET.advance(0L, 0.0f, false);
            clearColosseumParticles();
            return;
        }
        ColosseumMoteRenderer.hasParticles(); // Release a previous world's retained particles promptly.

        boolean auraReaderRequired = com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs.getInstance().getShadowConfig().auraReaderRequiredForAura();
        boolean hasAuraReader = auraReaderRequired && com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(mc.player);

        // Update & emit, remove dead/expired.
        long now = level.getGameTime();
        List<ColosseumRenderSource> colosseumSources = new ArrayList<>();
        int activeColosseumGroups = 0;
        Iterator<Map.Entry<Integer, EmitterGroup>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            EmitterGroup group = en.getValue();
            if (group == null || group.isExpired(now)) {
                it.remove();
                continue;
            }

            boolean visible = true;
            if (auraReaderRequired && !hasAuraReader) {
                Entity ent = group.entityRef.get();
                if (ent instanceof PokemonEntity pe && pe.getPokemon().cosmeticItem().is(com.jayemceekay.shadowedhearts.registry.ModItems.SHADOW_SHARD.get())) {
                    // Bypass Aura Reader requirement for Shadow Shard cosmetic item
                } else {
                    visible = false;
                }
            }

            boolean active = visible && !mc.isPaused();
            if (group.lifecycle.style() == ShadowAuraStyle.COLOSSEUM) {
                colosseumSources.add(new ColosseumRenderSource(en.getKey(), group, active));
                if (active) activeColosseumGroups++;
                continue;
            }
            if (!group.tickAndEmit(level, partialTicks, active,
                    com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity())) {
                it.remove();
            }
        }
        double colosseumBudget = colosseumBudgetScale(activeColosseumGroups)
                * com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity();
        colosseumBudget = AuraPerformanceBudget.forMode(
                com.jayemceekay.shadowedhearts.client.ClientEffects.auraPerformanceMode())
                .accentRate(colosseumBudget);
        COLOSSEUM_BUDGET.advance(now, partialTicks, activeColosseumGroups > 0);
        int first = COLOSSEUM_BUDGET.firstSource(colosseumSources.size());
        for (int offset = 0; offset < colosseumSources.size(); offset++) {
            int index = (first + offset) % colosseumSources.size();
            ColosseumRenderSource source = colosseumSources.get(index);
            int before = COLOSSEUM_BUDGET.remaining();
            if (!source.group.tickAndEmit(level, partialTicks, source.active, colosseumBudget)) {
                ACTIVE.remove(source.entityId, source.group);
            }
            if (COLOSSEUM_BUDGET.remaining() < before) {
                COLOSSEUM_BUDGET.sourceServed(index, colosseumSources.size());
            }
        }
    }

    private record ColosseumRenderSource(int entityId, EmitterGroup group, boolean active) {}

    // Group of emitters around the entity, count scaled by bbox size and spaced via Poisson sampling
    private static final class EmitterGroup {
        private final WeakReference<Entity> entityRef;
        private final LuminousMoteLifecycle lifecycle;
        private final Emitter[] emitters;
        private final LuminousMoteEmissionClock emissionClock = new LuminousMoteEmissionClock();
        private final ColosseumMoteSpawnSequence colosseumSequence;
        private double colosseumCarry;
        private ColosseumAuraVolume posedVolume;
        private Vec3 volumeOrigin;
        private long volumeTick = Long.MIN_VALUE;

        private EmitterGroup(PokemonEntity e, long now, ShadowAuraStyle style) {
            this.entityRef = new WeakReference<>(e);
            this.lifecycle = new LuminousMoteLifecycle(style, now,
                    ShadowAspectUtil.isHoldingShadowShard(e.getPokemon()));
            this.colosseumSequence = style == ShadowAuraStyle.COLOSSEUM
                    ? new ColosseumMoteSpawnSequence(e.getUUID().getMostSignificantBits()
                    ^ Long.rotateLeft(e.getUUID().getLeastSignificantBits(), 23) ^ now) : null;

            // Colosseum samples independent births throughout its whole aura.
            // Only Signature retains the original fixed lobe emitters.
            if (style == ShadowAuraStyle.COLOSSEUM) {
                this.emitters = new Emitter[0];
                return;
            }

            // Compute number of emitters based on bounding box size.
            // Map size ~1 -> 3 emitters, ~2.5 -> 6, up to a cap.
            double size = Math.max(0.5, e.getBoundingBox().getSize());
            int minEmitters = 3;
            int maxEmitters = 16;
            int count = (int) Math.round(minEmitters + Math.max(0, (size - 1.0) * 2.0));
            count = Math.min(maxEmitters, Math.max(minEmitters, count));

            this.emitters = new Emitter[count];

            // Generate Poisson-disc-like angles around the entity to avoid clumping.
            long seed = (((long) e.getId()) << 32) ^ now;
            double[] angles = poissonAngles(count, seed);

            // Keep the overall particle budget roughly constant by splitting base rate among emitters.
            double totalRatePerSec = 15.0;
            double perEmitterRate = totalRatePerSec / Math.max(1, count);

            // Alternate inside/outside bias so motes spawn just inside and just outside the AABB.
            for (int i = 0; i < count; i++) {
                boolean outside = (i % 2 == 0);
                emitters[i] = new Emitter(this.entityRef, angles[i], perEmitterRate, outside);
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
                    if (circ < minSep) {
                        ok = false;
                        break;
                    }
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

        static EmitterGroup create(PokemonEntity e, long now, ShadowAuraStyle style) {
            return new EmitterGroup(e, now, style);
        }

        boolean isExpired(long now) {
            Entity ent = entityRef.get();
            if (ent == null || !ent.isAlive() || ent.level() != Minecraft.getInstance().level) return true;
            if (ent instanceof PokemonEntity pe && (!ShadowAspectUtil.shouldHaveShadowAura(pe.getPokemon())
                    || lifecycle.expired(now, resolvedStyle(pe)))) {
                return true;
            }
            return false;
        }

        // Returns false if group should be removed
        boolean tickAndEmit(ClientLevel level, float partialTicks, boolean active, double colosseumBudget) {
            Entity ent = entityRef.get();
            if (ent == null || !ent.isAlive()) return false;
            double elapsedSeconds = emissionClock.sample(level.getGameTime(), partialTicks, active);
            if (elapsedSeconds <= 0.0) return true;
            if (lifecycle.style() == ShadowAuraStyle.COLOSSEUM) {
                emitColosseum(level, ent, elapsedSeconds, colosseumBudget);
                return true;
            }
            for (Emitter em : emitters) {
                if (em != null) em.tickAndEmit(level, elapsedSeconds * colosseumBudget);
            }
            return true;
        }

        void observeVolume(Entity entity, AABB bounds, long now) {
            posedVolume = ColosseumAuraVolume.fromBounds(bounds);
            volumeOrigin = entity.position();
            volumeTick = now;
        }

        private void emitColosseum(ClientLevel level, Entity entity, double elapsedSeconds, double budget) {
            boolean freshPose = posedVolume != null && volumeTick != Long.MIN_VALUE
                    && level.getGameTime() >= volumeTick && level.getGameTime() - volumeTick <= 12;
            ColosseumAuraVolume volume = freshPose ? posedVolume : ColosseumAuraVolume.fromBounds(entity.getBoundingBox());
            Vec3 translation = freshPose ? entity.position().subtract(volumeOrigin) : Vec3.ZERO;
            double sourceSize = volume.modelBounds().getSize();
            double desired = colosseumCarry + colosseumRate(sourceSize) * budget * elapsedSeconds;
            int requested = (int) Math.floor(desired);
            colosseumCarry = desired - requested;
            int count = COLOSSEUM_BUDGET.take(requested);
            double motionScale = Math.max(0.7, Math.min(1.6, Math.sqrt(Math.max(0.0, sourceSize))));
            for (int i = 0; i < count; i++) {
                var birth = colosseumSequence.next(volume, motionScale);
                Vec3 position = birth.position().add(translation);
                Vec3 velocity = birth.velocity();
                var particle = Minecraft.getInstance().particleEngine.createParticle(ModParticleTypes.LUMINOUS_MOTE.get(),
                        position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
                if (particle instanceof LuminousMoteParticle mote) {
                    mote.configureColosseum((float) sourceSize, (float) motionScale,
                            birth.paletteRoll(), birth.sizeRoll(), birth.lifetimeRoll());
                    if (entity instanceof PokemonEntity source) mote.setAuraSource(source);
                    ColosseumMoteRenderer.track(level, mote);
                }
            }
        }

    }

    // Single attached emitter instance for one lobe
    private static final class Emitter {
        private final WeakReference<Entity> entityRef;
        // Own the stream: model rendering may reseed the shared world RNG as the view changes.
        private final RandomSource random = RandomSource.create();
        private final double baseAngleRad;
        private final boolean outsideBias; // true = outside edge, false = just inside edge
        private final double ratePerSec; // average particles/sec for this emitter (group-scaled)
        private double carry = 0.0;       // fractional carry between frames

        Emitter(WeakReference<Entity> entityRef, double baseAngleRad,
                double ratePerSec, boolean outsideBias) {
            this.entityRef = entityRef;
            this.baseAngleRad = baseAngleRad;
            this.ratePerSec = ratePerSec;
            this.outsideBias = outsideBias;
        }

        // Returns false if emitter should be removed immediately
        boolean tickAndEmit(ClientLevel level, double elapsedSeconds) {
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

            // Integrate elapsed game time once per group, independent of frame rate.
            double emitRate = ratePerSec * (0.015 + scale);
            double want = emitRate * elapsedSeconds + carry;
            int count = (int) Math.floor(want);
            carry = want - count;

            // Emit
            for (int i = 0; i < count; i++) {
                // Lobe-biased emission around the entity with upward bias
                double spread = Math.PI / 6.0; // +/-30 degrees around base angle
                double ang = this.baseAngleRad + (random.nextDouble() * 2.0 - 1.0) * spread;

                // Compute distance to the horizontal AABB edge in direction ang, then offset
                // slightly to land either just outside or just inside the edge.
                double cosA = Math.cos(ang);
                double sinA = Math.sin(ang);
                double hw = Math.max(0.1, w * 0.5);
                double edge = hw / Math.max(Math.abs(cosA), Math.abs(sinA));
                double margin = Math.max(0.03, 0.06 * w);

                double r;
                if (outsideBias) {
                    double jitter = random.nextDouble() * (0.02 * Math.max(1.0, w));
                    r = edge + margin + jitter; // just outside
                } else {
                    double jitterIn = random.nextDouble() * (0.02 * Math.max(1.0, w));
                    r = Math.max(0.6 * edge, edge - margin - jitterIn); // just inside, but not too close to center
                }

                double sx = cx + cosA * r;
                double sz = cz + sinA * r;
                double sy = cy + (-0.05 + random.nextDouble() * 0.10) * Math.max(0.6, h);

                // Give each emitter a spawn area radius (disc in XZ) scaled to entity size
                double areaR = Math.max(0.08, 0.12 * w);
                // Local basis in XZ: outward normal (n) and tangential (t)
                double nx = cosA;
                double nz = sinA;
                double tx = -sinA;
                double tz = cosA;
                // Sample uniform-in-disc offset
                double phi2 = random.nextDouble() * (Math.PI * 2.0);
                double rr = Math.sqrt(random.nextDouble()) * areaR;
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
                double speedH = (0.008 + random.nextDouble() * 0.1) * speedScale; // horizontal speed
                double vy = (0.04 + random.nextDouble() * 0.10) * speedScale;     // upward speed
                double vx = dir.x * speedH;
                double vz = dir.z * speedH;

                // Add the particle; we pass the velocity vector via the speed parameters.
                LuminousMoteParticle.emitForPokemon(level, e instanceof PokemonEntity source ? source : null,
                        sx, sy, sz, vx, vy, vz);
            }
            return true;
        }
    }
}
