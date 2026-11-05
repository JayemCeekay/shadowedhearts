({
    name: 'Shadow Engine',
    effectType: 'Field',
    onFieldRestart(field, source, effect) {
        return true;
    },
    onFieldStart(field, source, effect) {
    },
    heartGaugeBars(pokemon) {
        // Live gauge updates carry the exact fraction. Derive the same rounded-percent
        // bars used by the UI, retaining the initial bar field for older team formats.
        const fraction = pokemon?.set?.heartGaugeFraction;
        if (Number.isFinite(fraction)) {
            return Math.ceil(Math.round(Math.max(0, Math.min(1, fraction)) * 100) / 20);
        }
        const bars = Number(pokemon?.set?.heartGaugeBars);
        return Number.isFinite(bars) ? Math.max(0, Math.min(5, Math.round(bars))) : 5;
    },
    onSwitchIn(pokemon) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config || {
            hyperMode: {enabled: false},
            reverseMode: {enabled: false}
        };
        if (pokemon?.set?.isShadow) {
            if (pokemon?.set?.isHyper && !pokemon.volatiles['hypermode'] && config.hyperMode.enabled) {
                pokemon.addVolatile('hypermode');
            }
            if (pokemon?.set?.isReverse && !pokemon.volatiles['reversemode'] && config.reverseMode.enabled) {
                pokemon.addVolatile('reversemode');
            }
        }
    },
    natureTableHyperMode: {
        calm: [30, 25, 20, 15, 10, 5],
        lonely: [30, 25, 20, 15, 10, 5],
        modest: [30, 25, 20, 15, 10, 5],
        timid: [30, 25, 20, 15, 10, 5],
        bold: [50, 40, 30, 20, 10, 5],
        brave: [50, 40, 30, 20, 10, 5],
        lax: [50, 40, 30, 20, 10, 5],
        quirky: [50, 40, 30, 20, 10, 5],
        sassy: [50, 40, 30, 20, 10, 5],
        hasty: [50, 40, 40, 30, 25, 12],
        impish: [50, 40, 40, 30, 25, 12],
        naughty: [50, 40, 40, 30, 25, 12],
        rash: [50, 40, 40, 30, 25, 12],
        careful: [20, 20, 15, 15, 10, 5],
        docile: [20, 20, 15, 15, 10, 5],
        quiet: [20, 20, 15, 15, 10, 5],
        serious: [20, 20, 15, 15, 10, 5],
        gentle: [50, 50, 50, 50, 50, 50],
        jolly: [50, 50, 50, 50, 50, 50],
        mild: [50, 50, 50, 50, 50, 50],
        naive: [50, 50, 50, 50, 50, 50],
        adamant: [30, 70, 70, 70, 50, 25],
        bashful: [30, 70, 70, 70, 50, 25],
        hardy: [30, 70, 70, 70, 50, 25],
        relaxed: [30, 70, 70, 70, 50, 25]
    },
    natureTableReverseMode: {
        adamant: [0, 5, 10, 20, 30, 30],
        brave: [0, 5, 10, 20, 30, 30],
        lonely: [0, 5, 10, 20, 30, 30],
        naughty: [0, 5, 10, 20, 30, 30],
        rash: [0, 5, 10, 20, 30, 30],
        hasty: [0, 5, 10, 20, 30, 30],
        naive: [0, 5, 10, 20, 30, 30],
        jolly: [0, 2, 5, 15, 25, 25],
        mild: [0, 2, 5, 15, 25, 25],
        lax: [0, 2, 5, 15, 25, 25],
        impish: [0, 2, 5, 15, 25, 25],
        bold: [0, 2, 5, 15, 25, 25],
        hardy: [0, 0, 5, 10, 20, 20],
        docile: [0, 0, 5, 10, 20, 20],
        serious: [0, 0, 5, 10, 20, 20],
        bashful: [0, 0, 5, 10, 20, 20],
        quirky: [0, 0, 5, 10, 20, 20],
        careful: [0, 0, 2, 5, 10, 10],
        calm: [0, 0, 2, 5, 10, 10],
        gentle: [0, 0, 2, 5, 10, 10],
        sassy: [0, 0, 2, 5, 10, 10],
        relaxed: [0, 0, 2, 5, 10, 10],
        modest: [0, 0, 0, 2, 5, 5],
        timid: [0, 0, 0, 2, 5, 5],
        quiet: [0, 0, 0, 2, 5, 5]
    },
    xdReverseChance(pokemon, rules) {
        // Original species groups, independently configurable for the expanded Pokedex.
        const groups = [
            ['weepinbell chansey spinarak togepi mareep swinub gulpin baltoy spheal', [0,2,5,10,15,15]],
            ['butterfree vulpix venomoth dugtrio farfetchd seel lickitung lapras natu magcargo houndour seedot shroomish mawile roselia numel snorunt', [0,5,10,15,20,20]],
            ['raticate paras meowth growlithe rapidash magneton grimer shellder marowak hitmonchan rhydon tangela kangaskhan mrmime dragonite pineco swellow makuhita nosepass delcatty manectric carvanha altaria duskull', [0,10,15,20,30,30]],
            ['pidgeotto spearow golduck poliwrath hypno voltorb hitmonlee starmie electabuzz magmar pinsir ledyba teddiursa poochyena ralts sableye lunatone solrock banette', [0,10,15,25,40,40]],
            ['beedrill arbok primeape dodrio exeggutor scyther tauros snorlax articuno zapdos moltres lugia zangoose salamence', [0,10,20,35,50,50]]
        ];
        const normalize = value => (value || '').toLowerCase().replace(/[^a-z0-9]/g, '');
        // Base species avoids copying Transform's entry rates; exact form overrides remain possible.
        const species = normalize(pokemon.baseSpecies.id);
        const base = normalize(pokemon.baseSpecies.baseSpecies);
        const overrides = rules.reverseSpeciesOverrides || {};
        const row = overrides[species] || overrides[base] ||
            groups.find(group => group[0].split(' ').includes(base))?.[1] || rules.reverseFallback || [0,10,15,20,30,30];
        const fraction = pokemon.set.heartGaugeFraction;
        const bands = rules.reverseGaugeBands || [99.9,80,60,40,20];
        // Thresholds are explicit configuration, not a claim that six modern UI bands were present in XD.
        const index = Number.isFinite(fraction) ? bands.filter(boundary => fraction * 100 < boundary).length : 5 - this.heartGaugeBars(pokemon);
        return row[Math.max(0, Math.min(5, index))];
    },
    onBeforeMove(source, target, move) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.hyperMode || {enabled: false};
        if (!config.enabled) return;
        if (!move || move.type !== 'Shadow') return;
        if (!source?.set?.isShadow) return;
        if (this.dex.data.Scripts.shadowedhearts?.Config?.modeRules?.hyperBehavior === 'COLOSSEUM' &&
            (!source.set.playerControlled || move.id !== 'shadowrush' || source.shadowedheartsCalmedTurn === this.turn)) return;
        if (source.volatiles['hypermode'] || source.volatiles['reversemode']) return;
        const nat = (source.set.nature || '').toLowerCase();
        const engine = this.field.getPseudoWeather('shadowengine');
        const bars = engine.heartGaugeBars(source);
        const table = engine.natureTableHyperMode;
        const row = table[nat];
        const idx = 5 - bars;
        const pct = row ? row[idx] : [30, 25, 20, 15, 10, 5][idx];
        if (pct && this.random(100) < pct) {
            source.addVolatile('hypermode');
            // A Calming Sachet can clear the new episode during its Start event.
            if (source.volatiles['hypermode']) return false;
        }
    },
    onAfterMove(pokemon, target, move) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.reverseMode || {enabled: false};
        if (!config.enabled) return;
        // XD checks the acting Shadow after its own move, regardless of move type.
        // AfterMove runs after attempted moves (including misses), but not when
        // sleep, flinching or another BeforeMove effect prevents the move entirely.
        if (!move || !pokemon?.set?.isShadow || pokemon.fainted || pokemon.hp <= 0) return;
        if (pokemon.volatiles['hypermode'] || pokemon.volatiles['reversemode']) return;

        const nat = (pokemon.set.nature || '').toLowerCase();
        const engine = this.field.getPseudoWeather('shadowengine');
        const bars = engine.heartGaugeBars(pokemon);
        const table = engine.natureTableReverseMode;
        const row = table[nat];
        const idx = 5 - bars;
        const rules = this.dex.data.Scripts.shadowedhearts?.Config?.modeRules;
        const pct = rules?.reverseEntry === 'XD_SPECIES' ? engine.xdReverseChance(pokemon, rules) : row ? row[idx] : 0;

        if (pct && this.random(100) < pct) {
            pokemon.addVolatile('reversemode');
        }
    },
    onModifyDamage(damage, source, target, move) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.GOdamageModifier || {enabled: false};
        if (!config.enabled) return;

        const modifierFor = pokemon => {
            if (!pokemon?.set?.isShadow) return 1;
            if (!config.scaleWithHeartGauge) return 1.2;
            // Exact remaining fraction, independent of the rounded five-bar mode thresholds.
            // Older teams without this field retain their full Shadow modifier.
            const fraction = pokemon.set.heartGaugeFraction;
            const remaining = Number.isFinite(fraction) ? Math.max(0, Math.min(1, fraction)) : 1;
            return 1 + 0.2 * remaining;
        };
        // Null Charm cancels only the incoming attacker's GO bonus, independently
        // of its effectiveness ward. hasItem retains native suppression rules.
        const nullCharmBlocksBonus = config.nullCharmCancelsGoBonus !== false && source !== target && target?.hasItem('nullcharm');
        const mod = (nullCharmBlocksBonus ? 1 : modifierFor(source)) * modifierFor(target);
        if (mod !== 1) return this.chainModify(mod);
    },
    onEffectiveness(typeMod, target, type, move) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.shadowMoves || {superEffectiveEnabled: true};
        if (!config.superEffectiveEnabled) return;
        if (!target || move.type !== 'Shadow') return;

        const targetTypes = target.getTypes();
        if (type !== targetTypes[0]) return 0;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
    }
})
