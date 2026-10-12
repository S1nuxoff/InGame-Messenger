package com.sinuxoff.ingamemessenger.core.server;

import com.sinuxoff.ingamemessenger.core.image.Pictures;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.*;
import com.sinuxoff.ingamemessenger.core.text.ChatText;
import com.sinuxoff.ingamemessenger.core.web.Http;
import com.sinuxoff.ingamemessenger.core.web.PictureHosts;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Picture messages: 1..4 pictures with a caption. Storage depends on the server config:
 * <ul>
 * <li>"server": in the server's file storage (world folder or S3), deleted after keepDays;</li>
 * <li>"imgbb": uploaded to ImgBB by the server; clients that can't reach ImgBB get them through the server;</li>
 * <li>"catbox": clients upload to Catbox themselves (it blocks data centers), the server validates the links.</li>
 * </ul>
 * Only the references go into the history.
 */
final class ImageInbox {
    /** Hard limit regardless of config: the picture must fit in one packet. */
    static final int HARD_MAX_KB = (Packets.IMAGE_DATA_MAX - 1024) / 1024;
    static final int MAX_PICTURES = 4;
    private static final int MAX_SIDE = 4096, MAX_CAPTION = 256;
    private static final long RELAY_CACHE = 48L << 20;

    /** Pictures of one message arriving; a null ref means that picture failed. */
    private static final class Album {
        final int id, replyTo;
        final String caption;
        final String[] refs;
        final int[] w, h;
        final boolean[] done;
        final long started = System.currentTimeMillis();

        Album(int id, int total, String caption, int replyTo) {
            this.id = id;
            this.caption = caption;
            this.replyTo = replyTo;
            refs = new String[total];
            w = new int[total];
            h = new int[total];
            done = new boolean[total];
        }

        boolean complete() {
            for (boolean d : done) {
                if (!d) {
                    return false;
                }
            }
            return true;
        }
    }

    private record Coming(Upload upload, int w, int h, int slot) {
    }

    private final ChatServer server;
    private final FileStore files;
    private final Map<UUID, Album> albums = new HashMap<>();
    private final Map<UUID, Coming> coming = new HashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();
    private final LinkedHashMap<String, byte[]> relayed = new LinkedHashMap<>(16, 0.75f, true);
    private long relayedBytes, lastSweep;

    ImageInbox(ChatServer server) {
        this.server = server;
        this.files = new FileStore(server, "images", "image/jpeg");
    }

    /** "server", "imgbb", "catbox" or "off". Unknown values and imgbb without a key mean off. */
    String provider() {
        ServerSettings s = server.platform.settings();
        String p = s.imageProvider().trim().toLowerCase();
        if (p.equals("imgbb") && s.imgbbKey().isBlank()) {
            return "off";
        }
        return p.equals("server") || p.equals("imgbb") || p.equals("catbox") ? p : "off";
    }

    int maxKb() {
        return Math.min(HARD_MAX_KB, server.platform.settings().imageMaxKb());
    }

    private boolean cooledDown(UUID who) {
        Long last = lastSent.get(who);
        return last == null || System.currentTimeMillis() - last >= 3000;
    }

    synchronized void album(UUID who, AlbumStart a) {
        String c = ChatText.caption(a.caption(), MAX_CAPTION);
        String p = provider();
        if (p.equals("off") || p.equals("catbox") || a.total() < 1 || a.total() > MAX_PICTURES || c == null || !cooledDown(who)) {
            return;
        }
        lastSent.put(who, System.currentTimeMillis());
        albums.put(who, new Album(a.album(), a.total(), c, Math.max(-1, a.replyTo())));
    }

    synchronized void start(UUID who, ImageStart v) {
        Album a = albums.get(who);
        if (a == null || a.id != v.album() || v.index() < 0 || v.index() >= a.refs.length || a.done[v.index()]
                || v.bytes() <= 0 || v.bytes() > maxKb() * 1024 || v.w() < 1 || v.h() < 1 || v.w() > MAX_SIDE || v.h() > MAX_SIDE) {
            return;
        }
        coming.put(who, new Coming(new Upload(v.upload(), v.bytes(), System.currentTimeMillis()), v.w(), v.h(), v.index()));
    }

    void chunk(UUID who, String name, ImageChunk c) {
        Coming u;
        Album a;
        synchronized (this) {
            u = coming.get(who);
            a = albums.get(who);
            if (u == null || a == null || !u.upload().accept(c.upload(), c.offset(), c.data()) || !u.upload().complete()) {
                return;
            }
            coming.remove(who);
        }
        byte[] data = u.upload().data;
        if (!Pictures.isJpegOrPng(data)) {
            fill(who, name, a, u.slot(), null, 0, 0);
            return;
        }
        if (provider().equals("imgbb")) {
            toImgbb(who, name, a, u, data);
            return;
        }
        files.put(data, key -> fill(who, name, a, u.slot(), key, u.w(), u.h()));
    }

