package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** The pack runs along short native navigation paths. Saved endpoints survive chunk and world reloads. */
final class OutbreakTrail {
    private static final DustColorTransitionOptions FLECK = new DustColorTransitionOptions(
            new Vector3f(0.98F, 0.77F, 1.0F), new Vector3f(0.63F, 0.15F, 0.94F), 1.1F);
    private static final int BEARINGS = 16;
    private static final int FAR = 15;
    private static final int[] REFUGE_DISTANCES = {FAR, 10};
    private static final int[] ESCORT_DISTANCES = {7, 4};
    private static final float RUN_SPEED = 1.3F;
    private static final Map<PokemonEntity, Travel> TRAVELLING = new WeakHashMap<>();
    private static final class Travel {
        double bestDistance;
        long lastProgress;
        long lastCheck;
        long nextAttempt;
        Travel(double distance, long now) { bestDistance = distance; lastProgress = lastCheck = now; }
    }

    private OutbreakTrail() {}

    /** The controller commits the refuge once the original carrier has a route to it. */
    static @Nullable BlockPos breakAway(ServerLevel level, BlockPos siteCenter, BlockPos carrierHome, PokemonEntity carrier) {
        if (!freeToMove(level, carrier)) return null;
        return startRefugeRoute(level, siteCenter, carrierHome, carrier);
    }

    static @Nullable BlockPos scatterEscort(ServerLevel level, BlockPos center, PokemonEntity escort,
                                             int ordinal, int escortCount, BlockPos carrierHome, BlockPos refuge,
                                             Collection<BlockPos> reserved) {
        if (!freeToMove(level, escort)) return null;
        double carrierBearing = Math.atan2(refuge.getZ() - carrierHome.getZ(), refuge.getX() - carrierHome.getX());
        // Divide bearings around the whole pack, leaving the first bearing to the carrier.
        double bearing = carrierBearing + 2 * Math.PI * (ordinal + 1) / (escortCount + 1);
        BlockPos origin = escort.blockPosition();
        for (int i = 0; i < 5; i++) {
            int offset = (i % 2 == 0 ? -1 : 1) * ((i + 1) / 2);
            double angle = bearing + offset * (2 * Math.PI / BEARINGS);
            for (int distance : ESCORT_DISTANCES) {
                BlockPos end = origin.offset((int) Math.round(distance * Math.cos(angle)), 0,
                        (int) Math.round(distance * Math.sin(angle)));
                BlockPos landing = CraterRescueSites.safeSurface(level, end);
                if (landing == null || !landing.closerThan(center, 40) || landing.closerThan(refuge, 3)
                        || reserved.stream().anyMatch(other -> other.closerThan(landing, 3))) continue;
                if (runTo(level, escort, landing)) return landing;
            }
        }
        return null;
    }

    /** Returns the anchor to save on arrival, or a safe stopping point if the landscape has changed. */
    static @Nullable BlockPos continueTo(ServerLevel level, PokemonEntity member, BlockPos destination) {
        if (!freeToMove(level, member) || !level.isPositionEntityTicking(destination)) {
            TRAVELLING.remove(member);
            return null;
        }
        Vec3 target = Vec3.atBottomCenterOf(destination);
        if (member.distanceToSqr(target) <= 2.25 && CraterRescueActors.safe(level, member, target)) {
            stopRun(member);
            return destination;
        }
        var navigation = member.getNavigation();
        var path = navigation.getPath();
        long now = level.getGameTime();
        double distance = member.position().distanceTo(target);
        Travel travel = TRAVELLING.computeIfAbsent(member, ignored -> new Travel(distance, now));
        if (now - travel.lastCheck > 40 || distance + .35 < travel.bestDistance) {
            travel.bestDistance = distance;
            travel.lastProgress = now;
        }
        travel.lastCheck = now;
        if (now - travel.lastProgress < 100) {
            if (path != null && !navigation.isDone() && !navigation.isStuck() && destination.equals(path.getTarget()))
                return null;
            // A battle, a reload, or a competing wild behaviour can interrupt a native path.
            if (now >= travel.nextAttempt) {
                travel.nextAttempt = now + 20;
                runTo(level, member, destination);
            }
            return null;
        }
        // If someone blocked the route, settle where the trainer last saw this Pokémon make progress.
        var feet = CraterRescueSites.safeSurface(level, member.blockPosition());
        if (feet == null || member.distanceToSqr(Vec3.atBottomCenterOf(feet)) > 2.25
                || hazardous(level, feet) || !CraterRescueActors.safe(level, member, Vec3.atBottomCenterOf(feet))) {
            // While airborne or displaced there is no suitable resting anchor yet. Retry a restored route.
            TRAVELLING.remove(member);
            return null;
        }
        stopRun(member);
        return feet;
    }

    private static boolean freeToMove(ServerLevel level, PokemonEntity member) {
        return member.level() == level && member.isAlive() && !member.getPokemon().isPlayerOwned()
                && !member.isBattling() && !member.isBusy() && member.getBeamMode() == 0
                && !CraterRescueBattles.busy(member.getPokemon().getUuid());
    }

