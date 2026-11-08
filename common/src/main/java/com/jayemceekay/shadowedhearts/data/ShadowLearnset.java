package com.jayemceekay.shadowedhearts.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** One species' level progression and optional additional TM compatibility. */
public record ShadowLearnset(int startingShadowSlots, List<Entry> levelUp, List<Entry> tm) {
    private static final Pattern MOVE_ID = Pattern.compile("[a-z0-9]+");
    private static final Set<String> LEVEL_FIELDS = Set.of("move", "level", "requires_shadow", "retain_on_purification");
    private static final Set<String> TM_FIELDS = Set.of("move", "requires_shadow", "retain_on_purification");

    public record Entry(int level, String move, boolean requiresShadow, boolean retainOnPurification) {
        public Entry {
            if (level < 1) throw new IllegalArgumentException("level must be positive");
            if (move.length() > 32767 || !MOVE_ID.matcher(move).matches()) {
                throw new IllegalArgumentException("Invalid Showdown move ID: " + move);
            }
        }

        boolean available(int currentLevel, boolean shadow) {
            return currentLevel >= level && (shadow || !requiresShadow);
        }
    }

    public ShadowLearnset {
        if (startingShadowSlots < 0 || startingShadowSlots > 4) {
            throw new IllegalArgumentException("starting_shadow_slots must be between 0 and 4");
        }
        // Stable sort: declarations at the same level keep the pack author's order.
        levelUp = levelUp.stream().sorted(Comparator.comparingInt(Entry::level)).toList();
        tm = List.copyOf(tm);
        requireUnique(levelUp, "level_up");
        requireUnique(tm, "tm");
    }

    public List<String> startingMoves(int level) {
        List<String> moves = levelMoves(level, true);
        return moves.subList(Math.max(0, moves.size() - startingShadowSlots), moves.size());
    }

    public List<String> levelMoves(int level, boolean shadow) {
        return levelUp.stream().filter(entry -> entry.available(level, shadow)).map(Entry::move).toList();
    }

    /** Manual teaching may use unlocked level-up entries and explicit TM entries. */
    public boolean permits(String move, int level, boolean shadow) {
        return levelUp.stream().anyMatch(entry -> entry.move.equals(move) && entry.available(level, shadow))
                || tm.stream().anyMatch(entry -> entry.move.equals(move) && entry.available(level, shadow));
    }

    public boolean retains(String move) {
        return levelUp.stream().anyMatch(entry -> entry.move.equals(move) && entry.retainOnPurification)
                || tm.stream().anyMatch(entry -> entry.move.equals(move) && entry.retainOnPurification);
    }

    public static ShadowLearnset parse(JsonElement json) {
        JsonObject object = json.getAsJsonObject();
        checkFields(object, Set.of("starting_shadow_slots", "level_up", "tm"));
        int slots = object.has("starting_shadow_slots") ? integer(object.get("starting_shadow_slots"), "starting_shadow_slots") : 4;
        return new ShadowLearnset(slots, entries(object, "level_up", true), entries(object, "tm", false));
    }

    private static List<Entry> entries(JsonObject root, String key, boolean levelUp) {
        if (!root.has(key)) return List.of();
        JsonArray array = root.getAsJsonArray(key);
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            try {
                if (!levelUp && element.isJsonPrimitive()) {
                    entries.add(new Entry(1, string(element, "move"), true, false));
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                checkFields(object, levelUp ? LEVEL_FIELDS : TM_FIELDS);
                int level = levelUp ? integer(object.get("level"), "level") : 1;
                entries.add(new Entry(level, string(object.get("move"), "move"), bool(object, "requires_shadow", true),
                        bool(object, "retain_on_purification", false)));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(key + "[" + index + "]: " + ex.getMessage(), ex);
            }
        }
        return entries;
    }

    private static void requireUnique(List<Entry> entries, String key) {
        Set<String> seen = new HashSet<>();
        for (Entry entry : entries) {
            if (!seen.add(entry.move)) throw new IllegalArgumentException("Duplicate " + key + " move: " + entry.move);
        }
    }

    private static void checkFields(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown field: " + key);
        }
    }

    private static String string(JsonElement value, String field) {
        if (!(value instanceof JsonPrimitive p) || !p.isString()) throw new IllegalArgumentException(field + " must be a string");
        return value.getAsString();
    }

    private static int integer(JsonElement value, String field) {
        if (!(value instanceof JsonPrimitive p) || !p.isNumber()) throw new IllegalArgumentException(field + " must be an integer");
        try {
            return p.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be an integer", ex);
        }
    }

    private static boolean bool(JsonObject object, String name, boolean fallback) {
        if (!object.has(name)) return fallback;
        if (!(object.get(name) instanceof JsonPrimitive p) || !p.isBoolean()) throw new IllegalArgumentException(name + " must be a boolean");
        return p.getAsBoolean();
    }
}
