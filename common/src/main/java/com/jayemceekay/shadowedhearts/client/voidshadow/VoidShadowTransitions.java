package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallCaptureVfx;
import com.jayemceekay.shadowedhearts.client.render.SnagDissolveRenderer;
import com.jayemceekay.shadowedhearts.client.particle.VoidShadowCloudParticle;
import com.jayemceekay.shadowedhearts.registry.util.ModParticleTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Visual-only shapeshifts; authoritative battle species/stats are never delayed or changed. */
public final class VoidShadowTransitions {
    private static final int MAX_ENTITIES = 16;
    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>(16, 0.75f, true);
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel level;
    private static long lastTick = Long.MIN_VALUE;

    private VoidShadowTransitions() { }

    public static MultiBufferSource beginRender(PokemonEntity entity, PoseStack poses,
                                                MultiBufferSource buffers, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        syncWorld(minecraft);
        VoidShadowTransformationAudio.tick(minecraft);
        if (level == null || entity.level() != level || level.getEntity(entity.getId()) != entity || !eligible(entity)) {
            ENTRIES.remove(entity.getUUID());
            return buffers;
        }
        long tick = level.getGameTime();
        Entry entry = ENTRIES.get(entity.getUUID());
        if (entry == null || entry.entity != entity) {
            entry = new Entry(entity, shape(entity), tick);
            ENTRIES.put(entity.getUUID(), entry);
            while (ENTRIES.size() > MAX_ENTITIES) ENTRIES.remove(ENTRIES.keySet().iterator().next());
        }
        entry.lastSeen = tick;
        boolean waiting = VoidShadowTransformationAudio.isPending(entity.getUUID());
        if (!waiting) refreshShape(entry);
        emit(entry, tick);
        var sample = sample(entry, tick + partialTicks);
        if (sample.finished()) entry.transition = null;
        Transition renderedTransition = entry.transition;
        if (waiting && entry.snapshot != null) {
            // Keep the last posed appearance while species and aspects arrive in separate updates.
            sample = new VoidShadowTransitionTimeline.Sample(1, 0, 0, false);
            renderedTransition = new Transition(null, entry.snapshot, entry.width, entry.height);
        }

        // Record at most once per game tick. Multipass/Iris renders cannot multiply
        // capture cost, and only a fully revealed current model becomes a new source.
        PoseStack.Pose root = poses.last().copy();
        VoidShadowSnapshotBufferSource capture = null;
        if (!waiting && sample.newOpacity() >= 0.999f && entry.lastCapture != tick) {
            entry.lastCapture = tick;
            capture = new VoidShadowSnapshotBufferSource(buffers, root);
            buffers = capture;
        }
        if (sample.newOpacity() < 0.999f) buffers = new VoidShadowFadeBufferSource(buffers, sample.newOpacity());
        return new Frame(buffers, entry, root, capture, renderedTransition, sample);
    }

    public static void endRender(PokemonEntity entity, PoseStack poses, MultiBufferSource buffers, float partialTicks) {
        Frame frame = frame(buffers);
        Entry entry = ENTRIES.get(entity.getUUID());
        if (frame == null || entry != frame.entry || entry.entity != entity || entity.level() != level) return;
        if (frame.capture != null) {
            entry.snapshot = frame.capture.finish();
            entry.snapshotTick = level.getGameTime();
            entry.width = entity.getBbWidth(); entry.height = entity.getBbHeight();
        }
        if (frame.transition != null && frame.transition.previous != null && frame.sample.oldOpacity() > 0.001f) {
            // Cobblemon may translate the caller stack while rendering a battle platform.
            // Replay against the copied HEAD root used to capture the relative vertices.
            frame.transition.previous.render(frame.root, nativeBuffers(buffers), frame.sample.oldOpacity());
        }
    }

