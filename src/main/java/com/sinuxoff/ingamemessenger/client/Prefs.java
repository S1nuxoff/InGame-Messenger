package com.sinuxoff.ingamemessenger.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** The player's own settings, saved in config/ingamemessenger/client.json. */
final class Prefs {
    static final int MIN_LINES = 3, MAX_LINES = 24;
    static final int MIN_HEIGHT = 10, MAX_HEIGHT = 40;
    static final int MIN_SCALE = 50, MAX_SCALE = 150;
    static final int MAX_VOICE_VOLUME = 200;
    private static final int MAX_RECENT = 32, MAX_RECENT_REACTIONS = 16;
    private static final Gson GSON = new Gson();

    private static boolean loaded;

    /** Sticker keys ("pack/id"), oldest first. */
    static final LinkedHashSet<String> recent = new LinkedHashSet<>();
    static final LinkedHashSet<String> favorites = new LinkedHashSet<>();
    private static final LinkedHashSet<String> recentReactions = new LinkedHashSet<>();
    /** UUIDs of players whose messages are hidden. */
    private static final LinkedHashSet<String> muted = new LinkedHashSet<>();

    /** Sticker height in chat lines. */
    private static int stickerLines = 9;
    /** Visible chat height in lines. */
    private static int chatHeight = 20;
    /** Chat scale in percent, on top of Minecraft's chat scale. */
    private static int chatScale = 100;
    private static boolean pinned;
    private static boolean showTyping = true, seeTyping = true;
    private static boolean chatRight, ownRight, keepOpen;
    private static boolean hideHotbar = true, blurBehind = true;
    /** Line spacing was raised once for the bubbles; not again if the player lowers it. */
    private static boolean spacingSet;
    private static int voiceVolume = 100, voiceSpeed = 100;
    private static String micDevice = "", speakerDevice = "";
    private static int micGainDb = 0, noiseGate = 24;
    private static String geminiKey = "";
    private static boolean geminiMemory = true;

    private Prefs() {
    }

