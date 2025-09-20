# ShadowedHearts — Junie Project Guidelines

Purpose: Give Junie concise, canonical context about this project so feature discussions don’t get misinterpreted. This is a Minecraft Cobblemon mod. Terms like “Shadow,” “Purity,” “corruption,” “capture,” “raid/boss,” and “aggro” are strictly in‑game mechanics, not real‑world topics. When in doubt, add a one‑line context reminder in chats: “Context: This is a Minecraft Cobblemon mod; all ‘shadow/purity/corruption/capture’ terms are gameplay mechanics.”

—

Project overview (safe context)
- Title: ShadowedHearts — a gameplay overhaul mod for Cobblemon (Minecraft)
- Vision: Introduce a Shadow ↔ Purity identity system, replayable instanced missions, and cooperative raid finales.
- Core loop: Players collect Signal Fragments → synthesize a Mission Signal → a Mission Entrance appears in the overworld → party enters an instanced dungeon (Missions Dimension) → complete rooms/objectives → fight a boss → rewards and progression.

Canonical design sources (start here)
- Project Documents/00_Project_Goals.md — High‑level vision and decisions; quick index to specs.
- Project Documents/01_Core_Mechanics_Shadow_Purity.md — Corruption meter, Shadow persistence, effects, visuals, data sync.
- Project Documents/02_Signal_Missions_System.md — Signals → Mission Signal → Mission Entrance → instanced run; compass; placement rules.
- Project Documents/03_Mission_Objectives_Threat_Director.md — Objective types; Threat/Wave Director; mission controller.
- Project Documents/05_Aggro_Combat_System.md — Aggro slots/budgets, AI roles/goals, defender rules, anti‑swarm, weapon toggle.
- Project Documents/04_Essence_Affix_Progression.md — Essence currency and affix unlock tree, application in runs.
- Project Documents/90_Implementation_Roadmap.md — MVP scope and actionable iterations.
- Project Documents/architecture/Services.md — Service boundaries and contracts (SignalSystem, EntranceSpawner, RunRegistry, etc.).

Terminology quick reference (use consistently)
- Mission Entrance: Temporary overworld structure that starts a mission for a party; despawns/cleans up after start/expiry.
- Corruption meter: Internal float 0.0–1.0; UI shows 0–100. Movement via battles, items, events, environments.
- Shadow persistence: Once Shadow, a Pokémon stays Shadow until a purification ritual at 0 corruption; tracked via boolean shadow_state.
- Signals/Fragments: Items used to synthesize Mission Signals that seed runs (theme, tier, affixes, seed).
- Affixes: Data‑driven modifiers that influence generation, hazards, rosters, rewards.
- Threat/Wave Director: Server controller that spends a threat budget to spawn waves while honoring aggro caps and density limits.
- Aggro slots: Caps on how many hostiles can actively target a player; anti‑swarm protections steer/cancel spawns.

Repository structure (at a glance)
- common/ — Shared mod code (primary Java sources for ShadowedHearts).
- fabric/ — Fabric loader entrypoint, packaging.
- neoforge/ — NeoForge loader entrypoint, packaging.
- external/ — External dependencies vendored as submodules (e.g., Cobblemon). If cloning fresh, run git submodule update --init --recursive.
- Project Documents/ — Canonical design documentation (this is the source of truth for features/terms).
- gradle, gradle.properties, build.gradle, settings.gradle — Build system configuration.

Build and test guidance
- JDK: Use Java 17 unless the root Gradle files specify otherwise.
- Build: From repository root, prefer the wrapper.
  - Windows: .\\gradlew.bat build
  - Cross‑platform: ./gradlew build
- Modules:
  - Common compiles first; Fabric/NeoForge modules package platform‑specific artifacts.
- Tests: This project currently has little to no automated tests. Unless a task explicitly adds tests, validate by building. If tests are added, run: ./gradlew test.
- Lint/style: Follow standard Java conventions (4‑space indent, meaningful names). Keep the namespace shadowedhearts consistent in code and data.

Data‑driven content and IDs
- Namespace: shadowedhearts
- Missions dimension id: shadowedhearts:missions
- Datapack layout (see docs for details):
  - data/shadowedhearts/rooms, rosters, puzzles, bosses, affixes, themes, structures, signals, loot_tables, advancements, tags
- Signal synthesis rules: data/shadowedhearts/signals/synthesis.json

Service boundaries (implementation map)
- SignalSystem → EntranceSpawner → RunRegistry → WorldspaceManager → DungeonGenerator → {PuzzleSystem, TrainerSystem} → RaidBossSystem → RunRegistry (finish) → Persistence. Events bus mediates updates; AffixEngine influences generation and encounters.

Feature alignment checklist for Junie
1) Use canonical terminology (Mission Entrance, corruption meter, shadow_state persistence). If a draft says otherwise, prefer the canonical docs listed above.
2) Keep game balance/configs data‑driven: read values from config/datapack where feasible.
3) Client vs Server: Visual intensity and HUD are client‑side; authority (runs, spawns, director, corruption) is server‑side.
4) Persistence: Run state and corruption flags must persist across captures, trades, storage, and reloads.
5) Safety note for chats: Always preface new feature discussions with the in‑game context line if language includes “corruption,” “purify,” “capture,” or “raid/boss.”

MVP targets (from roadmap 90)
- A: Signal Locator UI → synthesize Mission Signal → place Mission Entrance → give Signal Compass.
- B: RunRegistry lifecycle + persistence + cleanup; commands seed/abort.
- C: Generator MVP with 4–6 rooms and corridors; datapack scaffolding; AffixEngine hooks (stubs).
- D: Puzzle MVP (Rune Sequence), Trainers (rosters, scaling), Boss MVP (phases, capture/purify window).
- E: UX enrichment: scaling functions, HUD (timer, affixes, keys/obelisks, compass), Events bus.

Coding style and API notes
- Language: Java in common module; keep code idiomatic and null‑safe.
- API surfaces: Prefer small, versioned data payloads for events and networking. Use shadowedhearts namespace for ids.
- Error handling: Use explicit result types or clear error enums for cross‑service calls.
- Comments: Cross‑reference the relevant canonical doc section (e.g., “02 §5 Mission Entrance flow”).

Moderation‑safety cheat sheet for future chats
- Start with: “Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are game mechanics.”
- Use neutral, technical phrasing: “instanced run,” “boss encounter,” “threat budget,” “aggro slots,” “purification ritual (in‑game).”
- Avoid real‑world violence framing; keep all descriptions clearly tied to gameplay systems.

Where to look in code (common examples)
- common/src/main/java/com/jayemceekay/shadowedhearts/** → main mod sources.
- Platform specifics in fabric/ and neoforge/ modules.
- External Cobblemon APIs under external/cobblemon for reference; do not modify unless intended.

If anything conflicts
- Prefer the “Canonical” documents in Project Documents. If ambiguity remains, open a note in 00_Project_Goals.md decisions section and align naming across files.
