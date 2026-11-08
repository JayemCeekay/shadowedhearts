# Shadow learnsets

Copy this folder into your world's `datapacks` folder and run `/reload`. The included profile applies to Houndour. To target another species, use `data/<species namespace>/shadow_learnsets/<species path>.json`. A higher-priority pack replaces a lower-priority file at the same path.

## Try the Houndour encounter

Spawn a new Houndour with `/spawnpokemon houndour level=30 sh_shadow`. It starts with Shadow Hold and Shadow Fire. A level 20 Shadow Houndour starts with Shadow Rush and Shadow Hold. Corrupting an ordinary Houndour at either level selects the same pair. Try an ordinary level 30 Houndour as well. Shadow Fire is available in its move picker.

## Author a species

- `starting_shadow_slots` accepts 0–4 and defaults to 4. The newest eligible level-up entries win. If several moves unlock together, their order in the file breaks the tie. Earlier eligible entries remain available through the move picker. Set the cap to 0 or put the first entry above the encounter's level to start with ordinary moves only. Such a Pokémon can use its first ordinary move even at a full Heart Gauge. The server's GO Frustration assignment has priority.
- Each `level_up` entry takes a minimum `level` and a registered Shadow-type `move` ID. On an actual level gain, a newly unlocked move can fill an empty slot. A full moveset leaves the player's current choices in place.
- `requires_shadow` defaults to `true`. Set it to `false` to let ordinary members of this species learn and use the move by the listed method. In this example Shadow Fire remains accessible after purification.
- `tm` adds teaching options beyond the level-up list. A string means a Shadow-only move. An object accepts `move`, `requires_shadow`, and `retain_on_purification`. A TM-only move becomes available to remember after the Pokémon learns it. Shadow TMs also require an enabled disc on the server. Shadow Scales draw their Shadow-slot replacements from the eligible profile moves.
- `retain_on_purification` defaults to `false`. Set it to `true` to let an individual keep a move it knows at purification, including a benched move. Teaching Shadow Break to this Houndour gives that individual a move it can remember after purification. Other ordinary Houndour still need their own source of eligibility.

For a trainer encounter, use the Pokémon property `sh_shadow_moves=shadowrush,bite` with `sh_shadow` to specify the entire starting moveset in slot order. A story trainer can use this to lead with a particular move even when the species profile would normally select something else.

Profiles apply to new assignments and current learning eligibility. A successful reload updates connected players and preserves existing active movesets. If a running server rejects a profile, check the server log for the file or species and move ID, fix the file, and reload again. The previous profiles stay in effect until the reload succeeds. Restart the server when introducing a new species or move definition alongside its profile so Cobblemon 1.8.4 can register it.

Species without a profile keep the server's usual assignment and item rules. Servers that buffer Shadow EXP apply the new levels after purification and use the Pokémon's ordinary-state eligibility then.