    public static void tick(Minecraft minecraft) {
        syncWorld(minecraft);
        VoidShadowTransformationAudio.tick(minecraft);
        if (level == null || minecraft.isPaused()) return;
        long tick = level.getGameTime();
        Iterator<Entry> iterator = ENTRIES.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.entity.isRemoved() || level.getEntity(entry.entity.getId()) != entry.entity
                    || !eligible(entry.entity) || tick - entry.lastSeen > 40) {
                iterator.remove();
                continue;
            }
            boolean waiting = VoidShadowTransformationAudio.isPending(entry.entity.getUUID());
            if (!waiting) refreshShape(entry);
            emit(entry, tick);
            if (sample(entry, tick).finished()) entry.transition = null;
            if (!waiting && tick - entry.snapshotTick > 2) entry.snapshot = null;
        }
    }

    public static void clear() { ENTRIES.clear(); level = null; lastTick = Long.MIN_VALUE; }

    /** Deferred Iris material never replaces either side of an active crossfade. */
    public static boolean hasStableMaterial(MultiBufferSource buffers) {
        Frame frame = frame(buffers);
        return frame != null && frame.sample.newOpacity() >= 0.999f
                && frame.sample.oldOpacity() <= 0.001f;
    }

    private static void syncWorld(Minecraft minecraft) {
        ClientLevel current = minecraft.level;
        long tick = current == null ? Long.MIN_VALUE : current.getGameTime();
        if (current != level || tick < lastTick) { ENTRIES.clear(); level = current; }
        lastTick = tick;
    }

    private static boolean eligible(PokemonEntity entity) {
        return entity.getAspects().contains("void-shadow") && !entity.isInvisible() && !entity.isRemoved()
                && entity.getBeamMode() == 0 && DarkBallCaptureVfx.getByPokemonId(entity.getId()) == null;
    }

    private static Shape shape(PokemonEntity entity) {
        return new Shape(entity.getPokemon().getSpecies().getResourceIdentifier(), Set.copyOf(entity.getAspects()));
    }

    /** The event coordinator calls this only after both synchronized appearance fields match. */
    static void beginTransformation(PokemonEntity entity, long tick) {
        syncWorld(Minecraft.getInstance());
        if (level == null || entity.level() != level || !eligible(entity)) return;
        Entry entry = ENTRIES.get(entity.getUUID());
        if (entry == null || entry.entity != entity) {
            entry = new Entry(entity, shape(entity), tick);
            ENTRIES.put(entity.getUUID(), entry);
            while (ENTRIES.size() > MAX_ENTITIES) ENTRIES.remove(ENTRIES.keySet().iterator().next());
        }
        var previous = entry.snapshot;
        float width = Math.max(entry.width, entity.getBbWidth());
        float height = Math.max(entry.height, entity.getBbHeight());
        entry.transition = new Transition(new VoidShadowTransitionTimeline.State(tick), previous, width, height);
        entry.shape = shape(entity);
        entry.snapshot = null;
        entry.lastSeen = tick;
    }

    /** Initial tracking, repairs and unrelated metadata updates never invent a live event. */
    private static void refreshShape(Entry entry) {
        Shape current = shape(entry.entity);
        if (current.equals(entry.shape)) return;
        entry.transition = null;
        entry.shape = current;
        entry.snapshot = null;
    }

    private static VoidShadowTransitionTimeline.Sample sample(Entry entry, double tick) {
        return VoidShadowTransitionTimeline.sample(entry.transition == null
                ? VoidShadowTransitionTimeline.DURATION_TICKS : tick - entry.transition.clock.startedAtGameTick());
    }

    private static void emit(Entry entry, long tick) {
        if (entry.transition == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        Transition transition = entry.transition;
        for (var burst : transition.clock.consumeBursts(tick)) {
            ParticleStatus quality = minecraft.options.particles().get();
            int count = quality == ParticleStatus.MINIMAL ? Math.max(2, burst.count() / 3)
                    : quality == ParticleStatus.DECREASED ? Math.max(3, burst.count() * 2 / 3) : burst.count();
            count = Math.round(count * com.jayemceekay.shadowedhearts.client.ClientEffects.ambientDensity());
            float width = Math.min(8, Math.max(0.5f, transition.width));
            float height = Math.min(8, Math.max(0.6f, transition.height));
            float diameter = Math.min(4, Math.max(0.55f, Math.max(width, height) * 0.65f));
            for (int i = 0; i < count; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double radius = 0.25 + RANDOM.nextDouble() * 0.5;
                double dx = Math.cos(angle) * width * radius;
                double dz = Math.sin(angle) * width * radius;
                double y = entry.entity.getY() + height * (0.15 + RANDOM.nextDouble() * 0.7);
                var particle = minecraft.particleEngine.createParticle(ModParticleTypes.VOID_SHADOW_CLOUD.get(),
                        entry.entity.getX() + dx, y, entry.entity.getZ() + dz,
                        Math.cos(angle) * 0.02, 0.008 + RANDOM.nextDouble() * 0.012, Math.sin(angle) * 0.02);
                if (particle instanceof VoidShadowCloudParticle cloud) {
                    cloud.setCloudDiameter(diameter * (0.8f + RANDOM.nextFloat() * 0.4f));
                }
            }
        }
    }

    /** Support props such as battle platforms retain their own native appearance. */
    public static MultiBufferSource nativeBuffers(MultiBufferSource buffers) {
        while (true) {
            if (buffers instanceof Frame frame) buffers = frame.delegate;
            else if (buffers instanceof VoidShadowIrisBufferSource iris) buffers = iris.nativeBuffers();
            else if (buffers instanceof VoidShadowBufferSource liquid) buffers = liquid.nativeBuffers();
            else if (buffers instanceof VoidShadowFadeBufferSource fade) buffers = fade.nativeBuffers();
            else if (buffers instanceof VoidShadowSnapshotBufferSource snapshot) buffers = snapshot.nativeBuffers();
            else {
                MultiBufferSource snag = SnagDissolveRenderer.nativeBuffers(buffers);
                if (snag == buffers) return buffers;
                buffers = snag;
            }
        }
    }

    private static Frame frame(MultiBufferSource buffers) {
        while (true) {
            if (buffers instanceof Frame frame) return frame;
            if (buffers instanceof VoidShadowIrisBufferSource iris) buffers = iris.nativeBuffers();
            else if (buffers instanceof VoidShadowBufferSource liquid) buffers = liquid.nativeBuffers();
            else if (buffers instanceof VoidShadowFadeBufferSource fade) buffers = fade.nativeBuffers();
            else if (buffers instanceof VoidShadowSnapshotBufferSource snapshot) buffers = snapshot.nativeBuffers();
            else return null;
        }
    }

    /** Render-local state also keeps nested and repeated passes from replacing one another's roots. */
    private record Frame(MultiBufferSource delegate, Entry entry, PoseStack.Pose root,
                         VoidShadowSnapshotBufferSource capture, Transition transition,
                         VoidShadowTransitionTimeline.Sample sample) implements MultiBufferSource {
        @Override public @NotNull VertexConsumer getBuffer(@NotNull RenderType type) {
            return delegate.getBuffer(type);
        }
    }

    private record Shape(ResourceLocation species, Set<String> aspects) { }
    private record Transition(VoidShadowTransitionTimeline.State clock,
                              VoidShadowSnapshotBufferSource.Snapshot previous, float width, float height) { }
    private static final class Entry {
        final PokemonEntity entity;
        Shape shape;
        long lastSeen, snapshotTick, lastCapture = Long.MIN_VALUE;
        float width, height;
        Transition transition;
        VoidShadowSnapshotBufferSource.Snapshot snapshot;
        Entry(PokemonEntity entity, Shape shape, long tick) {
            this.entity = entity; this.shape = shape; lastSeen = tick;
            width = entity.getBbWidth(); height = entity.getBbHeight();
        }
    }
}
