### NPC Shadow Injector — Creator Guide

---

### What is it?
The NPC Shadow Injector lets you make any Cobblemon NPC temporarily use Shadow Pokémon for a single battle, without editing their datapack JSON. You toggle behavior by adding string tags/aspects directly on the NPC entity instance (via commands, structure NBT, or scripts).

- Server‑authoritative, applied pre‑battle.
- Per‑encounter only: modifies the battle team, not the stored party.
- Fully compatible with existing `party` providers (`simple`, `pool`, `script`).

---

### Quick start (commands)
1) Target an NPC in‑world and run:
```
/shadow npc tag add @e[type=cobblemon:npc,limit=1,sort=nearest] shadowedhearts:shadow_party
/shadow npc tag add @e[...] shadowedhearts:shadow_mode_convert
/shadow npc tag add @e[...] shadowedhearts:shadow_n2
```
This converts up to 2 of their existing Pokémon to Shadow for the next battle.

2) Append two unique Shadow candidates from a datapack pool at fixed level 30:
```
/shadow npc tag add @e[...] shadowedhearts:shadow_party
/shadow npc tag add @e[...] shadowedhearts:shadow_mode_append
/shadow npc tag add @e[...] shadowedhearts:shadow_n2
/shadow npc tag add @e[...] shadowedhearts:lvl_fixed_30
/shadow npc tag add @e[...] shadowedhearts:pool/yourpack/yourpool
/shadow npc tag add @e[...] shadowedhearts:unique
```

3) Use an NPC Aspect Preset to add multiple tags at once:
```
/shadow npc tag add @e[...] shadowedhearts:shadow_presets/yourpack/battle_convert_two
```
This expands to the preset’s tag list (e.g., enable + convert + n2) just before injection.

---

### Feature overview
- Modes: `append`, `convert` (default), `replace`.
- Count control: `shadowedhearts:shadow_n1` … `shadowedhearts:shadow_n6`.
- Level policy for injected/replaced mons: `lvl_match` (default), `lvl_plus_X`, `lvl_fixed_X`.
- Datapack Shadow Pools: pick candidates from `data/<ns>/shadow_pools/<id>.json` via `shadowedhearts:pool/<ns>/<id>`.
- Unique selection: `shadowedhearts:unique` avoids duplicate species when injecting.
- Convert probability: `shadowedhearts:convert_chance_<0-100>` applies a per‑slot roll in convert mode.
- Determinism: RNG seeded from world seed + NPC UUID + battle UUID.
- Safety: Shadow support aspects (heart gauge, buffers) are auto‑ensured.
- Party size cap: respects max 6.
- NPC Aspect Presets: batch one or more injector tags via `shadowedhearts:shadow_presets/<ns>/<id>`; data‑driven under `data/shadowedhearts/shadow_presets/`.

---

### How it works
- On battle start, the injector reads the NPC’s tags/aspects.
- If `shadowedhearts:shadow_party` is present, it mutates the in‑battle team according to the configured mode, count, level policy, and pool.
- After adding `shadowedhearts:shadow` to any Pokémon, the system calls `PokemonAspectUtil.ensureRequiredShadowAspects(...)` to guarantee a valid Shadow loadout.

---

### Tags you can set on an NPC
Add these as vanilla entity tags (e.g., via `/shadow npc tag add` or structure NBT `Tags`).

Required to enable:
- `shadowedhearts:shadow_party`

Mode (pick one; default is convert if omitted):
- `shadowedhearts:shadow_mode_append`
- `shadowedhearts:shadow_mode_convert`
- `shadowedhearts:shadow_mode_replace`

Count (pick one):
- `shadowedhearts:shadow_n1` … `shadowedhearts:shadow_n6`

Level policy for injected/replaced Pokémon:
- `shadowedhearts:lvl_match` (default)
- `shadowedhearts:lvl_plus_1`, `shadowedhearts:lvl_plus_2`, …
- `shadowedhearts:lvl_fixed_30` (example)

Candidate source (optional; used by append/replace):
- `shadowedhearts:pool/<namespace>/<id>`

Selection modifiers (optional):
- `shadowedhearts:unique` → avoid duplicate species while injecting.
- `shadowedhearts:convert_chance_50` → in convert mode, each eligible slot has a 50% chance to be converted; still bounded by your `shadow_n#` cap.

Preset expansion (optional):
- `shadowedhearts:shadow_presets/<namespace>/<id>` → expands to a list of tags defined in a datapack file; useful to reuse setups like “enable + convert + n2”.

