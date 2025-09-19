# Implementation Roadmap (Canonical)

Purpose: Provide a clear, actionable plan to reach MVP and beyond, consolidating implementation.md and what_else_needs_implementing_fleshing_ou.md. Terminology reflects canonical docs (Mission Entrance, hybrid corruption rules).

Related:
- 00_Project_Goals.md — decisions and top-level priorities
- 01_Core_Mechanics_Shadow_Purity.md — corruption/shadow mechanics
- 02_Signal_Missions_System.md — signals → missions loop + Mission Entrances
- 03_Mission_Objectives_Threat_Director.md — objective framework and director
- 04_Essence_Affix_Progression.md — affixes and research progression

---

## 1) Current Status Snapshot (from code + drafts)
- Missions dimension: datapack dimension `shadowedhearts:missions` and server-level cache/hook
- Worldspace: per-run bounds allocation, structure placement helper (scaffolding)
- Run scaffolding: RunId/RunBounds/RunConfig/RunParty/RunState and a simple static RunRegistry
- Commands: give_fragment, give_signal, start (temp single-player), tp, gen place/demo, seed/abort stubs
- Items: Signal Fragments and Mission Signal (CustomData) with basic models/lang
- Blocks: Signal Locator and Mission Entrance placeholders with BlockItems/assets
- DungeonGenerator: demo room builder + template stamping helper
- Client: minimal Missions HUD overlay in the missions dimension

---

## 2) MVP Goals
1. Signal Locator synthesis (grid → Mission Signal payload)
2. Mission Entrance placement and consume/start flow
3. RunRegistry lifecycle + persistence + worldspace cleanup
4. Generator MVP: short path of 4–6 rooms; simple doors/corridors
5. Puzzle MVP: Rune Sequence controller + UI
6. Trainers: room spawns + simple unlock gates; overworld trainer drops for fragments
7. Boss MVP: one themed boss; capture/purify window
8. HUD enrichment: timer, theme/tier/affixes, basic compass

---

## 3) Iteration Plan (Actionable Tickets)

Iteration A — Signals & Entrances
- Signal Locator BE + Menu + Screen
  • 3×3 grid, preview, Synthesize button; effects (sound/particles)
  • Server: validate → apply rules → create payload → consume inputs → set output; fire SignalSynthesized
- Data-driven synthesis rules
  • `data/shadowedhearts/signals/synthesis.json` (grid, required, optional, caps, resolve, validation)
- Overworld trainer drops
  • Hook defeat event → roll fragment loot tables; anti-farm cooldowns
- Mission Entrance placement
  • `EntranceSpawner.request(signal, party)`; ring search 600–3000 blocks; scoring filters; bind party + TTL
  • Grant bound `shadowedhearts:signal_compass`

Iteration B — Run Lifecycle & Worldspace
- RunRegistry
  • Allocate RunId; form RunParty; create ActiveRun from Mission Signal payload (RunConfig)
  • Track timers, progression keys/obelisks, boss state in RunState
- Persistence
  • Save ActiveRun to DimensionDataStorage (NBT); helper `getActiveRunByPlayer(UUID)`
- Cleanup
  • AABB wipe (air blocks), despawn entities, clear chunk tickets; teleport party back on finish/abort
- Commands
  • `/shadowmission seed <id>` prints config; `/shadowmission abort <id>` cleans up

Iteration C — Generator & Content
- Datapack scaffolding
  • `data/shadowedhearts/rooms/*.json`, `structures/*` NBTs (3–6 rooms; theme: grave or jungle)
- Pipeline MVP
  • Graph: small chain/T-branch; spatial solver with AABB checks + backtracking; corridors/doors
- Populate
  • Simple chests, puzzle anchors; trainer spawn placeholders
- AffixEngine hooks (stubs)
  • Influence room weights/content; log applied effects

Iteration D — Puzzles, Trainers, Boss
- Puzzles (Rune Sequence)
  • Server PuzzleController + networking; client screen; `puzzles/runes.json`
- Trainers
  • `rosters/*.json`; scale by tier/party; door/lock progression
- Boss MVP
  • Sequential mini-encounters draining shared HP pool; one boss JSON; simple shield phases
  • Capture/Purify window with contribution-based roll and Purifier charge (stub)

Iteration E — UX & Systems Glue
- Scaling functions
  • Map party size, tier, affixes → enemy level delta, room count, timer, boss HP
- Mission HUD enrichment
  • Show timer, affixes, keys/obelisks, compass hint, party status
- Events bus
  • `ShadowsEvents`: SignalSynthesized, RunCreated, RoomCleared, PuzzleSolved, TrainerDefeated, BossPhaseChanged, BossDefeated, CaptureWindowOpened, RunFinished, OverworldTrainerDefeated

---

## 4) Namespaces & Data Layout
- Use `shadowedhearts` namespace consistently
- Dimension id: `shadowedhearts:missions`
- Datapack layout:
```
data/shadowedhearts/
  rooms/ rosters/ puzzles/ bosses/ affixes/ themes/ structures/ signals/ loot_tables/ advancements/ tags/
```

---

## 5) Configuration & Corruption Semantics
- Corruption internal: 0.0–1.0; UI displays 0–100
- Threshold restrictions are server-configurable (default OFF): 25/50/75/100 for breed/trade/XP/full
- Shadow persistence: `shadow_state` remains until explicit purification at 0 corruption

---

## 6) Risks & Integration Concerns
- Networking: define simple, versioned packets early for HUD/puzzles/boss
- Structure sockets vs bounding boxes: validate a tiny room set first; add a debug validator
- Persistence: cleanup on crashes/restarts; resume or purge orphaned runs at server start
- Performance: chunk tickets constrained to run bounds; background stamp distant branches; cache StructureTemplates

---

## 7) Debug & Ops
- Commands (debug):
  • `/shadowmission seed <runId>`
  • `/shadowmission abort <runId>`
  • Optional: weather/traps/corruption debug commands post-MVP

---

## 8) Done Criteria for MVP
- Players can synthesize Mission Signals, locate and reach a Mission Entrance, start a run, complete a short dungeon with trainers, a puzzle, and a boss MVP, and receive rewards. System cleans up and persists state across reloads.
