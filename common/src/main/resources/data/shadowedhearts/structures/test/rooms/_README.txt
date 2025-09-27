Place your saved Structure Block .nbt files for TEST ROOMS here.

Each JSON room spec in data/shadowedhearts/rooms/test/*.json references a structure id like:
  shadowedhearts:test/rooms/small_room

This resolves to an .nbt at:
  data/shadowedhearts/structures/test/rooms/small_room.nbt

Expected files for the test dungeon:
- small_room.nbt   (7x5x7)
- medium_room.nbt  (11x7x11)
- large_room.nbt   (15x9x15)

Tip: Origin (0,0,0) of the structure should be the southwest, floor-level corner unless you deliberately offset and then set y_offset in the JSON.