Notes:
- Convert mode retains the existing level of converted mons (level policy applies only to injected/replaced mons).
- If a referenced pool is missing or parses empty, a server warning is logged and the injector falls back to cloning species from the current team for injection/replace.

---

### Modes explained
- `convert` (default):
  - Up to N currently non‑Shadow slots gain the `shadowedhearts:shadow` aspect.
  - Preserves moves/IVs/item/level/etc.
  - Optional: `convert_chance_%` for per‑slot probability.

- `append`:
  - Adds up to N new Shadow Pokémon from a pool (or fallback) without exceeding 6 total.
  - Honors `unique` and the level policy.

- `replace`:
  - Removes up to N existing Pokémon and inserts N Shadow candidates from a pool (or fallback).
  - Honors `unique` and the level policy.

---

### Datapack Shadow Pools
Place files at: `data/<namespace>/shadow_pools/<id>.json`

Minimal schema (simple array or object with `entries`):
```json
{
  "entries": [
    "gligar lvl=28",
    { "pokemon": "sneasel lvl=29", "weight": 2 }
  ]
}
```
- Entries accept Cobblemon `PokemonProperties` strings.
- Weighted entries increase the chance of selection.
- Refer to a pool using NPC tag: `shadowedhearts:pool/yourpack/yourpool`.

Tips:
- You can include full `properties` objects if you need finer control; the loader merges species/level from the `pokemon` string when needed.
- Use `lvl_match` with properties strings that omit `lvl=` to align with the NPC’s intended level.

---

### NPC Aspect Presets
Purpose: batch multiple injector tags into a reusable, data‑driven preset. This is only a convenience layer; the injector ultimately sees the expanded individual tags.

How to use on an NPC:
- Add a single tag: `shadowedhearts:shadow_presets/<ns>/<id>`
- At runtime, this preset is expanded into its contained tags before processing the injector.

Datapack path:
- Files live under the ShadowedHearts namespace, grouped by your content namespace:
  `data/shadowedhearts/shadow_presets/<ns>/<id>.json`

Schema options:
- Simple array of strings:
```json
[
  "shadowedhearts:shadow_party",
  "shadowedhearts:shadow_mode_convert",
  "shadowedhearts:shadow_n2"
]
```
- Object with an `aspects` array:
```json
{ "aspects": [
  "shadowedhearts:shadow_party",
  "shadowedhearts:shadow_mode_append",
  "shadowedhearts:shadow_n1"
]}
```

Notes and rules:
- Presets are additive with any other tags you place directly on the NPC. If both define a mode, the usual “pick one mode” rule applies based on the final expanded set (avoid conflicting modes).
- Unknown or empty presets safely do nothing; check the server log if a preset file is missing.
- Example file included: `data/shadowedhearts/shadow_presets/shadowedhearts/example_shadow_single.json`.

Examples:
- Convert two using a preset:
```
/shadow npc tag add @e[...] shadowedhearts:shadow_presets/yourpack/battle_convert_two
```
- Combine a preset with a per‑encounter tweak:
```
/shadow npc tag add @e[...] shadowedhearts:shadow_presets/yourpack/battle_convert_two
/shadow npc tag add @e[...] shadowedhearts:convert_chance_50
```

### Using the convenience commands
Under the `/shadow` root:
- Add a tag to NPCs:  
  `
  /shadow npc tag add <selector> <tag>
  `
- Remove a tag from NPCs:  
  `
  /shadow npc tag remove <selector> <tag>
  `

Examples:
- Enable injector and convert 3 slots with 50% per‑slot chance:  
  `
  /shadow npc tag add @e[type=cobblemon:npc,limit=1,sort=nearest] shadowedhearts:shadow_party
  /shadow npc tag add @e[...] shadowedhearts:shadow_mode_convert
  /shadow npc tag add @e[...] shadowedhearts:shadow_n3
  /shadow npc tag add @e[...] shadowedhearts:convert_chance_50
  `

- Append 2 unique Shadows from pool at +2 levels over match:
```
/shadow npc tag add @e[...] shadowedhearts:shadow_party
/shadow npc tag add @e[...] shadowedhearts:shadow_mode_append
/shadow npc tag add @e[...] shadowedhearts:shadow_n2
/shadow npc tag add @e[...] shadowedhearts:lvl_plus_2
/shadow npc tag add @e[...] shadowedhearts:pool/yourpack/yourpool
/shadow npc tag add @e[...] shadowedhearts:unique
```

