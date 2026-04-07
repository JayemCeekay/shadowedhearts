package com.jayemceekay.shadowedhearts.client.gui;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.api.text.TextKt;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import com.cobblemon.mod.common.client.render.RenderHelperKt;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokedex.scanner.PokedexEntityData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.util.LocalizationUtilsKt;
import com.jayemceekay.fluxui.hud.animation.Animator;
import com.jayemceekay.fluxui.hud.core.HudContext;
import com.jayemceekay.fluxui.hud.core.HudNode;
import com.jayemceekay.fluxui.hud.layout.Anchor;
import com.jayemceekay.fluxui.hud.layout.LayoutNode;
import com.jayemceekay.fluxui.hud.state.StateBindings;
import com.jayemceekay.fluxui.hud.widgets.*;
import com.jayemceekay.shadowedhearts.common.aura.PokedexUsageContext;
import com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.List;

public class AuraScannerHudFactory {
    public static final ResourceLocation SCAN_RING_OUTER = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/scan_ring_outer.png");
    public static final ResourceLocation SCAN_RING_MIDDLE = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/scan_ring_middle.png");
    public static final ResourceLocation SCAN_RING_INNER = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/scan_ring_inner.png");
    public static final ResourceLocation SCAN_SCREEN = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/pokedex_screen_scan.png");
    public static final ResourceLocation SCAN_OVERLAY_CORNERS = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/overlay_corners.png");
    public static final ResourceLocation SCAN_OVERLAY_TOP = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/overlay_border_top.png");
    public static final ResourceLocation SCAN_OVERLAY_BOTTOM = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/overlay_border_bottom.png");
    public static final ResourceLocation SCAN_OVERLAY_LEFT = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/overlay_border_left.png");
    public static final ResourceLocation SCAN_OVERLAY_RIGHT = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/overlay_border_right.png");
    public static final ResourceLocation SCAN_OVERLAY_LINES = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/overlay_scanlines.png");
    public static final ResourceLocation SCAN_OVERLAY_NOTCH = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/overlay_notch.png");
    public static final ResourceLocation CENTER_INFO_FRAME = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/scan_info_frame.png");
    public static final ResourceLocation UNKNOWN_MARK = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/scan_unknown.png");
    public static final ResourceLocation POINTER = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/pointer.png");
    public static final ResourceLocation TOOLTIP_EDGE = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/tooltip_edge.png");
    public static final ResourceLocation TOOLTIP_BACKGROUND = com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/tooltip_background.png");

    public static final int CENTER_INFO_FRAME_WIDTH = 128;
    public static final int CENTER_INFO_FRAME_HEIGHT = 16;
    public static final int OUTER_INFO_FRAME_WIDTH = 92;
    public static final int OUTER_INFO_FRAME_HEIGHT = 55;
    public static final int INNER_INFO_FRAME_WIDTH = 120;
    public static final int INNER_INFO_FRAME_HEIGHT = 20;
    public static final int INNER_INFO_FRAME_STEM_WIDTH = 28;
    public static final int SCAN_OVERLAY_NOTCH_WIDTH = 200;

    public static ResourceLocation infoFrameResource(boolean isLeft, int tier) {
        return com.cobblemon.mod.common.util.MiscUtilsKt.cobblemonResource("textures/gui/pokedex/scan/info_frame_" + (isLeft ? "left" : "right") + "_" + tier + ".png");
    }

    public static net.minecraft.network.chat.MutableComponent getRegisterText(com.cobblemon.mod.common.api.pokedex.PokedexLearnedInformation info) {
        if (info == com.cobblemon.mod.common.api.pokedex.PokedexLearnedInformation.SPECIES) {
            return com.cobblemon.mod.common.util.LocalizationUtilsKt.lang("ui.pokedex.scan.registered_pokemon");
        } else {
            return com.cobblemon.mod.common.util.LocalizationUtilsKt.lang("ui.pokedex.scan.registered_aspect");
        }
    }

    public static HudNode create(AuraScannerHudState state, Animator animator) {
        GroupNode root = new GroupNode();
        StateBindings.bindVisible(root, state.slideOffsetY.map(y -> y > AuraScannerHudState.SLIDE_HIDDEN_Y + 1f));
        StateBindings.bindFloat(root::setY, state.slideOffsetY);
        StateBindings.bindOpacity(root, state.fadeAmount);

        // Jitter for glitch effects
        JitterNode glitchWrapper = new JitterNode();
        StateBindings.bindJitter(glitchWrapper, state.glitchTimer);
        root.addChild(glitchWrapper);

        // Mode Switcher
        SwitchNode<AuraReaderManager.AuraScannerMode> modeSwitch = new SwitchNode<>();
        StateBindings.bindSwitch(modeSwitch, state.mode);
        glitchWrapper.addChild(modeSwitch);

        // --- AURA READER MODE ---
        GroupNode auraReaderGroup = createAuraReaderMode(state, animator);
        modeSwitch.addCase(AuraReaderManager.AuraScannerMode.AURA_READER, auraReaderGroup);

        // --- POKEDEX SCANNER MODE ---
        GroupNode pokedexScannerGroup = createPokedexScannerMode(state, animator);
        modeSwitch.addCase(AuraReaderManager.AuraScannerMode.POKEDEX_SCANNER, pokedexScannerGroup);

        // --- DOWSING MACHINE MODE ---
        GroupNode dowsingMachineGroup = createDowsingMachineMode(state, animator);
        modeSwitch.addCase(AuraReaderManager.AuraScannerMode.DOWSING_MACHINE, dowsingMachineGroup);

        // --- COMMON OVERLAYS ---
        // Energy Bar
        LayoutNode energyBarLayout = new LayoutNode();
        energyBarLayout.setAnchor(Anchor.BOTTOM_CENTER);
        energyBarLayout.setOffsetY(-40f);
        BarMeterNode energyBar = new BarMeterNode();
        energyBar.setWidth(100f);
        energyBar.setHeight(4f);
        energyBar.setX(-50f);
        StateBindings.bindFloat(energyBar::setFillAmount, state.charge.map(c -> c / (float) com.jayemceekay.shadowedhearts.content.items.AuraReaderItem.MAX_CHARGE));
        energyBarLayout.addChild(energyBar);
        glitchWrapper.addChild(energyBarLayout);

        // Radial Menu moved to AuraReaderModeScreen
        // setupRadialMenu(glitchWrapper, state);

        return root;
    }

    private static void setupRadialMenu(HudNode parent, AuraScannerHudState state) {
        LayoutNode menuLayout = new LayoutNode();
        menuLayout.setAnchor(Anchor.CENTER);
        StateBindings.bindOpacity(menuLayout, state.modeMenuAlpha);
        parent.addChild(menuLayout);

        // Aura Reader (Top)
        addModeNode(menuLayout, 0, -80, "aura_scanner.mode.aura_reader", AuraReaderManager.AuraScannerMode.AURA_READER, state);
        // Pokedex Scanner (Bottom Left)
        addModeNode(menuLayout, -80, 60, "aura_scanner.mode.pokedex_scanner", AuraReaderManager.AuraScannerMode.POKEDEX_SCANNER, state);
        // Dowsing Machine (Bottom Right)
        addModeNode(menuLayout, 80, 60, "aura_scanner.mode.dowsing_machine", AuraReaderManager.AuraScannerMode.DOWSING_MACHINE, state);
    }

    private static void addModeNode(LayoutNode parent, float x, float y, String translationKey, AuraReaderManager.AuraScannerMode mode, AuraScannerHudState state) {
        PokedexTooltipNode text = new PokedexTooltipNode(net.minecraft.network.chat.Component.translatable(translationKey), 0);
        text.setX(x);
        text.setY(y);

        Runnable apply = () -> {
            AuraReaderManager.AuraScannerMode selected = state.mode.get();
            AuraReaderManager.AuraScannerMode hovered = state.hoveredMode.get();
            int baseRGB = (selected == mode) ? 0xFFA500 /* orange */ : 0x00FFFF /* cyan */;
            int rgb = (hovered == mode) ? 0xFFFFFF /* white on hover */ : baseRGB;
            text.setBaseColor(baseRGB);
            // Apply the current visual color immediately
            text.getText().setStyle(text.getText().getStyle().withColor(rgb));
        };

        state.mode.subscribe((o, n) -> apply.run());
        state.hoveredMode.subscribe((o, n) -> apply.run());
        apply.run();

        parent.addChild(text);
    }

    private static class WaveformNode extends HudNode {
        private final AuraScannerHudState state;
        private final int color;

