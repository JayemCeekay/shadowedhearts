({
    name: "Hyper Mode",
    effectType: "VolatileStatus",
    noCopy: true,
    onStart(pokemon) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.hyperMode || {enabled: false};
        if (!config.enabled || !pokemon?.set?.isShadow) return false;
        if (this.dex.data.Scripts.shadowedhearts?.Config?.modeRules?.hyperBehavior === 'COLOSSEUM' && !pokemon.set.playerControlled) return false;
        pokemon.set.isHyper = true;
        pokemon.set.isReverse = false;
        this.add('-start', pokemon, 'Hyper Mode');
        if (pokemon.volatiles['reversemode']) {
            pokemon.removeVolatile('reversemode');
        }
        if (this.turn && this.turn >= 1) {
            this.add('hyper', 'start', pokemon.getSlot());
        }
        if (pokemon.hasItem('calmingsachet')) {
            this.singleEvent('Update', pokemon.getItem(), pokemon.itemState, pokemon);
        }
    },
    onEnd(pokemon) {
        pokemon.set.isHyper = false;
        this.add('-end', pokemon, 'Hyper Mode');
        this.add('hyper', 'end', pokemon.getSlot());
    },
    onResidualOrder: 6,
    onResidual(pokemon) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.hyperMode || {enabled: false};
        if (!config.enabled || this.dex.data.Scripts.shadowedhearts?.Config?.modeRules?.hyperBehavior === 'COLOSSEUM') return;
        if (this.randomChance(1, 256)) {
            this.add('sh_message', 'cobblemon.battle.hypermode.calm', pokemon.getSlot());
            pokemon.removeVolatile('hypermode');
        }
    },
    onFaint(pokemon) {
        if (pokemon.volatiles['hypermode']) {
            // Native removeVolatile refuses a zero-HP target, so finish this episode
            // explicitly before revival or a later switch can restore its entry flag.
            pokemon.set.isHyper = false;
            delete pokemon.volatiles['hypermode'];
            this.add('-end', pokemon, 'Hyper Mode');
            this.add('hyper', 'end', pokemon.getSlot());
        }
    },
    onBeforeMove(source, target, move) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.hyperMode || {enabled: false};
        if (!config.enabled) return;
        if (this.dex.data.Scripts.shadowedhearts?.Config?.modeRules?.hyperBehavior === 'COLOSSEUM') {
            return this.dex.conditions.get('hypermode').colosseumAction.call(this, source, target, move);
        }
        if (!move || move.type === 'Shadow') return;
        if (this.randomChance(9, 10)) {
            const outcomes = ['otherMove', 'attackAlly', 'selfHurt', 'heldItem', 'yellAtTrainer', 'returnBall'];
            const pick = outcomes[this.random(outcomes.length)];
            const commandedMove = move.shadowedheartsBaseMove || move.baseMove || move.id;
            const otherKnown = source.moveSlots.filter(m =>
                m.id !== commandedMove && !m.disabled && (m.pp > 0 ||
                    this.dex.data.Scripts.shadowedhearts?.usesUnlimitedShadowPP?.(source, m.id))
            );
            switch (pick) {
                case 'otherMove': {
                    if (otherKnown.length) {
                        const chosen = otherKnown[this.random(otherKnown.length)].id;
                        this.add('sh_message', 'cobblemon.battle.hypermode.disobey', source.getSlot(), 'move:' + chosen);
                        // The native runMove seam replaces the entire base move after
                        // BeforeMove prevention, then handles targeting, PP and events once.
                        move.shadowedheartsAction = {move: chosen};
                        return true;
                    }
                    this.add('sh_message', 'cobblemon.battle.hypermode.refuse', source.getSlot());
                    return false;
                }
                case 'attackAlly': {
                    const allies = source.allies().filter(ally => ally.hp > 0 && !ally.fainted
                        && this.validTargetLoc(source.getLocOf(ally), source, move.target));
                    // Spread/field/self moves cannot be redirected to a single ally.
                    if (!['normal', 'any', 'adjacentAlly', 'adjacentAllyOrSelf'].includes(move.target)
                        || !allies.length) {
                        this.add('sh_message', 'cobblemon.battle.hypermode.refuse', source.getSlot());
                        return false;
                    }
                    const ally = allies[this.random(allies.length)];
                    this.add('sh_message', 'cobblemon.battle.hypermode.attack_ally', source.getSlot());
                    move.shadowedheartsAction = {target: ally};
                    return true;
                }
                case 'selfHurt': {
                    this.add('sh_message', 'cobblemon.battle.hypermode.thrashing', source.getSlot());
                    this.damage(Math.max(1, Math.floor(source.baseMaxhp / 8)), source, source);
                    return false;
                }
                case 'heldItem': {
                    const item = source.getItem();
                    if (item.exists) {
                        this.add('sh_message', 'cobblemon.battle.hypermode.helditem', source.getSlot());
                    } else {
                        this.add('sh_message', 'cobblemon.battle.hypermode.helditem_fail', source.getSlot());
                    }
                    return false;
                }
                case 'yellAtTrainer': {
                    this.add('sh_message', 'cobblemon.battle.hypermode.lash_out', source.getSlot());
                    return false;
                }
                case 'returnBall': {
                    const sw = this.canSwitch(source.side);
                    if (sw) {
                        this.add('sh_message', 'cobblemon.battle.hypermode.return_ball', source.getSlot());
                        source.switchFlag = true;
                        return false;
                    } else {
                        this.add('sh_message', 'cobblemon.battle.hypermode.switch_fail', source.getSlot());
                        return false;
                    }
                }
                default: {
                    this.add('sh_message', 'cobblemon.battle.hypermode.refuse', source.getSlot());
                    return false;
                }
            }
        }
    },
    colosseumWeights(nature) {
        const groups = [
            [['adamant', 'bashful', 'hardy', 'relaxed'], [20,10,10,10,10,10,20,10]],
            [['calm', 'lonely', 'modest', 'timid'], [5,10,5,20,20,5,25,10]],
            [['bold', 'brave', 'lax', 'quirky', 'sassy'], [20,20,20,10,5,5,10,10]],
            [['hasty', 'impish', 'naughty', 'rash'], [30,10,5,5,30,5,5,10]],
            [['careful', 'docile', 'quiet', 'serious'], [12,12,12,12,12,12,12,16]],
            [['gentle', 'jolly', 'mild', 'naive'], [40,5,5,5,5,25,5,10]]
        ];
        return groups.find(group => group[0].includes((nature || '').toLowerCase()))?.[1] || groups[4][1];
    },
    colosseumItem(source) {
        const item = source.getItem();
        if (!source.hasItem(item.id)) return false;
        const healing = ['oranberry', 'sitrusberry', 'figyberry', 'wikiberry', 'magoberry', 'aguavberry', 'iapapaberry'];
        const cures = {cheriberry:'par', chestoberry:'slp', pechaberry:'psn', rawstberry:'brn', aspearberry:'frz'};
        const stats = {liechiberry:'atk', ganlonberry:'def', salacberry:'spe', petayaberry:'spa', apicotberry:'spd'};
        let useful = healing.includes(item.id) && source.hp < source.maxhp;
        if (item.id in cures) useful = source.status === cures[item.id] || item.id === 'pechaberry' && source.status === 'tox';
        if (item.id === 'persimberry') useful = !!source.volatiles.confusion;
        if (item.id === 'lumberry') useful = !!source.status || !!source.volatiles.confusion;
        if (item.id === 'leppaberry') useful = source.moveSlots.some(slot => slot.pp < slot.maxpp);
        if (item.id in stats) useful = source.boosts[stats[item.id]] < 6;
        if (item.id === 'lansatberry') useful = !source.volatiles.focusenergy;
        if (item.id === 'starfberry') useful = ['atk','def','spa','spd','spe'].some(stat => source.boosts[stat] < 6);
        if (useful) return source.eatItem();
        if (item.id === 'berryjuice' && source.hp < source.maxhp) {
            if (source.useItem()) { this.heal(20, source, source, item); return true; }
        }
        if (['whiteherb', 'mentalherb'].includes(item.id)) {
            this.singleEvent('Update', item, source.itemState, source);
            return source.item !== item.id;
        }
        return false;
    },
    colosseumAction(source, target, move) {
        if (this.randomChance(1, 256)) {
            this.add('sh_message', 'cobblemon.battle.hypermode.calm', source.getSlot());
            source.removeVolatile('hypermode');
            source.shadowedheartsCalmedTurn = this.turn;
            return;
        }
        if (!move || move.id === 'shadowrush') return;
        const condition = this.dex.conditions.get('hypermode');
        const weights = condition.colosseumWeights(source.set.nature);
        let roll = this.random(100), outcome = 0;
        for (; outcome < weights.length - 1 && roll >= weights[outcome]; outcome++) roll -= weights[outcome];
        switch (outcome) {
        case 0: {
            const known = source.moveSlots.filter(slot => !slot.disabled && (slot.pp > 0 ||
                this.dex.data.Scripts.shadowedhearts?.usesUnlimitedShadowPP?.(source, slot.id)) &&
                !(source.volatiles.taunt && this.dex.moves.get(slot.id).category === 'Status'));
            if (known.length) {
                const chosen = known[this.random(known.length)].id;
                this.add('sh_message', 'cobblemon.battle.hypermode.disobey', source.getSlot(), 'move:' + chosen);
                move.shadowedheartsAction = {move: chosen};
                return true;
            }
            this.add('sh_message', 'cobblemon.battle.hypermode.refuse', source.getSlot());
            return false;
        }
        case 1:
            this.add('sh_message', 'cobblemon.battle.hypermode.attack_trainer', source.getSlot());
            return false;
        case 2:
            this.add('sh_message', 'cobblemon.battle.hypermode.lash_out', source.getSlot());
            return false;
        case 3: {
            this.add('sh_message', 'cobblemon.battle.hypermode.thrashing', source.getSlot());
            const damage = this.actions.getConfusionDamage(source, 40);
            if (typeof damage === 'number') this.damage(damage, source, source, {id:'confused', effectType:'Move', type:'???'});
            return false;
        }
        case 4:
            this.add('sh_message', 'cobblemon.battle.hypermode.refuse', source.getSlot());
            return false;
        case 5: {
            const success = condition.colosseumItem.call(this, source);
            this.add('sh_message', success ? 'cobblemon.battle.hypermode.helditem' : 'cobblemon.battle.hypermode.helditem_fail', source.getSlot());
            return false;
        }
        case 6: {
            const reserved = this.queue.list.filter(action => ['switch', 'instaswitch'].includes(action.choice)).map(action => action.target);
            const next = source.side.pokemon.find(pokemon => pokemon.hp > 0 && !pokemon.fainted && !pokemon.isActive && !reserved.includes(pokemon));
            if (next) {
                this.add('sh_message', 'cobblemon.battle.hypermode.return_ball', source.getSlot());
                this.actions.switchIn(next, source.position);
            } else {
                this.add('sh_message', 'cobblemon.battle.hypermode.switch_fail', source.getSlot());
            }
            return false;
        }
        default:
            return;
        }
    }
})
