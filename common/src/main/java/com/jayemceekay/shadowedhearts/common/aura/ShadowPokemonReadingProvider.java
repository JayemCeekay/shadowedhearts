package com.jayemceekay.shadowedhearts.common.aura;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonData;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public final class ShadowPokemonReadingProvider implements AuraReadingProvider {
    private static final int MAX_READINGS = 4;
    private static final double RETICLE_CONE_DEGREES = 4.0;
    private static final double MIN_RETICLE_RADIUS = 0.75;

    @Override
    public boolean supports(AuraScanContext context) {
        AuraReaderMode mode = context.mode();
        return mode == AuraReaderMode.PASSIVE_AURA
                || mode == AuraReaderMode.PULSE_SCAN
                || mode == AuraReaderMode.SIGNAL_TRACKING
                || mode == AuraReaderMode.ENTITY_ANALYSIS;
    }

    @Override
    public List<AuraReading> scan(AuraScanContext context) {
        int range = Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange());
        return context.pulseScan() ? pulseScan(context, range) : targetedScan(context, range);
    }

    private static List<AuraReading> pulseScan(AuraScanContext context, int range) {
        AABB bounds = context.player().getBoundingBox().inflate(range);
        Vec3 origin = context.player().position();

        return context.level()
                .getEntitiesOfClass(PokemonEntity.class, bounds, ShadowPokemonData::isShadow)
                .stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(context.player())))
                .limit(MAX_READINGS)
                .map(entity -> toReading(context, entity, origin, range))
                .toList();
    }

    private static List<AuraReading> targetedScan(AuraScanContext context, int range) {
        AABB bounds = context.player().getBoundingBox().inflate(range);
        Vec3 eye = context.player().getEyePosition();
        Vec3 look = context.player().getLookAngle().normalize();
        Vec3 origin = context.player().position();
        double coneThreshold = Math.cos(Math.toRadians(RETICLE_CONE_DEGREES));

        return context.level()
                .getEntitiesOfClass(PokemonEntity.class, bounds, ShadowPokemonData::isShadow)
                .stream()
                .filter(entity -> isInsideReticle(context, entity, eye, look, range, coneThreshold))
                .sorted(Comparator
                        .comparingDouble((PokemonEntity entity) -> reticleMissDistance(entity, eye, look))
                        .thenComparingDouble(entity -> entity.distanceToSqr(context.player())))
                .limit(1)
                .map(entity -> toReading(context, entity, origin, range))
                .toList();
    }

    private static boolean isInsideReticle(AuraScanContext context, PokemonEntity entity, Vec3 eye, Vec3 look, int range, double coneThreshold) {
        Vec3 target = targetPoint(entity);
        Vec3 toTarget = target.subtract(eye);
        double distance = toTarget.length();
        if (distance <= 0.001 || distance > range) {
            return false;
        }

        Vec3 direction = toTarget.normalize();
        double alignment = look.dot(direction);
        if (alignment < coneThreshold) {
            return false;
        }

        double missDistance = reticleMissDistance(entity, eye, look);
        double reticleRadius = Math.max(MIN_RETICLE_RADIUS, (entity.getBbWidth() * 0.5) + 0.35);
        if (missDistance > reticleRadius) {
            return false;
        }

        return context.player().hasLineOfSight(entity);
    }

    private static Vec3 targetPoint(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.55, 0.0);
    }

    private static double reticleMissDistance(Entity entity, Vec3 eye, Vec3 look) {
        Vec3 toTarget = targetPoint(entity).subtract(eye);
        double projectedDistance = toTarget.dot(look);
        if (projectedDistance <= 0.0) {
            return Double.MAX_VALUE;
        }

        Vec3 closestPoint = eye.add(look.scale(projectedDistance));
        return targetPoint(entity).distanceTo(closestPoint);
    }

    @Override
    public int priority() {
        return 100;
    }

    private static AuraReading toReading(AuraScanContext context, PokemonEntity entity, Vec3 origin, int range) {
        Vec3 delta = entity.position().subtract(origin);
        double distance = delta.length();
        Vec3 direction = distance > 0.001 ? delta.normalize() : Vec3.ZERO;
        float strength = (float) Math.max(0.0, Math.min(1.0, 1.0 - (distance / Math.max(1, range))));

        var pokemon = entity.getPokemon();
        String speciesName = pokemon.getSpecies().getName();
        int heartPercent = Math.max(0, Math.min(100, ShadowAspectUtil.getHeartGaugePercent(pokemon)));
        String auraState = auraState(pokemon.getAspects().contains(SHAspects.REVERSE_MODE), pokemon.getAspects().contains(SHAspects.HYPER_MODE), heartPercent);

        return new AuraReading(
                entity.getUUID().toString(),
                AuraReadingType.SHADOW_POKEMON,
                speciesName,
                auraState,
                context.level().dimension().location(),
                direction.x,
                direction.y,
                direction.z,
                distanceBand(distance),
                verticalHint(delta.y),
                strength,
                1.0f,
                false
        );
    }

    private static String auraState(boolean reverseMode, boolean hyperMode, int heartPercent) {
        if (hyperMode) {
            return "Hyper Mode";
        }
        if (reverseMode) {
            return "Reverse Mode";
        }
        if (heartPercent >= 80) {
            return "Sealed Heart";
        }
        if (heartPercent >= 50) {
            return "Closed Heart";
        }
        if (heartPercent >= 25) {
            return "Stirring Heart";
        }
        if (heartPercent >= 1) {
            return "Opening Heart";
        }
        return "Purify Ready";
    }

    private static String distanceBand(double distance) {
        if (distance < 16.0) {
            return "Close";
        }
        if (distance < 48.0) {
            return "Near";
        }
        if (distance < 96.0) {
            return "Distant";
        }
        return "Faint";
    }

    private static String verticalHint(double yDelta) {
        if (yDelta > 6.0) {
            return "Above";
        }
        if (yDelta < -6.0) {
            return "Below";
        }
        return "Level";
    }
}