        public WaveformNode(AuraScannerHudState state, int color) {
            this.state = state;
            this.color = color;
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            float intensity = state.maxIntensity.get();
            if (intensity <= 0) return;

            float alpha = state.fadeAmount.get();
            int textAlpha = (int) (alpha * 255) << 24;
            float partialTick = ctx.partialTick();
            float waveTime = (Minecraft.getInstance().level.getGameTime() + partialTick) * 0.5f;

            for (int i = 0; i < 20; i++) {
                float h = (float) Math.sin(waveTime + i * 0.5f) * 10 * intensity;
                if (state.glitchTimer.get() > 0)
                    h *= Minecraft.getInstance().level.random.nextFloat() * 2;
                graphics.fill(i * 5, 0, i * 5 + 2, -(int) h, (color & 0xFFFFFF) | (textAlpha / 2));
            }
        }
    }

    private static class DirectionalPointersNode extends HudNode {
        private final AuraScannerHudState state;
        private static final ResourceLocation POINTER = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/arrow_pointer_up.png");
        private static final ResourceLocation SELECT_ARROW = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/select_arrow.png");

        public DirectionalPointersNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            java.util.List<AuraScannerHudState.DirectionalPointer> pointers = state.directionalPointers.get();
            float alpha = state.fadeAmount.get();

            for (AuraScannerHudState.DirectionalPointer p : pointers) {
                graphics.pose().pushPose();
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(p.angle()));

                if (p.isLocked()) {
                    RenderSystem.setShaderColor(1.0f, 0.4f, 1.0f, Math.max(0.6f, alpha * p.intensity()));
                } else if (p.isSelected()) {
                    RenderSystem.setShaderColor(0.9f, 0.6f, 1.0f, Math.max(0.5f, alpha * p.intensity()));
                } else if (p.isMeteoroid()) {
                    RenderSystem.setShaderColor(0.8f, 0.2f, 0.9f, alpha * p.intensity());
                } else {
                    RenderSystem.setShaderColor(0.6f, 0.3f, 1.0f, alpha * p.intensity());
                }

                graphics.blit(POINTER, -8, -70, 0, 0, 16, 6, 16, 16);

                if (p.isSelected() && state.lockedTarget.get() == null) {
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                    graphics.blit(SELECT_ARROW, -8, -82, 0, 0, 16, 16, 16, 16);
                }

                graphics.pose().popPose();
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static class InfoFramesNode extends HudNode {
        private final AuraScannerHudState state;

        public InfoFramesNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            PokedexUsageContext usageContext = state.pokedexUsageContext.get();
            if (usageContext == null) return;
            float alpha = state.fadeAmount.get();
            renderInfoFrames(graphics, graphics.pose(), usageContext, 0, 0, alpha);
        }

