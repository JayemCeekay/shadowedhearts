# ShadowedHearts — Project Goals (Canonical)

This document provides the high-level vision, core pillars, and an index to the detailed system specs for the ShadowedHearts mod. Legacy drafts are preserved in this folder for reference; this file and its siblings are the canonical sources going forward.

## Vision
- Bring a Shadow vs Purity identity to Cobblemon.
- Replace binary shadow mechanics with a meaningful progression spectrum.
- Offer replayable, instanced missions/dungeons with cooperative raid finales.
- Encourage roleplay and PvP/RP incentives without forcing one playstyle.

## Core Pillars
- Corruption ↔ Purity as a progression axis for Pokémon.
- Thematic stat tradeoffs and behavior changes tied to shadow/purity.
- Faction hooks (Shadow vs Purity) influencing events and rewards.
- Instanced Missions Dimension: seeded runs, puzzles, trainers, and raid bosses.

## Systems Overview
- Core Mechanics — Shadow/Purity spectrum, corruption meter, visual identity.
  See: 01_Core_Mechanics_Shadow_Purity.md
- Signals → Missions — Collect fragments, synthesize Mission Signals, use a Mission Entrance to enter instanced runs.
  See: 02_Signal_Missions_System.md
- Mission Objectives & Director — Objectives (Point Defense, Escort, Purge, Boss Raid, etc.) and the Threat/Wave Director.
  See: 03_Mission_Objectives_Threat_Director.md
- Aggro & Combat System — Aggro slots/budgets, AI roles/goals, defender behavior, anti-swarm rules, and weapon combat toggle.
  See: 05_Aggro_Combat_System.md
- Essence & Affixes — Dungeon currency and tech-tree unlocking affixes and customization.
  See: 04_Essence_Affix_Progression.md
- Implementation Roadmap — MVP scope and backlog.
  See: 90_Implementation_Roadmap.md

## World Integration (at a glance)
- Faction bases, server events (Corruption Storms, Purification Festivals).
- NPC trainers as content seeds and lore holders.
- Artifacts, relics, and cosmetics to support long-term progression.

## Implementation Priorities (high-level)
1) Corruption–Purity core mechanics and visual identity
2) Stats/loyalty modifiers tied to corruption
3) Signals → Mission synthesis and Mission Entrance flow
4) Instanced generator with basic rooms, trainers, and one raid boss MVP
5) Faction hooks and scoreboard
6) UX polish: HUD, indicators, particles, compasses

## Decisions — Resolved
1) Portal/Mission Entry Naming
- Standard term: Mission Entrance.
- Meaning: a temporary, themed world structure (e.g., cave mouth, airship/hot air balloon, portal) that acts as the entrance to a mission. It is spawned/placed for a party and removed on completion/expiry, restoring the area to its prior state.

2) Corruption Meter Semantics
- Internal representation: 0.0–1.0 float.
- UI display: 0–100 scale mapped from the internal value.
- Threshold restrictions (breeding/trading/XP) are server-configurable. Default: OFF. When ON, the bands are 25/50/75/100 with the effects described in 01_Core_Mechanics_Shadow_Purity.md.

3) Shadow Persistence Rule
- A Pokémon that becomes Shadow remains Shadow until it undergoes a purification ritual. Having corruption at 0 is a requirement for purification, but does not by itself clear the Shadow state.

These decisions are reflected across all canonical documents and should be used for future code, assets, and content.
