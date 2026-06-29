package com.jayemceekay.shadowedhearts.common.aura;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexLearnedInformation;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI;
import com.cobblemon.mod.common.client.pokedex.PokedexType;
import com.cobblemon.mod.common.net.messages.client.pokedex.ServerConfirmedRegisterPacket;
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.FinishScanningPacket;
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.StartScanningPacket;
import com.cobblemon.mod.common.pokedex.scanner.PokemonScanner;
import com.cobblemon.mod.common.pokedex.scanner.ScannableEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class PokedexUsageContext {
    public static final float ZOOM_MAX_STEP = 9.0F;
    public static final float ZOOM_TARGET_FOV = 10.0F;
    public static final float ZOOM_BASE_FOV = 80.0F;
    public static final int BLOCK_LENGTH_PER_ZOOM_STAGE = 2;
    public static final int OPEN_SCANNER_BUFFER_TICKS = 5;
    public static final int VIEW_INFO_BUFFER_TICKS = 10;
    public static final int SUCCESS_SCAN_SERVER_TICKS = 15;
    public static final int MAX_SCAN_PROGRESS = 100;
    public static final float TRANSITION_INTERVALS = 12.0F;
    public static final float FOCUS_INTERVALS = 9.0F;
    public static final float CENTER_INFO_DISPLAY_INTERVALS = 5.0F;
    public static final float CENTER_INFO_LINGER_INTERVALS = 35.0F;

    private boolean infoGuiOpen = false;
    private boolean scanningGuiOpen = false;
    private boolean isPokemonInFocusOwned = false;
    private boolean registerCompleted = false;
    @Nullable
    private ResourceLocation scannedSpecies = null;
    @Nullable
    private ScannableEntity scannableEntityInFocus = null;
    private int viewInfoTicks = 0;
    private float scanningProgress = 0.0F;
    private float displayRegisterInfoIntervals = 0.0F;
    private float transitionIntervals = 0.0F;
    private float focusIntervals = 0.0F;
    private float zoomLevel = 0.0F;
    private float zoomModifier = 1.0F;
    private PokedexLearnedInformation newPokemonInfo = PokedexLearnedInformation.NONE;
    private PokedexType type = PokedexType.RED;
    private List<Boolean> availableInfoFrames = new ArrayList<>(Arrays.asList(null, null, null, null));
    //private final PokedexScannerRendererImpl renderer = new PokedexScannerRendererImpl();

    public void stopUsing(int ticksInUse) {
        stopUsing(ticksInUse, null);
    }

    public void stopUsing(int ticksInUse, @Nullable ResourceLocation speciesId) {
        if (ticksInUse < OPEN_SCANNER_BUFFER_TICKS) {
            openPokedexGUI(type, speciesId);
            setInfoGuiOpen(true);
        }
        resetState(false);
    }

    public void renderUpdate(GuiGraphics graphics, DeltaTracker tickCounter) {
        float tickDelta = Minecraft.getInstance().isPaused() ? 0.0F : tickCounter.getRealtimeDeltaTicks();
        float updateInterval = (tickDelta / 20.0F) * (1.0F / 0.0175F);

        if (scanningGuiOpen && viewInfoTicks < VIEW_INFO_BUFFER_TICKS) {
            if (transitionIntervals < TRANSITION_INTERVALS) {
                transitionIntervals = Math.min(transitionIntervals + updateInterval, TRANSITION_INTERVALS);
            }
        } else {
            if (transitionIntervals > 0) {
                if (transitionIntervals == TRANSITION_INTERVALS) {
                    playSound(CobblemonSounds.POKEDEX_SCAN_CLOSE);
                }
                transitionIntervals = Math.max(transitionIntervals - updateInterval, 0.0F);
                if (transitionIntervals <= 0) {
                    if (viewInfoTicks >= VIEW_INFO_BUFFER_TICKS && scannableEntityInFocus != null) {
                        var resolved = scannableEntityInFocus.resolvePokemonScan();
                        openPokedexGUI(type, resolved != null ? resolved.getApparentSpecies().getResourceIdentifier() : null);
                    }
                    resetState();
                }
            }
        }

        if (scannedSpecies != null && scannableEntityInFocus != null) {
            int targetId = scannableEntityInFocus.resolveEntityScan().getId();
            if (scanningProgress == 0.0F) {
                CobblemonNetwork.INSTANCE.sendToServer(new StartScanningPacket(targetId, (int) zoomLevel));
            }
            if (scanningProgress < (MAX_SCAN_PROGRESS + CENTER_INFO_DISPLAY_INTERVALS)) {
                scanningProgress += updateInterval;
            }
            if (scanningProgress >= MAX_SCAN_PROGRESS) {
                CobblemonNetwork.INSTANCE.sendToServer(new FinishScanningPacket(targetId, (int) zoomLevel));
            }
            if (focusIntervals > 0) {
                focusIntervals = Math.max(0.0F, focusIntervals - updateInterval);
            }
        } else {
            if (scannableEntityInFocus != null) {
                if (focusIntervals < FOCUS_INTERVALS) {
                    focusIntervals = Math.min(focusIntervals + updateInterval, FOCUS_INTERVALS);
                }
            } else {
                if (focusIntervals > 0) {
                    focusIntervals = Math.max(0.0F, focusIntervals - updateInterval);
                }
            }
        }

        if (registerCompleted) {
            if (displayRegisterInfoIntervals < (CENTER_INFO_DISPLAY_INTERVALS + CENTER_INFO_LINGER_INTERVALS)) {
                displayRegisterInfoIntervals = Math.min((CENTER_INFO_DISPLAY_INTERVALS + CENTER_INFO_LINGER_INTERVALS), displayRegisterInfoIntervals + updateInterval);
            }
            if (displayRegisterInfoIntervals >= (CENTER_INFO_DISPLAY_INTERVALS + CENTER_INFO_LINGER_INTERVALS)) {
                registerCompleted = false;
            }
        } else {
            if (displayRegisterInfoIntervals > 0) {
                displayRegisterInfoIntervals = Math.max(0.0F, displayRegisterInfoIntervals - updateInterval);
            }
        }

        //renderer.renderScanOverlay(graphics, tickCounter.getRealtimeDeltaTicks());
    }

    public void useTick(LocalPlayer user, int ticksInUse, boolean inUse) {
        tryOpenScanGui(ticksInUse, inUse);
        if (scanningGuiOpen) {
            tryScanPokemon(user);
        }
        if (scannedSpecies != null && scannableEntityInFocus != null) {
            playSound(CobblemonSounds.POKEDEX_SCAN_LOOP);
        }
    }

    public void tryOpenScanGui(int ticksInUse, boolean inUse) {
        if (inUse && ticksInUse == OPEN_SCANNER_BUFFER_TICKS) {
            scanningGuiOpen = true;
            playSound(CobblemonSounds.POKEDEX_SCAN_OPEN);
        }
    }

    public void openPokedexGUI() {
        openPokedexGUI(PokedexType.RED, null);
    }

    public void openPokedexGUI(PokedexType types) {
        openPokedexGUI(types, null);
    }

    public void openPokedexGUI(PokedexType types, @Nullable ResourceLocation speciesId) {
        PokedexGUI.Companion.open(CobblemonClient.INSTANCE.getClientPokedexData(), types, speciesId, null);
        playSound(CobblemonSounds.POKEDEX_OPEN);
    }

    public void attackKeyHeld(boolean isHeld) {
        if (isHeld && scannableEntityInFocus != null && viewInfoTicks < VIEW_INFO_BUFFER_TICKS && scanningProgress == 0.0F) {
            viewInfoTicks++;
            if (viewInfoTicks % 2 == 0) {
                playSound(CobblemonSounds.POKEDEX_SCAN_LOOP);
            }
        } else if (viewInfoTicks > 0 && viewInfoTicks < VIEW_INFO_BUFFER_TICKS) {
            viewInfoTicks--;
        }
    }

    public void tryScanPokemon(LocalPlayer user) {
        ScannableEntity targetScannableEntity = PokemonScanner.INSTANCE.findScannableEntity(user, (int) zoomLevel);
        if (targetScannableEntity != null) {
            if (targetScannableEntity != scannableEntityInFocus) {
                resetFocusedPokemonState();
                scannableEntityInFocus = targetScannableEntity;
                var resolvedPokemon = scannableEntityInFocus.resolvePokemonScan();
                if (resolvedPokemon == null) {
                    resetFocusedPokemonState();
                    return;
                }

                isPokemonInFocusOwned = CobblemonClient.INSTANCE.getClientPokedexData().getHighestKnowledgeForSpecies(resolvedPokemon.getApparentSpecies().getResourceIdentifier()) == PokedexEntryProgress.CAUGHT;

                if (focusIntervals == 0.0F) {
                    availableInfoFrames = new ArrayList<>(Arrays.asList(null, null, null, null));
                    Random random = new Random();
                    for (int i = 0; i <= 2; i++) {
                        int randomIndex = random.nextInt(availableInfoFrames.size());
                        if (availableInfoFrames.get(randomIndex) != null) {
                            randomIndex = -1;
                            for (int j = 0; j < availableInfoFrames.size(); j++) {
                                if (availableInfoFrames.get(j) == null) {
                                    randomIndex = j;
                                    break;
                                }
                            }
                        }
                        if (randomIndex != -1) {
                            availableInfoFrames.set(randomIndex, random.nextBoolean());
                        }
                    }
                }

                newPokemonInfo = CobblemonClient.INSTANCE.getClientPokedexData().getNewInformation(resolvedPokemon);
                if (newPokemonInfo == PokedexLearnedInformation.NONE) {
                    playSound(CobblemonSounds.POKEDEX_SCAN_DETAIL);
                } else {
                    scannedSpecies = resolvedPokemon.getApparentSpecies().getResourceIdentifier();
                }
            }
        } else {
            resetFocusedPokemonState();
        }
    }

    public void onServerConfirmedRegister(ServerConfirmedRegisterPacket packet) {
        if (scannedSpecies != null && scannedSpecies.equals(packet.getSpecies())) {
            newPokemonInfo = packet.getNewInformation();
            registerCompleted = true;
            scannedSpecies = null;
            scanningProgress = 0.0F;
            playSound(newPokemonInfo == PokedexLearnedInformation.SPECIES ? CobblemonSounds.POKEDEX_SCAN_REGISTER_POKEMON : CobblemonSounds.POKEDEX_SCAN_REGISTER_ASPECT);
        }
    }

    public void resetFocusedPokemonState() {
        scannableEntityInFocus = null;
        scannedSpecies = null;
        viewInfoTicks = 0;
        scanningProgress = 0.0F;
        displayRegisterInfoIntervals = 0.0F;
        registerCompleted = false;
        isPokemonInFocusOwned = false;
        newPokemonInfo = PokedexLearnedInformation.NONE;
    }

    public void resetState() {
        resetState(true);
    }

    public void resetState(boolean resetAnimationStates) {
        scanningGuiOpen = false;
        zoomLevel = 0.0F;
        zoomModifier = 1.0F;
        focusIntervals = 0.0F;
        resetFocusedPokemonState();

        if (resetAnimationStates) {
            transitionIntervals = 0.0F;
        }
    }

    public void adjustZoom(double verticalScrollAmount) {
        if ((zoomLevel <= 0.0F && verticalScrollAmount < 0) || (zoomLevel >= ZOOM_MAX_STEP && verticalScrollAmount > 0)) {
            return;
        }

        playSound(CobblemonSounds.POKEDEX_SCAN_ZOOM_INCREMENT);
        zoomLevel = Math.max(0.0F, Math.min(ZOOM_MAX_STEP, zoomLevel + (float) verticalScrollAmount));
        double startLog = Math.log(ZOOM_BASE_FOV);
        double targetLog = Math.log(ZOOM_TARGET_FOV);
        double stepSize = (startLog - targetLog) / ZOOM_MAX_STEP;
        double currentZoomLog = startLog - (zoomLevel * stepSize);
        double currentFov = Math.exp(currentZoomLog);
        zoomModifier = (float) (currentFov / ZOOM_BASE_FOV);
    }

    public float getFovMultiplier() {
        return zoomModifier;
    }

    public void playSound(SoundEvent soundEvent) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F));
    }

    public boolean getInfoGuiOpen() { return infoGuiOpen; }
    public void setInfoGuiOpen(boolean infoGuiOpen) { this.infoGuiOpen = infoGuiOpen; }

    public boolean getScanningGuiOpen() { return scanningGuiOpen; }
    public void setScanningGuiOpen(boolean scanningGuiOpen) { this.scanningGuiOpen = scanningGuiOpen; }

    public boolean isPokemonInFocusOwned() { return isPokemonInFocusOwned; }
    public void setPokemonInFocusOwned(boolean isPokemonInFocusOwned) { this.isPokemonInFocusOwned = isPokemonInFocusOwned; }

    public boolean getRegisterCompleted() { return registerCompleted; }
    public void setRegisterCompleted(boolean registerCompleted) { this.registerCompleted = registerCompleted; }

    @Nullable
    public ResourceLocation getScannedSpecies() { return scannedSpecies; }
    public void setScannedSpecies(@Nullable ResourceLocation scannedSpecies) { this.scannedSpecies = scannedSpecies; }

    @Nullable
    public ScannableEntity getScannableEntityInFocus() { return scannableEntityInFocus; }
    public void setScannableEntityInFocus(@Nullable ScannableEntity scannableEntityInFocus) { this.scannableEntityInFocus = scannableEntityInFocus; }

    public int getViewInfoTicks() { return viewInfoTicks; }
    public void setViewInfoTicks(int viewInfoTicks) { this.viewInfoTicks = viewInfoTicks; }

    public float getScanningProgress() { return scanningProgress; }
    public void setScanningProgress(float scanningProgress) { this.scanningProgress = scanningProgress; }

    public float getDisplayRegisterInfoIntervals() { return displayRegisterInfoIntervals; }
    public void setDisplayRegisterInfoIntervals(float displayRegisterInfoIntervals) { this.displayRegisterInfoIntervals = displayRegisterInfoIntervals; }

    public float getTransitionIntervals() { return transitionIntervals; }
    public void setTransitionIntervals(float transitionIntervals) { this.transitionIntervals = transitionIntervals; }

    public float getFocusIntervals() { return focusIntervals; }
    public void setFocusIntervals(float focusIntervals) { this.focusIntervals = focusIntervals; }

    public float getZoomLevel() { return zoomLevel; }
    public void setZoomLevel(float zoomLevel) { this.zoomLevel = zoomLevel; }

    public float getZoomModifier() { return zoomModifier; }
    public void setZoomModifier(float zoomModifier) { this.zoomModifier = zoomModifier; }

    public PokedexLearnedInformation getNewPokemonInfo() { return newPokemonInfo; }
    public void setNewPokemonInfo(PokedexLearnedInformation newPokemonInfo) { this.newPokemonInfo = newPokemonInfo; }

    public PokedexType getType() { return type; }
    public void setType(PokedexType type) { this.type = type; }

    public List<Boolean> getAvailableInfoFrames() { return availableInfoFrames; }
    public void setAvailableInfoFrames(List<Boolean> availableInfoFrames) { this.availableInfoFrames = availableInfoFrames; }

    //public PokedexScannerRendererImpl getRenderer() { return renderer; }
}