        public void renderInfoFrames(GuiGraphics graphics, PoseStack poseStack, PokedexUsageContext usageContext, int centerX, int centerY, float opacity) {
            if (usageContext.getFocusIntervals() > 0) {
                int infoDisplayedCounter = 0;
                for (int index = 0; index < usageContext.getAvailableInfoFrames().size(); index++) {
                    Boolean isLeftSide = usageContext.getAvailableInfoFrames().get(index);
                    if (isLeftSide != null) {
                        if (infoDisplayedCounter > 1 && !usageContext.isPokemonInFocusOwned())
                            continue;
                        infoDisplayedCounter++;
                        // Frames
                        boolean isInnerFrame = index == 1 || index == 2;
                        int frameHeight = isInnerFrame ? INNER_INFO_FRAME_HEIGHT : OUTER_INFO_FRAME_HEIGHT;
                        int xOffset = (isInnerFrame ? (-177) : (-120)) + (isLeftSide ? 0 : (isInnerFrame ? 234 : 148));
                        int yOffset = switch (index) {
                            case 0 -> -80;
                            case 1 -> -26;
                            case 2 -> 6;
                            case 3 -> 25;
                            default -> 0;
                        };
                        GuiUtilsKt.blitk(
                                poseStack,
                                infoFrameResource(isLeftSide, index),
                                centerX + xOffset,
                                centerY + yOffset,
                                frameHeight,
                                !isInnerFrame ? OUTER_INFO_FRAME_WIDTH : INNER_INFO_FRAME_WIDTH,
                                0,
                                (int) (Math.ceil(usageContext.getFocusIntervals()) * frameHeight),
                                !isInnerFrame ? OUTER_INFO_FRAME_WIDTH : INNER_INFO_FRAME_WIDTH,
                                frameHeight * 10,
                                0, 1, 1, 1, opacity, true, 1F
                        );

                        int xOffsetText = isInnerFrame ? (((INNER_INFO_FRAME_WIDTH - INNER_INFO_FRAME_STEM_WIDTH) / 2) + (isLeftSide ? 0 : INNER_INFO_FRAME_STEM_WIDTH)) : (OUTER_INFO_FRAME_WIDTH / 2);
                        int yOffsetText = switch (index) {
                            case 0 -> 5;
                            case 1 -> 4;
                            case 2 -> 8;
                            case 3 -> 42;
                            default -> 0;
                        };

                        // Text
                        if (usageContext.getFocusIntervals() == PokedexUsageContext.FOCUS_INTERVALS && usageContext.getScannableEntityInFocus() != null) {
                            PokedexEntityData pokedexEntityData = usageContext.getScannableEntityInFocus().resolvePokemonScan();
                            if (pokedexEntityData == null) continue;

                            if (infoDisplayedCounter == 1) {
                                RenderHelperKt.drawScaledText(
                                        graphics,
                                        CobblemonResources.INSTANCE.getDEFAULT_LARGE(),
                                        TextKt.bold(LocalizationUtilsKt.lang("ui.lv.number", pokedexEntityData.getPokemon().getLevel())),
                                        centerX + xOffset + xOffsetText,
                                        centerY + yOffset + yOffsetText,
                                        1F, 1F, Integer.MAX_VALUE, 0xFFFFFFFF, true, true, null, null
                                );
                            }

                            if (infoDisplayedCounter == 2) {
                                boolean hasTrainer = false;
                                Object resolvedEntity = usageContext.getScannableEntityInFocus().resolveEntityScan();
                                if (resolvedEntity instanceof PokemonEntity pokemonEntity) {
                                    hasTrainer = pokemonEntity.getOwnerUUID() != null;
                                }

                                MutableComponent speciesName = TextKt.bold(pokedexEntityData.getApparentSpecies().getTranslatedName());
                                int yOffsetName = hasTrainer ? 2 : 0;
                                if (hasTrainer) {
                                    RenderHelperKt.drawScaledText(
                                            graphics,
                                            null,
                                            LocalizationUtilsKt.lang("ui.pokedex.scan.trainer_owned"),
                                            centerX + xOffset + xOffsetText,
                                            centerY + yOffset + yOffsetText - yOffsetName,
                                            0.5F, 1F, Integer.MAX_VALUE, 0xFFFFFFFF, true, true, null, null
                                    );
                                }
                                RenderHelperKt.drawScaledText(
                                        graphics,
                                        CobblemonResources.INSTANCE.getDEFAULT_LARGE(),
                                        speciesName,
                                        centerX + xOffset + xOffsetText,
                                        centerY + yOffset + yOffsetText + yOffsetName,
                                        1F, 1F, Integer.MAX_VALUE, 0xFFFFFFFF, true, true, null, null
                                );

                                Gender gender = pokedexEntityData.getPokemon().getGender();
                                int speciesNameWidth = Minecraft.getInstance().font.width(TextKt.font(speciesName, CobblemonResources.INSTANCE.getDEFAULT_LARGE()));
                                if (gender != Gender.GENDERLESS) {
                                    boolean isMale = gender == Gender.MALE;
                                    MutableComponent textSymbol = TextKt.bold(net.minecraft.network.chat.Component.literal(isMale ? "♂" : "♀"));
                                    RenderHelperKt.drawScaledText(
                                            graphics,
                                            CobblemonResources.INSTANCE.getDEFAULT_LARGE(),
                                            textSymbol,
                                            centerX + xOffset + xOffsetText + 2 + (speciesNameWidth / 2),
                                            centerY + yOffset + yOffsetText + yOffsetName,
                                            1F, 1F, Integer.MAX_VALUE, isMale ? 0xFF32CBFF : 0xFFFC5454, false, true, null, null
                                    );
                                }

                                if (usageContext.isPokemonInFocusOwned()) {
                                    GuiUtilsKt.blitk(
                                            poseStack,
                                            BattleOverlay.Companion.getCaughtIndicator(),
                                            (centerX + xOffset + xOffsetText - 7 - (speciesNameWidth / 2)) / BattleOverlay.SCALE,
                                            (centerY + yOffset + yOffsetText + yOffsetName + 2) / BattleOverlay.SCALE,
                                            10, 10, 0, 0, 10, 10, 0, 1, 1, 1, 1F, true, BattleOverlay.SCALE
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static class ScanlinesNode extends HudNode {
        private final AuraScannerHudState state;

        public ScanlinesNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        public void resolveLayout(HudContext ctx) {
            this.width = ctx.screenWidth();
            this.height = ctx.screenHeight();
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            float alpha = state.fadeAmount.get();
            double interlacePos = Math.ceil((state.usageIntervals % 14) * 0.5) * 0.5;
            for (int i = 0; i < height; i++) {
                if (i % 4 == 0) {
                    GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_LINES, 0, i - interlacePos, 4, width, 0, 0, 1, 4, 0, 1, 1, 1, opacity, true, 1F);
                }
            }
        }
    }

    private static class ThreeDItemNode extends HudNode {
        private final ItemStack stack;
        private final AuraScannerHudState state;

        public ThreeDItemNode(ItemStack stack, AuraScannerHudState state) {
            this.stack = stack;
            this.state = state;
            this.width = 16;
            this.height = 16;
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            if (stack.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            Quaternionf rotation = state.dowsingArrowRotation.get();
            float alpha = state.fadeAmount.get();

            graphics.pose().pushPose();
            graphics.pose().scale(25, -25, 25);
            graphics.pose().mulPose(rotation);

            net.minecraft.client.resources.model.BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);

            com.mojang.blaze3d.platform.Lighting.setupForFlatItems();
            mc.getItemRenderer().render(
                    stack,
                    net.minecraft.world.item.ItemDisplayContext.GUI,
                    false,
                    graphics.pose(),
                    graphics.bufferSource(),
                    0xF000F0,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    model
            );
            graphics.flush();
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            graphics.pose().popPose();
        }
    }

    private static class CenterRegistrationNode extends HudNode {
        private final AuraScannerHudState state;

        public CenterRegistrationNode(AuraScannerHudState state) {
            this.state = state;
            this.width = CENTER_INFO_FRAME_WIDTH;
            this.height = CENTER_INFO_FRAME_HEIGHT;
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            PokedexUsageContext usageContext = state.pokedexUsageContext.get();
            if (usageContext == null || usageContext.getDisplayRegisterInfoIntervals() <= 0)
                return;

            float alpha = state.fadeAmount.get();
            int vOffset = (int) (Math.min(Math.ceil(usageContext.getDisplayRegisterInfoIntervals()), com.jayemceekay.shadowedhearts.common.aura.PokedexUsageContext.CENTER_INFO_DISPLAY_INTERVALS) * height);

            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(
                    graphics.pose(),
                    CENTER_INFO_FRAME,
                    (int) -width / 2,
                    (int) -height / 2,
                    (int) height,
                    (int) width,
                    0,
                    vOffset,
                    (int) width,
                    (int) height * 6,
                    0, 1, 1, 1, alpha, true, 1F
            );

            if (usageContext.getDisplayRegisterInfoIntervals() >= com.jayemceekay.shadowedhearts.common.aura.PokedexUsageContext.CENTER_INFO_DISPLAY_INTERVALS) {
                com.cobblemon.mod.common.client.render.RenderHelperKt.drawScaledText(
                        graphics,
                        com.cobblemon.mod.common.client.CobblemonResources.INSTANCE.getDEFAULT_LARGE(),
                        getRegisterText(usageContext.getNewPokemonInfo()),
                        0,
                        (int) (-height / 2 + 4),
                        1F, 1F, Integer.MAX_VALUE, 0xFFFFFFFF, true, true, null, null
                );
            }
        }
    }

    private static class PokedexTooltipNode extends HudNode {
        private final MutableComponent text;
        private final int offsetY;
        private int baseColor = 0x00FFFF;

        public PokedexTooltipNode(MutableComponent text, int offsetY) {
            this.text = text;
            this.offsetY = offsetY;
        }

        public void setBaseColor(int color) {
            this.baseColor = color;
            text.setStyle(text.getStyle().withColor(baseColor));
        }

        public MutableComponent getText() {
            return text;
        }

        @Override
        public void resolveLayout(HudContext ctx) {
            int textWidth = Minecraft.getInstance().font.width(
                    com.cobblemon.mod.common.api.text.TextKt.font(text, com.cobblemon.mod.common.client.CobblemonResources.INSTANCE.getDEFAULT_LARGE())
            );
            this.width = textWidth + 4;
            this.height = Minecraft.getInstance().font.lineHeight; // typically 9 or 10
        }

        @Override
        public boolean contains(double mouseX, double mouseY) {
            float halfWidth = width / 2f;
            int tooltipTop = offsetY + 1;
            return mouseX >= -halfWidth && mouseX <= halfWidth &&
                    mouseY >= tooltipTop && mouseY <= tooltipTop + height;
        }

        @Override
        public boolean onMouseHovered(double mouseX, double mouseY) {
            if(!contains(mouseX, mouseY)) return false;
            text.setStyle(text.getStyle().withColor(0xFFFFFF));
            return true;
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            PoseStack poseStack = graphics.pose();
            int tooltipWidth = (int) width;
            int tooltipHeight = (int) height;
            int tooltipTop = offsetY + 1;

            poseStack.pushPose();
            poseStack.translate(0.0, 0.0, 1000.0);

            Minecraft mc = Minecraft.getInstance();
            mc.getMainRenderTarget().bindWrite(false);

            graphics.enableScissor(
                    (int) (this.getX() - (tooltipWidth / 2f)),
                    (int) (this.getY() + tooltipTop + 1),
                    (int) (this.getX() - (tooltipWidth / 2f) + tooltipWidth),
                    (int) (this.getY() + tooltipTop + tooltipHeight - 1)
            );

            graphics.disableScissor();
            mc.getMainRenderTarget().bindWrite(true);

            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(poseStack, TOOLTIP_EDGE, (int) -(tooltipWidth / 2f) - 1, tooltipTop, tooltipHeight, 1, 0, 0, 1, tooltipHeight, 0, 1, 1, 1, 1.0f, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(poseStack, TOOLTIP_BACKGROUND, (int) -(tooltipWidth / 2f), tooltipTop, tooltipHeight, tooltipWidth, 0, 0, tooltipWidth, tooltipHeight, 0, 1, 1, 1, 1.0f, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(poseStack, TOOLTIP_EDGE, (int) (tooltipWidth / 2f), tooltipTop, tooltipHeight, 1, 0, 0, 1, tooltipHeight, 0, 1, 1, 1, 1.0f, true, 1F);

            int rgb = 0xFFFFFF; // fallback
            net.minecraft.network.chat.TextColor styleColor = text.getStyle().getColor();
            if (styleColor != null) {
                rgb = styleColor.getValue(); // RRGGBB
            }
            int argb = 0xFF000000 | rgb; // make it opaque ARGB

            com.cobblemon.mod.common.client.render.RenderHelperKt.drawScaledText(
                    graphics,
                    com.cobblemon.mod.common.client.CobblemonResources.INSTANCE.getDEFAULT_LARGE(),
                    text,
                    0,
                    tooltipTop,
                    1F,
                    1F,
                    Integer.MAX_VALUE,
                    argb,
                    true,
                    true,
                    null,
                    null
            );
            poseStack.popPose();
        }
    }

    private static GroupNode createAuraReaderMode(AuraScannerHudState state, Animator animator) {
        GroupNode group = new GroupNode();

        // Scanlines
        group.addChild(new ScanlinesNode(state));

        // Borders & Notch
        LayoutNode bordersLayout = new LayoutNode();
        bordersLayout.setAnchor(Anchor.CENTER);
        bordersLayout.addChild(new PokedexBordersNode(state));
        group.addChild(bordersLayout);

        // Scan Rings
        group.addChild(createScanRings(state));

        // Reticle
        /*LayoutNode reticleLayout = new LayoutNode();
        reticleLayout.setAnchor(Anchor.CENTER);
        ReticleNode reticle = new ReticleNode();
        reticle.setRadius(25f);
        state.lockedTarget.subscribe((oldV, newV) -> {
            reticle.setLocked(newV != null);
            if (newV != null) {
                animator.animate(reticle.scaleXProperty(), 1.2f, new com.jayemceekay.fluxui.hud.animation.AnimationSpec(0.3f, com.jayemceekay.fluxui.hud.animation.Easing.SPRING));
                animator.animate(reticle.scaleYProperty(), 1.2f, new com.jayemceekay.fluxui.hud.animation.AnimationSpec(0.3f, com.jayemceekay.fluxui.hud.animation.Easing.SPRING));
            } else {
                animator.animate(reticle.scaleXProperty(), 1.0f, com.jayemceekay.fluxui.hud.animation.AnimationSpec.NORMAL);
                animator.animate(reticle.scaleYProperty(), 1.0f, com.jayemceekay.fluxui.hud.animation.AnimationSpec.NORMAL);
            }
        });
        reticle.setLocked(state.lockedTarget.get() != null);
        reticleLayout.addChild(reticle);
        group.addChild(reticleLayout);*/

        // Signal Strength Arc
        /*LayoutNode signalLayout = new LayoutNode();
        signalLayout.setAnchor(Anchor.CENTER);
        ProceduralArcNode signalMeter = new ProceduralArcNode();
        signalMeter.setRadius(65f);
        signalMeter.setThickness(4f);
        signalMeter.setStartAngle(135f);
        signalMeter.setEndAngle(225f);
        signalMeter.setSegments(10);
        signalMeter.setSegmentGap(2f);
        StateBindings.bindArcFill(signalMeter, state.signalStrength);
        signalLayout.addChild(signalMeter);
        group.addChild(signalLayout);*/

        // Directional Pointers
        /*LayoutNode pointersLayout = new LayoutNode();
        pointersLayout.setAnchor(Anchor.CENTER);
        pointersLayout.addChild(new DirectionalPointersNode(state));
        group.addChild(pointersLayout);*/

        // Signal Labels
        LayoutNode labelLayout = new LayoutNode();
        labelLayout.setAnchor(Anchor.CENTER);
        labelLayout.setOffsetY(-110f);
        TextNode signalLabel = new TextNode();
        signalLabel.setCentered(true);
        StateBindings.bindText(signalLabel, state.signalLabel);
        state.signalColor.subscribe((oldV, newV) -> signalLabel.setColor(newV));
        signalLabel.setColor(state.signalColor.get());
        labelLayout.addChild(signalLabel);

        TextNode infoLine = new TextNode();
        infoLine.setCentered(true);
        infoLine.setY(12f);
        StateBindings.bindText(infoLine, state.infoLine.map(s -> s == null ? "" : s));
        labelLayout.addChild(infoLine);
        group.addChild(labelLayout);

        // Signal Meter Bars (Bottom)
        /*LayoutNode barsLayout = new LayoutNode();
        barsLayout.setAnchor(Anchor.BOTTOM_CENTER);
        barsLayout.setOffsetY(-44f);

        BarMeterNode strBar = new BarMeterNode();
        strBar.setX(-85f);
        strBar.setWidth(80f);
        strBar.setHeight(11f);
        strBar.setColor(0xA330FF);
        StateBindings.bindFloat(strBar::setFillAmount, state.signalStrength);
        barsLayout.addChild(strBar);

        BarMeterNode intBar = new BarMeterNode();
        intBar.setX(5f);
        intBar.setWidth(80f);
        intBar.setHeight(11f);
        intBar.setColor(0x00FFFF);
        StateBindings.bindFloat(intBar::setFillAmount, state.interference);
        barsLayout.addChild(intBar);

        TextNode strLabel = new TextNode("STR");
        strLabel.setX(-83f);
        strLabel.setY(2f);
        barsLayout.addChild(strLabel);

        TextNode intLabel = new TextNode("INT");
        intLabel.setX(7f);
        intLabel.setY(2f);
        barsLayout.addChild(intLabel);

        group.addChild(barsLayout);*/

        // Waveform
        /*LayoutNode waveLayout = new LayoutNode();
        waveLayout.setAnchor(Anchor.CENTER);
        waveLayout.setX(-50f);
        waveLayout.setY(-65f);
        waveLayout.addChild(new WaveformNode(state, 0x00FFFF));
        group.addChild(waveLayout);*/

        // Info Frames
        LayoutNode infoFramesLayout = new LayoutNode();
        infoFramesLayout.setAnchor(Anchor.CENTER);
        infoFramesLayout.addChild(new InfoFramesNode(state));
        group.addChild(infoFramesLayout);

        // Calibration Overlay
        LayoutNode calibrationLayout = new LayoutNode();
        calibrationLayout.setAnchor(Anchor.CENTER);
        calibrationLayout.addChild(new CalibrationOverlayNode(state));
        StateBindings.bindVisible(calibrationLayout, state.calibrationActive);
        group.addChild(calibrationLayout);

        // Calibration Grade Display (shown briefly after completion)
        LayoutNode gradeLayout = new LayoutNode();
        gradeLayout.setAnchor(Anchor.CENTER);
        gradeLayout.setOffsetY(-50f);
        gradeLayout.addChild(new CalibrationGradeNode(state));
        StateBindings.bindVisible(gradeLayout, state.calibrationGradeDisplay.map(t -> t > 0));
        group.addChild(gradeLayout);

        // Node Event Overlay (evidence interpretation, search, wild, provocation)
        LayoutNode nodeEventLayout = new LayoutNode();
        nodeEventLayout.setAnchor(Anchor.CENTER);
        nodeEventLayout.addChild(new NodeEventOverlayNode(state));
        StateBindings.bindVisible(nodeEventLayout, state.nodeEventActive);
        group.addChild(nodeEventLayout);

        // Manifestation buildup overlay
        LayoutNode manifestationLayout = new LayoutNode();
        manifestationLayout.setAnchor(Anchor.CENTER);
        manifestationLayout.addChild(new ManifestationOverlayNode(state));
        StateBindings.bindVisible(manifestationLayout, state.manifestationActive);
        group.addChild(manifestationLayout);

        // Grade flash overlay (shown for all event types)
        LayoutNode gradeFlashLayout = new LayoutNode();
        gradeFlashLayout.setAnchor(Anchor.CENTER);
        gradeFlashLayout.setOffsetY(-35f);
        gradeFlashLayout.addChild(new GradeFlashNode(state));
        StateBindings.bindVisible(gradeFlashLayout, state.gradeFlashTicks.map(t -> t > 0));
        group.addChild(gradeFlashLayout);

        // Signal blackout overlay
        group.addChild(new SignalBlackoutNode(state));

        // Tension vignette overlay (screen-edge darkening at high tension)
        // group.addChild(new TensionVignetteNode(state));

        return group;
    }

    private static GroupNode createPokedexScannerMode(AuraScannerHudState state, Animator animator) {
        GroupNode group = new GroupNode();

        // Scanlines
        group.addChild(new ScanlinesNode(state));

        // Borders & Notch
        LayoutNode bordersLayout = new LayoutNode();
        bordersLayout.setAnchor(Anchor.CENTER);
        bordersLayout.addChild(new PokedexBordersNode(state));
        group.addChild(bordersLayout);

        // Scan Rings
        group.addChild(createScanRings(state));

        // Info Frames
        LayoutNode infoFramesLayout = new LayoutNode();
        infoFramesLayout.setAnchor(Anchor.CENTER);
        infoFramesLayout.addChild(new InfoFramesNode(state));
        group.addChild(infoFramesLayout);

        // Center Info & Scanning Pointers
        LayoutNode centerLayout = new LayoutNode();
        centerLayout.setAnchor(Anchor.CENTER);

        var scanningOpacity = state.scanningProgress.map(p -> {
            float progress = p * 100f;
            float centerOpacity = (progress > (com.jayemceekay.shadowedhearts.common.aura.PokedexUsageContext.MAX_SCAN_PROGRESS - 10) ?
                    (com.jayemceekay.shadowedhearts.common.aura.PokedexUsageContext.MAX_SCAN_PROGRESS - progress) : progress) * 0.1F;
            return Math.max(0F, Math.min(1.0f, centerOpacity));
        });

        // Unknown Mark
        ImageNode unknownMark = new ImageNode(UNKNOWN_MARK, 34, 46);
        unknownMark.setX(-17);
        unknownMark.setY(-21);
        StateBindings.bindVisible(unknownMark, state.scanningProgress.map(p -> p > 0 && p < 1.0));
        StateBindings.bindOpacity(unknownMark, scanningOpacity);
        centerLayout.addChild(unknownMark);

        // Scanning Pointers
        GroupNode pointersGroup = new GroupNode();
        ImageNode leftPointer = new ImageNode(POINTER, 6, 10);
        leftPointer.setRegionWidth(6);
        leftPointer.setRegionHeight(10);
        leftPointer.setTextureWidth(12);
        leftPointer.setTextureHeight(10);
        leftPointer.setX(-6 - 30);
        leftPointer.setY(-5);

        ImageNode rightPointer = new ImageNode(POINTER, 6, 10);
        rightPointer.setRegionWidth(6);
        rightPointer.setRegionHeight(10);
        rightPointer.setTextureWidth(12);
        rightPointer.setTextureHeight(10);
        rightPointer.setU(6);
        rightPointer.setX(30);
        rightPointer.setY(-5);

        StateBindings.bindVisible(pointersGroup, state.scanningProgress.map(p -> p > 0 && p < 1.0));
        StateBindings.bindOpacity(pointersGroup, scanningOpacity);
        StateBindings.bindRotation(pointersGroup, state.sweepAngle.map(a -> (float) Math.toDegrees(a * 0.5)));

        pointersGroup.addChild(leftPointer);
        pointersGroup.addChild(rightPointer);
        centerLayout.addChild(pointersGroup);

        // Center Registration Info
        centerLayout.addChild(new CenterRegistrationNode(state));

        group.addChild(centerLayout);

        return group;
    }

    private static class PokedexBordersNode extends HudNode {
        private final AuraScannerHudState state;

        public PokedexBordersNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        public void resolveLayout(HudContext ctx) {
            this.width = ctx.screenWidth();
            this.height = ctx.screenHeight();
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            float alpha = state.fadeAmount.get();
            int screenWidth = (int) width;
            int screenHeight = (int) height;

            // Draw borders exactly as in Impl
            // Corners
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_CORNERS, -screenWidth / 2, -screenHeight / 2, 4, 4, 0, 0, 8, 8, 0, 1, 1, 1, alpha, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_CORNERS, screenWidth / 2 - 4, -screenHeight / 2, 4, 4, 4, 0, 8, 8, 0, 1, 1, 1, alpha, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_CORNERS, -screenWidth / 2, screenHeight / 2 - 4, 4, 4, 0, 4, 8, 8, 0, 1, 1, 1, alpha, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_CORNERS, screenWidth / 2 - 4, screenHeight / 2 - 4, 4, 4, 4, 4, 8, 8, 0, 1, 1, 1, alpha, true, 1F);

            // Sides and Notch
            int notchStartX = (screenWidth - SCAN_OVERLAY_NOTCH_WIDTH) / 2;
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_TOP, 4 - screenWidth / 2, -screenHeight / 2, 3, notchStartX - 4, 0, 0, notchStartX - 4, 3, 0, 1, 1, 1, alpha, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_TOP, notchStartX + SCAN_OVERLAY_NOTCH_WIDTH - screenWidth / 2, -screenHeight / 2, 3, (screenWidth - (notchStartX + SCAN_OVERLAY_NOTCH_WIDTH + 4)), 0, 0, (screenWidth - (notchStartX + SCAN_OVERLAY_NOTCH_WIDTH + 4)), 3, 0, 1, 1, 1, alpha, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_BOTTOM, 4 - screenWidth / 2, (screenHeight / 2 - 3), 3, (screenWidth - 8), 0, 0, (screenWidth - 8), 3, 0, 1, 1, 1, alpha, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_LEFT, -screenWidth / 2, 4 - screenHeight / 2, (screenHeight - 8), 3, 0, 0, 3, (screenHeight - 8), 0, 1, 1, 1, alpha, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_RIGHT, (screenWidth / 2 - 3), 4 - screenHeight / 2, (screenHeight - 8), 3, 0, 0, 3, (screenHeight - 8), 0, 1, 1, 1, alpha, true, 1F);
            com.cobblemon.mod.common.api.gui.GuiUtilsKt.blitk(graphics.pose(), SCAN_OVERLAY_NOTCH, notchStartX - screenWidth / 2, -screenHeight / 2, 12, SCAN_OVERLAY_NOTCH_WIDTH, 0, 0, SCAN_OVERLAY_NOTCH_WIDTH, 12, 0, 1, 1, 1, alpha, true, 1F);
        }
    }

    private static GroupNode createDowsingMachineMode(AuraScannerHudState state, Animator animator) {
        GroupNode group = new GroupNode();

        group.addChild(new ScanlinesNode(state));

        // 3D Item Arrow
        LayoutNode arrowLayout = new LayoutNode();
        arrowLayout.setAnchor(Anchor.CENTER);
        arrowLayout.setZ(100f);
        arrowLayout.addChild(new ThreeDItemNode(new ItemStack(ModItems.DIRECTION_ARROW.get()), state));
        group.addChild(arrowLayout);

        // Distance Text
        LayoutNode distTextLayout = new LayoutNode();
        distTextLayout.setAnchor(Anchor.CENTER);
        distTextLayout.setOffsetY(20f);
        TextNode distText = new TextNode();
        distText.setCentered(true);
        distText.setColor(0x00FFFF);
        StateBindings.bindText(distText, state.dowsingDistance.map(d -> (int) d.floatValue() + "m"));
        distTextLayout.addChild(distText);
        group.addChild(distTextLayout);

        // Material Name
        LayoutNode materialTextLayout = new LayoutNode();
        materialTextLayout.setAnchor(Anchor.CENTER);
        materialTextLayout.setOffsetY(-124f);
        TextNode materialText = new TextNode();
        materialText.setCentered(true);
        materialText.setGlow(true);
        materialText.setColor(0x00FFFF);
        StateBindings.bindText(materialText, state.dowsingMaterialName);
        materialTextLayout.addChild(materialText);
        group.addChild(materialTextLayout);

        return group;
    }

    private static GroupNode createScanRings(AuraScannerHudState state) {
        GroupNode rings = new GroupNode();

        LayoutNode centerLayout = new LayoutNode();
        centerLayout.setAnchor(Anchor.CENTER);
        rings.addChild(centerLayout);

        // Outer Ring
        ImageNode outer = new ImageNode(SCAN_RING_OUTER);
        outer.setTextureHeight(116);
        outer.setTextureWidth(116);
        outer.setRegionWidth(116);
        outer.setRegionHeight(116);
        outer.setWidth(116);
        outer.setHeight(116);
        outer.setX(-58);
        outer.setY(-58);
        StateBindings.bindRotation(outer, state.sweepAngle.map(a -> (float) Math.toDegrees(-a * 0.5)));
        centerLayout.addChild(outer);

        // Middle Rings (40 segments, 9° spacing = full 360°)
        // Each spoke spans only the annular gap between inner (r=42) and outer (r=58) ring.
        // A GroupNode wrapper rotates around the ring center (0,0); the ImageNode is offset to x=42.
        GroupNode middleRingsGroup = new GroupNode();
        for (int i = 0; i < 80; i++) {
            final int idx = i;

            GroupNode spokeWrapper = new GroupNode();

            ImageNode middle = new ImageNode(SCAN_RING_MIDDLE, 100, 1);
            middle.setTextureWidth(100);
            middle.setTextureHeight(1);
            middle.setRegionWidth(100);
            middle.setRegionHeight(1);
            middle.setX(-50);
            middle.setY(-0.5f);

            // Logic for visibility/opacity mirroring PokedexScannerRenderer.renderScanRings
            state.scanningProgress.subscribe((oldP, newP) -> {
                float progress = newP * 100f;
                float progressOpacity = 1.0f;
                int segments = 80;
                if (progress > 0) {
                    if (progress < 20) {
                        progressOpacity -= progress * 0.05f;
                    } else {
                        progressOpacity = 1.0f;
                        segments = (int) Math.floor((progress - 20.0) / 2.0);
                    }
                }
                middle.setVisible(idx < segments);
                middle.setOpacity(progressOpacity);
            });

            // Rotate the wrapper around ring center; 9° per segment covers full 360°
            StateBindings.bindRotation(spokeWrapper, state.sweepAngle.map(a -> (float) (idx * 4.5 + Math.toDegrees(a * 0.5))));
            spokeWrapper.addChild(middle);
            middleRingsGroup.addChild(spokeWrapper);
        }
        centerLayout.addChild(middleRingsGroup);

        // Inner Ring
        ImageNode inner = new ImageNode(SCAN_RING_INNER, 84, 84);
        inner.setTextureHeight(84);
        inner.setTextureWidth(84);
        inner.setX(-42);
        inner.setY(-42);
        // Using AbstractModeLogic.innerRingRotation logic (simplified to bind to sweep or similar)
        StateBindings.bindRotation(inner, state.sweepAngle.map(a -> (float) Math.toDegrees(-a)));
        centerLayout.addChild(inner);

        // Minimal hotspot scan overlay (v1): text + progress bar while holding to scan
        LayoutNode scanHud = new LayoutNode();
        scanHud.setAnchor(Anchor.CENTER);
        scanHud.setOffsetY(64f);

        TextNode scanningText = new TextNode();
        scanningText.setCentered(true);
        scanningText.setGlow(true);
        scanningText.setColor(0x00FFFF);
        scanningText.setText(net.minecraft.network.chat.Component.literal("SCANNING…"));
        StateBindings.bindVisible(scanningText, state.hotspotScanning);
        scanHud.addChild(scanningText);

        // Progress bar (uses BarMeterNode like energy bar)
        LayoutNode barLayout = new LayoutNode();
        barLayout.setAnchor(Anchor.CENTER);
        barLayout.setOffsetY(78f);
        BarMeterNode scanBar = new BarMeterNode();
        scanBar.setWidth(96f);
        scanBar.setHeight(4f);
        scanBar.setX(-48f);
        StateBindings.bindFloat(scanBar::setFillAmount, state.hotspotScanProgress);
        StateBindings.bindVisible(scanBar, state.hotspotScanning);
        barLayout.addChild(scanBar);

        rings.addChild(scanHud);
        rings.addChild(barLayout);

        return rings;
    }

    /**
     * Renders the calibration directional-input sequence overlay.
     * Shows arrow prompts in a horizontal row, highlighting the current input,
     * with a time bar and variant label.
     */
    private static class CalibrationOverlayNode extends HudNode {
        private final AuraScannerHudState state;

        // Arrow symbols for each direction
        private static final String ARROW_UP = "▲";
        private static final String ARROW_DOWN = "▼";
        private static final String ARROW_LEFT = "◀";
        private static final String ARROW_RIGHT = "▶";

        // Thematic labels for directions
        private static final String LABEL_UP = "AMPLIFY";
        private static final String LABEL_DOWN = "DAMPEN";
        private static final String LABEL_LEFT = "PHASE";
        private static final String LABEL_RIGHT = "FREQ";

        public CalibrationOverlayNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        public void resolveLayout(HudContext ctx) {
            this.width = ctx.screenWidth();
            this.height = ctx.screenHeight();
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            if (!state.calibrationActiveVal) return;
            float alpha = state.fadeAmount.get();
            if (alpha <= 0) return;

            List<CalibrationSequence.Direction> seq = state.calibrationDirections.get();
            if (seq == null || seq.isEmpty()) return;

            int currentIdx = state.calibrationCurrentIndex.get();
            float timeRemaining = state.calibrationTimeRemaining.get();
            boolean correctFlash = state.calibrationCorrectFlash.get();
            boolean wrongFlash = state.calibrationWrongFlash.get();
            int variantId = state.calibrationVariantId.get();
            CalibrationSequence.Variant variant = CalibrationSequence.Variant.fromId(variantId);

            Minecraft mc = Minecraft.getInstance();
            int centerX = 0;
            int centerY = 0;

            // Background overlay — darken screen edges
            int overlayAlpha = (int) (alpha * 80);
            graphics.fill(-((int) width / 2), -((int) height / 2), (int) width / 2, (int) height / 2,
                    (overlayAlpha << 24));

            // Variant label at top
            String variantLabel = getVariantLabel(variant);
            graphics.drawCenteredString(mc.font, variantLabel, centerX, centerY - 45, 0x80FFFF | ((int) (alpha * 255) << 24));

            // "CALIBRATING..." text
            graphics.drawCenteredString(mc.font, "CALIBRATING...", centerX, centerY - 35, 0xFFFFFF | ((int) (alpha * 200) << 24));

            // Arrow sequence
            int arrowSpacing = 20;
            int totalWidth = seq.size() * arrowSpacing;
            int startX = centerX - totalWidth / 2 + arrowSpacing / 2;

            for (int i = 0; i < seq.size(); i++) {
                CalibrationSequence.Direction dir = seq.get(i);
                String arrow = getArrowSymbol(dir);
                int x = startX + i * arrowSpacing;
                int y = centerY - 8;

                int color;
                if (i < currentIdx) {
                    // Completed — green
                    color = 0x00FF00;
                } else if (i == currentIdx) {
                    // Current — bright white, pulsing
                    float pulse = (float) (0.7 + 0.3 * Math.sin(System.currentTimeMillis() * 0.008));
                    int brightness = (int) (200 + 55 * pulse);
                    color = (brightness << 16) | (brightness << 8) | brightness;
                    // Flash feedback
                    if (correctFlash) color = 0x00FF88;
                    if (wrongFlash) color = 0xFF4444;
                } else {
                    // Upcoming — dim
                    color = 0x666688;
                }

                int argb = color | ((int) (alpha * 255) << 24);
                // Scale up the current arrow
                if (i == currentIdx) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(x, y + 4, 0);
                    graphics.pose().scale(1.5f, 1.5f, 1f);
                    graphics.drawCenteredString(mc.font, arrow, 0, -4, argb);
                    graphics.pose().popPose();

                    // Show thematic label below current arrow
                    String label = getDirectionLabel(dir);
                    graphics.drawCenteredString(mc.font, label, x, centerY + 12,
                            0xAABBCC | ((int) (alpha * 180) << 24));
                } else {
                    graphics.drawCenteredString(mc.font, arrow, x, y, argb);
                }
            }

            // Time remaining bar
            int barWidth = Math.min(120, totalWidth + 20);
            int barHeight = 3;
            int barX = centerX - barWidth / 2;
            int barY = centerY + 24;
            // Background
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight,
                    0x40FFFFFF);
            // Fill based on time remaining
            int timeColor = timeRemaining > 0.3f ? 0xFF00CCFF : 0xFFFF4444;
            int fillWidth = (int) (barWidth * timeRemaining);
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, timeColor);

            // Progress indicator: "3/5" style
            String progressText = currentIdx + "/" + seq.size();
            graphics.drawCenteredString(mc.font, progressText, centerX, centerY + 30,
                    0xCCCCCC | ((int) (alpha * 200) << 24));
        }

        private String getArrowSymbol(CalibrationSequence.Direction dir) {
            return switch (dir) {
                case UP -> ARROW_UP;
                case DOWN -> ARROW_DOWN;
                case LEFT -> ARROW_LEFT;
                case RIGHT -> ARROW_RIGHT;
            };
        }

        private String getDirectionLabel(CalibrationSequence.Direction dir) {
            return switch (dir) {
                case UP -> LABEL_UP;
                case DOWN -> LABEL_DOWN;
                case LEFT -> LABEL_LEFT;
                case RIGHT -> LABEL_RIGHT;
            };
        }

        private String getVariantLabel(CalibrationSequence.Variant variant) {
            return switch (variant) {
                case HARMONIC_LOCK -> "◈ HARMONIC LOCK ◈";
                case SIGNAL_DRIFT -> "◈ SIGNAL DRIFT ◈";
                case SHADOW_ECHO -> "◈ SHADOW ECHO ◈";
                case REVERSE_INTERFERENCE -> "◈ REVERSE INTERFERENCE ◈";
                case DUAL_BAND -> "◈ DUAL-BAND CALIBRATION ◈";
                case OVERLOAD_RECOVERY -> "◈ OVERLOAD RECOVERY ◈";
            };
        }
    }

    /**
     * Briefly displays the calibration grade result after completion.
     */
    private static class CalibrationGradeNode extends HudNode {
        private final AuraScannerHudState state;

        public CalibrationGradeNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            CalibrationSequence.Grade grade = state.calibrationGrade.get();
            if (grade == null) return;
            int displayTicks = state.calibrationGradeDisplay.get();
            if (displayTicks <= 0) return;

            float alpha = state.fadeAmount.get() * Math.min(1.0f, displayTicks / 10.0f);
            Minecraft mc = Minecraft.getInstance();

            String text;
            int color;
            switch (grade) {
                case PERFECT -> { text = "★ PERFECT ★"; color = 0x00FF88; }
                case STANDARD -> { text = "STANDARD"; color = 0x00CCFF; }
                case SLOPPY -> { text = "SLOPPY"; color = 0xFFAA00; }
                case FAILED -> { text = "FAILED"; color = 0xFF4444; }
                default -> { text = ""; color = 0xFFFFFF; }
            }

            int argb = color | ((int) (alpha * 255) << 24);

            graphics.pose().pushPose();
            graphics.pose().scale(2.0f, 2.0f, 1.0f);
            graphics.drawCenteredString(mc.font, text, 0, 0, argb);
            graphics.pose().popPose();
        }
    }

    /**
     * Renders the active node event HUD overlay.
     * Shows event-specific information: clue selection for Evidence Interpretation,
     * signal strength for Environmental Search, timer for Wild Interruption,
     * and signal buildup bar for Provocation.
     */
    private static class NodeEventOverlayNode extends HudNode {
        private final AuraScannerHudState state;

        private static final int COLOR_CYAN = 0x00FFFF;
        private static final int COLOR_GREEN = 0x00FF88;
        private static final int COLOR_ORANGE = 0xFFAA00;
        private static final int COLOR_RED = 0xFF4444;
        private static final int COLOR_PURPLE = 0xA330FF;

        public NodeEventOverlayNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        public void resolveLayout(HudContext ctx) {
            this.width = ctx.screenWidth();
            this.height = ctx.screenHeight();
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            if (!state.nodeEventActive.get()) return;
            float alpha = state.fadeAmount.get();
            int eventTypeId = state.nodeEventType.get();
            Minecraft mc = Minecraft.getInstance();

            switch (eventTypeId) {
                case 1 -> renderEvidenceInterpretation(graphics, mc, alpha);
                case 3 -> renderEnvironmentalSearch(graphics, mc, alpha);
                case 2 -> renderWildInterruption(graphics, mc, alpha);
                case 4 -> renderProvocation(graphics, mc, alpha);
            }

            // Time remaining bar (common to all events)
            renderTimeBar(graphics, alpha);
        }

        private void renderEvidenceInterpretation(GuiGraphics graphics, Minecraft mc, float alpha) {
            int argb = COLOR_CYAN | ((int) (alpha * 220) << 24);
            int warnArgb = COLOR_ORANGE | ((int) (alpha * 220) << 24);
            int greenArgb = COLOR_GREEN | ((int) (alpha * 220) << 24);

            // Title
            graphics.pose().pushPose();
            graphics.pose().translate(0, -70, 0);
            graphics.drawCenteredString(mc.font, "◈ EVIDENCE INTERPRETATION ◈", 0, 0, argb);
            graphics.pose().popPose();

            // Progress: found X / required Y valid clues out of Z total
            int clueCount = state.nodeEventClueCount.get();
            int required = state.nodeEventRequiredValidCount.get();
            int found = state.nodeEventFoundValidCount.get();
            int wrong = state.nodeEventWrongGuesses.get();
            java.util.List<Integer> selected = state.nodeEventSelectedClueIndices.get();

            String instruction = "Find " + required + " valid trace" + (required > 1 ? "s" : "") + " among " + clueCount + " clue locations";
            graphics.pose().pushPose();
            graphics.pose().translate(0, -56, 0);
            graphics.drawCenteredString(mc.font, instruction, 0, 0, argb);
            graphics.pose().popPose();

            // Progress bar: found / required
            String progressText = "Evidence found: " + found + " / " + required;
            graphics.pose().pushPose();
            graphics.pose().translate(0, -42, 0);
            graphics.drawCenteredString(mc.font, progressText, 0, 0, found >= required ? greenArgb : argb);
            graphics.pose().popPose();

            // Wrong guesses warning
            if (wrong > 0) {
                String warnText = "Wrong: " + wrong + "/3";
                graphics.pose().pushPose();
                graphics.pose().translate(0, -30, 0);
                graphics.drawCenteredString(mc.font, warnText, 0, 0, warnArgb);
                graphics.pose().popPose();
            }

            // Clue slot indicators: show each clue as [1] [2] [3] ... with status
            int slotY = 48;
            int slotSpacing = 22;
            int totalWidth = clueCount * slotSpacing;
            int startX = -totalWidth / 2 + slotSpacing / 2;
            for (int i = 0; i < clueCount; i++) {
                int sx = startX + i * slotSpacing;
                boolean isSelected = selected != null && selected.contains(i);
                String label = "[" + (i + 1) + "]";
                int color;
                if (isSelected) {
                    // Grey out selected clues (player already chose this one)
                    color = 0x888888 | ((int) (alpha * 150) << 24);
                    label = " ✓ ";
                } else {
                    color = argb;
                }
                graphics.pose().pushPose();
                graphics.pose().translate(sx, slotY, 0);
                graphics.drawCenteredString(mc.font, label, 0, 0, color);
                graphics.pose().popPose();
            }

            // Hint: use number keys
            graphics.pose().pushPose();
            graphics.pose().translate(0, 66, 0);
            graphics.drawCenteredString(mc.font, "[1-" + clueCount + "] Select clue", 0, 0,
                    (COLOR_CYAN & 0xFFFFFF) | ((int) (alpha * 150) << 24));
            graphics.pose().popPose();
        }

        private void renderEnvironmentalSearch(GuiGraphics graphics, Minecraft mc, float alpha) {
            int argb = COLOR_CYAN | ((int) (alpha * 220) << 24);

            // Title
            graphics.pose().pushPose();
            graphics.pose().translate(0, -70, 0);
            graphics.drawCenteredString(mc.font, "◈ ENVIRONMENTAL SEARCH ◈", 0, 0, argb);
            graphics.pose().popPose();

            // Signal strength bar (hot/cold)
            float signal = state.nodeEventSearchSignal.get();
            int barWidth = 120;
            int barHeight = 8;
            int barX = -barWidth / 2;
            int barY = -50;

            // Background
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight,
                    0x40000000 | ((int) (alpha * 100) << 24));

            // Fill based on signal
            int fillWidth = (int) (barWidth * signal);
            int fillColor;
            if (signal > 0.7f) fillColor = COLOR_GREEN;
            else if (signal > 0.4f) fillColor = COLOR_ORANGE;
            else fillColor = COLOR_RED;
            fillColor = fillColor | ((int) (alpha * 200) << 24);
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

            // Label
            String signalText = signal > 0.8f ? "VERY HOT" : signal > 0.6f ? "HOT" :
                    signal > 0.4f ? "WARM" : signal > 0.2f ? "COOL" : "COLD";
            graphics.pose().pushPose();
            graphics.pose().translate(0, barY - 12, 0);
            graphics.drawCenteredString(mc.font, "Signal: " + signalText, 0, 0, argb);
            graphics.pose().popPose();

            // Instruction
            graphics.pose().pushPose();
            graphics.pose().translate(0, 60, 0);
            graphics.drawCenteredString(mc.font, "Move closer to the hidden trace", 0, 0,
                    (COLOR_CYAN & 0xFFFFFF) | ((int) (alpha * 150) << 24));
            graphics.pose().popPose();
        }

        private void renderWildInterruption(GuiGraphics graphics, Minecraft mc, float alpha) {
            int argb = COLOR_RED | ((int) (alpha * 220) << 24);

            // Title
            graphics.pose().pushPose();
            graphics.pose().translate(0, -70, 0);
            graphics.drawCenteredString(mc.font, "◈ WILD DISTURBANCE ◈", 0, 0, argb);
            graphics.pose().popPose();

            boolean resolved = state.nodeEventWildsResolved.get();
            String statusText = resolved ? "Disturbance cleared!" : "Agitated Pokémon detected nearby!";
            int statusColor = resolved ? COLOR_GREEN : COLOR_ORANGE;
            graphics.pose().pushPose();
            graphics.pose().translate(0, -56, 0);
            graphics.drawCenteredString(mc.font, statusText, 0, 0,
                    statusColor | ((int) (alpha * 220) << 24));
            graphics.pose().popPose();

            if (!resolved) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 60, 0);
                graphics.drawCenteredString(mc.font, "Survive the disturbance to continue", 0, 0,
                        (COLOR_CYAN & 0xFFFFFF) | ((int) (alpha * 150) << 24));
                graphics.pose().popPose();
            }
        }

        private void renderProvocation(GuiGraphics graphics, Minecraft mc, float alpha) {
            int argb = COLOR_PURPLE | ((int) (alpha * 220) << 24);

            // Title
            graphics.pose().pushPose();
            graphics.pose().translate(0, -70, 0);
            graphics.drawCenteredString(mc.font, "◈ SIGNAL PROVOCATION ◈", 0, 0, argb);
            graphics.pose().popPose();

            // Signal buildup bar
            float buildup = state.nodeEventSignalBuildup.get();
            int barWidth = 140;
            int barHeight = 10;
            int barX = -barWidth / 2;
            int barY = -48;

            // Background
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight,
                    0x40000000 | ((int) (alpha * 100) << 24));

            // Fill
            int fillWidth = (int) (barWidth * buildup);
            int fillColor;
            if (buildup > 0.8f) fillColor = COLOR_GREEN;
            else if (buildup > 0.5f) fillColor = COLOR_ORANGE;
            else fillColor = COLOR_PURPLE;
            fillColor = fillColor | ((int) (alpha * 200) << 24);
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

            // Label
            String buildupText = String.format("Signal Lock: %.0f%%", buildup * 100);
            graphics.pose().pushPose();
            graphics.pose().translate(0, barY - 14, 0);
            graphics.drawCenteredString(mc.font, buildupText, 0, 0, argb);
            graphics.pose().popPose();

            // Instruction
            graphics.pose().pushPose();
            graphics.pose().translate(0, 60, 0);
            graphics.drawCenteredString(mc.font, "Hold position in the hotspot", 0, 0,
                    (COLOR_CYAN & 0xFFFFFF) | ((int) (alpha * 150) << 24));
            graphics.pose().popPose();
        }