    private static boolean runTo(ServerLevel level, PokemonEntity member, BlockPos destination) {
        if (!level.isPositionEntityTicking(destination)
                || !CraterRescueActors.safe(level, member, Vec3.atBottomCenterOf(destination))) return false;
        var navigation = member.getNavigation();
        var path = navigation.createPath(destination, 0);
        if (path == null || !path.canReach() || path.getNodeCount() == 0 || path.getNodeCount() > FAR * 4) return false;
        BlockPos previous = CraterRescueSites.safeSurface(level, member.blockPosition());
        if (previous == null || hazardous(level, previous)) return false;
        // The native path can bend around the meteor. Validate the ground beneath those waypoints so
        // a trainer can follow a flying species as well; a straight ray can cross an impassable cliff.
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNodePos(i);
            if (!level.hasChunkAt(node) || !level.getWorldBorder().isWithinBounds(node) || hazardous(level, node)) return false;
            previous = walkableEnd(level, previous, node);
            if (previous == null) return false;
        }
        CraterRescueActors.releaseRoamingLimit(member);
        if (!navigation.moveTo(path, RUN_SPEED)) return false;
        member.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, RUN_SPEED, 1));
        return true;
    }

    private static void stopRun(PokemonEntity member) {
        TRAVELLING.remove(member);
        member.getNavigation().stop();
        member.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static @Nullable BlockPos startRefugeRoute(ServerLevel level, BlockPos center, BlockPos home, PokemonEntity carrier) {
        double outward = Math.atan2(home.getZ() - center.getZ(), home.getX() - center.getX());
        List<BlockPos> openGround = new ArrayList<>();
        // Prefer outward routes; a Pokémon on an irregular meteor summit may only have a way down on the far side.
        for (int i = 0; i < BEARINGS; i++) {
            int offset = (i % 2 == 0 ? -1 : 1) * ((i + 1) / 2);
            double angle = outward + offset * (2 * Math.PI / BEARINGS);
            for (int distance : REFUGE_DISTANCES) {
                BlockPos end = home.offset((int) Math.round(distance * Math.cos(angle)), 0,
                        (int) Math.round(distance * Math.sin(angle)));
                BlockPos landing = CraterRescueSites.safeSurface(level, end);
                if (landing == null || !landing.closerThan(center, 40)
                        || !CraterRescueActors.safe(level, carrier, Vec3.atBottomCenterOf(landing))) continue;
                if (sheltered(level, landing)) {
                    if (runTo(level, carrier, landing)) return landing;
                } else openGround.add(landing);
            }
        }
        for (BlockPos landing : openGround) if (runTo(level, carrier, landing)) return landing;
        return null;
    }

    /** Check the actual heightmap below a native segment, including either safe side of a diagonal corner. */
    private static @Nullable BlockPos walkableEnd(ServerLevel level, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps > FAR * 2) return null;
        BlockPos previous = from;
        for (int i = 1; i <= steps; i++) {
            int x = from.getX() + Math.round((float) i * dx / steps);
            int z = from.getZ() + Math.round((float) i * dz / steps);
            BlockPos next = CraterRescueSites.safeSurface(level, new BlockPos(x, previous.getY(), z));
            if (!step(level, previous, next)) return null;
            if (x != previous.getX() && z != previous.getZ()
                    && !corner(level, previous, next, x, previous.getZ())
                    && !corner(level, previous, next, previous.getX(), z)) return null;
            previous = next;
        }
        return previous;
    }

    private static boolean corner(ServerLevel level, BlockPos from, BlockPos to, int x, int z) {
        BlockPos side = CraterRescueSites.safeSurface(level, new BlockPos(x, from.getY(), z));
        return step(level, from, side) && step(level, side, to);
    }

    private static boolean step(ServerLevel level, BlockPos from, @Nullable BlockPos to) {
        if (to == null || hazardous(level, to)) return false;
        int rise = to.getY() - from.getY();
        if (rise < -2 || rise > 1) return false;
        if (rise == 0) return true;
        BlockPos lower = rise < 0 ? to : from;
        for (int extra = 0; extra < Math.abs(rise); extra++) {
            BlockPos headroom = lower.above(2 + extra);
            if (!level.getBlockState(headroom).getCollisionShape(level, headroom).isEmpty()) return false;
        }
        return true;
    }

    private static boolean hazardous(ServerLevel level, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below()), standing = level.getBlockState(feet);
        return floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || standing.is(BlockTags.FIRE)
                || standing.is(Blocks.WITHER_ROSE) || standing.is(Blocks.SWEET_BERRY_BUSH);
    }

    private static boolean sheltered(ServerLevel level, BlockPos feet) {
        // Prefer ground beside a trunk, under foliage, or against a natural bank. Open terrain is also valid.
        if (level.getBlockState(feet.above(2)).is(BlockTags.LEAVES)) return true;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = feet.relative(side);
            if (!level.hasChunkAt(adjacent)) continue;
            BlockState block = level.getBlockState(adjacent);
            if (block.is(BlockTags.LOGS) || block.isFaceSturdy(level, adjacent, side.getOpposite())) return true;
        }
        return false;
    }

    /** Three ground flecks at the departure point indicate a bearing after a successful breakaway. */
    static List<BlockPos> departureColumns(BlockPos origin, BlockPos refuge) {
        long dx = (long) refuge.getX() - origin.getX(), dz = (long) refuge.getZ() - origin.getZ();
        if (Math.abs(dx) > FAR || Math.abs(dz) > FAR) return List.of();
        double distance = Math.hypot(dx, dz);
        if (distance < 9) return List.of();
        List<BlockPos> hints = new ArrayList<>(3);
        for (int step = 1; step <= 3; step++) {
            BlockPos column = origin.offset((int) Math.round(step * dx / distance), 0,
                    (int) Math.round(step * dz / distance));
            if (!hints.contains(column)) hints.add(column);
        }
        return hints;
    }

    static void showDeparture(ServerLevel level, BlockPos origin, BlockPos refuge) {
        for (BlockPos column : departureColumns(origin, refuge)) {
            BlockPos feet = CraterRescueSites.safeSurface(level, column);
            if (feet != null) level.sendParticles(FLECK, feet.getX() + .5, feet.getY() + .18, feet.getZ() + .5,
                    1, .08, .03, .08, 0);
        }
    }
}
