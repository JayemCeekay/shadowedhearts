package com.jayemceekay.shadowedhearts.mixin;

import com.bedrockk.molang.Expression;
import com.cobblemon.mod.common.api.ai.config.task.MoveToOwnerTaskConfig;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.MoveToOwnerTask;
import com.cobblemon.mod.common.util.MoLangExtensionsKt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.behavior.OneShot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MoveToOwnerTaskConfig.class, remap = false)
public abstract class MixinMoveToOwnerTaskConfig {

    private static final String MIN_COMPLETION_RANGE = "52.0";
    private static final String MIN_PARTY_MAX_DISTANCE = "104.0";
    private static final String MIN_OWNED_MAX_DISTANCE = "136.0";
    private static final String MIN_TELEPORT_DISTANCE = "96.0";

    @WrapOperation(
            method = "createTask",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/entity/pokemon/ai/tasks/MoveToOwnerTask;create(Lcom/bedrockk/molang/Expression;Lcom/bedrockk/molang/Expression;Lcom/bedrockk/molang/Expression;Lcom/bedrockk/molang/Expression;Lcom/bedrockk/molang/Expression;)Lnet/minecraft/world/entity/ai/behavior/OneShot;"
            )
    )
    private OneShot<PokemonEntity> shadowedhearts$raiseOwnedPokemonFollowDistance(
            MoveToOwnerTask instance, Expression condition, Expression completionRange, Expression maxDistance, Expression teleportDistance, Expression speedMultiplier, Operation<OneShot<PokemonEntity>> original
    ) {
        return original.call(instance,
                condition,
                shadowedhearts$atLeast(completionRange, MIN_COMPLETION_RANGE),
                shadowedhearts$raisedMaxDistance(maxDistance),
                shadowedhearts$atLeast(teleportDistance, MIN_TELEPORT_DISTANCE),
                speedMultiplier
        );
    }

    private static Expression shadowedhearts$raisedMaxDistance(Expression expression) {
        String original = MoLangExtensionsKt.getString(expression);
        return MoLangExtensionsKt.asExpression(
                "q.entity.is_in_party ? math.max((" + original + "), " + MIN_PARTY_MAX_DISTANCE + ") : math.max((" + original + "), " + MIN_OWNED_MAX_DISTANCE + ")"
        );
    }

    private static Expression shadowedhearts$atLeast(Expression expression, String minimum) {
        String original = MoLangExtensionsKt.getString(expression);
        return MoLangExtensionsKt.asExpression("math.max((" + original + "), " + minimum + ")");
    }
}
