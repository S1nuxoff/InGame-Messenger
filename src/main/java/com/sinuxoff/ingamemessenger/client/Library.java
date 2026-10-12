package com.sinuxoff.ingamemessenger.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sticker and emoji packs from the server, and their textures. Images are requested from the server when first drawn,
 * decoded on background threads and uploaded to the GPU a few per frame. Unused textures are released after a while.
 * Sent pictures (album photos) use the same loading and drawing.
 */
final class Library {
    /** frames > 1 means an animation laid out in a grid of cols columns. */
    record Sticker(String pack, String id, int frames, int fps, int cols, boolean thumb, int w, int h) {
        float aspect() {
            return w > 0 && h > 0 ? w / (float) h : 1f;
        }

        String key() {
            return pack + "/" + id;
        }
    }

    /** emoji packs are used inline in text and as reactions. */
    record Pack(String id, String title, List<Sticker> stickers, boolean emoji) {
    }

    private record Tex(ResourceLocation rl, int texW, int texH, int frameW, int frameH, int frames, int fps, int cols) {
    }

    /** Pack id used for sent pictures; their id is a URL or a server key. */
    static final String IMG = "@img";
    private static final Object FAILED = new Object(), LOADING = new Object();

    private static volatile List<Pack> packs = List.of(), emojiPacks = List.of();
    private static final Map<String, Sticker> byKey = new ConcurrentHashMap<>();
    private static final Map<String, Sticker> images = new ConcurrentHashMap<>();
    private static long version = -1;
    /** Texture key -> Tex, LOADING or FAILED. */
    private static final Map<String, Object> textures = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();

    private Library() {
    }

    // Packs

    static List<Pack> packs() {
        return packs;
    }

    static List<Pack> emojiPacks() {
        return emojiPacks;
    }

    static boolean isEmoji(String pack) {
        for (Pack p : emojiPacks) {
            if (p.id().equals(pack)) {
                return true;
            }
        }
        return false;
    }

    /** Sticker packs first, then emoji packs. */
    static List<Pack> allPacks() {
        List<Pack> all = new ArrayList<>(packs);
        all.addAll(emojiPacks);
        return all;
    }

    static Sticker get(String pack, String id) {
        return IMG.equals(pack) ? images.get(id) : byKey.get(pack + "/" + id);
    }

    static Sticker image(String ref, int w, int h) {
        return images.computeIfAbsent(ref, r -> new Sticker(IMG, r, 1, 0, 1, false, Math.max(1, w), Math.max(1, h)));
    }

    /** The server sent its packs (on join or after a reload). */
    static void onIndex(String json) {
        try {
            parse(json);
        } catch (RuntimeException e) {
            InGameMessenger.LOG.warn("Bad sticker index", e);
        }
    }

