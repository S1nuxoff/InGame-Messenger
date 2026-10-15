package com.sinuxoff.ingamemessenger.core.hub;

import com.google.gson.JsonObject;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.VoiceChunk;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.VoiceData;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.VoiceGet;
import com.sinuxoff.ingamemessenger.core.protocol.Proto.VoiceUp;
import com.sinuxoff.ingamemessenger.core.server.ServerSettings;
import com.sinuxoff.ingamemessenger.core.storage.Store.Channel;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Voice message uploads: validated, stored, posted to their channel, served on request, deleted after keepDays. */
final class Voices {
    /** Upper bound for Opus speech; bigger uploads are rejected. */
    static final int BYTES_PER_SECOND = 8000;

    private record Coming(Upload upload, long channel, String nonce, int durationMs, byte[] wave, long replyTo) {
    }

    private final Hub hub;
    private final Files files;
    private final Map<UUID, Coming> coming = new HashMap<>();
    private long lastSweep;

    Voices(Hub hub) {
        this.hub = hub;
        this.files = new Files(hub, "voice", "application/octet-stream");
    }

    synchronized void start(UUID who, VoiceUp v) {
        ServerSettings s = hub.platform.settings();
        int max = s.voiceMaxSeconds();
        if (!s.voiceOn() || v.bytes() <= 0 || v.durationMs() <= 0 || v.durationMs() > max * 1000 + 1500
                || v.bytes() > (max + 2) * BYTES_PER_SECOND || v.wave().length > 128 || !hub.mayPost(who, v.channel(), Perms.VOICE)) {
            return;
        }
        coming.put(who, new Coming(new Upload(v.upload(), v.bytes(), System.currentTimeMillis()), v.channel(), v.nonce(), v.durationMs(), v.wave(),
                v.replyTo()));
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
            Channel ch = hub.channel(u.channel());
            if (key == null || ch == null) {
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("k", key);
            body.addProperty("ms", u.durationMs());
            body.addProperty("w", HexFormat.of().formatHex(u.wave()));
            Hub.Peer peer = hub.voiceAsPeer(who);
            UUID from = peer != null ? peer.id() : who;
            String as = peer != null ? peer.name() : name;
            hub.platform.info("<" + as + "> [voice " + Math.round(u.durationMs() / 1000f) + " s]");
            hub.deliver(from, as, ch, "voice", body.toString(), null, u.replyTo(), peer != null ? "" : u.nonce());
        });
    }

    /** Sends the file, or an empty one if it was deleted. */
    void get(UUID who, VoiceGet g) {
        files.get(g.key(), data -> hub.send(who, new VoiceData(g.key(), data == null ? new byte[0] : data)));
    }

    synchronized void left(UUID who) {
        coming.remove(who);
    }

    /** Drops stale uploads and sweeps old files every 6 hours. */
    synchronized void tick(long now) {
        coming.values().removeIf(u -> now - u.upload().started > 120_000);
        if (now - lastSweep > 6 * 3600_000L) {
            lastSweep = now;
            files.sweep(hub.platform.settings().voiceKeepDays(), now);
        }
    }
}