    private static Path file() {
        return Platform.get().configDir().resolve("client.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            readSet(o, "recent", recent);
            readSet(o, "favorites", favorites);
            readSet(o, "recentReactions", recentReactions);
            readSet(o, "muted", muted);
            stickerLines = clamp(getInt(o, "stickerLines", stickerLines), MIN_LINES, MAX_LINES);
            chatHeight = clamp(getInt(o, "chatHeight", chatHeight), MIN_HEIGHT, MAX_HEIGHT);
            chatScale = clamp(getInt(o, "chatScale", chatScale), MIN_SCALE, MAX_SCALE);
            pinned = getBool(o, "pinned", pinned);
            showTyping = getBool(o, "showTyping", showTyping);
            seeTyping = getBool(o, "seeTyping", seeTyping);
            chatRight = getBool(o, "chatRight", chatRight);
            ownRight = getBool(o, "ownRight", ownRight);
            keepOpen = getBool(o, "keepOpen", keepOpen);
            hideHotbar = getBool(o, "hideHotbar", hideHotbar);
            blurBehind = getBool(o, "blurBehind", blurBehind);
            spacingSet = getBool(o, "spacingSet", spacingSet);
            voiceVolume = clamp(getInt(o, "voiceVolume", voiceVolume), 0, MAX_VOICE_VOLUME);
            int speed = getInt(o, "voiceSpeed", voiceSpeed);
            voiceSpeed = speed == 150 || speed == 200 ? speed : 100;
            micDevice = getString(o, "micDevice", micDevice);
            speakerDevice = getString(o, "speakerDevice", speakerDevice);
            micGainDb = clamp(getInt(o, "micGainDb", micGainDb), -10, 20);
            noiseGate = clamp(getInt(o, "noiseGate", noiseGate), 0, 100);
            geminiKey = getString(o, "geminiKey", geminiKey);
            geminiMemory = getBool(o, "geminiMemory", geminiMemory);
        } catch (Exception e) {
            InGameMessenger.LOG.warn("Can't read {}: {}", f, e.toString());
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        o.add("recent", array(recent));
        o.add("favorites", array(favorites));
        o.add("recentReactions", array(recentReactions));
        o.add("muted", array(muted));
        o.addProperty("stickerLines", stickerLines);
        o.addProperty("chatHeight", chatHeight);
        o.addProperty("chatScale", chatScale);
        o.addProperty("pinned", pinned);
        o.addProperty("showTyping", showTyping);
        o.addProperty("seeTyping", seeTyping);
        o.addProperty("chatRight", chatRight);
        o.addProperty("ownRight", ownRight);
        o.addProperty("keepOpen", keepOpen);
        o.addProperty("hideHotbar", hideHotbar);
        o.addProperty("blurBehind", blurBehind);
        o.addProperty("spacingSet", spacingSet);
        o.addProperty("voiceVolume", voiceVolume);
        o.addProperty("voiceSpeed", voiceSpeed);
        o.addProperty("micDevice", micDevice);
        o.addProperty("speakerDevice", speakerDevice);
        o.addProperty("micGainDb", micGainDb);
        o.addProperty("noiseGate", noiseGate);
        o.addProperty("geminiKey", geminiKey);
        o.addProperty("geminiMemory", geminiMemory);
        try {
            Files.writeString(file(), GSON.toJson(o));
        } catch (Exception e) {
            InGameMessenger.LOG.warn("Can't save {}: {}", file(), e.toString());
        }
    }

    private static void readSet(JsonObject o, String key, LinkedHashSet<String> into) {
        if (o.has(key)) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                into.add(e.getAsString());
            }
        }
    }

    private static JsonArray array(Iterable<String> values) {
        JsonArray a = new JsonArray();
        values.forEach(a::add);
        return a;
    }

    private static int getInt(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    private static boolean getBool(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // Stickers

    static int stickerLines() {
        load();
        return stickerLines;
    }

    static void setStickerLines(int lines) {
        load();
        stickerLines = clamp(lines, MIN_LINES, MAX_LINES);
        save();
    }

    /** Sticker keys, newest first. */
    static List<String> recentStickers() {
        load();
        List<String> keys = new ArrayList<>(recent);
        Collections.reverse(keys);
        return keys;
    }

    static List<String> favoriteStickers() {
        load();
        return new ArrayList<>(favorites);
    }

    static boolean isFavorite(String key) {
        load();
        return favorites.contains(key);
    }

    static void toggleFavorite(String key) {
        load();
        if (!favorites.remove(key)) {
            favorites.add(key);
        }
        save();
    }

    static void usedSticker(String key) {
        load();
        recent.remove(key);
        recent.add(key);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.iterator().next());
        }
        save();
    }

    /** Newest first. */
    static List<String> recentReactions() {
        load();
        List<String> out = new ArrayList<>(recentReactions);
        Collections.reverse(out);
        return out;
    }

    static void usedReaction(String key) {
        load();
        recentReactions.remove(key);
        recentReactions.add(key);
        while (recentReactions.size() > MAX_RECENT_REACTIONS) {
            recentReactions.remove(recentReactions.iterator().next());
        }
        save();
    }

    // Chat

    static int chatHeight() {
        load();
        return chatHeight;
    }

    static void setChatHeight(int lines) {
        load();
        chatHeight = clamp(lines, MIN_HEIGHT, MAX_HEIGHT);
        save();
    }

    static int chatScale() {
        load();
        return chatScale;
    }

    static void setChatScale(int percent) {
        load();
        int v = clamp(percent, MIN_SCALE, MAX_SCALE);
        if (v != chatScale) {
            chatScale = v;
            save();
            Minecraft.getInstance().gui.getChat().rescaleChat();
        }
    }

    static boolean pinned() {
        load();
        return pinned;
    }

    static void setPinned(boolean on) {
        load();
        pinned = on;
        save();
    }

    static boolean chatRight() {
        load();
        return chatRight;
    }

    static void toggleChatRight() {
        load();
        chatRight = !chatRight;
        save();
    }

    static boolean ownRight() {
        load();
        return ownRight;
    }

    static void toggleOwnRight() {
        load();
        ownRight = !ownRight;
        save();
    }

    /** The chat stays open after sending. */
    static boolean keepOpen() {
        load();
        return keepOpen;
    }

    static void toggleKeepOpen() {
        load();
        keepOpen = !keepOpen;
        save();
    }

    static boolean hideHotbar() {
        load();
        return hideHotbar;
    }

    static void toggleHideHotbar() {
        load();
        hideHotbar = !hideHotbar;
        save();
    }

    static boolean blurBehind() {
        load();
        return blurBehind;
    }

    static void toggleBlurBehind() {
        load();
        blurBehind = !blurBehind;
        save();
    }

    static boolean spacingSet() {
        load();
        return spacingSet;
    }

    static void markSpacingSet() {
        load();
        if (!spacingSet) {
            spacingSet = true;
            save();
        }
    }

    static boolean showMyTyping() {
        load();
        return showTyping;
    }

    static void toggleShowTyping() {
        load();
        showTyping = !showTyping;
        save();
    }

    static boolean seeTyping() {
        load();
        return seeTyping;
    }

    static void toggleSeeTyping() {
        load();
        seeTyping = !seeTyping;
        save();
    }

    static boolean isMuted(UUID id) {
        load();
        return id != null && muted.contains(id.toString());
    }

    static void toggleMuted(UUID id) {
        load();
        if (id != null && !muted.remove(id.toString())) {
            muted.add(id.toString());
        }
        save();
    }

    static int mutedCount() {
        load();
        return muted.size();
    }

    static void unmuteAll() {
        load();
        muted.clear();
        save();
    }

    // Sound

    static int voiceVolume() {
        load();
        return voiceVolume;
    }

    static void setVoiceVolume(int percent) {
        load();
        voiceVolume = clamp(percent, 0, MAX_VOICE_VOLUME);
        save();
    }

    /** 100, 150 or 200. */
    static int voiceSpeed() {
        load();
        return voiceSpeed;
    }

    static void setVoiceSpeed(int percent) {
        load();
        voiceSpeed = percent == 150 || percent == 200 ? percent : 100;
        save();
    }

    /** "" = system default. */
    static String micDevice() {
        load();
        return micDevice;
    }

    static void setMicDevice(String d) {
        load();
        micDevice = d == null ? "" : d;
        save();
    }

    /** "" = the game's own output. */
    static String speakerDevice() {
        load();
        return speakerDevice;
    }

    static void setSpeakerDevice(String d) {
        load();
        speakerDevice = d == null ? "" : d;
        save();
    }

    static int micGainDb() {
        load();
        return micGainDb;
    }

    static void setMicGainDb(int db) {
        load();
        micGainDb = clamp(db, -10, 20);
        save();
    }

    static int noiseGate() {
        load();
        return noiseGate;
    }

    static void setNoiseGate(int g) {
        load();
        noiseGate = clamp(g, 0, 100);
        save();
    }

    // Gemini

    static String geminiKey() {
        load();
        return geminiKey;
    }

    static void setGeminiKey(String key) {
        load();
        geminiKey = key == null ? "" : key.strip();
        save();
    }

    static boolean geminiMemory() {
        load();
        return geminiMemory;
    }

    static void toggleGeminiMemory() {
        load();
        geminiMemory = !geminiMemory;
        save();
    }
}
