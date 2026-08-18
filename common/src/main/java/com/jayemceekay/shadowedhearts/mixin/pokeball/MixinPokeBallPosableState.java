package com.jayemceekay.shadowedhearts.mixin.pokeball;

import com.cobblemon.mod.common.api.scheduling.Schedulable;
import com.cobblemon.mod.common.api.scheduling.ScheduledTask;
import com.cobblemon.mod.common.api.scheduling.SchedulingTracker;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.pokeball.PokeBallPosableState;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallCaptureVfx;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PokeBallPosableState.class, remap = false)
public abstract class MixinPokeBallPosableState {
    private static final float HIT_OPEN_TO_SHUT_SECONDS = 1.75F;
    private static final float HIT_REMAP_EPSILON = 0.002F;

    public ScheduledTask after(float seconds, Function0<Unit> action) {
        float finalSeconds = seconds;
        if (isDarkBallHitCloseStep(seconds) && isDarkBallHitEntity()) {
            finalSeconds = DarkBallCaptureVfx.BALL_ABSORB_END;
        }

        SchedulingTracker tracker = ((Schedulable) (Object) this).getSchedulingTracker();
        return tracker.addTask(
                new ScheduledTask(
                        (task) -> {
                            action.invoke();
                            return Unit.INSTANCE;
                        },
                        null,
                        finalSeconds,
                        -1.0F,
                        1
                )
        );
    }

    private boolean isDarkBallHitCloseStep(float seconds) {
        return Math.abs(seconds - HIT_OPEN_TO_SHUT_SECONDS) <= HIT_REMAP_EPSILON;
    }

    private boolean isDarkBallHitEntity() {
        Entity entity = ((PosableState) (Object) this).getEntity();
        return entity instanceof EmptyPokeBallEntity ball
                && "dark_ball".equals(ball.getPokeBall().getName().getPath());
    }
}
