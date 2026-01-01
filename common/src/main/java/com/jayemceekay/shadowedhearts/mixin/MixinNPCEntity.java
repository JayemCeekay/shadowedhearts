package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.jayemceekay.shadowedhearts.AspectHolder;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

@Mixin(NPCEntity.class)
public abstract class MixinNPCEntity implements AspectHolder {
    @Override
    public Set<String> shadowedhearts$getAspects() {
        return ((NPCEntity) (Object) this).getAppliedAspects();
    }

    @Override
    public void shadowedhearts$setAspects(Set<String> aspects) {
        NPCEntity npc = (NPCEntity) (Object) this;
        npc.getAppliedAspects().clear();
        npc.getAppliedAspects().addAll(aspects);
        npc.updateAspects();
    }

    @Override
    public void shadowedhearts$addAspect(String aspect) {
        NPCEntity npc = (NPCEntity) (Object) this;
        npc.getAppliedAspects().add(aspect);
        npc.updateAspects();
    }
}