Pro tip: Use selectors like `@e[type=cobblemon:npc,tag=trainer,limit=10]` to batch‑apply tags to groups of NPCs.

---

### Structure NBT and scripts
- Structure NBT: add the strings under the entity’s `Tags` list (vanilla). These will be present whenever the structure spawns the NPC.
- MoLang interaction scripts: at runtime, call functions to add tags then start the battle:
```molang
c.npc.add_tag("shadowedhearts:shadow_party");
c.npc.add_tag("shadowedhearts:shadow_mode_append");
c.npc.add_tag("shadowedhearts:shadow_n2");
c.npc.start_battle(c.player, 'singles');
```

---

### Determinism and RNG
- Selection uses a seed based on world seed + NPC UUID + the battle’s UUID.
- Outcome varies per battle but is deterministic given the same inputs.

---

### Best practices and recommendations
- For consistent “key” encounters (boss trainers), prefer `replace` with a curated pool and `unique`.
- For ambient variety, use `convert` with `convert_chance_XX` to keep team identity while adding Shadow spice.
- Keep levels data‑driven: prefer `lvl_match` unless you purposely want spikes via `lvl_plus_X` or `lvl_fixed_X`.
- Define pools per theme/biome/mision tier under your own namespace to keep content organized.
- Remember the battle cap of 6; plan `append` counts accordingly.

---

### Troubleshooting
- Nothing changed in battle:
  - Ensure the NPC has `shadowedhearts:shadow_party` and a valid mode/count tag.
  - If using a pool, verify the file path and that it loads (check server log for warnings).
- Too many/few Shadows:
  - Confirm your `shadow_n#` tag and that `append` didn’t exceed the team size cap.
- Duplicate species still appear:
  - Add `shadowedhearts:unique`. Note uniqueness is checked against current party species and among injected picks.
- Converted mons lost level/moves:
  - Convert keeps the original Pokémon’s stats and level by design. Use `replace` if you need full control via properties.

---

### FAQ
- Does this edit the NPC’s datapack JSON?  
  No. All changes are per‑battle and controlled by runtime tags/aspects.

- Can I target specific species without pools?  
  Yes, by using a pool that lists only those species, then `append` or `replace` from that pool.

- Do visual effects/HUD change automatically?  
  Yes, once `shadowedhearts:shadow` is applied, the usual client visuals and meters apply; authority remains server‑side.

---

### Reference
- Enable: `shadowedhearts:shadow_party`
- Modes: `shadowedhearts:shadow_mode_append | shadowedhearts:shadow_mode_convert | shadowedhearts:shadow_mode_replace`
- Count: `shadowedhearts:shadow_n1` … `shadowedhearts:shadow_n6`
- Level: `shadowedhearts:lvl_match | shadowedhearts:lvl_plus_X | shadowedhearts:lvl_fixed_X`
- Pool: `shadowedhearts:pool/<ns>/<id>` (file: `data/<ns>/shadow_pools/<id>.json`)
- Modifiers: `shadowedhearts:unique`, `shadowedhearts:convert_chance_<0-100>`
- Presets: `shadowedhearts:shadow_presets/<ns>/<id>` (file: `data/shadowedhearts/shadow_presets/<ns>/<id>.json`)
- Commands: `/shadow npc tag add <selector> <tag>`, `/shadow npc tag remove <selector> <tag>`

---

### Example pool files
- Simple weighted list:
```json
{
  "entries": [
    "murkrow",
    { "pokemon": "houndour", "weight": 3 },
    "zubat lvl=24"
  ]
}
```

- Fully specified properties entry:
```json
{
  "entries": [
    {
      "pokemon": "sneasel",
      "weight": 2,
      "properties": {
        "level": 29,
        "moves": "ice_shard,thief,taunt,brick_break",
        "helditem": "cobblemon:razor_claw"
      }
    }
  ]
}
```

---

### Compatibility and safety
- Works with Cobblemon NPC party providers `simple`, `pool`, `script`.
- Injector runs on the server just before battle begins.
- After adding Shadow, the mod auto‑ensures required support aspects (heart gauge, XP/EV buffers) are present.
- Does not modify stored parties or datapack assets.

---

### Need help?
- Verify tags with `/data get entity <npc> Tags`.
- Check server logs for `shadow_pools` loading warnings.
- If issues persist, share your NPC tags and (if applicable) the pool JSON so others can repro.
