package com.jayemceekay.shadowedhearts.mixin.rctmod;

import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.api.service.TrainerManager;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.server.NPCShadowInjector;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Battle-time conversions — add tags before RCTMod.makeBattle() starts the battle.
 * If the trainer type is "team_rocket":
 *  - If their party has space (<6), append exactly 1 Shadow Pokémon for this battle.
 *  - Else convert exactly 1 existing Pokémon (random pick happens in injector) to Shadow.
 *
 * We leverage the existing NPCShadowInjector, which listens to BATTLE_STARTED_PRE and applies
 * APPEND or CONVERT based on entity tags. This keeps all mutations scoped to the battle instance.
 */
@Mixin(value = RCTMod.class, remap = false)
public abstract class MixinRCTModMakeBattle {

    @Inject(method = "makeBattle", at = @At("HEAD"))
    private void shadowedhearts$applyConfigDrivenRCTIntegration(TrainerMob mob, Player player, CallbackInfoReturnable<Boolean> cir) {
        try {
            // Read config
            var cfg = ShadowedHeartsConfigs.getInstance().getShadowConfig();
            if (!cfg.rctIntegrationEnabled()) return;

            TrainerManager tm = RCTMod.getInstance().getTrainerManager();
            var tmd = tm.getData(mob);

            // Resolve trainer type and id (be defensive against API changes)
            String typeId = null;
            try { var type = tmd.getType(); typeId = type != null ? type.id() : null; } catch (Throwable ignored) {}
            if (typeId == null) return;
            String trainerId = resolveTrainerIdentifier(tmd);

            String typeIdLc = typeId.toLowerCase(Locale.ROOT);
            String trainerIdLc = trainerId == null ? null : trainerId.toLowerCase(Locale.ROOT);

            // Determine which section applies: specific trainer entry has priority; otherwise type-based lists.
            String section = null; // one of: append, replace, convert
            String typePreset = null;

            // 1) replace section
            if (cfg.replace() != null) {
                // trainers first
                if (trainerIdLc != null) {
                    for (String tRaw : cfg.replace().trainers()) {
                        String[] parts = tRaw.split(";");
                        if (parts.length >= 1 && trainerIdLc.equalsIgnoreCase(parts[0])) {
                            section = "replace";
                        }
                    }
                }
                if (section == null) {
                    boolean listed = cfg.replace().typePresets().stream().anyMatch(s -> s.startsWith(typeIdLc + "=")) ||
                                     cfg.replace().trainerTypes().stream().anyMatch(s -> s != null && s.equalsIgnoreCase(typeIdLc));
                    boolean blocked = cfg.replace().trainerBlacklist().stream().anyMatch(s -> s != null && s.equalsIgnoreCase(typeIdLc));
                    if (listed && !blocked) section = "replace";
                }
            }

            // 2) append section
            if (section == null && cfg.append() != null) {
                if (trainerIdLc != null) {
                    for (String tRaw : cfg.append().trainers()) {
                        String[] parts = tRaw.split(";");
                        if (parts.length >= 1 && trainerIdLc.equalsIgnoreCase(parts[0])) {
                            section = "append";
                        }
                    }
                }
                if (section == null) {
                    typePreset = cfg.append().typePresets().stream()
                            .filter(s -> s.startsWith(typeIdLc + "="))
                            .map(s -> s.substring(typeIdLc.length() + 1))
                            .findFirst().orElse(null);
                    boolean listed = typePreset != null || cfg.append().trainerTypes().stream().anyMatch(s -> s != null && s.equalsIgnoreCase(typeIdLc));
                    boolean blocked = cfg.append().trainerBlacklist().stream().anyMatch(s -> s != null && s.equalsIgnoreCase(typeIdLc));
                    if (listed && !blocked) section = "append";
                    else typePreset = null;
                }
            }

            // 3) convert section
            if (section == null && cfg.convert() != null) {
                if (trainerIdLc != null) {
                    for (String tRaw : cfg.convert().trainers()) {
                        String[] parts = tRaw.split(";");
                        if (parts.length >= 1 && trainerIdLc.equalsIgnoreCase(parts[0])) {
                            section = "convert";
                        }
                    }
                }
                if (section == null) {
                    typePreset = cfg.convert().typePresets().stream()
                            .filter(s -> s.startsWith(typeIdLc + "="))
                            .map(s -> s.substring(typeIdLc.length() + 1))
                            .findFirst().orElse(null);
                    boolean listed = typePreset != null || cfg.convert().trainerTypes().stream().anyMatch(s -> s != null && s.equalsIgnoreCase(typeIdLc));
                    boolean blocked = cfg.convert().trainerBlacklist().stream().anyMatch(s -> s != null && s.equalsIgnoreCase(typeIdLc));
                    if (listed && !blocked) section = "convert";
                    else typePreset = null;
                }
            }

            if (section == null) return; // nothing to do

            // Apply tags to the trainer entity according to the selected section
            mob.addTag(NPCShadowInjector.TAG_ENABLE);
            // Default to converting/adding a single mon unless the config's custom tags override this
            boolean hasCount = false;
            
            // Re-resolve chosen trainer tags/presets from raw strings if specific trainer matched
            if (trainerIdLc != null) {
                List<? extends String> rawTrainers = Collections.emptyList();
                if ("replace".equals(section)) rawTrainers = cfg.replace().trainers();
                else if ("append".equals(section)) rawTrainers = cfg.append().trainers();
                else if ("convert".equals(section)) rawTrainers = cfg.convert().trainers();

                for (String tRaw : rawTrainers) {
                    String[] parts = tRaw.split(";");
                    if (parts.length >= 1 && trainerIdLc.equalsIgnoreCase(parts[0])) {
                        if (parts.length >= 2 && !parts[1].isBlank()) {
                            mob.addTag(NPCShadowInjector.TAG_PRESET_PREFIX + parts[1]);
                            hasCount = true;
                        }
                        if (parts.length >= 3 && !parts[2].isBlank()) {
                            for (String tag : parts[2].split(",")) {
                                if (!tag.isBlank()) {
                                    mob.addTag(tag);
                                    if (tag.startsWith(NPCShadowInjector.TAG_COUNT_PREFIX)) hasCount = true;
                                }
                            }
                        }
                        break;
                    }
                }
            }

            if (!hasCount && typePreset != null) {
                mob.addTag(NPCShadowInjector.TAG_PRESET_PREFIX + typePreset);
                hasCount = true;
            }

            if (!hasCount) {
                mob.addTag(NPCShadowInjector.TAG_COUNT_PREFIX + 1);
            }

            // Ensure mode tag is present based on the section
            switch (section) {
                case "append" -> {
                        mob.addTag(NPCShadowInjector.TAG_MODE_APPEND);
                }
                case "replace" -> {
                    mob.addTag(NPCShadowInjector.TAG_MODE_REPLACE);
                }
                default -> {
                   Shadowedhearts.LOGGER.info("RCT Trainer Battle: defaulting to convert");
                }
            }
        } catch (Throwable ignored) {
            ignored.printStackTrace();
            // Be fail-safe: never block battles if anything goes wrong here.
        }
    }
    private String resolveTrainerIdentifier(Object tmd) {
        if (tmd == null) return null;
        try {
            // Known possibilities via reflection without hard dependency on API signatures
            String[] methods = {"getId", "id", "getIdentifier", "getTrainerId", "getName"};
            for (String m : methods) {
                try {
                    var mm = tmd.getClass().getMethod(m);
                    Object val = mm.invoke(tmd);
                    if (val == null) continue;
                    if (val instanceof String s) return s;
                    return val.toString();
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
