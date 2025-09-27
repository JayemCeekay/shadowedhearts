Place your saved Structure Block .nbt files for TEST TRANSITIONS here.

Each JSON transition spec in data/shadowedhearts/rooms/test/transitions/*.json references a structure id like:
  shadowedhearts:test/transitions/corridor_straight_short

This resolves to an .nbt at:
  data/shadowedhearts/structures/test/transitions/corridor_straight_short.nbt

Expected files for the test dungeon:
- corridor_straight_short.nbt (straight 7 long)
- corridor_straight_long.nbt  (straight 15 long)
- junction_T.nbt              (T junction)
- turn_L_90.nbt               (90° turn)
- stairs_up.nbt               (rises by deltaY)
- stairs_down.nbt             (drops by deltaY)

Tip: Keep passage clearance consistent with the JSON (clearance [width,height]).