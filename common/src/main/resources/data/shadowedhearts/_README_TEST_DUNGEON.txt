ShadowedHearts — Test Dungeon Datapack Scaffolding

Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.

This folder contains a minimal, example datapack layout for a “Test Dungeon” to drive the Missions/Dungeon system during early development.
Nothing here is final content; these are templates and instructions explaining what files you should replace and how.

Where these files live
- Built-in datapack location in this mod: common/src/main/resources/data/shadowedhearts
- Structure templates (for rooms/corridors) must be placed under: data/shadowedhearts/structures/**.nbt
  • Each referenced structure id like shadowedhearts:test/rooms/small_room maps to
    data/shadowedhearts/structures/test/rooms/small_room.nbt

How to produce .nbt structures from a dev world
1) In a creative test world, build the room/corridor using blocks.
2) Place a Structure Block, set mode=SAVE, name to the desired id path (e.g., test/rooms/small_room).
3) Configure the bounding box to tightly surround the build (include air where needed), then click SAVE.
4) The saved file appears in your world save under generated/minecraft/structures/<path>.nbt.
5) Copy the .nbt file into this project at data/shadowedhearts/structures/<path>.nbt.
6) Keep sizes consistent with the JSON specs in data/shadowedhearts/rooms/test/*.json.

What’s included
- rooms/test/*.json — 3 example room specs (small/medium/large), with 4 wall sockets each.
- rooms/test/transitions/*.json — example transitions: straight halls, a T junction, 90° L-turn, stairs up/down.
- themes/test_dungeon.json — a theme that whitelists the above rooms/transitions for easy selection.
- signals/synthesis.json — adds the test theme to synthesis choices (example config; merge with your real one later).

What you need to replace
- Replace the placeholder .json files with real values as you create your .nbt structures.
- Add the corresponding .nbt files under data/shadowedhearts/structures/test/** so StructureTemplateManager can find them.

Conventions used by ShadowedHearts JSON (draft schema)
- Room JSON fields:
  • id: namespaced id of the structure for stamping (shadowedhearts:test/rooms/…)
  • type: "room"
  • size: [x, y, z] in blocks; must match the .nbt bounding box.
  • tags: arbitrary strings for selection/filters (e.g., ["test","basic"]).
  • weight: relative selection weight in the theme.
  • structure: ResourceLocation of the .nbt to stamp.
  • y_offset: vertical offset to place floor level at run baseline (0 = structure origin).
  • rotatable: whether 90° rotations are allowed when placing.
  • sockets: list of door/hall connection points defined relative to the structure bounding box.
    - face: NORTH/SOUTH/EAST/WEST/UP/DOWN
    - x,y,z: local coordinates on the face (0..size-1)
    - width,height: opening dimensions (for fit checks)
    - kind: door | hall | stairs | lift

- Transition JSON fields:
  • id: namespaced id.
  • type: "transition"
  • kind: straight | turn_90 | junction_T | stairs
  • length or deltaY: depends on kind (e.g., stairs specifies vertical change).
  • clearance: [width, height] of the passage space.
  • structure: ResourceLocation of the .nbt.
  • rotatable: allow rotations.
  • connectors: list of connection faces/roles for graph linking.

Note: The current generator only includes scaffolding. These files are to standardize data and unblock content creation; code will consume them in upcoming iterations (see Project Documents/90_Implementation_Roadmap.md).