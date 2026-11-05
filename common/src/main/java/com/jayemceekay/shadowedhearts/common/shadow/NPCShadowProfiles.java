package com.jayemceekay.shadowedhearts.common.shadow;

import com.jayemceekay.shadowedhearts.AspectHolder;
import com.jayemceekay.shadowedhearts.data.ShadowAspectPresets;
import com.jayemceekay.shadowedhearts.data.ShadowPools;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTShadowRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.*;

/** Shared read-only resolution for battle injection and operator inspection. */
public final class NPCShadowProfiles {
    private NPCShadowProfiles() { }

    public static NPCShadowProfile.Resolution resolve(Entity entity) {
        MinecraftServer server = entity.level().getServer();
        var rct = RCTShadowRules.resolve(entity);
        Set<String> authored = new HashSet<>(entity.getTags());
        if (entity instanceof AspectHolder holder) authored.addAll(holder.shadowedhearts$getAspects());
        List<Component> errors = new ArrayList<>(rct.diagnostics());
        var resolved = NPCShadowProfile.resolve(List.of(
                new NPCShadowProfile.Layer("RCT defaults", rct.defaults()),
                new NPCShadowProfile.Layer("RCT rule", rct.tags()),
                new NPCShadowProfile.Layer("NPC tags/aspects", authored)), id -> {
            var resourceId = ResourceLocation.parse(id);
            errors.addAll(ShadowAspectPresets.diagnostics(server, resourceId));
            return ShadowAspectPresets.get(server, resourceId);
        });
        errors.addAll(resolved.errors());
        var profile = resolved.profile();
        if (profile.poolId() != null) {
            var poolId = ResourceLocation.parse(profile.poolId());
            errors.addAll(ShadowPools.diagnostics(server, poolId));
            if (ShadowPools.get(server, poolId).isEmpty()) errors.add(Component.translatable("message.shadowedhearts.npc_profile.pool_missing", poolId.toString()));
        }
        return new NPCShadowProfile.Resolution(profile, new ArrayList<>(new LinkedHashSet<>(errors)),
                resolved.warnings(), resolved.sources(), rct.description());
    }

    public static boolean isShadowTag(String tag) { return NPCShadowProfile.isShadowTag(tag); }
    public static String canonicalId(String id) { return NPCShadowProfile.canonicalId(id); }
    public static Component validateTag(String tag) { return NPCShadowProfile.validateTag(tag); }
}
