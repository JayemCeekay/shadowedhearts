# Signals → Missions System (Canonical)

Purpose: Define the end-to-end loop from overworld Signals to instanced Missions, including fragments, synthesis, Mission Entrances, instancing, compass mechanics, and integration points. This consolidates overlapping drafts and serves as the canonical spec.

Primary sources consolidated:
- combined_signal_missions_system_clean.md, combined_signal_missions_system_with_entrances.md, shadowed_hearts_signal_missions_system.md
- signal_missions_system.md (PMD-inspired mechanics, entrance/compass details)
- what_else_needs_implementing_fleshing_ou.md (MVP gaps and data layout)

---

## 0) Terminology
- Signal Fragment — item shards encoding theme/tier/affix hints, dropped by overworld NPC trainers.
- Mission Signal (Sigil) — crafted/assembled key that seeds a run and carries payload (theme, tier, affixes, seed).
- Mission Entrance — a temporary, themed world structure (e.g., cave mouth, airship/hot air balloon, portal) spawned for a party after synthesizing a Mission Signal; it teleports the party into the mission. It despawns on run start/expiry and restores the area.
- Missions Dimension — server-owned dimension hosting many simultaneous instanced runs.
- Run — a single instance of a dungeon: room graph, puzzles, trainers, boss, timers, affixes.

---

## 1) Player Loops

### 1.1 Overworld Loop (Signal Hunting)
1) Randomly spawned NPC Trainers appear in biomes by weights/time of day.
2) Defeat trainers → they drop 1–3 Signal Fragments (plus usual rewards), with anti-farm rules.
3) Combine fragments in the Signal Locator block UI to synthesize a Mission Signal with:
   - Theme (e.g., Grave, Jungle Ruins, Crystal Cavern, Industrial Lab)
   - Tier (I–V)
   - Affix set (e.g., Shadow Surge, Haze, Labyrinthine, Elites)
   - Optional Puzzle bias
4) Synthesizing a Mission Signal triggers placement of a Mission Entrance in the overworld and grants a Signal Compass to the party.

### 1.2 Mission Loop (Instanced Dungeon)
1) Party reaches and interacts with the Mission Entrance while holding the Mission Signal.
2) Server allocates a reserved region in the Missions Dimension and generates the dungeon.
3) Clear rooms (trainers, puzzles, events, hazards) → activate gates/obelisks → unlock boss arena.
4) 4‑player raid vs Shadow Boss Pokémon → defeat → Capture Window or Purify Attempt.
5) Loot, faction tallies (Shadow vs Purity), cleanup, and return.

---

## 2) Architecture & Services
- RunRegistry — creates/owns runs; party, seed, affixes, timers, score, checkpoints.
- WorldspaceManager — allocates per‑run bounds inside Missions Dimension; structure stamping; cleanup.
- DungeonGenerator — graph‑first room selection → spatial placement → structure stamping; corridor/door logic.
- PuzzleSystem — deterministic server controllers with small client UIs.
- TrainerSystem — spawns NPC trainers (rooms + overworld) from datapack rosters; defeat and drops.
- RaidBossSystem — 4v1 raid orchestration, boss phases, shields, capture window.
- AffixEngine — mutators influencing spawns, stats, fog, rewards, hazards, traps.
- SignalSystem — fragment items, Signal Locator UI, rules → Mission Signal payload.
- EntranceSpawner — places Mission Entrances in the overworld and binds parties + compasses.
- Persistence — per‑run state and per‑player ledger (faction points, unlocks, pity counters).

---

## 3) Data-Driven Content (Datapack Layout)
```
data/shadowedhearts/
  rooms/                 # JSON room specs (bounds, sockets, tags, weights)
  rosters/               # trainer sets: species, levels, AI tags, rewards
  puzzles/               # puzzle descriptors mapped to controller ids
  bosses/                # boss species, phases, movesets, mechanics
  affixes/               # mutators pool + weights/rules
  themes/                # biome palettes, fog colors, tile set variants
  structures/            # NBT templates used by rooms
  signals/               # fragment tables, synthesis rules, tier mappings
  loot_tables/           # chest/puzzle/boss/overworld trainer drops
  advancements/          # progression beats and tutorials
  tags/blocks,items/     # placement whitelists, loot categories
```
Note: Namespace standardized to shadowedhearts (see what_else_needs_implementing_fleshing_ou.md lines 110–114).

---

## 4) Signals & Fragments

Fragment Types
- Theme Fragments — Grave, Jungle, Crystal, Industrial, etc.
- Tier Fragments — I–V (or numeric 1–5).
- Affix Fragments — e.g., Haze, Labyrinthine, Elites, Shadow Surge, Purifier’s Blessing.
- Puzzle Fragments — bias puzzle families (Runes, Mirrors, Pressure, Pathing).

Example Fragment JSON
```json
{
  "id": "fragment_theme_grave",
  "type": "theme",
  "value": "grave",
  "rarity": "uncommon",
  "weight": 12,
  "affinity": ["undead","shadow"],
  "conflicts": ["theme:jungle","theme:crystal"],
  "lore": "A cold whisper traces these shards."
}
```

