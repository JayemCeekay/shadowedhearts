Theme JSONs group compatible rooms and transitions and provide selection weights and simple placement constraints.

File: data/shadowedhearts/themes/test_dungeon.json
- id: namespaced theme id (shadowedhearts:test_dungeon)
- room_pools.rooms: selectable room ids with weights
- room_pools.transitions: corridor/junction/stairs ids with weights
- placement_rules: early constraints used by the generator (MVP placeholder)

Add more themes by dropping additional JSONs in this folder and referencing your room/transition ids.
