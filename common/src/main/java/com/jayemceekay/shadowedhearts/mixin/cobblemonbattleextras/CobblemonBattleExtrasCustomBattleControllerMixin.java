package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import name.modid.client.CustomBattleController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

// Only for >=1.7.41
// CustomBattleController manages the rendering of a few things:
// * The tooptip that appears when hovering a Pokémon on the "Switch Pokémon" menu.
// * The rendering of the custom Move Tiles when "enableCustomMoveTiles" is true in the config
@Mixin(CustomBattleController.class)
public class CobblemonBattleExtrasCustomBattleControllerMixin {

    @Unique
    private static boolean shadowedhearts$shouldMask(String m, Pokemon pokemon) {
        if (m == null || pokemon == null) return false;
        if (ShadowGate.isShadowMoveId(m)) return false; // Shadow moves always visible

        // Compute this move's index among non-Shadow moves in move order
        int nonShadowIndex = 0;
        int allowed = PokemonAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        for (var mv : pokemon.getMoveSet().getMovesWithNulls()) {
            if (mv == null) continue;
            if (ShadowGate.isShadowMoveId(mv.getName())) continue;
            if (mv.getName().equals(m)) {
                // If this move's position is at or beyond allowed, mask it
                return nonShadowIndex >= allowed;
            }
            nonShadowIndex++;
        }
        return false;
    }

    /*
    Switch Pokémon tooltip
     */

