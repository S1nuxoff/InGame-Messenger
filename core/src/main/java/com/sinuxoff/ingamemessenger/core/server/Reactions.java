package com.sinuxoff.ingamemessenger.core.server;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Reactions per message: one emoji per player, in the order they were added. */
public final class Reactions {
    private record Reaction(String emoji, String name) {
    }

    private final Map<Integer, LinkedHashMap<UUID, Reaction>> byMessage = new HashMap<>();

    public synchronized void open(int id) {
        byMessage.putIfAbsent(id, new LinkedHashMap<>());
    }

    /** Replays a reaction from the history file ("" removes it). */
    public synchronized void restore(int id, UUID who, String name, String emoji) {
        LinkedHashMap<UUID, Reaction> on = byMessage.computeIfAbsent(id, k -> new LinkedHashMap<>());
        on.remove(who);
        if (!emoji.isEmpty()) {
            on.put(who, new Reaction(emoji, name));
        }
    }

    /** Sets the player's reaction, or removes it if it's the same emoji. Returns the new one ("" = none), null if no such message. */
    public synchronized String toggle(int id, UUID who, String name, String emoji) {
        LinkedHashMap<UUID, Reaction> on = byMessage.get(id);
        if (on == null) {
            return null;
        }
        Reaction had = on.remove(who);
        String now = had == null || !had.emoji().equals(emoji) ? emoji : "";
        if (!now.isEmpty()) {
            on.put(who, new Reaction(emoji, name));
        }
        return now;
    }

    /** Encoded as "emoji:name,name;emoji:name" ("" if none). */
    public synchronized String data(int id) {
        LinkedHashMap<UUID, Reaction> on = byMessage.get(id);
        if (on == null || on.isEmpty()) {
            return "";
        }
        Map<String, StringBuilder> by = new LinkedHashMap<>();
        for (Reaction r : on.values()) {
            StringBuilder sb = by.computeIfAbsent(r.emoji(), k -> new StringBuilder());
            sb.append(sb.isEmpty() ? "" : ",").append(r.name());
        }
        StringBuilder out = new StringBuilder();
        by.forEach((e, names) -> out.append(out.isEmpty() ? "" : ";").append(e).append(':').append(names));
        return out.toString();
    }

    public synchronized void clear() {
        byMessage.clear();
    }
}
