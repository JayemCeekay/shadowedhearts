package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Optional move policies; the defaults preserve the original assignment and restoration paths. */
public final class MoveLifecycleConfig {
    public static final MoveLifecycleConfig INSTANCE = new MoveLifecycleConfig();
    private ModConfigSpec owner;
    private ModConfigSpec.BooleanValue retainShadowMovesUntilPurification, goMoves, frustrationRemovalAllowed;

    private MoveLifecycleConfig() { }

    public void build(ModConfigSpec.Builder builder) {
        builder.translation("shadowedhearts.configuration.moveLifecycle").push("moveLifecycle");
        retainShadowMovesUntilPurification = builder
                .comment("Keep every assigned Shadow move until the purification ceremony. Ordinary move unlock thresholds still apply.")
                .translation("shadowedhearts.configuration.moveLifecycle.retainShadowMovesUntilPurification")
                .define("retainShadowMovesUntilPurification", false);
        goMoves = builder
                .comment("New corruption assigns Frustration instead of Shadow moves, and purification grants Return. Existing moves are not rewritten when this option changes. Uses Cobblemon's turn-based versions of these moves.")
                .translation("shadowedhearts.configuration.moveLifecycle.goMoves")
                .define("goMoves", false);
        frustrationRemovalAllowed = builder
                .comment("Allow manual replacement of GO-assigned Frustration, for example during a server event. Removed GO Frustration/Return cannot be relearned through ordinary move swapping.")
                .translation("shadowedhearts.configuration.moveLifecycle.frustrationRemovalAllowed")
                .define("frustrationRemovalAllowed", false);
        builder.pop();
    }

    public void bind(ModConfigSpec spec) { owner = spec; }
    private boolean loaded() { return owner != null && owner.isLoaded(); }
    public boolean retainShadowMovesUntilPurification() { return loaded() && retainShadowMovesUntilPurification.get(); }
    public boolean goMoves() { return loaded() && goMoves.get(); }
    public boolean frustrationRemovalAllowed() { return loaded() && frustrationRemovalAllowed.get(); }
}
