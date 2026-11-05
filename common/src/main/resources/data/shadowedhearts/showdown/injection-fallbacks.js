// Stock Cobblemon adapters. Existing providers retain ownership of their functions.
(() => {
    const dex = require('./sim/dex').Dex;
    const cobblemon = require('./sim/cobblemon/cobblemon').Cobblemon;
    const mod = () => dex.mod('cobblemon');
    const parse = source => eval('(' + source + '\n)');

    // Native providers may update only the raw tables. Refresh the lookup layer too,
    // so a reload cannot keep serving an earlier cached or registry-owned entry.
    globalThis.shadowedheartsRefreshData = (functionName, id) => {
        const target = mod();
        if (functionName === 'receiveMoveData' || functionName === 'receiveHeldItemData') {
            const move = functionName === 'receiveMoveData';
            const value = (move ? target.data.Moves : target.data.Items)[id];
            if (!value) throw new Error('Missing injected data: ' + functionName + '/' + id);
            const registry = cobblemon.getRegistry(move ? 'move' : 'heldItem');
            registry.register(value, id);
            registry.invalidate();
            (move ? target.moves.moveCache : target.items.itemCache).delete(id);
            // Held items can supply their own volatile condition (for example,
            // Dissonance Band); reloading the item must also refresh that code.
            if (!move) target.conditions.conditionCache.delete(id);
        } else if (functionName === 'receiveConditionData') {
            target.conditions.conditionCache.delete(id);
        } else if (functionName === 'receiveTypeChartData') {
            for (const typedDex of [dex, target]) {
                typedDex.types.typeCache.delete(id);
                typedDex.types.allCache = null;
            }
        }
    };

    if (typeof receiveConditionData === 'undefined') {
        globalThis.receiveConditionData = (id, source) => {
            const target = mod();
            target.data.Conditions[id] = parse(source);
            shadowedheartsRefreshData('receiveConditionData', id);
        };
    }
    if (typeof receiveTypeChartData === 'undefined') {
        globalThis.receiveTypeChartData = (id, source) => {
            const value = parse(source);
            for (const target of [dex, mod()]) {
                target.data.TypeChart[id] = value;
            }
            shadowedheartsRefreshData('receiveTypeChartData', id);
        };
    }
    if (typeof receiveScriptData === 'undefined') {
        globalThis.receiveScriptData = (id, source) => {
            const scripts = mod().data.Scripts;
            scripts[id] = Object.assign(scripts[id] || {}, parse(source));
        };
    }
    if (typeof receiveMoveData === 'undefined') {
        globalThis.receiveMoveData = (id, source) => {
            const value = parse(source);
            const target = mod();
            target.data.Moves[id] = value;
            shadowedheartsRefreshData('receiveMoveData', id);
        };
    }
    if (typeof receiveHeldItemData === 'undefined') {
        globalThis.receiveHeldItemData = (id, source) => {
            const value = parse(source);
            const target = mod();
            target.data.Items[id] = value;
            shadowedheartsRefreshData('receiveHeldItemData', id);
        };
    }
    // Mega's mapping API edits its module-level maps. Stock has per-battle maps and
    // no Mega mechanics integration, so do not install a fictitious mapping API.
})();
