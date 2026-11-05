({
    name: "Breach Lens",
    spritename: "breachlens",
    onStart(pokemon) {
        if (pokemon.set?.isShadow) pokemon.addVolatile("breachlens");
    },
    onUpdate(pokemon) {
        if (pokemon.set?.isShadow && !pokemon.volatiles["breachlens"]) {
            pokemon.addVolatile("breachlens");
        }
    },
    condition: {
        noCopy: true,
        onStart(pokemon) {
            this.effectState.owner = pokemon.itemState;
            this.effectState.primed = false;
        },
        onRestart(pokemon) {
            this.effectState.owner = pokemon.itemState;
            this.effectState.primed = false;
        },
        onUpdate(pokemon) {
            if (!pokemon.set?.isShadow || !pokemon.hasItem("breachlens")
                || this.effectState.owner !== pokemon.itemState) {
                this.effectState.primed = false;
                if (!this.effectState.action) pokemon.removeVolatile("breachlens");
            }
        },
        onBeforeMovePriority: 100,
        onBeforeMove() {
            delete this.effectState.action;
        },
        onModifyMovePriority: -100,
        onModifyMove(move, pokemon) {
            if (!pokemon.set?.isShadow || !pokemon.hasItem("breachlens")
                || this.effectState.owner !== pokemon.itemState) return;
            const action = this.effectState.action || (this.effectState.action = {});
            if (move.type !== "Shadow" || move.category === "Status" || action.shadow) return;
            action.shadow = true;
            if (this.effectState.primed) {
                this.effectState.primed = false;
                action.breached = true;
                // Native damage handling preserves negative stages, screens and abilities.
                move.ignorePositiveDefensive = true;
            }
        },
        // Resolve after the ordinary Shadow Engine entry check; never start a second episode.
        onAfterMovePriority: -1,
        onAfterMove(pokemon, target, move) {
            const action = this.effectState.action;
            delete this.effectState.action;
            if (!action) return;
            if (pokemon.set?.isShadow && pokemon.hasItem("breachlens")
                && this.effectState.owner === pokemon.itemState) {
                if (!action.shadow && move.type !== "Shadow" && move.category === "Status"
                    && pokemon.moveThisTurnResult === true) {
                    this.effectState.primed = true;
                }
            } else {
                this.effectState.primed = false;
            }
            const config = this.dex.data.Scripts.shadowedhearts?.Config?.reverseMode;
            if (!action.breached || !pokemon.set?.isShadow || !config?.enabled
                || pokemon.fainted || pokemon.hp <= 0
                || pokemon.volatiles["hypermode"] || pokemon.volatiles["reversemode"]) return;
            if (this.randomChance(1, 4)) pokemon.addVolatile("reversemode");
        },
    },
    num: -1011,
    gen: 9,
    isNonstandard: "Custom",
})
