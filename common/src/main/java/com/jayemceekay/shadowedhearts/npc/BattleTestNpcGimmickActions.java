package com.jayemceekay.shadowedhearts.npc;

import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.platform.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Runtime adapter; returns null whenever the normal NPC AI should retain control. */
public final class BattleTestNpcGimmickActions {
    // Capture the original lead once. Snagging removes it from pokemonList, so its successor must not
    // inherit the test gimmick. Weak keys release this state with each completed battle actor.
    private static final Map<NPCBattleActor, UUID> LEADS = Collections.synchronizedMap(new WeakHashMap<>());

    private BattleTestNpcGimmickActions() {}

    public static MoveActionResponse tryChoose(ActiveBattlePokemon active, ShowdownMoveset moveset,
                                                boolean forceSwitch) {
        if (!(active.getActor() instanceof NPCBattleActor actor)) return null;
        String gimmick = BattleTestNpcGimmicks.gimmickFor(actor.getNpc().getNpc().getId().toString());
        if (gimmick == null || !Platform.isModLoaded("mega_showdown")) return null;
        if (actor.getPokemonList().isEmpty()) return null;
        UUID lead = LEADS.computeIfAbsent(actor, key -> key.getPokemonList().getFirst().getUuid());
        if (forceSwitch || moveset == null || active.isGone() || active.getBattlePokemon() == null
                || !lead.equals(active.getBattlePokemon().getUuid())) return null;

        var pokemon = active.getBattlePokemon().getEffectedPokemon();
        if (ShadowAspectUtil.hasShadowAspect(pokemon)
                && !shadowAllowed(gimmick, ShadowedHeartsConfigs.getInstance().getShadowConfig())) return null;
        if (!available(gimmick, moveset)) return null;

        boolean variant = "zmove".equals(gimmick) || "max".equals(gimmick);
        List<InBattleGimmickMove> variants = "zmove".equals(gimmick)
                ? moveset.getCanZMove() : moveset.getMaxMoves();
        List<BattleTestNpcGimmicks.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < moveset.getMoves().size(); index++) {
            var move = moveset.getMoves().get(index);
            if (move == null) continue;
            var transformed = variants != null && index < variants.size() ? variants.get(index) : null;
            MoveTarget targetType = variant && transformed != null ? transformed.getTarget() : move.getTarget();
            var possibleTargets = targetType.getTargetList().invoke(active);
            var targets = possibleTargets == null ? List.<BattleTestNpcGimmicks.Target>of()
                    : possibleTargets.stream()
                    .filter(target -> target.hasPokemon()
                            && (!(target instanceof ActiveBattlePokemon battleTarget) || !battleTarget.isGone()))
                    .map(target -> new BattleTestNpcGimmicks.Target(target.getPNX(), target.isAllied(active)))
                    .toList();
            candidates.add(new BattleTestNpcGimmicks.Candidate(move.getId(), move.canBeUsed(), move.getPp(),
                    move.mustBeUsed(), transformed != null && !transformed.getDisabled(),
                    possibleTargets != null, targets));
        }
        var choice = BattleTestNpcGimmicks.selectMove(gimmick, candidates);
        if (choice == null) return null;
        var response = new MoveActionResponse(choice.baseMoveId(), choice.targetPnx(), choice.gimmickId());
        return response.isValid(active, moveset, false) ? response : null;
    }

    static boolean available(String gimmick, ShowdownMoveset moveset) {
        return switch (gimmick) {
            case "mega" -> moveset.getCanMegaEvo();
            case "ultra" -> moveset.getCanUltraBurst();
            case "zmove" -> moveset.getCanZMove() != null;
            // Native Dynamax tiles also keep sending 'max' while already transformed.
            case "max" -> moveset.getCanDynamax() || moveset.hasActiveGimmick();
            case "terastal" -> moveset.getCanTerastallize() != null;
            default -> false;
        };
    }

    static boolean shadowAllowed(String gimmick, IShadowConfig config) {
        return switch (gimmick) {
            case "mega" -> config.shadowCanMegaEvolve();
            case "ultra" -> config.shadowCanUltraBurst();
            case "zmove" -> config.shadowCanUseZMoves();
            case "max" -> config.shadowCanDynamax();
            case "terastal" -> config.shadowCanTerastallize();
            default -> false;
        };
    }
}