Signal Synthesis Rules (signals/synthesis.json)
```json
{
  "grid": [3,3],
  "required": ["theme","tier"],
  "optional": ["affix","puzzle"],
  "caps": {"affix": 3},
  "resolve": {
    "seed": "hash(xor(all_fragment_ids) + party_uuid + server_salt)",
    "theme": "highest_priority(theme)",
    "tier": "max(tier)",
    "affixes": "pickUpTo(3, weightedUnique(affix))",
    "puzzle_bias": "majority(puzzle)"
  },
  "validation": {
    "no_conflicts": true,
    "min_total_rarity": 3
  }
}
```

Signal Locator UI/Flow (MVP)
- BlockEntity + Menu + Screen with 3×3 grid, preview panel, Synthesize button.
- Server validates fragments, applies rules, creates Mission Signal ItemStack with payload; consumes inputs; places output.
- Client shows live preview; effects: sound/particles; optional redstone pulse.

---

## 5) Mission Entrance Flow

Use-on with Mission Signal (MVP)
1) Synthesizing a Mission Signal calls EntranceSpawner.request(signal, party) to place a Mission Entrance.
2) Party travels to the Mission Entrance using a bound Signal Compass.
3) Interact with the Entrance while holding the Mission Signal → RunRegistry.createRun → WorldspaceManager allocates bounds → stamp entry pad → teleport party.
4) Consume Mission Signal; feedback (chat/UI). Error handling for missing dimension.
5) Mission Entrance despawns on run start or TTL expiry; area restored.

Optional Entrance Screen (later)
- Party list, seed preview, recommended level, Start button.

---

## 6) Mission Entrances & Signal Compass

Goals
- Spawn entrances away from player hubs and existing structures; prefer suitable terrain and theme-appropriate biomes.
- Provide a compass-style hunt to reach them.

Entrance Types (themed)
- Shrine/Ruin (Purity/Jungle/Grave variants), Shadow Rift, Crystal Geode Mouth, Derelict Lift/Minehead, Airship/Balloon mooring, etc.

Placement Pipeline (summary)
1) Trigger: Mission Signal synthesized → EntranceSpawner.request(signal, party).
2) Search Region: Center on player; expand ring search to R = [600..3000] blocks (configurable).
3) Candidate Sampling: Sample N candidate chunks on rings; respect theme biome filters.
4) Score candidates: distance preference; player density penalty; player-modification penalty; near-structure rejection; terrain suitability; pathing score.
5) Select & Reserve: pick best; region lock; stamp entrance structure.
6) Bind Party: store EntranceId → party UUIDs; set TTL (e.g., 45 min or until run start).
7) Compass Grant: give each party member a Signal Compass bound to EntranceId.

Anti-Grief / Safety
- Party-only use by default; auto-despawn on run start/TTL expiry; relocation on claim/protection changes.

Compass Mechanics
- Item id: shadowedhearts:signal_compass (NBT: EntranceId, Theme, Tier).
- Points to entrance or nearest loaded proxy; HUD distance bands (Near/Far/Very Far).

Example JSONs
```json
{
  "id": "grave_mausoleum",
  "biomes": ["minecraft:plains","minecraft:forest"],
  "avoid_structures_within": 196,
  "footprint": [13,9,13],
  "flatness": {"radius": 8, "max_delta": 2},
  "terrain": {"allow_liquid": false, "prefer_surface": true},
  "lore_text": "A chill seeps from the cracked stone.",
  "structure": "shadowedhearts:structures/grave/mausoleum"
}
```
```json
{
  "id": "signal_compass",
  "lore": "Whispers toward the awakened entrance.",
  "binds_to": "EntranceId"
}
```

---

## 7) PMD‑Inspired Mechanics (Dungeon Feel)
- Pseudo-turn flow: movement, spawns, hazards tick with player actions.
- Survival pressure: corruption as a timer; consumables to reduce.
- Traps & hazards: random floor traps; weather/status conditions.
- Recruitment mid-dungeon: special conditions allow befriending.
- Orbs/consumables: Warp Orb, Purity Seed, Shadow Bomb, Reveal Orb (dungeon-only).
- Rescue & revival: reviver-like items; optional rescue missions by outside players.
- Boss battles: multi-phase with environmental effects.

Integration knobs live in AffixEngine, Loot Tables, and Director system (see 03_Mission_Objectives_Threat_Director.md).

---

## 8) Scaling & Rewards (MVP hooks)
- Scaling functions map party size, tier, affixes → enemy level delta, room count, timer, boss HP.
- Rewards via loot tables for trainers/rooms/bosses; Shadow vs Purity tallies and season scoreboard variable.

---

## 9) Events & Commands (Debug)
- Events: SignalSynthesized, RunCreated, RoomCleared, PuzzleSolved, TrainerDefeated, BossPhaseChanged, BossDefeated, CaptureWindowOpened, RunFinished, OverworldTrainerDefeated.
- Commands: seed <runId>, abort <runId>, optional weather/traps/corruption debug.

---

## 10) MVP Checklist (from backlog)
- Signal Locator BE + Menu + Screen; synthesis rules (hardcode → data-driven).
- Mission Entrance placement + consume-and-start flow.
- RunRegistry lifecycle + persistence; worldspace cleanup and chunk tickets.
- Generator MVP with 4–6 rooms and corridors; door/lock placeholders.
- Puzzle MVP (Rune Sequence); Room trainers and unlocks; Boss MVP with capture/purify window.
- HUD enrichment: timer, affixes, keys/obelisks, simple compass.
