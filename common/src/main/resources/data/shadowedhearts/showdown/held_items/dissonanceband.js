({
    name: "Dissonance Band",
    spritename: "dissonanceband",
    onStart(pokemon) {
        if (pokemon.set?.isShadow) pokemon.addVolatile("dissonanceband");
    },
    onUpdate(pokemon) {
        if (pokemon.set?.isShadow && !pokemon.volatiles["dissonanceband"]) {
            pokemon.addVolatile("dissonanceband");
        }
    },
    condition: {
        noCopy: true,
        onStart(pokemon) {
            this.effectState.owner = pokemon.itemState;
            this.effectState.primed = false;
            this.effectState.previousShadow = false;
        },
        onRestart(pokemon) {
            this.effectState.owner = pokemon.itemState;
            this.effectState.primed = false;
            this.effectState.previousShadow = false;
        },
        // This volatile still observes suppression when native item callbacks stop.
        onUpdate(pokemon) {
            if (!pokemon.set?.isShadow || !pokemon.hasItem("dissonanceband")
                || this.effectState.owner !== pokemon.itemState) {
                this.effectState.primed = false;
                this.effectState.previousShadow = false;
                if (!this.effectState.action) pokemon.removeVolatile("dissonanceband");
            }
        },
        onBeforeMovePriority: 100,
        onBeforeMove() {
            delete this.effectState.action;
        },
        onModifyMovePriority: -100,
        onModifyMove(move, pokemon) {
            if (!pokemon.set?.isShadow || !pokemon.hasItem("dissonanceband")
                || this.effectState.owner !== pokemon.itemState) return;
            // ModifyMove follows BeforeMove prevention, including sleep and flinch.
            // Nested called moves share one action; multi-hit attacks share one move.
            const action = this.effectState.action || (this.effectState.action = {});
            if (move.type !== "Shadow" || move.category === "Status" || action.shadow) return;
            action.shadow = true;
            action.recoil = !!this.effectState.previousShadow;
            if (this.effectState.primed) {
                this.effectState.primed = false;
                move.dissonanceBandBoost = true;
            }
        },
        onBasePowerPriority: 16,
        onBasePower(basePower, source, target, move) {
            if (move.dissonanceBandBoost && source.hasItem("dissonanceband")
                && this.effectState.owner === source.itemState) {
                return this.chainModify(1.5);
            }
        },
        onAfterMove(pokemon, target, move) {
            const action = this.effectState.action;
            delete this.effectState.action;
            if (!action) return;
            if (pokemon.set?.isShadow && pokemon.hasItem("dissonanceband")
                && this.effectState.owner === pokemon.itemState) {
                this.effectState.previousShadow = !!action.shadow;
                if (!action.shadow && move.type !== "Shadow" && pokemon.moveThisTurnResult === true) {
                    this.effectState.primed = true;
                }
            } else {
                this.effectState.primed = false;
                this.effectState.previousShadow = false;
            }
            // Once incurred, contact-time item loss cannot erase this action's cost.
            if (action.recoil && pokemon.hp > 0) {
                this.damage(pokemon.baseMaxhp / 8, pokemon, pokemon, this.dex.items.get("dissonanceband"));
            }
        },
    },
    num: -1010,
    gen: 9,
    isNonstandard: "Custom",
})