    /** Settles one slot; when all are settled the message is posted with the pictures that made it. */
    private void fill(UUID who, String name, Album a, int slot, String ref, int w, int h) {
        StringBuilder data = new StringBuilder();
        synchronized (this) {
            if (slot < 0 || slot >= a.refs.length || a.done[slot]) {
                return;
            }
            a.done[slot] = true;
            a.refs[slot] = ref;
            a.w[slot] = w;
            a.h[slot] = h;
            if (!a.complete()) {
                return;
            }
            if (albums.get(who) == a) {
                albums.remove(who);
            }
            for (int i = 0; i < a.refs.length; i++) {
                if (a.refs[i] != null) {
                    data.append(data.length() > 0 ? "\n" : "").append(a.refs[i]).append(' ').append(a.w[i]).append(' ').append(a.h[i]);
                }
            }
        }
        if (data.length() == 0) {
            server.platform.tell(who, "ingamemessenger.image.failed");
            return;
        }
        String d = data.toString();
        server.platform.info("<" + name + "> [" + d.split("\n").length + " picture(s)] " + a.caption);
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("data", d);
        if (!a.caption.isEmpty()) {
            json.addProperty("text", a.caption);
        }
        server.post(who, name, "image:" + d, id -> ChatServer.message(id, "i", who, name, json.toString(), a.replyTo),
                new ImageShow(who, name, d, a.caption, a.replyTo));
    }

    /** Catbox mode: validates the links the client uploaded (host, format and size, by downloading them). */
    void links(UUID who, String name, ImageLinks l) {
        String c = ChatText.caption(l.caption(), MAX_CAPTION);
        String[] lines = l.data().split("\n");
        Album a;
        synchronized (this) {
            if (!provider().equals("catbox") || c == null || lines.length < 1 || lines.length > MAX_PICTURES || !cooledDown(who)) {
                return;
            }
            a = new Album(-1, lines.length, c, Math.max(-1, l.replyTo()));
            for (int i = 0; i < lines.length; i++) {
                String[] f = lines[i].trim().split(" ");
                int w, h;
                try {
                    w = Integer.parseInt(f[1]);
                    h = Integer.parseInt(f[2]);
                } catch (RuntimeException e) {
                    return;
                }
                if (f.length != 3 || !PictureHosts.CATBOX_URL.matcher(f[0]).matches() || w < 1 || h < 1 || w > MAX_SIDE || h > MAX_SIDE) {
                    return;
                }
                a.refs[i] = f[0];
                a.w[i] = w;
                a.h[i] = h;
            }
            lastSent.put(who, System.currentTimeMillis());
        }
        int max = maxKb() * 1024;
        for (int i = 0; i < lines.length; i++) {
            int slot = i;
            String url = a.refs[i];
            int w = a.w[i], h = a.h[i];
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).build();
            Http.withRetries(() -> Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()), 3).whenComplete((resp, err) -> {
                boolean ok = err == null && resp.statusCode() == 200 && resp.body().length <= max && Pictures.isJpegOrPng(resp.body());
                if (!ok) {
                    server.platform.warn("Catbox picture refused: " + (err != null ? err.toString()
                            : "HTTP " + resp.statusCode() + ", " + resp.body().length + " bytes"));
                }
                server.platform.execute(() -> fill(who, name, a, slot, ok ? url : null, w, h));
            });
        }
    }

    private void toImgbb(UUID who, String name, Album a, Coming u, byte[] data) {
        ServerSettings s = server.platform.settings();
        PictureHosts.toImgbb(data, s.imgbbKey(), s.imageKeepDays()).whenComplete((url, err) -> {
            if (url == null) {
                server.platform.warn("ImgBB upload failed" + (err != null ? ": " + err : ""));
            }
            server.platform.execute(() -> {
                if (url != null) {
                    remember(url, data); // ImgBB is slow to serve fresh uploads
                }
                fill(who, name, a, u.slot(), url, u.w(), u.h());
            });
        });
    }

    // Relaying ImgBB pictures for clients that can't reach ImgBB

    private static boolean relayable(String ref) {
        return ref.startsWith(PictureHosts.IMGBB_PREFIX) && ref.length() <= 400 && !ref.contains("..");
    }

    private void remember(String ref, byte[] data) {
        synchronized (relayed) {
            byte[] old = relayed.put(ref, data);
            relayedBytes += data.length - (old == null ? 0 : old.length);
            var it = relayed.entrySet().iterator();
            while (relayedBytes > RELAY_CACHE && it.hasNext()) {
                relayedBytes -= it.next().getValue().length;
                it.remove();
            }
        }
    }

    private void relay(UUID who, String ref) {
        byte[] cached;
        synchronized (relayed) {
            cached = relayed.get(ref);
        }
        if (cached != null) {
            server.platform.send(who, new ImageData(ref, cached));
            return;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(ref)).timeout(Duration.ofSeconds(30)).build();
        Http.withRetries(() -> Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()), 4).whenComplete((resp, err) -> {
            byte[] data = err == null && resp.statusCode() == 200 && resp.body().length <= HARD_MAX_KB * 1024 ? resp.body() : new byte[0];
            if (data.length > 0) {
                remember(ref, data);
            } else {
                server.platform.warn("ImgBB picture not fetched: " + (err != null ? err.toString() : "HTTP " + resp.statusCode()));
            }
            server.platform.execute(() -> server.platform.send(who, new ImageData(ref, data)));
        });
    }

    void get(UUID who, ImageGet g) {
        if (relayable(g.key())) {
            relay(who, g.key());
            return;
        }
        files.get(g.key(), data -> server.platform.send(who, new ImageData(g.key(), data == null ? new byte[0] : data)));
    }

    /** The message was deleted: delete the stored file (ImgBB ones just expire). */
    void forget(String ref) {
        files.delete(ref);
    }

    /** Drops stale uploads and sweeps old files every 6 hours. */
    synchronized void tick(long now) {
        coming.values().removeIf(u -> now - u.upload().started > 120_000);
        albums.values().removeIf(a -> now - a.started > 180_000);
        if (now - lastSweep > 6 * 3600_000L) {
            lastSweep = now;
            files.sweep(server.platform.settings().imageKeepDays(), now);
        }
    }
}
