package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.AspectHolder;
import com.jayemceekay.shadowedhearts.SyncedEntityAspects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements AspectHolder {

    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void shadowedhearts$defineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        SyncedEntityAspects.define(builder);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void shadowedhearts$addAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        Set<String> aspects = shadowedhearts$getAspects();
        if (!aspects.isEmpty()) {
            ListTag list = new ListTag();
            for (String aspect : aspects) {
                list.add(StringTag.valueOf(aspect));
            }
            compound.put("shadowedhearts:aspects", list);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void shadowedhearts$readAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("shadowedhearts:aspects", Tag.TAG_LIST)) {
            ListTag list = compound.getList("shadowedhearts:aspects", Tag.TAG_STRING);
            Set<String> aspects = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                aspects.add(list.getString(i));
            }
            shadowedhearts$setAspects(aspects);
        }
    }

    @Override
    public Set<String> shadowedhearts$getAspects() {
        return ((LivingEntity) (Object) this).getEntityData().get(SyncedEntityAspects.ASPECTS);
    }

    @Override
    public void shadowedhearts$setAspects(Set<String> aspects) {
        ((LivingEntity) (Object) this).getEntityData().set(SyncedEntityAspects.ASPECTS, aspects);
    }

    @Override
    public void shadowedhearts$addAspect(String aspect) {
        Set<String> aspects = new HashSet<>(shadowedhearts$getAspects());
        aspects.add(aspect);
        shadowedhearts$setAspects(aspects);
    }
}