        private void renderTimeBar(GuiGraphics graphics, float alpha) {
            float timeRemaining = state.nodeEventTimeRemaining.get();
            int barWidth = 80;
            int barHeight = 3;
            int barX = -barWidth / 2;
            int barY = 70;

            // Background
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight,
                    0x30FFFFFF);
            // Fill
            int fillWidth = (int) (barWidth * timeRemaining);
            int color = timeRemaining > 0.3f ? COLOR_CYAN : COLOR_RED;
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight,
                    color | ((int) (alpha * 180) << 24));
        }
    }

    /**
     * Renders the dramatic manifestation buildup sequence:
     * Phase 1: signal spike, Phase 2: trail convergence, Phase 3: distortion crescendo.
     */
    private static class ManifestationOverlayNode extends HudNode {
        private final AuraScannerHudState state;
        private static final int COLOR_SHADOW = 0x8B00FF;
        private static final int COLOR_DARK = 0x200030;
        private static final int COLOR_WHITE = 0xFFFFFF;

        public ManifestationOverlayNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        public void resolveLayout(HudContext ctx) {
            this.width = ctx.screenWidth();
            this.height = ctx.screenHeight();
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            if (!state.manifestationActiveVal) return;
            Minecraft mc = Minecraft.getInstance();
            float alpha = state.fadeAmount.get();
            float progress = state.manifestationProgressVal;
            int phase = state.manifestationPhaseVal;
            int halfW = (int) width / 2;
            int halfH = (int) height / 2;

            // Phase 1: Signal Spike — pulsing border flash
            if (phase >= 1) {
                float pulseIntensity = (float) (Math.sin(System.currentTimeMillis() * 0.015) * 0.5 + 0.5);
                float phaseAlpha = Math.min(1f, progress * 3f) * alpha;
                int spikeAlpha = (int) (phaseAlpha * pulseIntensity * 80);
                int spikeColor = (spikeAlpha << 24) | (COLOR_SHADOW & 0xFFFFFF);
                int edgeSize = (int) (15 + progress * 25);
                graphics.fill(-halfW, -halfH, halfW, -halfH + edgeSize, spikeColor);
                graphics.fill(-halfW, halfH - edgeSize, halfW, halfH, spikeColor);
                graphics.fill(-halfW, -halfH, -halfW + edgeSize, halfH, spikeColor);
                graphics.fill(halfW - edgeSize, -halfH, halfW, halfH, spikeColor);
            }

            // Phase 2: Convergence — central convergence rings
            if (phase >= 2) {
                float ringProgress = Math.max(0, (progress - 0.25f) / 0.3f);
                float ringAlpha = Math.min(1f, ringProgress) * alpha * 0.6f;
                int ringA = (int) (ringAlpha * 120);
                float ringScale = 1.0f - ringProgress * 0.5f;
                int ringSize = (int) (80 * ringScale);
                int ringColor = (ringA << 24) | (COLOR_SHADOW & 0xFFFFFF);
                // Draw converging ring
                graphics.fill(-ringSize, -ringSize, ringSize, -ringSize + 2, ringColor);
                graphics.fill(-ringSize, ringSize - 2, ringSize, ringSize, ringColor);
                graphics.fill(-ringSize, -ringSize, -ringSize + 2, ringSize, ringColor);
                graphics.fill(ringSize - 2, -ringSize, ringSize, ringSize, ringColor);
            }

            // Phase 3: Crescendo — heavy distortion + darkening
            if (phase >= 3) {
                float crescendoProgress = Math.max(0, (progress - 0.55f) / 0.3f);
                float darkAlpha = Math.min(1f, crescendoProgress) * alpha * 0.4f;
                int darkA = (int) (darkAlpha * 100);
                int darkColor = (darkA << 24) | (COLOR_DARK & 0xFFFFFF);
                graphics.fill(-halfW, -halfH, halfW, halfH, darkColor);

                // Flicker effect
                float flicker = (float) (Math.random() * 0.3f * crescendoProgress);
                int flickerA = (int) (flicker * alpha * 60);
                graphics.fill(-halfW, -halfH, halfW, halfH, (flickerA << 24) | 0xFFFFFF);
            }

            // Title text
            String phaseText = switch (phase) {
                case 1 -> "◈ SIGNAL SPIKE DETECTED ◈";
                case 2 -> "◈ TRAIL CONVERGING ◈";
                case 3 -> "◈ MANIFESTATION IMMINENT ◈";
                default -> "";
            };
            int textAlpha = (int) (alpha * 220);
            int textColor = (textAlpha << 24) | (COLOR_SHADOW & 0xFFFFFF);
            graphics.pose().pushPose();
            graphics.pose().translate(0, -80, 0);
            graphics.drawCenteredString(mc.font, phaseText, 0, 0, textColor);
            graphics.pose().popPose();

            // Progress bar
            int barWidth = 120;
            int barHeight = 6;
            int barX = -barWidth / 2;
            int barY = -60;
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight,
                    0x40000000 | ((int) (alpha * 80) << 24));
            int fillWidth = (int) (barWidth * progress);
            int fillColor = (textAlpha << 24) | (COLOR_SHADOW & 0xFFFFFF);
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
        }
    }

    /**
     * Renders a grade flash text ("PERFECT!", "SLOPPY", "FAILED") after event completion.
     */
    private static class GradeFlashNode extends HudNode {
        private final AuraScannerHudState state;

        public GradeFlashNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            String text = state.gradeFlashTextVal;
            int ticks = state.gradeFlashTicksVal;
            if (text == null || ticks <= 0) return;

            Minecraft mc = Minecraft.getInstance();
            float alpha = state.fadeAmount.get();
            // Fade out over last 20 ticks
            float fadeProgress = ticks > 20 ? 1.0f : ticks / 20.0f;
            float finalAlpha = alpha * fadeProgress;

            // Scale effect: starts slightly larger and settles
            float scale = ticks > 30 ? 1.3f : 1.0f + (ticks > 20 ? 0.3f * ((ticks - 20) / 10.0f) : 0f);

            int color;
            String displayText;
            switch (text) {
                case "PERFECT" -> {
                    color = 0x00FF00;
                    displayText = "★ PERFECT ★";
                }
                case "SLOPPY" -> {
                    color = 0xFF8800;
                    displayText = "~ Sloppy ~";
                }
                case "FAILED" -> {
                    color = 0xFF2200;
                    displayText = "✗ FAILED ✗";
                }
                default -> {
                    color = 0x00FFFF;
                    displayText = "✓ Complete";
                }
            }

            int argb = ((int) (finalAlpha * 240) << 24) | (color & 0xFFFFFF);

            graphics.pose().pushPose();
            graphics.pose().scale(scale, scale, 1f);
            graphics.drawCenteredString(mc.font, displayText, 0, 0, argb);
            graphics.pose().popPose();
        }
    }

    /**
     * Renders a signal blackout overlay — darkens screen and shows static when signal is blacked out.
     */
    private static class SignalBlackoutNode extends HudNode {
        private final AuraScannerHudState state;

        public SignalBlackoutNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        public void resolveLayout(HudContext ctx) {
            this.width = ctx.screenWidth();
            this.height = ctx.screenHeight();
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            if (!state.signalBlackoutVal) return;
            float alpha = state.fadeAmount.get();
            int halfW = (int) width / 2;
            int halfH = (int) height / 2;

            // Dark overlay with static noise
            int darkAlpha = (int) (alpha * 80);
            graphics.fill(-halfW, -halfH, halfW, halfH, (darkAlpha << 24) | 0x100010);

            // Random static lines
            java.util.Random rng = new java.util.Random(System.currentTimeMillis() / 50);
            for (int i = 0; i < 5; i++) {
                int y = rng.nextInt((int) height) - halfH;
                int staticAlpha = (int) (alpha * (30 + rng.nextInt(40)));
                graphics.fill(-halfW, y, halfW, y + 1, (staticAlpha << 24) | 0x8B00FF);
            }

            // "SIGNAL LOST" text
            Minecraft mc = Minecraft.getInstance();
            float flicker = (float) (Math.sin(System.currentTimeMillis() * 0.02) * 0.3 + 0.7);
            int textAlpha = (int) (alpha * flicker * 200);
            int textColor = (textAlpha << 24) | 0xFF2200;
            graphics.drawCenteredString(mc.font, "◈ SIGNAL LOST ◈", 0, 0, textColor);
        }
    }

    /**
     * Renders a screen-edge vignette that intensifies with hunt tension.
     */
    private static class TensionVignetteNode extends HudNode {
        private final AuraScannerHudState state;

        public TensionVignetteNode(AuraScannerHudState state) {
            this.state = state;
        }

        @Override
        public void resolveLayout(HudContext ctx) {
            this.width = ctx.screenWidth();
            this.height = ctx.screenHeight();
        }

        @Override
        protected void renderSelf(HudContext ctx, GuiGraphics graphics) {
            float tension = state.huntTension.get();
            if (tension <= 0.1f) return;

            float alpha = state.fadeAmount.get();
            int halfW = (int) width / 2;
            int halfH = (int) height / 2;

            // Edge vignette: purple-tinted darkness at screen edges, intensity scales with tension
            int vignetteAlpha = (int) (tension * alpha * 60);
            int vignetteColor = (vignetteAlpha << 24) | 0x200030; // dark purple tint

            int edgeSize = (int) (20 + tension * 30);

            // Top edge
            graphics.fill(-halfW, -halfH, halfW, -halfH + edgeSize, vignetteColor);
            // Bottom edge
            graphics.fill(-halfW, halfH - edgeSize, halfW, halfH, vignetteColor);
            // Left edge
            graphics.fill(-halfW, -halfH, -halfW + edgeSize, halfH, vignetteColor);
            // Right edge
            graphics.fill(halfW - edgeSize, -halfH, halfW, halfH, vignetteColor);

            // At high tension (>0.6), add subtle flicker via random alpha variation
            if (tension > 0.6f) {
                float flicker = (float) (Math.sin(System.currentTimeMillis() * 0.01) * 0.3 + 0.7);
                int flickerAlpha = (int) ((tension - 0.6f) * alpha * 40 * flicker);
                int flickerColor = (flickerAlpha << 24) | 0x400050;
                graphics.fill(-halfW, -halfH, halfW, halfH, flickerColor);
            }
        }
    }
}
