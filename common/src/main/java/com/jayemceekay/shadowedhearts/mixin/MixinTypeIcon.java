package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import static com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk;

@Mixin(value = TypeIcon.class, remap = false)
public class MixinTypeIcon {

    @Final
    @Shadow
    private boolean small;
    @Final
    @Shadow
    private ElementalType type;
    @Final
    @Shadow
    private float opacity;
    @Final
    @Shadow
    private boolean centeredX;

    @Unique
    private float SCALE = 0.5f;

    @WrapMethod(method = "render")
    public void shadowedhearts$renderShadowIcon(GuiGraphics context, Operation<Void> original) {

        if(type == ElementalTypes.INSTANCE.get("shadow")) {
            int diameter = this.small ? 36 / 2 : 36;
            int offsetX = this.centeredX ? diameter / 2 : 0;
            // Use small icon asset when the TypeIcon is flagged as small
            var texture = ResourceLocation.fromNamespaceAndPath(
                    Shadowedhearts.MOD_ID,
                    this.small ? "textures/gui/shadow_type_small.png" : "textures/gui/shadow_type.png"
            );
            var textureWidth = diameter;
            float alpha = this.opacity;
            int x = ((TypeIcon)(Object)this).getX().intValue();
            int y = ((TypeIcon)(Object)this).getY().intValue();
            blitk(context.pose(), texture, (x - offsetX) / SCALE, y / SCALE, diameter, diameter, 0, 0,  textureWidth, textureWidth,0,1,1,1, alpha,true, SCALE);
        } else if (type == ElementalTypes.INSTANCE.get("shadow-locked")){
            int diameter = this.small ? 36 / 2 : 36;
            int offsetX = this.centeredX ? diameter / 2 : 0;
            // Use the small disabled icon when TypeIcon is small
            var texture = ResourceLocation.fromNamespaceAndPath(
                    Shadowedhearts.MOD_ID,
                    this.small ? "textures/gui/disabled_move_small.png" : "textures/gui/disabled_move.png"
            );
            var textureWidth = diameter;
            float alpha = this.opacity;
            int x = ((TypeIcon)(Object)this).getX().intValue();
            int y = ((TypeIcon)(Object)this).getY().intValue();
            blitk(context.pose(), texture, (x - offsetX) / SCALE, y / SCALE, diameter, diameter, 0, 0,  textureWidth, textureWidth,0,1,1,1, alpha,true, SCALE);
        }else {
            original.call(context);
        }
    }
}