    // Mask attack details and change type in Switch Pokémon tooltip
    @Inject(
            method = "renderSwitchMovesTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lname/modid/client/CustomBattleController;renderClassicSwitchMovesTooltip(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/List;Ljava/lang/String;Ljava/lang/Integer;Ljava/util/List;II)V"
            )
    )
    private static void shadowedhearts$maskAttackDetailsSwitchMenu(CallbackInfo ci, @Local(name = "pokemon") Object pokemon, @Local(name = "moveInfos") List<?> moveInfos, @Local(name = "movePreviewLines") LocalRef<List<Component>> movePreviewLines) {
        List<Component> originalPreviewLines = movePreviewLines.get();

        for (int i = 0; i < moveInfos.size(); i++) {
            CobblemonBattleExtrasMoveDisplayInfoAccessor moveInfo = (CobblemonBattleExtrasMoveDisplayInfoAccessor) moveInfos.get(i);
            Component previewLine = originalPreviewLines.get(i);

            String moveId = moveInfo.shadowedhearts$getRawName();

            if (shadowedhearts$shouldMask(moveId, (Pokemon) pokemon)) {
                moveInfo.shadowedhearts$setDisplayName("????");
                moveInfo.shadowedhearts$setPpText("??/??");
                moveInfo.shadowedhearts$setPpColor(16777215); // White
                moveInfo.shadowedhearts$setTypeTextureX(0); // This is needed for the custom shadow icon to render correctly
                moveInfo.shadowedhearts$setMoveType(ElementalTypes.get("shadow-locked"));
                originalPreviewLines.set(i, null);
            } else {
                if (ShadowGate.isShadowMoveId(moveId))
                    moveInfo.shadowedhearts$setTypeTextureX(0); // This is needed for the custom shadow icon to render correctly

                originalPreviewLines.set(i, previewLine);
            }
        }
    }

    // Render Shadow icon in Switch Pokémon tooltip
    @WrapOperation(method = "renderClassicSwitchMovesTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"))
    private static void shadowedhearts$renderShadowIconSwitchMenu(GuiGraphics instance, ResourceLocation resourceLocation, int x, int y, int renderSizeX, int renderSizeY, float texU, float texV, int uvWidth, int uvHeight, int imgSizeX, int imgSizeY, Operation<Void> original, @Local(name = "moveInfos") List<?> moveInfos, @Local(name = "i") int index) {
        CobblemonBattleExtrasMoveDisplayInfoAccessor moveInfo = (CobblemonBattleExtrasMoveDisplayInfoAccessor) moveInfos.get(index);
        ElementalType type = (ElementalType) moveInfo.shadowedhearts$getMoveType();

        // The original size is the wide of a spritesheet containing all type icons.
        // If we are to render the shadow icon, we need to change it to the same as "imgSizeX", which is the vertical size of the image
        int size = imgSizeX;
        if (type.equals(ElementalTypes.get("shadow"))) {
            resourceLocation = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/shadow_type.png");
            size = imgSizeY;
        } else if (type.equals(ElementalTypes.get("shadow-locked"))) {
            resourceLocation = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/disabled_move.png");
            size = imgSizeY;
        }

        original.call(instance, resourceLocation, x, y, renderSizeX, renderSizeY, texU, texV, uvWidth, uvHeight, size, imgSizeY);
    }

    /*
    Custom move tiles
     */

    // Modify MoveTileVisualData for Custom Tiles
    @WrapOperation(method = "renderCustomMoveTiles", at = @At(value = "INVOKE", target = "Lname/modid/client/CustomBattleController;resolveMoveTileVisualData(Ljava/lang/Object;)Lname/modid/client/CustomBattleController$MoveTileVisualData;"))
    private static @Coerce Object shadowedhearts$shadowMoveTileVisualData(Object tile, Operation<Object> original) {
        Object originalObj = original.call(tile);

        String moveName = "";
        Field moveTemplateField = shadowedhearts$findFieldInHierarchy(tile.getClass(), "moveTemplate");
        try {
            Object moveTemplateObject = null;
            if (moveTemplateField != null) {
                moveTemplateField.setAccessible(true);
                moveTemplateObject = moveTemplateField.get(tile);
            }
            if (moveTemplateObject instanceof MoveTemplate moveTemplate) {
                moveTemplateField.setAccessible(true);
                moveName = moveTemplate.getName();
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        Object pokemonObject = null;
        Field pokemonField = shadowedhearts$findFieldInHierarchy(tile.getClass(), "pokemon");
        if (pokemonField != null) {
            pokemonField.setAccessible(true);
            try {
                pokemonObject = pokemonField.get(tile);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            if (shadowedhearts$shouldMask(moveName, (Pokemon) pokemonObject)) {
                CobblemonBattleExtrasMoveTileVisualDataAccessor tileVisualDataExt = (CobblemonBattleExtrasMoveTileVisualDataAccessor) originalObj;

                // Create a new instance with the new data
                Class<?> clazz = Class.forName("name.modid.client.CustomBattleController$MoveTileVisualData");
                Constructor<?> ctor = clazz.getDeclaredConstructor(
                        String.class, int.class, int.class, boolean.class, String.class, int.class, String.class, int.class, int.class
                );
                ctor.setAccessible(true);

                return ctor.newInstance(
                        "????", // moveName
                        tileVisualDataExt.shadowedhearts$getCurrentPp(),
                        tileVisualDataExt.shadowedhearts$getMaxPp(),
                        false, // selectable
                        "???", // categoryKey
                        0, // power
                        "shadow-locked", //moveTypeName
                        522133503, // typeColor; Same color from MixinMoveSlotWidget, but in int RGBA format
                        0 // typeTextureMultiplier
                );
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return originalObj;
    }

    // Copied from CustomBattleController
    @Unique
    private static Field shadowedhearts$findFieldInHierarchy(Class<?> startClass, String fieldName) {
        for (Class<?> currentClass = startClass; currentClass != null && currentClass != Object.class; currentClass = currentClass.getSuperclass()) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    // Render shadow icon in Custom Tiles
    @WrapOperation(method = "renderCustomMoveTile", at = @At(value = "INVOKE", target = "Lname/modid/client/CustomBattleController;renderMoveTypeBadgeIcon(Lnet/minecraft/client/gui/GuiGraphics;Lname/modid/client/CustomBattleController$MoveTileVisualData;IIF)V"))
    private static void shadowedhearts$renderShadowIcon(GuiGraphics graphics, @Coerce Object data, int x, int y, float opacity, Operation<Void> original) {
        CobblemonBattleExtrasMoveTileVisualDataAccessor tileVisualDataExt = (CobblemonBattleExtrasMoveTileVisualDataAccessor) data;
        if (tileVisualDataExt.shadowedhearts$getMoveTypeName().equals("shadow")) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/shadow_type.png");
            graphics.blit(texture, x, y, 12, 12, 0.0F, 0.0F, 36, 36, 36, 36);
            return;
        } else if (tileVisualDataExt.shadowedhearts$getMoveTypeName().equals("shadow-locked")) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/disabled_move.png");
            graphics.blit(texture, x, y, 12, 12, 0.0F, 0.0F, 36, 36, 36, 36);
            return;
        }
        original.call(graphics, data, x, y, opacity);
    }

    // Create custom CategoryChipStyle with ??? data.
    // Both "Object" are CategoryChipStyle.
    @WrapOperation(method = "renderCustomMoveTile", at = @At(value = "INVOKE", target = "Lname/modid/client/CustomBattleController;resolveMoveCategoryChipStyle(Ljava/lang/String;)Lname/modid/client/CustomBattleController$CategoryChipStyle;"))
    private static @Coerce Object shadowedhearts$resolveChipStyle(String categoryKey, Operation<Object> original) {
        try {
            Class<?> clazz = Class.forName("name.modid.client.CustomBattleController$CategoryChipStyle");
            Constructor<?> ctor = clazz.getDeclaredConstructor(
                    String.class, int.class, int.class, int.class, int.class, int.class
            );
            ctor.setAccessible(true);

            // Same values as STA, copied from resolveMoveCategoryChipStyle
            return ctor.newInstance(
                    "???", // label
                    16, // width
                    -8683114, // bgTop
                    -11907231, // bgBottom
                    -3814695, // border
                    -1 // text
            );
        } catch (Throwable e) {
            //throw new RuntimeException(e);
            return original.call(categoryKey);
        }
    }
}
