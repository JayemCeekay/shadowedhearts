module.exports = `
{
    "call": function(pokemon) {
        this.add('call', pokemon.getSlot());
        let success = false;
        const config = this.dex.data.Scripts.shadowedhearts_config?.Config || {
            callButton: { accuracyBoost: true, removeSleep: true }
        };

        if (pokemon.volatiles['hypermode']) {
            this.add('sh_message', 'cobblemon.battle.hypermode.calm', pokemon.getSlot());
            pokemon.removeVolatile('hypermode');
            success = true;
        }
        if (pokemon.volatiles['reversemode']) {
            this.add('sh_message', 'cobblemon.battle.reversemode.calm', pokemon.getSlot());
            pokemon.removeVolatile('reversemode');
            success = true;
        }
        if (config.callButton.removeSleep && pokemon.status === 'slp') {
            // Sleep is handled by curestatus automatically when pokemon.cureStatus() is called
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
