package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.battles.BattleCaptureAction;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureStartPacket;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.google.common.collect.Iterables;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import static com.cobblemon.mod.common.util.LocalizationUtilsKt.lang;

@Mixin(value = EmptyPokeBallEntity.class, priority = 10000)
public abstract class MixinEmptyPokeBallEntity extends ThrowableItemProjectile {


    public MixinEmptyPokeBallEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    protected abstract void drop();

    @Shadow
    @Final
    private static EntityDataAccessor<Vec3> HIT_VELOCITY;

    @Shadow
    @Final
    private static EntityDataAccessor<Vec3> HIT_TARGET_POSITION;

    @Shadow
    protected abstract void attemptCatch(PokemonEntity pokemonEntity);

    /**
     * @author JayemCeekay
     * @reason Fix capture eligibility guard for shadow Pokémon.
     */
    @WrapMethod(method = "onHitEntity")
    public void onHitEntity(EntityHitResult hitResult, Operation<Void> original) {
        if (hitResult.getEntity() instanceof PokemonEntity pokemonEntity && pokemonEntity.getAspects().contains(SHAspects.SHADOW)) {
            if (((EmptyPokeBallEntity) (Object) this).getCaptureState() == EmptyPokeBallEntity.CaptureState.NOT) {
                if (!hitResult.getEntity().level().isClientSide) {
                    if (pokemonEntity.getDelegate() instanceof PokemonServerDelegate delegate) {
                        var battle = delegate.getBattle();
                        Entity owner = ((EmptyPokeBallEntity) (Object) this).getOwner();

                        if ((pokemonEntity.getPokemon().isPlayerOwned())) {
                            owner.sendSystemMessage(lang("capture.not_wild", pokemonEntity.getExposedSpecies().getTranslatedName()).withColor(TextColor.parseColor("red").getOrThrow().getValue()));
                            drop();
                            return;
                        }

                        if (!UncatchableProperty.INSTANCE.isCatchable(pokemonEntity)) {
                            owner.sendSystemMessage(lang("capture.cannot_be_caught").withStyle(ChatFormatting.RED));
                            drop();
                            return;
                        }

                        if (battle != null && owner != null) {
                            if (!((EmptyPokeBallEntity) (Object) this).getAspects().contains("snag_ball") && !pokemonEntity.getPokemon().isWild()) {
                                owner.sendSystemMessage(lang("message.shadowedhearts.snag_machine.requires_snag_ball").withStyle(ChatFormatting.RED));
                                drop();
                                return;
                            }
                            var throwerActor = battle.getActor(owner.getUUID());
                            var hitActor = Iterables.find(battle.getActors(), actor -> actor.isForPokemon(pokemonEntity));
                            var hitBattlePokemon = Iterables.find(hitActor.getActivePokemon(), battlePokemon -> battlePokemon.getBattlePokemon().getEffectedPokemon().getEntity() == pokemonEntity, null);

                            if (throwerActor == null) {
                                owner.sendSystemMessage(lang("capture.in_battle", pokemonEntity.getExposedSpecies().getTranslatedName().withStyle(ChatFormatting.RED)));
                                drop();
                                return;
                            }

                            if (hitActor == null || hitBattlePokemon == null) {
                                drop();
                                return;
                            }

                            var canFitForcedAction = throwerActor.canFitForcedAction();
                            if (!canFitForcedAction) {
                                owner.sendSystemMessage(lang("capture.not_your_turn").withStyle(ChatFormatting.RED));
                                drop();
                                return;
                            }
                            BattleCaptureAction battleCaptureAction = new BattleCaptureAction(battle, hitBattlePokemon, ((EmptyPokeBallEntity) (Object) this));
                            battleCaptureAction.attach();
                            battle.getCaptureActions().add(battleCaptureAction);

                            battle.broadcastChatMessage(
                                    lang("capture.attempted_capture",
                                            throwerActor.getName(),
                                            ((EmptyPokeBallEntity) (Object) this).getPokeBall().item.getDescription(),
                                            pokemonEntity.getExposedSpecies().getTranslatedName()
                                    ).withStyle(ChatFormatting.YELLOW));
                            battle.sendUpdate(new BattleCaptureStartPacket(((EmptyPokeBallEntity) (Object) this).getPokeBall().getName(), ((EmptyPokeBallEntity) (Object) this).getAspects(), hitBattlePokemon.getPNX()));
                            if (owner instanceof ServerPlayer sp) {
                                var cap = com.jayemceekay.shadowedhearts.snag.SnagCaps.get(sp);
                                if (cap.isArmed()) {
                                    cap.setArmed(false);
                                    com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork.sendToPlayer(sp, new com.jayemceekay.shadowedhearts.network.SnagArmedPacket(false));
                                }
                            }
                            throwerActor.forceChoose(PassActionResponse.INSTANCE);
                        } else if (pokemonEntity.isBusy()) {
                            owner.sendSystemMessage(lang("capture.busy", pokemonEntity.getExposedSpecies().getTranslatedName().withStyle(ChatFormatting.RED)));
                            drop();
                            return;
                        } else if (owner instanceof ServerPlayer && BattleRegistry.INSTANCE.getBattleByParticipatingPlayer((ServerPlayer) owner) != null) {
                            owner.sendSystemMessage(lang("capture.in_battle", pokemonEntity.getExposedSpecies().getTranslatedName().withStyle(ChatFormatting.RED)));
                            drop();
                            return;
                        }

                        ((EmptyPokeBallEntity) (Object) this).setCapturingPokemon(pokemonEntity);
                        ((EmptyPokeBallEntity) (Object) this).getEntityData().set(HIT_VELOCITY, ((EmptyPokeBallEntity) (Object) this).getDeltaMovement());
                        ((EmptyPokeBallEntity) (Object) this).getEntityData().set(HIT_TARGET_POSITION, hitResult.getLocation());
                        CobblemonEvents.THROWN_POKEBALL_HIT.postThen(
                                new ThrownPokeballHitEvent(((EmptyPokeBallEntity) (Object) this), pokemonEntity),
                                thrownPokeballHitEvent -> {
                                    if (thrownPokeballHitEvent.isCanceled()) {
                                        drop();
                                    }
                                    return Unit.INSTANCE;
                                },
                                thrownPokeballHitEvent -> {
                                    attemptCatch(pokemonEntity);
                                    return Unit.INSTANCE;
                                }
                        );
                    }
                    return;
                }
            }
            super.onHitEntity(hitResult);
        } else {
            original.call(hitResult);
        }
    }
}