    private static void parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        long v = root.has("version") ? root.get("version").getAsLong() : 0;
        List<Pack> out = new ArrayList<>(), emojiOut = new ArrayList<>();
        byKey.clear();
        for (JsonElement pe : root.getAsJsonArray("packs")) {
            JsonObject p = pe.getAsJsonObject();
            String pid = p.get("id").getAsString();
            List<Sticker> list = new ArrayList<>();
            for (JsonElement se : p.getAsJsonArray("stickers")) {
                JsonObject s = se.getAsJsonObject();
                Sticker st = new Sticker(pid, s.get("id").getAsString(),
                        s.has("frames") ? Math.max(1, s.get("frames").getAsInt()) : 1,
                        s.has("fps") ? s.get("fps").getAsInt() : 0,
                        s.has("cols") ? Math.max(1, s.get("cols").getAsInt()) : 1,
                        s.has("thumb") && s.get("thumb").getAsBoolean(),
                        s.has("w") ? s.get("w").getAsInt() : 0, s.has("h") ? s.get("h").getAsInt() : 0);
                list.add(st);
                byKey.put(st.key(), st);
            }
            boolean emoji = p.has("emoji") && p.get("emoji").getAsBoolean();
            (emoji ? emojiOut : out).add(new Pack(pid, p.has("title") ? p.get("title").getAsString() : pid, List.copyOf(list), emoji));
        }
        if (v != version) {
            Minecraft.getInstance().execute(Library::releaseStickers);
        }
        version = v;
        packs = List.copyOf(out);
        emojiPacks = List.copyOf(emojiOut);
        prefetch();
    }

    /** Packs changed on the server: drop every sticker texture, they're loaded again when drawn. */
    private static void releaseStickers() {
        for (var it = textures.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getKey().startsWith(IMG + "/") || e.getValue() == LOADING) {
                continue;
            }
            if (e.getValue() instanceof Tex tex) {
                Minecraft.getInstance().getTextureManager().release(tex.rl());
            }
            used.remove(e.getKey());
            it.remove();
        }
    }

    /** A sticker image arrived from the server. Called on the network thread. */
    static void onData(Packets.StickerData d) {
        CompletableFuture<byte[]> f = pending.remove(d.pack() + "/" + d.id() + (d.thumb() ? "#t" : ""));
        if (f != null) {
            f.complete(d.data());
        }
    }

    // Recent and favorites

    static List<Sticker> recent() {
        return resolve(Prefs.recentStickers());
    }

    static List<Sticker> favorites() {
        return resolve(Prefs.favoriteStickers());
    }

    static boolean isFavorite(Sticker s) {
        return Prefs.isFavorite(s.key());
    }

    static void toggleFavorite(Sticker s) {
        Prefs.toggleFavorite(s.key());
    }

    static void used(Sticker s) {
        Prefs.usedSticker(s.key());
    }

    /** Stickers by key, skipping the ones the server doesn't have. */
    private static List<Sticker> resolve(List<String> keys) {
        List<Sticker> out = new ArrayList<>();
        for (String k : keys) {
            Sticker s = byKey.get(k);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    // Drawing

    static boolean ready(Sticker s) {
        if (textures.get(s.key()) instanceof Tex) {
            return true;
        }
        request(s, false, NOW_FULL);
        return false;
    }

    static boolean failed(Sticker s) {
        return textures.get(s.key()) == FAILED;
    }

    /** Fits the sticker into the box keeping proportions; animated; shows the preview until the full image is in. */
    static void draw(GuiGraphics g, Sticker s, int x, int y, int w, int h, float alpha) {
        drawTinted(g, s, x, y, w, h, 1f, 1f, 1f, alpha);
    }

    /** Same, with the colors multiplied (black gives a shadow). */
    static void drawTinted(GuiGraphics g, Sticker s, int x, int y, int w, int h, float r, float gr, float b, float alpha) {
        Object t = textures.get(s.key());
        if (!(t instanceof Tex)) {
            request(s, false, NOW_FULL);
            if (s.thumb()) {
                request(s, true, NOW_THUMB);
                t = textures.get(key(s, true));
            }
        }
        if (!(t instanceof Tex tex)) {
            if (t != FAILED) {
                g.fill(x + w / 4, y + h / 4, x + w * 3 / 4, y + h * 3 / 4, ((int) (alpha * 0x30) << 24) | 0xFFFFFF);
            }
            return;
        }
        used.put(t == textures.get(s.key()) ? s.key() : key(s, true), System.currentTimeMillis());
        int frame = 0;
        if (tex.frames() > 1 && tex.fps() > 0) {
            frame = (int) ((System.currentTimeMillis() * tex.fps() / 1000) % tex.frames());
        }
        float scale = Math.min(w / (float) tex.frameW(), h / (float) tex.frameH());
        int dw = Math.max(1, Math.round(tex.frameW() * scale)), dh = Math.max(1, Math.round(tex.frameH() * scale));
        int dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
        int u = (frame % tex.cols()) * tex.frameW(), v = (frame / tex.cols()) * tex.frameH();
        int argb = Math.round(alpha * 255) << 24 | Math.round(r * 255) << 16 | Math.round(gr * 255) << 8 | Math.round(b * 255);
        Gfx.blit(g, tex.rl(), dx, dy, dw, dh, u, v, tex.frameW(), tex.frameH(), tex.texW(), tex.texH(), argb);
    }

    static void drawRounded(GuiGraphics g, Sticker s, int x, int y, int w, int h, float r, float alpha) {
        drawRounded(g, s, x, y, w, h, r, alpha, false);
    }

    /** With rounded corners. cover = fill the box and crop, instead of fitting inside it. */
    static void drawRounded(GuiGraphics g, Sticker s, int x, int y, int w, int h, float r, float alpha, boolean cover) {
        Object t = textures.get(s.key());
        if (!(t instanceof Tex tex)) {
            request(s, false, NOW_FULL);
            return;
        }
        used.put(s.key(), System.currentTimeMillis());
        float fw = tex.frameW() / (float) tex.texW(), fh = tex.frameH() / (float) tex.texH();
        float dw, dh, dx, dy, u0, v0, u1, v1;
        if (cover) {
            float scale = Math.max(w / (float) tex.frameW(), h / (float) tex.frameH());
            float vw = w / scale / tex.frameW(), vh = h / scale / tex.frameH(); // visible part of the frame, 0..1
            dw = w;
            dh = h;
            dx = x;
            dy = y;
            u0 = (1 - vw) / 2 * fw;
            v0 = (1 - vh) / 2 * fh;
            u1 = u0 + vw * fw;
            v1 = v0 + vh * fh;
        } else {
            float scale = Math.min(w / (float) tex.frameW(), h / (float) tex.frameH());
            dw = tex.frameW() * scale;
            dh = tex.frameH() * scale;
            dx = x + (w - dw) / 2f;
            dy = y + (h - dh) / 2f;
            u0 = 0;
            v0 = 0;
            u1 = fw;
            v1 = fh;
        }
        roundedFan(g, tex.rl(), dx, dy, dw, dh, r, 8, u0, v0, u1, v1, alpha);
    }

    /** Part of a texture (uv in 0..1) in a box with rounded corners. */
    static void roundedTex(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float r,
                           float u0, float v0, float u1, float v1, float alpha) {
        roundedFan(g, tex, x, y, w, h, r, 6, u0, v0, u1, v1, alpha);
    }

    /** A triangle fan from the center around the rounded outline, seg steps per corner. */
    private static void roundedFan(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float r, int seg,
                                   float u0, float v0, float u1, float v1, float alpha) {
        r = Math.min(r, Math.min(w, h) / 2f);
        int n = 2 + 4 * (seg + 1);
        float[] xy = new float[n * 2], uv = new float[n * 2];
        int[] at = {0};
        java.util.function.BiConsumer<Float, Float> pt = (px, py) -> {
            int i = at[0];
            xy[i] = px;
            xy[i + 1] = py;
            uv[i] = u0 + (px - x) / w * (u1 - u0);
            uv[i + 1] = v0 + (py - y) / h * (v1 - v0);
            at[0] = i + 2;
        };
        // counterclockwise on screen like Minecraft's quads, otherwise it gets back-face culled
        pt.accept(x + w / 2f, y + h / 2f);
        float[][] corners = {{x + r, y + r, 270}, {x + r, y + h - r, 180}, {x + w - r, y + h - r, 90}, {x + w - r, y + r, 0}};
        for (float[] c : corners) {
            for (int i = 0; i <= seg; i++) {
                double ang = Math.toRadians(c[2] - 90.0 * i / seg);
                pt.accept((float) (c[0] + Math.cos(ang) * r), (float) (c[1] + Math.sin(ang) * r));
            }
        }
        pt.accept(x + r, y);
        Gfx.fan(g, tex, xy, uv, alpha);
    }

    // Loading

    /** Lower runs first. */
    private static final int NOW_THUMB = 0, NOW_FULL = 1, BG_THUMB = 2;
    private static final AtomicLong SEQ = new AtomicLong();
    private static final Map<String, Task> queued = new ConcurrentHashMap<>();
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new PriorityBlockingQueue<>(), r -> {
        Thread t = new Thread(r, "InGameMessenger loader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });
    private static int failures;

    private static final class Task implements Runnable, Comparable<Task> {
        final String key;
        final Sticker s;
        final boolean thumb;
        final int prio;
        final long seq = SEQ.incrementAndGet();

        Task(String key, Sticker s, boolean thumb, int prio) {
            this.key = key;
            this.s = s;
            this.thumb = thumb;
            this.prio = prio;
        }

        @Override
        public int compareTo(Task o) {
            return prio != o.prio ? Integer.compare(prio, o.prio) : Long.compare(seq, o.seq);
        }

        @Override
        public void run() {
            queued.remove(key, this);
            fetch(this);
        }
    }

    private static String key(Sticker s, boolean thumb) {
        return thumb ? s.key() + "#t" : s.key();
    }

    /** Queues a load; requesting again with a higher priority moves it up. */
    private static void request(Sticker s, boolean thumb, int prio) {
        String key = key(s, thumb);
        Object t = textures.get(key);
        if (t instanceof Tex || t == FAILED) {
            return;
        }
        Task old = queued.get(key);
        if (old != null) {
            if (old.prio <= prio || !POOL.getQueue().remove(old)) {
                return;
            }
        } else if (textures.putIfAbsent(key, LOADING) != null) {
            return; // already loading
        }
        Task task = new Task(key, s, thumb, prio);
        queued.put(key, task);
        POOL.execute(task);
    }

    /** All previews in the background, so the sticker panel opens ready. */
    private static void prefetch() {
        for (Pack p : allPacks()) {
            for (Sticker s : p.stickers()) {
                if (s.thumb()) {
                    request(s, true, BG_THUMB);
                }
            }
        }
    }

    private static void fetch(Task task) {
        Sticker s = task.s;
        try {
            NativeImage img;
            if (IMG.equals(s.pack())) {
                img = Images.decode(Images.fetch(s.id()), 1280);
            } else {
                CompletableFuture<byte[]> f = new CompletableFuture<>();
                pending.put(task.key, f);
                Net.toServer(new Packets.StickerGet(s.pack(), s.id(), task.thumb));
                byte[] bytes = f.get(30, TimeUnit.SECONDS);
                if (bytes.length == 0) {
                    throw new IllegalStateException("not on the server");
                }
                img = NativeImage.read(new ByteArrayInputStream(bytes));
            }
            NativeImage decoded = img;
            uploads.add(() -> upload(task, decoded));
        } catch (Exception e) {
            pending.remove(task.key);
            textures.put(task.key, FAILED);
            if (failures++ < 3) {
                InGameMessenger.LOG.warn("Sticker {} not loaded: {}", task.key, e.toString());
            }
        }
    }

    private static void upload(Task task, NativeImage img) {
        Sticker s = task.s;
        try {
            DynamicTexture dt = Compat.texture("pic", img);
            Gfx.smooth(dt);
            int texW = img.getWidth(), texH = img.getHeight();
            dt.setPixels(null); // uploaded: no need to keep a copy in memory
            ResourceLocation rl = IMG.equals(s.pack())
                    ? ResourceLocation.fromNamespaceAndPath(InGameMessenger.MOD_ID, "dyn/img/" + Integer.toHexString(s.id().hashCode()) + "_" + s.id().length())
                    : ResourceLocation.fromNamespaceAndPath(InGameMessenger.MOD_ID, "dyn/" + s.pack().toLowerCase() + "/"
                    + s.id().toLowerCase().replaceAll("[^a-z0-9_./-]", "_") + (task.thumb ? "_t" : ""));
            Minecraft.getInstance().getTextureManager().register(rl, dt);
            int frames = task.thumb ? 1 : Math.max(1, s.frames());
            int cols = task.thumb ? 1 : Math.max(1, Math.min(s.cols(), frames));
            int rows = (frames + cols - 1) / cols;
            textures.put(task.key, new Tex(rl, texW, texH, texW / cols, texH / rows, frames, task.thumb ? 0 : s.fps(), cols));
            used.put(task.key, System.currentTimeMillis());
        } catch (Exception e) {
            img.close();
            textures.put(task.key, FAILED);
            InGameMessenger.LOG.warn("Sticker {} not uploaded", task.key, e);
        }
    }

    // GPU memory

    /** Decoded images waiting for the GPU; uploaded on the render thread, a few per frame. */
    private static final ConcurrentLinkedQueue<Runnable> uploads = new ConcurrentLinkedQueue<>();
    /** Texture key -> last time it was drawn. */
    private static final Map<String, Long> used = new ConcurrentHashMap<>();
    private static long lastSweep;
    /** Full images kept even when unused; anything unused for longer than UNUSED_MS beyond that is released. */
    private static final int KEEP = 96;
    private static final long UNUSED_MS = 60_000;

    /** Every frame: a few uploads, and now and then releasing unused textures. */
    static void pump() {
        for (int i = 0; i < 4; i++) {
            Runnable r = uploads.poll();
            if (r == null) {
                break;
            }
            r.run();
        }
        long now = System.currentTimeMillis();
        if (now - lastSweep < 5000) {
            return;
        }
        lastSweep = now;
        List<Map.Entry<String, Long>> full = new ArrayList<>();
        for (Map.Entry<String, Long> e : used.entrySet()) {
            if (!e.getKey().endsWith("#t")) { // previews are small, they stay
                full.add(e);
            }
        }
        full.sort(Map.Entry.comparingByValue());
        int over = full.size() - KEEP;
        for (Map.Entry<String, Long> e : full) {
            if (over <= 0 && now - e.getValue() < UNUSED_MS || now - e.getValue() < 3000) {
                break;
            }
            Object t = textures.remove(e.getKey());
            used.remove(e.getKey());
            if (t instanceof Tex tex) {
                Minecraft.getInstance().getTextureManager().release(tex.rl());
            }
            over--;
        }
    }
}
