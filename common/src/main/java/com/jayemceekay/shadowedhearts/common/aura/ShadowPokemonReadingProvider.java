package com.jayemceekay.shadowedhearts.common.aura;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads Shadow status from the backing Pokemon so fresh or already-sent-out
 * owned Pokemon are detectable even when their transient entity flag is stale.
 */
public final class ShadowPokemonReadingProvider implements AuraReadingProvider {
    private static final int MAX_READINGS = 4;

    /** Caller must validate participating/opposing battle actors and equipped Reader eligibility. */
    public static AuraReading battleAnalysis(ServerPlayer viewer, PokemonEntity target) {
        if (!viewer.isAlive() || viewer.serverLevel() != target.level() || target.getBattle() == null
                || !target.isAlive() || target.isRemoved() || !ShadowAspectUtil.hasShadowAspect(target.getPokemon())) return null;
        int range = Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange());
        AuraScanContext context = new AuraScanContext(viewer, viewer.serverLevel(), ItemStack.EMPTY,
                AuraReaderMode.PASSIVE_AURA, viewer.serverLevel().getGameTime(), false);
        boolean pinned = target.getUUID().equals(AuraReaderService.session(viewer).contacts().pinnedId());
        return toReading(context, target, range, true,
                pinned ? AuraReadingSource.TRACKED : AuraReadingSource.PASSIVE, 0L, pinned);
    }

    @Override
    public boolean supports(AuraScanContext context) {
        return true;
    }

    @Override
    public List<AuraReading> scan(AuraScanContext context) {
        int range = Math.max(1, ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange());
        AuraReaderContacts contacts = AuraReaderService.session(context.player()).contacts();
        contacts.observeDimension(context.level().dimension().location().toString());
        if (context.pulseScan()) {
            return pulseScan(context, range, contacts);
        }

        List<AuraReading> readings = new ArrayList<>(knownContacts(context, range, contacts));
        targetedScan(context, range, contacts).stream()
                .findFirst()
                .ifPresent(targeted -> {
                    // A visible reticle hit authorizes analysis, independently of pulse discovery.
                    readings.removeIf(r -> r.id().equals(targeted.id()));
                    readings.add(targeted);
                });

        return readings;
    }

    private static List<AuraReading> knownContacts(AuraScanContext context, int range, AuraReaderContacts contacts) {
        List<AuraReading> readings = new ArrayList<>();
        for (var id : contacts.contactIds(context.gameTime())) {
            var entity = context.level().getEntity(id);
            if (!(entity instanceof PokemonEntity pokemonEntity) || !isUsable(context, pokemonEntity, range)) {
                contacts.forget(id);
                continue;
            }
            boolean pinned = id.equals(contacts.pinnedId());
            readings.add(toReading(context, pokemonEntity, range, false,
                    pinned ? AuraReadingSource.TRACKED : AuraReadingSource.PASSIVE,
                    pinned ? 0L : contacts.expiryTick(id), pinned));
        }
        return readings;
    }

    private static List<AuraReading> targetedScan(AuraScanContext context, int range, AuraReaderContacts contacts) {
        var target = AuraReaderTargeting.pickEntity(context.level(), context.player(), 1.0f, range);
        if (!(target instanceof PokemonEntity pokemonEntity)
                || !pokemonEntity.isAlive()
                || !ShadowAspectUtil.hasShadowAspect(pokemonEntity.getPokemon())) {
            return List.of();
        }
        boolean pinned = pokemonEntity.getUUID().equals(contacts.pinnedId());
        return List.of(toReading(context, pokemonEntity, range, true,
                pinned ? AuraReadingSource.TRACKED : AuraReadingSource.PASSIVE, 0L, pinned));
    }

    private static List<AuraReading> pulseScan(AuraScanContext context, int range, AuraReaderContacts contacts) {
        AABB bounds = context.player().getBoundingBox().inflate(range);

        return context.level()
                .getEntitiesOfClass(PokemonEntity.class, bounds, entity -> isUsable(context, entity, range))
                .stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(context.player())))
                .limit(MAX_READINGS)
                .map(entity -> {
                    contacts.discover(entity.getUUID(), context.gameTime());
                    return toReading(context, entity, range, false, AuraReadingSource.PULSE,
                            contacts.expiryTick(entity.getUUID()), entity.getUUID().equals(contacts.pinnedId()));
                })
                .toList();
    }

    static boolean isUsable(AuraScanContext context, PokemonEntity entity, int range) {
        return AuraReaderContacts.usable(entity.isAlive() && !entity.isRemoved(),
                ShadowAspectUtil.hasShadowAspect(entity.getPokemon()),
                AuraReaderTargeting.distanceToHitboxSqr(context.player(), entity), range);
    }

    @Override
    public int priority() {
        return 100;
    }

    private static AuraReading toReading(AuraScanContext context, PokemonEntity entity, int range, boolean targeted,
                                         AuraReadingSource source, long expiryTick, boolean pinned) {
        Vec3 delta = entity.position().subtract(context.player().position());
        double distance = delta.length();
        Vec3 direction = distance > 0.001 ? delta.normalize() : Vec3.ZERO;
        float strength = (float) Math.max(0.0, Math.min(1.0, 1.0 - (distance / Math.max(1, range))));

        var pokemon = entity.getPokemon();
        Component label;
        Component auraState;
        float confidence;
        AuraHeartGaugeReadout heartGauge = null;

        if (targeted) {
            AuraScanOwnership ownership = AuraScanOwnership.resolve(context.player().getUUID(), entity);
            int heartMeter = ShadowAspectUtil.getHeartGaugeMeter(pokemon);
            int heartMaximum = HeartGaugeConfig.getMax(pokemon);
            heartGauge = ownership.heartGauge(heartMeter, heartMaximum);
            boolean hyper = pokemon.getAspects().contains(SHAspects.HYPER_MODE);
            boolean reverse = pokemon.getAspects().contains(SHAspects.REVERSE_MODE);

            if (ownership == AuraScanOwnership.SELF_OWNED) {
                label = pokemon.getSpecies().getTranslatedName();
                auraState = auraStateText(reverse, hyper, heartMeter, heartMaximum);
                confidence = 1.0f;
            } else if (ownership == AuraScanOwnership.OTHER_TRAINER_OWNED) {
                label = pokemon.getSpecies().getTranslatedName();
                auraState = Component.translatable("hud.shadowedhearts.aura_reader.reading.corrupted_aura");
                confidence = 0.75f;
            } else { // Wild
                label = Component.translatable("hud.shadowedhearts.aura_reader.reading.wild", pokemon.getSpecies().getTranslatedName());
                auraState = Component.translatable("hud.shadowedhearts.aura_reader.reading.shadow_aura");
                confidence = 0.6f;
            }
        } else {
            label = Component.translatable("hud.shadowedhearts.aura_reader.reading.shadow_signature");
            auraState = pinned ? Component.translatable("hud.shadowedhearts.aura_reader.reading.pinned_pokemon") : Component.translatable("hud.shadowedhearts.aura_reader.reading.fresh_contact");
            confidence = 0.4f;
        }

        return new AuraReading(
                entity.getUUID().toString(),
                AuraReadingType.SHADOW_POKEMON,
                source,
                label,
                auraState,
                context.level().dimension().location(),
                direction.x,
                direction.y,
                direction.z,
                targeted ? 0.0f : 15.0f,
                distanceBand(distance),
                verticalHint(delta.y),
                strength,
                confidence,
                0.0f,
                0,
                expiryTick,
                pinned,
                false,
                false,
                false,
                heartGauge
        );
    }

    static String auraState(boolean reverseMode, boolean hyperMode, int heartMeter, int heartMaximum) {
        return auraStateText(reverseMode, hyperMode, heartMeter, heartMaximum).getString();
    }

    static Component auraStateText(boolean reverseMode, boolean hyperMode, int heartMeter, int heartMaximum) {
        if (hyperMode) {
            return Component.translatable("hud.shadowedhearts.aura_reader.reading.hyper_mode");
        }
        if (reverseMode) {
            return Component.translatable("hud.shadowedhearts.aura_reader.reading.reverse_mode");
        }
        return switch (AuraHeartGaugeReadout.bandFor(heartMeter, heartMaximum)) {
            case SEALED -> Component.translatable("hud.shadowedhearts.aura_reader.reading.sealed_heart");
            case CLOSED -> Component.translatable("hud.shadowedhearts.aura_reader.reading.closed_heart");
            case STIRRING -> Component.translatable("hud.shadowedhearts.aura_reader.reading.stirring_heart");
            case OPENING -> Component.translatable("hud.shadowedhearts.aura_reader.reading.opening_heart");
            case READY -> Component.translatable("hud.shadowedhearts.aura_reader.reading.purify_ready");
        };
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
