package com.jayemceekay.shadowedhearts.client.aura.reader;

/** Battle detection opens the visor without changing its saved manual preference. */
public final class AuraReaderBattleActivation {
    private String battleId = "";
    private boolean detected;
    private boolean suppressed;

    public void update(String id, boolean shadowDetected) {
        String next = id == null ? "" : id;
        if (!battleId.equals(next)) suppressed = false;
        battleId = next;
        detected = !next.isEmpty() && shadowDetected;
    }

    public void onManualToggle(boolean active) {
        if (!active && !battleId.isEmpty()) suppressed = true;
    }

    public boolean isActive(boolean manualActive) { return manualActive || (detected && !suppressed); }
    public boolean inBattle() { return !battleId.isEmpty(); }
    public void clear() { update("", false); }
}
