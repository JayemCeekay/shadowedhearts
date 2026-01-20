package com.jayemceekay.shadowedhearts.client.gui;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.ShadowPokemonData;
import com.jayemceekay.shadowedhearts.aura.AuraReaderCharge;
import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModSounds;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.items.AuraReaderItem;
import com.jayemceekay.shadowedhearts.network.AuraPulsePacket;
import com.jayemceekay.shadowedhearts.network.AuraScannerC2SPacket;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class AuraScannerHUD {
    private static final ResourceLocation SCAN_RING_INNER = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/scan_ring_inner.png");
    private static final ResourceLocation SCAN_RING_OUTER = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/scan_ring_outer.png");
    private static final ResourceLocation SCAN_RING_MIDDLE = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/scan_ring_middle.png");
    private static final ResourceLocation SCANLINES = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/overlay_scanlines.png");
    private static final ResourceLocation POINTER = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/pointer.png");
    private static final ResourceLocation SELECT_ARROW = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/select_arrow.png");
    private static final ResourceLocation INFO_FRAME = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/scan_info_frame.png");
    private static final ResourceLocation SCAN_OVERLAY_CORNERS = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/overlay_corners.png");
    private static final ResourceLocation SCAN_OVERLAY_TOP = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/overlay_border_top.png");
    private static final ResourceLocation SCAN_OVERLAY_BOTTOM = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/overlay_border_bottom.png");
    private static final ResourceLocation SCAN_OVERLAY_LEFT = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/overlay_border_left.png");
    private static final ResourceLocation SCAN_OVERLAY_RIGHT = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/overlay_border_right.png");
    private static final ResourceLocation SCAN_OVERLAY_NOTCH = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/overlay_notch.png");
    private static final ResourceLocation UNKNOWN_MARK = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/scan_unknown.png");

    private static ResourceLocation getInfoFrameResource(boolean isLeft, int tier) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/scan_info_frame_" + (isLeft ? "left" : "right") + "_" + tier + ".png");
    }

    private static final int SCAN_OVERLAY_NOTCH_WIDTH = 200;
    private static final ResourceLocation CENTER_INFO_FRAME = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/scan/scan_info_frame.png");
    private static final int CENTER_INFO_FRAME_WIDTH = 128;
    private static final int CENTER_INFO_FRAME_HEIGHT = 16;
    private static final int OUTER_INFO_FRAME_WIDTH = 92;
    private static final int OUTER_INFO_FRAME_HEIGHT = 55;
    private static final int INNER_INFO_FRAME_WIDTH = 120;
    private static final int INNER_INFO_FRAME_HEIGHT = 20;
    private static final int INNER_INFO_FRAME_STEM_WIDTH = 28;

    private static boolean active = false;
    private static float fadeAmount = 0.0f;
    private static float prevFadeAmount = 0.0f;
    private static float bootTimer = 0.0f;
    private static float prevBootTimer = 0.0f;
    private static final float BOOT_DURATION = 0.5f;
    private static float sweepAngle = 0.0f;
    private static float prevSweepAngle = 0.0f;
    private static float glitchTimer = 0.0f;
    private static float prevGlitchTimer = 0.0f;
    private static float lastXRot = 0.0f;
    private static float lastYRot = 0.0f;
    private static float scanningProgress = 0.0f;
    private static float prevScanningProgress = 0.0f;
    private static PokemonEntity scannedPokemon = null;
    private static int beepTimer = 0;
    private static int pulseQueue = 0;
    private static int pulseTimer = 0;
    private static final Map<UUID, Integer> DETECTED_SHADOWS = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_RESPONSES = new HashMap<>();
    private static final int DETECTION_DURATION_POKEMON = 100; // 5 seconds
    private static final int DETECTION_DURATION_METEOROIDS = 200;
    private static final int RESPONSE_DELAY = 40; // 2 seconds
    private static final Map<BlockPos, Integer> DETECTED_METEOROIDS = new HashMap<>();
    private static final Map<BlockPos, Integer> PENDING_METEOROID_RESPONSES = new HashMap<>();

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        prevFadeAmount = fadeAmount;
        prevBootTimer = bootTimer;
        prevSweepAngle = sweepAngle;
        prevGlitchTimer = glitchTimer;
        prevScanningProgress = scanningProgress;

        float xRot = mc.player.getXRot();
        float yRot = mc.player.getYRot();

        lastXRot = xRot;
        lastYRot = yRot;

        boolean hasAuraReader = SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(mc.player);

        if (ModKeybinds.consumeAuraScannerPress()) {
            if (hasAuraReader && ShadowedHeartsConfigs.getInstance().getClientConfig().auraScannerEnabled()) {
                ItemStack auraReader = SnagAccessoryBridgeHolder.INSTANCE.getEquippedStack(mc.player);
                if (auraReader.isEmpty() || !(auraReader.getItem() instanceof AuraReaderItem)) {
                    auraReader = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                }
                
                if (!auraReader.isEmpty() && AuraReaderCharge.get(auraReader) > 0) {
                    active = !active;
                    ShadowedHeartsNetwork.sendToServer(new AuraScannerC2SPacket(active));
                    if (active) {
                        Minecraft.getInstance().player.playSound(ModSounds.AURA_READER_EQUIP.get(), 1.0f, 1.0f);
                        bootTimer = BOOT_DURATION;
                        sweepAngle = 0.0f;
                        prevSweepAngle = 0.0f;
                    } else {
                        Minecraft.getInstance().player.playSound(ModSounds.AURA_READER_UNEQUIP.get(), 1.0f, 1.0f);
                    }
                }
            } else {
                if (active) {
                    active = false;
                    ShadowedHeartsNetwork.sendToServer(new AuraScannerC2SPacket(false));
                }
            }
        }

        if (active && !hasAuraReader) {
            active = false;
            ShadowedHeartsNetwork.sendToServer(new AuraScannerC2SPacket(false));
        }

        if (ModKeybinds.consumeAuraPulsePress()) {
            if (active) {
                pulseQueue = 3;
                pulseTimer = 0;
                ShadowedHeartsNetwork.sendToServer(new AuraPulsePacket());
                mc.player.playSound(ModSounds.AURA_SCANNER_BEEP.get());

                // Start response timer for nearby shadows
                int shadowRange = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange();
                List<Entity> entities = mc.level.getEntities(null, mc.player.getBoundingBox().inflate(shadowRange));
                for (Entity entity : entities) {
                    if (entity instanceof PokemonEntity pe && ShadowPokemonData.isShadow(pe)) {
                        PENDING_RESPONSES.put(entity.getUUID(), RESPONSE_DELAY);
                    }
                }
                
                // Scan for meteoroid blocks manually in a radius (client-side fallback if POI manager is unavailable)
                BlockPos playerPos = mc.player.blockPosition();
                int meteoroidRange = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerMeteoroidRange();
                for (BlockPos p : BlockPos.betweenClosed(playerPos.offset(-meteoroidRange, -16, -meteoroidRange), playerPos.offset(meteoroidRange, 16, meteoroidRange))) {
                    if (mc.level.getBlockState(p).is(com.jayemceekay.shadowedhearts.core.ModBlocks.SHADOWFALL_METEOROID.get())) {
                        PENDING_METEOROID_RESPONSES.put(p.immutable(), RESPONSE_DELAY);
                    }
                }
            }
        }

        if (active) {
            if (pulseQueue > 0) {
                if (pulseTimer <= 0) {
                    AuraPulseRenderer.spawnPulse(mc.player.position());
                    pulseQueue--;
                    pulseTimer = 10; // 0.5s between pulses
                } else {
                    pulseTimer--;
                }
            }

            // Handle pending responses
            Iterator<Map.Entry<UUID, Integer>> respIter = PENDING_RESPONSES.entrySet().iterator();
            while (respIter.hasNext()) {
                Map.Entry<UUID, Integer> entry = respIter.next();
                entry.setValue(entry.getValue() - 1);
                if (entry.getValue() <= 0) {
                    Entity entity = null;
                    // Check all entities in the level for this UUID
                    for (Entity e : mc.level.entitiesForRendering()) {
                        if (e.getUUID().equals(entry.getKey())) {
                            entity = e;
                            break;
                        }
                    }

                    if (entity != null) {
                        AuraPulseRenderer.spawnPulse(entity.position(), 0.6f, 0.3f, 1.0f, 128.0f); // Purple pulse
                        DETECTED_SHADOWS.put(entity.getUUID(), DETECTION_DURATION_POKEMON);
                    }
                    respIter.remove();
                }
            }

            // Handle pending meteoroid responses
            Iterator<Map.Entry<BlockPos, Integer>> metRespIter = PENDING_METEOROID_RESPONSES.entrySet().iterator();
            while (metRespIter.hasNext()) {
                Map.Entry<BlockPos, Integer> entry = metRespIter.next();
                entry.setValue(entry.getValue() - 1);
                if (entry.getValue() <= 0) {
                    AuraPulseRenderer.spawnPulse(entry.getKey().getCenter(), 0.6f, 0.3f, 1.0f, 256.0f); // Purple pulse
                    DETECTED_METEOROIDS.put(entry.getKey(), DETECTION_DURATION_METEOROIDS);
                    metRespIter.remove();
                }
            }

            // Update detections
            Iterator<Map.Entry<UUID, Integer>> detectIter = DETECTED_SHADOWS.entrySet().iterator();
            while (detectIter.hasNext()) {
                Map.Entry<UUID, Integer> entry = detectIter.next();
                entry.setValue(entry.getValue() - 1);
                if (entry.getValue() <= 0) {
                    detectIter.remove();
                }
            }

            // Update meteoroid detections
            Iterator<Map.Entry<BlockPos, Integer>> metDetectIter = DETECTED_METEOROIDS.entrySet().iterator();
            while (metDetectIter.hasNext()) {
                Map.Entry<BlockPos, Integer> entry = metDetectIter.next();
                entry.setValue(entry.getValue() - 1);
                if (entry.getValue() <= 0) {
                    metDetectIter.remove();
                }
            }

            fadeAmount = Math.min(1.0f, fadeAmount + 0.1f);
            if (bootTimer > 0) {
                bootTimer -= 0.05f;
            }
            sweepAngle += 0.1f;
            if (sweepAngle > (float) Math.PI * 2) {
                sweepAngle -= (float) Math.PI * 2;
                prevSweepAngle -= (float) Math.PI * 2;
            }

            // Scanning logic
            PokemonEntity nearest = null;
            double minDist = Double.MAX_VALUE;
            List<Entity> nearbyEntities = mc.level.getEntities(null, mc.player.getBoundingBox().inflate(16.0));
            for (Entity entity : nearbyEntities) {
                if (entity instanceof PokemonEntity pe && ShadowPokemonData.isShadow(pe) && DETECTED_SHADOWS.containsKey(pe.getUUID())) {
                    double dist = entity.distanceTo(mc.player);
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = pe;
                    }
                }
            }

            if (nearest != null) {
                if (scannedPokemon != nearest) {
                    scannedPokemon = nearest;
                    scanningProgress = 0;
                }
                scanningProgress = Math.min(1.0f, scanningProgress + 0.01f);
            } else {
                scannedPokemon = null;
                scanningProgress = Math.max(0.0f, scanningProgress - 0.05f);
            }

            // Deactivate if charge is empty (client side check)
            ItemStack auraReader = SnagAccessoryBridgeHolder.INSTANCE.getEquippedStack(mc.player);
            if (auraReader.isEmpty() || !(auraReader.getItem() instanceof AuraReaderItem)) {
                auraReader = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            }
            if (!auraReader.isEmpty() && auraReader.getItem() instanceof AuraReaderItem) {
                if (AuraReaderCharge.get(auraReader) <= 0) {
                    active = false;
                }
            }

            // Check for legendaries to trigger glitches
            boolean legendaryNearby = false;
            nearbyEntities = mc.level.getEntities(null, mc.player.getBoundingBox().inflate(32.0));
            for (Entity entity : nearbyEntities) {
                if (entity instanceof PokemonEntity pe && ShadowPokemonData.isShadow(pe) && pe.getPokemon().isLegendary()) {
                    legendaryNearby = true;
                    break;
                }
            }
            
            if (legendaryNearby && mc.level.random.nextFloat() < 0.1f) {
                glitchTimer = 0.2f;
            }
            if (glitchTimer > 0) {
                glitchTimer -= 0.05f;
            }

            // Update maxIntensity for signal strength and audio cues
            maxIntensity = 0;
            int shadowRange = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange();
            List<Entity> nearbyShadows = mc.level.getEntities(null, mc.player.getBoundingBox().inflate(shadowRange));
            for (Entity entity : nearbyShadows) {
                if (entity instanceof PokemonEntity pe && ShadowPokemonData.isShadow(pe) && DETECTED_SHADOWS.containsKey(pe.getUUID())) {
                    double dist = entity.distanceTo(mc.player);
                    maxIntensity = Math.max(maxIntensity, (float) (1.0 - (dist / shadowRange)));
                }
            }
        
            // Intensity from meteoroids
            int meteoroidRange = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerMeteoroidRange();
            for (BlockPos p : DETECTED_METEOROIDS.keySet()) {
                double dist = Math.sqrt(p.distSqr(mc.player.blockPosition()));
                maxIntensity = Math.max(maxIntensity, (float) (1.0 - (dist / meteoroidRange)));
            }

            // Hot/Cold Audio Cues
            if (beepTimer > 0) {
                beepTimer--;
            } else {
                if (maxIntensity > 0) {
                    // maxIntensity is 0.0 to 1.0 based on distance (0 to 64 blocks)
                    // We want faster beeps when closer (higher intensity)
                    // min delay: 5 ticks (0.25s) at max intensity
                    // max delay: 60 ticks (3s) at min intensity
                    int delay = (int) Mth.lerp(maxIntensity, 60, 5);
                    float pitch = Mth.lerp(maxIntensity, 0.8f, 1.5f);
                    mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(), ModSounds.AURA_SCANNER_BEEP.get(), SoundSource.PLAYERS, 0.3f, pitch);
                    beepTimer = delay;
                } else {
                    // No shadow nearby, slow idle beep or no beep?
                    // Let's go with no beep if no signal, or very slow beep if scanner is on.
                    // Actually, "NO SIGNAL" usually means silence in these tropes.
                }
            }
        } else {
            fadeAmount = Math.max(0.0f, fadeAmount - 0.1f);
            beepTimer = 0;
            maxIntensity = 0;
            pulseQueue = 0;
            pulseTimer = 0;
            DETECTED_SHADOWS.clear();
            PENDING_RESPONSES.clear();
        }
    }

    private static float maxIntensity = 0;

    public static void render(GuiGraphics guiGraphics, float partialTick) {
        float baseAlpha = Mth.lerp(partialTick, prevFadeAmount, fadeAmount);
        if (baseAlpha <= 0.0f) return;

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        float time = (mc.level.getGameTime() + partialTick) * 0.05f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float renderAlpha = baseAlpha;
        float currentBootTimer = Mth.lerp(partialTick, prevBootTimer, bootTimer);
        if (currentBootTimer > 0) {
            if ((int) (currentBootTimer * 20) % 2 == 0) renderAlpha *= 0.5f;
        }

        float currentGlitchTimer = Mth.lerp(partialTick, prevGlitchTimer, glitchTimer);
        if (currentGlitchTimer > 0) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((mc.level.random.nextFloat() - 0.5f) * 10, (mc.level.random.nextFloat() - 0.5f) * 10, 0);
            if (mc.level.random.nextFloat() < 0.3f) renderAlpha *= 0.5f;
        }

        guiGraphics.pose().pushPose();
        float yOffset = (1.0f - baseAlpha) * -height;
        guiGraphics.pose().translate(0, yOffset, 0);

        // 1. Overlay (Scanlines, Borders, Notch)
        renderOverlay(guiGraphics, width, height, renderAlpha, time);

        // 2. Central Reticle Rings
        renderReticle(guiGraphics, width / 2, height / 2, renderAlpha, time, partialTick);

        // 3. Shadow Pokémon Directional Indicators & Sweep Arc
        renderDirectionalIndicatorsAndSweep(guiGraphics, width / 2, height / 2, renderAlpha, time, partialTick);

        // 4. Signal Strength Readout
        renderSignalStrength(guiGraphics, width, height, renderAlpha);

        // 5. Info Pane
        //renderInfoPane(guiGraphics, width, height, renderAlpha, partialTick);

        guiGraphics.pose().popPose();

        if (currentGlitchTimer > 0) {
            guiGraphics.pose().popPose();
        }

        RenderSystem.disableBlend();
    }

    private static void renderOverlay(GuiGraphics guiGraphics, int width, int height, float alpha, float time) {
        // Draw scan lines
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        int interlacePos = (int) (Math.ceil(((time * 20) % 14) * 0.5) * 0.5);
        for (int i = 0; i < height; i++) {
            if (i % 4 == 0) {
                guiGraphics.blit(SCANLINES, 0, i - interlacePos, 0, 0, width, 4, width, 4);
            }
        }

        // Draw borders
        // Top left corner
        guiGraphics.blit(SCAN_OVERLAY_CORNERS, 0, 0, 0, 0, 4, 4, 8, 8);
        // Top right corner
        guiGraphics.blit(SCAN_OVERLAY_CORNERS, width - 4, 0, 4, 0, 4, 4, 8, 8);
        // Bottom left corner
        guiGraphics.blit(SCAN_OVERLAY_CORNERS, 0, height - 4, 0, 4, 4, 4, 8, 8);
        // Bottom right corner
        guiGraphics.blit(SCAN_OVERLAY_CORNERS, width - 4, height - 4, 4, 4, 4, 4, 8, 8);

        // Border sides
        int notchStartX = (width - SCAN_OVERLAY_NOTCH_WIDTH) / 2;
        guiGraphics.blit(SCAN_OVERLAY_TOP, 4, 0, 0, 0, notchStartX - 4, 3, notchStartX - 4, 3);
        guiGraphics.blit(SCAN_OVERLAY_TOP, notchStartX + SCAN_OVERLAY_NOTCH_WIDTH, 0, 0, 0, width - (notchStartX + SCAN_OVERLAY_NOTCH_WIDTH + 4), 3, width - (notchStartX + SCAN_OVERLAY_NOTCH_WIDTH + 4), 3);
        guiGraphics.blit(SCAN_OVERLAY_BOTTOM, 4, height - 3, 0, 0, width - 8, 3, width - 8, 3);
        guiGraphics.blit(SCAN_OVERLAY_LEFT, 0, 4, 0, 0, 3, height - 8, 3, height - 8);
        guiGraphics.blit(SCAN_OVERLAY_RIGHT, width - 3, 4, 0, 0, 3, height - 8, 3, height - 8);
        guiGraphics.blit(SCAN_OVERLAY_NOTCH, notchStartX, 0, 0, 0, SCAN_OVERLAY_NOTCH_WIDTH, 12, SCAN_OVERLAY_NOTCH_WIDTH, 12);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void renderReticle(GuiGraphics guiGraphics, int centerX, int centerY, float alpha, float time, float partialTick) {
        float currentBootTimer = Mth.lerp(partialTick, prevBootTimer, bootTimer);
        float scale = 1.0f;
        if (currentBootTimer > 0) {
            scale = 1.0f - (currentBootTimer / BOOT_DURATION);
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);

        float rotation = (time * 20) % 360;

        // Outer ring (Cobblemon style: SCAN_RING_OUTER_DIAMETER = 116)
        guiGraphics.pose().pushPose();
        //guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-rotation * 0.5f));
        RenderSystem.setShaderColor(0.0f, 1.0f, 1.0f, alpha);
        guiGraphics.blit(SCAN_RING_OUTER, -58, -58, 0, 0, 116, 116, 116, 116);
        guiGraphics.pose().popPose();

        // Middle ring segments (Cobblemon style: 40 segments)
        int segments = 40;
        for (int i = 0; i < segments; i++) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) (i * 4.5 + rotation * 0.5)));
            RenderSystem.setShaderColor(0.0f, 0.8f, 1.0f, alpha * 0.8f);
            // Middle ring is 100x1
            guiGraphics.blit(SCAN_RING_MIDDLE, -50, 0, 0, 0, 100, 1, 100, 1);
            guiGraphics.pose().popPose();
        }

        // Inner ring (Cobblemon style: SCAN_RING_INNER_DIAMETER = 84)
        guiGraphics.pose().pushPose();
       // guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-rotation));
        RenderSystem.setShaderColor(0.0f, 0.6f, 1.0f, alpha * 0.6f);
        guiGraphics.blit(SCAN_RING_INNER, -42, -42, 0, 0, 84, 84, 84, 84);
        guiGraphics.pose().popPose();

        // Unknown mark in center during early scanning
        float currentScanningProgress = Mth.lerp(partialTick, prevScanningProgress, scanningProgress);
        if (scannedPokemon != null && currentScanningProgress < 0.5f) {
            float markAlpha = (0.5f - currentScanningProgress) * 2.0f * alpha;
            RenderSystem.setShaderColor(0.0f, 1.0f, 1.0f, markAlpha);
            // UNKNOWN_MARK is 34x46
            guiGraphics.blit(UNKNOWN_MARK, -17, -23, 0, 0, 34, 46, 34, 46);
        }

        guiGraphics.pose().popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void renderDirectionalIndicatorsAndSweep(GuiGraphics guiGraphics, int centerX, int centerY, float alpha, float time, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        float currentSweep = Mth.lerp(partialTick, prevSweepAngle, sweepAngle);
        float currentGlitchTimer = Mth.lerp(partialTick, prevGlitchTimer, glitchTimer);
        if (currentGlitchTimer > 0) {
            currentSweep += (mc.level.random.nextFloat() - 0.5f) * 0.5f;
        }
        
        // Render Sweep Arc
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0);
        guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(currentSweep));
        RenderSystem.setShaderColor(0.0f, 1.0f, 1.0f, alpha * 0.4f);
        guiGraphics.fill(-1, -55, 1, 0, ((int)(alpha * 0.4f * 255) << 24) | 0x00FFFF);
        guiGraphics.pose().popPose();

        int shadowRange = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange();
        List<Entity> nearbyEntities = mc.level.getEntities(null, player.getBoundingBox().inflate(shadowRange));
        for (Entity entity : nearbyEntities) {
            if (entity instanceof PokemonEntity pe && ShadowPokemonData.isShadow(pe) && DETECTED_SHADOWS.containsKey(pe.getUUID())) {
                double dx = entity.getX() - player.getX();
                double dz = entity.getZ() - player.getZ();
                double angle = Math.atan2(dz, dx) - Math.toRadians(player.getYRot()) - Math.PI / 2;
                double dist = Math.sqrt(dx * dx + dz * dz);

                float intensity = (float) Math.max(0, 1.0 - (dist / shadowRange));
                
                // Spike sweep when passing over
                float angleDiff = (float) (angle - currentSweep);
                while (angleDiff < -Math.PI) angleDiff += Math.PI * 2;
                while (angleDiff > Math.PI) angleDiff -= Math.PI * 2;
                
                float spike = Math.max(0, 1.0f - Math.abs(angleDiff) * 5.0f);
                
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(centerX, centerY, 0);
                guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) angle));
                
                // Signal segment
                float segmentAlpha = alpha * intensity * (0.3f + 0.7f * spike);
                // Purple for shadow energy
                RenderSystem.setShaderColor(0.6f, 0.3f, 1.0f, segmentAlpha);
                // SELECT_ARROW is pointing down by default, we flip it vertically so it points UP
                // (away from the center) as requested by using an "upside down" version.
                guiGraphics.blit(SELECT_ARROW, -8, -80, 0, 16, 16, -16, 16, 16);
                
                guiGraphics.pose().popPose();
            }
        }

        // Render Meteoroid Indicators
        int meteoroidRangeIndicator = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerMeteoroidRange();
        for (BlockPos p : DETECTED_METEOROIDS.keySet()) {
            double dx = p.getX() + 0.5 - player.getX();
            double dz = p.getZ() + 0.5 - player.getZ();
            double angle = Math.atan2(dz, dx) - Math.toRadians(player.getYRot()) - Math.PI / 2;
            double dist = Math.sqrt(dx * dx + dz * dz);

            float intensity = (float) Math.max(0, 1.0 - (dist / meteoroidRangeIndicator));

            // Spike sweep when passing over
            float angleDiff = (float) (angle - currentSweep);
            while (angleDiff < -Math.PI) angleDiff += Math.PI * 2;
            while (angleDiff > Math.PI) angleDiff -= Math.PI * 2;

            float spike = Math.max(0, 1.0f - Math.abs(angleDiff) * 5.0f);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, centerY, 0);
            guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) angle));

            // Signal segment
            float segmentAlpha = alpha * intensity * (0.3f + 0.7f * spike);
            // Slightly different purple/pink for meteoroids? 
            // Or just the same to indicate "Shadow Source"
            RenderSystem.setShaderColor(0.8f, 0.2f, 0.9f, segmentAlpha);
            guiGraphics.blit(SELECT_ARROW, -8, -80, 0, 16, 16, -16, 16, 16);

            guiGraphics.pose().popPose();
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private static void renderSignalStrength(GuiGraphics guiGraphics, int width, int height, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        
        String label = "NO SIGNAL";
        int color = 0xAAAAAA;
        if (maxIntensity > 0.8) {
            label = "STRONG SHADOW PRESENCE";
            color = 0xA330FF; // Purple
        } else if (maxIntensity > 0.5) {
            label = "MODERATE DISTURBANCE";
            color = 0x00A3FF; // Blue-Cyan
        } else if (maxIntensity > 0) {
            label = "WEAK SIGNAL";
            color = 0x00FFFF; // Cyan
        }
        
        int textAlpha = (int)(alpha * 255) << 24;

        // Use CENTER_INFO_FRAME for the label
        int frameX = (width - CENTER_INFO_FRAME_WIDTH) / 2;
        int frameY = height / 2 + 85;
        RenderSystem.setShaderColor(0.0f, 1.0f, 1.0f, alpha);
        // CENTER_INFO_FRAME has multiple animation frames vertically, we use the first one (vOffset=0)
        //guiGraphics.blit(CENTER_INFO_FRAME, frameX, frameY, 0, 0, CENTER_INFO_FRAME_WIDTH, CENTER_INFO_FRAME_HEIGHT, CENTER_INFO_FRAME_WIDTH, CENTER_INFO_FRAME_HEIGHT * 6);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        guiGraphics.drawCenteredString(mc.font, label, width / 2, frameY + 4, color | textAlpha);
        
        // Render Waveform
        int waveX = width / 2 - 50;
        int waveY = frameY + 45;
        for (int i = 0; i < 20; i++) {
            float h = (float) Math.sin(mc.level.getGameTime() * 0.5f + i * 0.5f) * 10 * maxIntensity;
            if (glitchTimer > 0) h *= mc.level.random.nextFloat() * 2;
            guiGraphics.fill(waveX + i * 5, waveY, waveX + i * 5 + 2, (int)(waveY - h), (color & 0xFFFFFF) | (textAlpha / 2));
        }

        // Render Charge Bar
        renderChargeBar(guiGraphics, width, height, alpha);
    }

    private static void renderChargeBar(GuiGraphics guiGraphics, int width, int height, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack auraReader = SnagAccessoryBridgeHolder.INSTANCE.getEquippedStack(mc.player);
        if (auraReader.isEmpty() || !(auraReader.getItem() instanceof AuraReaderItem)) {
            auraReader = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        }

        if (auraReader.isEmpty() || !(auraReader.getItem() instanceof AuraReaderItem)) return;

        int charge = AuraReaderCharge.get(auraReader);
        float chargeRatio = (float) charge / (float) AuraReaderItem.MAX_CHARGE;

        int barWidth = 100;
        int barHeight = 4;
        int x = (width - barWidth) / 2;
        int y = height / 2 + 75;

        int textAlpha = (int)(alpha * 255) << 24;

        // Background
        guiGraphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xAA000000 | (textAlpha & 0xFF000000));
        // Fill
        int fillWidth = (int) (barWidth * chargeRatio);
        int color = 0x00FFFF; // Cyan
        if (chargeRatio < 0.2f) color = 0xFF0000; // Red

        guiGraphics.fill(x, y, x + fillWidth, y + barHeight, color | textAlpha);

        // Label
        String chargeText = String.format("CHARGE: %d%%", (int)(chargeRatio * 100));
        guiGraphics.drawCenteredString(mc.font, chargeText, width / 2, y - 10, color | textAlpha);
    }
    
    private static void renderInfoPane(GuiGraphics guiGraphics, int width, int height, float alpha, float partialTick) {
        float currentScanningProgress = Mth.lerp(partialTick, prevScanningProgress, scanningProgress);
        if (currentScanningProgress <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (scannedPokemon == null) return;

        int centerX = width / 2;
        int centerY = height / 2;

        // Render multiple info frames like PokedexScannerRenderer
        // Frame 0: Top outer (Level) - Left side
        renderFrame(guiGraphics, centerX, centerY, true, 0, alpha);
        if (currentScanningProgress > 0.25f) {
            int level = scannedPokemon.getPokemon().getLevel();
            drawFrameText(guiGraphics, mc, "LV. " + level, centerX, centerY, true, 0, alpha);
        }

        // Frame 1: Inner (Species) - Right side
        renderFrame(guiGraphics, centerX, centerY, false, 1, alpha);
        if (currentScanningProgress > 0.5f) {
            String name = scannedPokemon.getPokemon().getSpecies().getName().toUpperCase();
            drawFrameText(guiGraphics, mc, name, centerX, centerY, false, 1, alpha);
        }

        // Frame 3: Bottom outer (Status) - Left side
        renderFrame(guiGraphics, centerX, centerY, true, 3, alpha);
        if (currentScanningProgress > 0.75f) {
            float corruption = ShadowPokemonData.getHeartGauge(scannedPokemon);
            String status = corruption > 0.8f ? "ELITE" : "COMMON";
            if (scannedPokemon.getPokemon().isLegendary()) status = "LEGENDARY";
            drawFrameText(guiGraphics, mc, status, centerX, centerY, true, 3, alpha);
        }
    }

    private static void renderFrame(GuiGraphics guiGraphics, int centerX, int centerY, boolean isLeft, int tier, float alpha) {
        ResourceLocation tex = getInfoFrameResource(isLeft, tier);
        boolean isInner = tier == 1 || tier == 2;
        int frameWidth = isInner ? INNER_INFO_FRAME_WIDTH : OUTER_INFO_FRAME_WIDTH;
        int frameHeight = isInner ? INNER_INFO_FRAME_HEIGHT : OUTER_INFO_FRAME_HEIGHT;

        int xOffset = (isInner ? -177 : -120) + (isLeft ? 0 : (isInner ? 234 : 148));
        int yOffset = switch (tier) {
            case 0 -> -80;
            case 1 -> -26;
            case 2 -> 6;
            case 3 -> 25;
            default -> 0;
        };

        RenderSystem.setShaderColor(0.0f, 1.0f, 1.0f, alpha);
        guiGraphics.blit(tex, centerX + xOffset, centerY + yOffset, 0, 0, frameWidth, frameHeight, frameWidth, frameHeight);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawFrameText(GuiGraphics guiGraphics, Minecraft mc, String text, int centerX, int centerY, boolean isLeft, int tier, float alpha) {
        boolean isInner = tier == 1 || tier == 2;
        int xOffset = (isInner ? -177 : -120) + (isLeft ? 0 : (isInner ? 234 : 148));
        int yOffset = switch (tier) {
            case 0 -> -80;
            case 1 -> -26;
            case 2 -> 6;
            case 3 -> 25;
            default -> 0;
        };

        int frameWidth = isInner ? INNER_INFO_FRAME_WIDTH : OUTER_INFO_FRAME_WIDTH;
        int xOffsetText = isInner ? (((INNER_INFO_FRAME_WIDTH - INNER_INFO_FRAME_STEM_WIDTH) / 2) + (isLeft ? 0 : INNER_INFO_FRAME_STEM_WIDTH)) : (OUTER_INFO_FRAME_WIDTH / 2);
        int yOffsetText = switch (tier) {
            case 0 -> 15;
            case 1 -> 6;
            case 2 -> 6;
            case 3 -> 35;
            default -> 0;
        };

        if (glitchTimer > 0 && mc.level.random.nextFloat() < 0.2f) {
            text = "########";
        }

        int textAlpha = (int) (alpha * 255) << 24;
        guiGraphics.drawCenteredString(mc.font, text, centerX + xOffset + xOffsetText, centerY + yOffset + yOffsetText, 0x00FFFF | textAlpha);
    }
    
    public static boolean isActive() {
        return active && fadeAmount > 0;
    }

    public static void setActive(boolean activeIn) {
        active = activeIn;
        if (active) {
            bootTimer = BOOT_DURATION;
            sweepAngle = 0.0f;
            prevSweepAngle = 0.0f;
        }
    }
}
