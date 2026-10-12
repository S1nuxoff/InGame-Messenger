package com.sinuxoff.ingamemessenger.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.ai.Gemini;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Private chat with Gemini. The player's own API key (from Google AI Studio) stays on their computer; requests go
 * straight from the client to Google, nothing passes through the server.
 */
final class Ai {
    static final UUID ID = new UUID(0, 0xA1);
    static final String NAME = "Gemini";
    static final String KEY_PAGE = "https://aistudio.google.com/apikey";
    private static final Gemini GEMINI = new Gemini(InGameMessenger.LOG::info);
    /** The last few turns, so Gemini keeps the thread. */
    private static final List<JsonObject> talk = new ArrayList<>();
    private static volatile boolean busy;
    private static boolean greeted;

    private Ai() {
    }

    static boolean busy() {
        return busy;
    }

    /** Shows the remembered conversation, or a greeting (or how to get a key). */
    static void opened() {
        if (greeted) {
            return;
        }
        greeted = true;
        load();
        var mc = Minecraft.getInstance();
        if (!talk.isEmpty() && mc.player != null) {
            for (JsonObject t : talk) {
                String text = t.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
                if ("user".equals(t.get("role").getAsString())) {
                    ChatMessages.aiMessage(mc.player.getUUID(), mc.player.getGameProfile().getName(), text);
                } else {
                    answer(text);
                }
            }
            return;
        }
        answer(Component.translatable(Prefs.geminiKey().isBlank() ? "ingamemessenger.ai.no_key" : "ingamemessenger.ai.hello").getString());
    }

    // Memory: the conversation is kept in config/ingamemessenger/gemini.json unless disabled

    private static final int MEMORY = 50;
    private static boolean loaded;

    private static java.nio.file.Path memoryFile() {
        return Platform.get().configDir().resolve("gemini.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        talk.clear();
        if (!Prefs.geminiMemory()) {
            return;
        }
        try {
            var f = memoryFile();
            if (java.nio.file.Files.exists(f)) {
                for (var el : JsonParser.parseString(java.nio.file.Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    talk.add(el.getAsJsonObject());
                }
            }
        } catch (Exception e) {
            InGameMessenger.LOG.warn("Gemini's memory not read: {}", e.toString());
        }
    }

    private static void save() {
        if (!Prefs.geminiMemory()) {
            return;
        }
        try {
            JsonArray a = new JsonArray();
            talk.forEach(a::add);
            java.nio.file.Files.writeString(memoryFile(), a.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            InGameMessenger.LOG.warn("Gemini's memory not saved: {}", e.toString());
        }
    }

    static void forget() {
        talk.clear();
        try {
            java.nio.file.Files.deleteIfExists(memoryFile());
        } catch (Exception ignored) {
        }
    }

    static void say(String text) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || text.isBlank()) {
            return;
        }
        ChatMessages.aiMessage(mc.player.getUUID(), mc.player.getGameProfile().getName(), text);
        ask(Gemini.turn("user", text));
    }

    /** A voice message, converted to WAV in the background. */
    static void sayVoice(byte[] mcv) {
        if (Prefs.geminiKey().isBlank()) {
            answer(Component.translatable("ingamemessenger.ai.no_key").getString());
            return;
        }
        Thread t = new Thread(() -> {
            try {
                JsonObject o = Gemini.voiceTurn(VoiceClient.toWav(mcv), "(a voice message from the player: answer what they said)");
                Minecraft.getInstance().execute(() -> ask(o));
            } catch (Exception e) {
                InGameMessenger.LOG.warn("Voice for Gemini not made: {}", e.toString());
            }
        }, "InGameMessenger voice for Gemini");
        t.setDaemon(true);
        t.start();
    }

    private static void ask(JsonObject mine) {
        var mc = Minecraft.getInstance();
        String key = Prefs.geminiKey().trim();
        if (key.isEmpty()) {
            answer(Component.translatable("ingamemessenger.ai.no_key").getString());
            return;
        }
        if (busy) {
            return;
        }
        load();
        talk.add(mine);
        while (talk.size() > MEMORY) {
            talk.remove(0);
        }
        String body = Gemini.request(AiContext.system(), talk); // the game context is rebuilt for every request
        busy = true;
        Typing.local(NAME, ID, true);
        GEMINI.ask(key, body).whenComplete((resp, err) -> {
            String reply;
            boolean ok = false;
            if (err != null) {
                reply = Component.translatable("ingamemessenger.ai.offline").getString();
            } else if (resp == null || resp.statusCode() == 429) {
                reply = Component.translatable("ingamemessenger.ai.limit").getString();
            } else if (resp.statusCode() == 400 || resp.statusCode() == 401 || resp.statusCode() == 403) {
                reply = Component.translatable("ingamemessenger.ai.bad_key").getString();
            } else if (resp.statusCode() != 200) {
                reply = Component.translatable("ingamemessenger.ai.error", resp.statusCode()).getString();
            } else {
                reply = Gemini.textOf(resp.body());
                ok = reply != null;
                if (!ok) {
                    reply = Component.translatable("ingamemessenger.ai.empty").getString();
                }
            }
            if (err != null || resp == null || resp.statusCode() != 200) {
                InGameMessenger.LOG.warn("Gemini: {}", err != null ? err.toString() : resp == null ? "all models out of quota" : "HTTP " + resp.statusCode() + " " + resp.body());
            }
            String r = reply;
            boolean good = ok;
            mc.execute(() -> {
                busy = false;
                Typing.local(NAME, ID, false);
                if (good) {
                    for (int i = 0; i < talk.size(); i++) {
                        if (Gemini.isVoice(talk.get(i))) {
                            talk.set(i, Gemini.turn("user", "[voice message]"));
                        }
                    }
                    talk.add(Gemini.turn("model", r));
                    save();
                } else if (!talk.isEmpty()) {
                    talk.remove(talk.size() - 1); // failed turns aren't kept
                }
                answer(r);
            });
        });
    }

    private static void answer(String text) {
        ChatMessages.aiMessage(ID, NAME, text);
    }

    static void reset() {
        talk.clear();
        loaded = false;
        greeted = false;
        busy = false;
    }
}
