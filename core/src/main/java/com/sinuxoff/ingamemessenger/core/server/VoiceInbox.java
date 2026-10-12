package com.sinuxoff.ingamemessenger.core.server;

import com.google.gson.JsonObject;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.*;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Voice message uploads: validated, stored, posted to the chat, served on request, deleted after keepDays. */
final class VoiceInbox {
    /** Upper bound for Opus speech; bigger uploads are rejected. */
    static final int BYTES_PER_SECOND = 8000;

    private record Coming(Upload upload, int durationMs, byte[] wave, int replyTo) {
    }

    private final ChatServer server;
    private final FileStore files;
    private final Map<UUID, Coming> coming = new HashMap<>();
    private long lastSweep;

    VoiceInbox(ChatServer server) {
        this.server = server;
        this.files = new FileStore(server, "voice", "application/octet-stream");
    }

    synchronized void start(UUID who, VoiceStart v) {
        ServerSettings s = server.platform.settings();
        int max = s.voiceMaxSeconds();
        if (!s.voiceOn() || v.bytes() <= 0 || v.durationMs() <= 0 || v.durationMs() > max * 1000 + 1500
                || v.bytes() > (max + 2) * BYTES_PER_SECOND || v.wave().length > 128) {
            return;
        }
        coming.put(who, new Coming(new Upload(v.upload(), v.bytes(), System.currentTimeMillis()), v.durationMs(), v.wave(),
                Math.max(-1, v.replyTo())));
    }

    void chunk(UUID who, String name, VoiceChunk c) {
        Coming u;
        synchronized (this) {
            u = coming.get(who);
            if (u == null || !u.upload().accept(c.upload(), c.offset(), c.data()) || !u.upload().complete()) {
                return;
            }
            coming.remove(who);
        }
        files.put(u.upload().data, key -> {
            if (key == null) {
                return;
            }
            JsonObject json = new JsonObject();
            json.addProperty("key", key);
            json.addProperty("ms", u.durationMs());
            json.addProperty("wave", HexFormat.of().formatHex(u.wave()));
            server.platform.info("<" + name + "> [voice " + Math.round(u.durationMs() / 1000f) + " s]");
            server.post(who, name, "voice:" + key, id -> ChatServer.message(id, "v", who, name, json.toString(), u.replyTo()),
                    new VoiceShow(who, name, key, u.durationMs(), u.wave(), u.replyTo()));
        });
    }

    /** Sends the file, or an empty one if it was deleted. */
    void get(UUID who, VoiceGet g) {
        files.get(g.key(), data -> server.platform.send(who, new VoiceData(g.key(), data == null ? new byte[0] : data)));
    }

    /** Drops stale uploads and sweeps old files every 6 hours. */
    synchronized void tick(long now) {
        coming.values().removeIf(u -> now - u.upload().started > 120_000);
        if (now - lastSweep > 6 * 3600_000L) {
            lastSweep = now;
            files.sweep(server.platform.settings().voiceKeepDays(), now);
        }
    }
}
