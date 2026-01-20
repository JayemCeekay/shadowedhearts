({
    name: "Hyper Mode",
    effectType: "VolatileStatus",
    onStart(pokemon) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.hyperMode || {enabled: false};
        this.add('-start', pokemon, 'Hyper Mode');
        if (pokemon.volatiles['reversemode']) {
            pokemon.removeVolatile('reversemode');
        }
        if (this.turn && this.turn >= 1) {
            if (!config.enabled) {
                delete pokemon.volatiles['hypermode'];
                return;
            }
            this.add('hyper', 'start', pokemon.getSlot());
        }
    },
    onEnd(pokemon) {
        this.add('-end', pokemon, 'Hyper Mode');
        this.add('hyper', 'end', pokemon.getSlot());
    },
    onResidualOrder: 6,
    onResidual(pokemon) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.hyperMode || {enabled: false};
        if (!config.enabled) return;
        if (this.randomChance(1, 256)) {
            this.add('sh_message', 'cobblemon.battle.hypermode.calm', pokemon.getSlot());
            pokemon.removeVolatile('hypermode');
            this.add('hyper', 'end', pokemon.getSlot());
        }
    },
    onFaint(pokemon) {
        if (pokemon.volatiles['hypermode']) {
            pokemon.removeVolatile('hypermode');
            this.add('hyper', 'end', pokemon.getSlot());
        }
    },
    onBeforeMove(source, target, move) {
        if (!move || move.type === 'Shadow') return;
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.hyperMode || {enabled: false};
        if (!config.enabled) return;
        if (this.randomChance(9, 10)) {
            const outcomes = ['otherMove', 'attackAlly', 'selfHurt', 'heldItem', 'yellAtTrainer', 'returnBall'];
            const pick = outcomes[this.random(outcomes.length)];
            const otherKnown = source.moveSlots.filter(m =>
                m.id !== move.id && !m.disabled && m.pp > 0
            );
            switch (pick) {
                case 'otherMove': {
                    if (otherKnown.length) {
                        this.add('sh_message', 'cobblemon.battle.hypermode.disobey', source.getSlot(), move.name);
                        const chosen = otherKnown[this.random(otherKnown.length)].id;
                        move.id = chosen;
                        move.name = this.dex.moves.get(chosen).name;
                        return true;
                    }
                    this.add('sh_message', 'cobblemon.battle.hypermode.refuse', source.getSlot());
                    return false;
                }
                case 'attackAlly': {
                    this.add('sh_message', 'cobblemon.battle.hypermode.attack_ally', source.getSlot());
                    const ally = source.side.active[1] && !source.side.active[1].fainted ? source.side.active[1] : null;
                    if (ally) {
                        if (target && target !== ally) target = ally;
                    }
                    return false;
                }
                case 'selfHurt': {
                    this.add('sh_message', 'cobblemon.battle.hypermode.thrashing', source.getSlot());
                    this.damage(Math.max(1, Math.floor(source.baseMaxhp / 8)), source, source);
                    return false;
                }
                case 'heldItem': {
                    const item = source.getItem();
                    if(item.exists) {
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
    }
})
