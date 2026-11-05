module.exports = `
{
    "usesUnlimitedShadowPP": function(pokemon, move) {
        return !!pokemon?.set?.isShadow &&
            !!pokemon.battle.dex.data.Scripts.shadowedhearts?.Config?.shadowBattleRules?.unlimitedShadowPP &&
            pokemon.battle.dex.moves.get(move).type === 'Shadow';
    },
    "usesColosseumCritical": function(pokemon, move) {
        const config = pokemon?.battle.dex.data.Scripts.shadowedhearts?.Config;
        return !!config?.shadowBattleRules?.hyperShadowRushCrit && !!config?.hyperMode?.enabled &&
            !!pokemon?.set?.isShadow && !!pokemon.volatiles.hypermode && move.id === 'shadowrush';
    },
    "actions": {
        "Z_MOVES": {
            "Shadow": "Shadow Supernova"
        },
        "MAX_MOVES": {
            "Shadow": "Max Shadow"
        }
    },
    "call": function(pokemon) {
        this.add('call', pokemon.getSlot());
        let success = false;
        const config = this.dex.data.Scripts.shadowedhearts?.Config || {
            callButton: { accuracyBoost: false, removeSleep: false }
        };
  
        if (pokemon.volatiles['hypermode']) {
            pokemon.removeVolatile('hypermode');
            success = true;
        }
        if (pokemon.volatiles['reversemode']) {
            pokemon.removeVolatile('reversemode');
            success = true;
        }
        if (config.callButton.removeSleep && pokemon.status === 'slp') {
            pokemon.cureStatus();
            success = true;
        }

        if (!success && config.callButton.accuracyBoost) {
            this.add('sh_message', 'cobblemon.battle.perked_up', pokemon.getSlot());
            this.boost({accuracy: 1}, pokemon);
            success = true;
        }
        
        if(!success) {
            this.add('sh_message', 'cobblemon.battle.call.no_effect', pokemon.getSlot());
        }
    }
}
`
