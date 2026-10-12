package com.sinuxoff.ingamemessenger.core.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sinuxoff.ingamemessenger.core.text.ChatText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Polls with 2..8 options, single or multiple choice. A poll is a message with JSON {"q", "o": [...], "m"}; votes are
 * stored as bit masks of the chosen options.
 */
public final class PollBook {
    public static final int MAX_OPTIONS = 8, MAX_QUESTION = 200, MAX_OPTION = 80;
    /** Minimum time between two polls from one player. */
    public static final long COOLDOWN_MS = 5000;

    private final Map<UUID, Long> lastCreated = new HashMap<>();

    /** Validates a new poll and returns its JSON, or null if invalid or the player is on cooldown. */
    public synchronized String compose(UUID who, String question, String options, boolean multi, long now) {
        String q = question.strip();
        List<String> clean = new ArrayList<>();
        for (String o : options.split("\n")) {
            String t = o.strip();
            if (!t.isEmpty()) {
                clean.add(t);
            }
        }
        Long last = lastCreated.get(who);
        if (!ChatText.isPlainText(q, MAX_QUESTION) || clean.size() < 2 || clean.size() > MAX_OPTIONS
                || last != null && now - last < COOLDOWN_MS || clean.stream().anyMatch(t -> !ChatText.isPlainText(t, MAX_OPTION))) {
            return null;
        }
        lastCreated.put(who, now);
        JsonObject o = new JsonObject();
        o.addProperty("q", q);
        JsonArray arr = new JsonArray();
        clean.forEach(arr::add);
        o.add("o", arr);
        if (multi) {
            o.addProperty("m", true);
        }
        return o.toString();
    }

    /** New bits after clicking an option: toggles it; single choice replaces the previous one. */
    public static int toggle(int had, int option, boolean multi) {
        int bit = 1 << option;
        return (had & bit) != 0 ? had & ~bit : multi ? had | bit : bit;
    }
}
