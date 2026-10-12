package com.sinuxoff.ingamemessenger.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.image.Pictures;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.web.PictureHosts;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Pictures, client side. Sending: screenshots, files or the clipboard, shrunk to a JPEG (at most 1600 px, under the
 * server's limit) and uploaded in chunks. Showing: downloaded from a URL or requested from the server, decoded for the
 * GPU. Nothing is saved to disk.
 */
final class Images {
    private Images() {
    }

    /** The server accepts pictures (up to imageMaxKb). */
    static boolean enabled;
    static int maxKb = 800;
    /** "server", "imgbb" or "catbox" (the client uploads them itself). */
    static String host = "server";

    static void onFeatures(Packets.Features f) {
        enabled = f.images();
        maxKb = f.imageMaxKb();
        host = f.imageHost();
    }

    // Attaching and sending. Attached pictures go on a tray above the input and are shrunk in the background; up to 4
    // are sent together, with the input text as their caption.

    static final int MAX = 4;

    /** jpeg is null until ready (or if it failed). */
    static final class Att {
        volatile byte[] jpeg;
        volatile int w, h;
        volatile Thumb thumb;
        volatile boolean failed;
    }

    static final List<Att> atts = new java.util.concurrent.CopyOnWriteArrayList<>();

    static boolean full() {
        return atts.size() >= MAX;
    }

    /** Opens the system file picker in the background. */
    static void pickFile() {
        if (full()) {
            tooMany();
            return;
        }
        Path shots = Platform.get().gameDir().resolve("screenshots");
        Compat.pickPicture(Component.translatable("ingamemessenger.attach.file").getString(),
                Component.translatable("ingamemessenger.attach.kinds").getString(),
                Files.isDirectory(shots) ? shots : Platform.get().gameDir(), p -> add(p, false));
    }

    private static void tooMany() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setOverlayMessage(Component.translatable("ingamemessenger.image.max", MAX), false);
    }

    /** temp files are deleted once read. */
    static void add(Path file, boolean temp) {
        if (full()) {
            tooMany();
            if (temp) {
                deleteQuietly(file);
            }
            return;
        }
        Att a = new Att();
        atts.add(a);
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> {
            try {
                if (Files.size(file) > 64L << 20) {
                    throw new IllegalArgumentException("too big");
                }
                BufferedImage src = Pictures.read(Files.readAllBytes(file), 3200);
                if (src == null) {
                    mc.execute(() -> mc.gui.setOverlayMessage(Component.translatable("ingamemessenger.image.bad"), false));
                    throw new IllegalArgumentException("not a picture");
                }
                Pictures.Jpeg made = Pictures.shrinkToJpeg(src, maxKb * 1024);
                if (made == null) {
                    throw new IllegalArgumentException("can't make it small enough");
                }
                byte[] jpeg = made.data();
                NativeImage img = decode(jpeg, 160);
                mc.execute(() -> {
                    DynamicTexture dt = Compat.texture("pic", img);
                    Gfx.smooth(dt);
                    ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("ingamemessenger", "att/" + Long.toHexString(System.nanoTime()));
                    mc.getTextureManager().register(rl, dt);
                    a.thumb = new Thumb(rl, img.getWidth(), img.getHeight());
                    a.w = made.width();
                    a.h = made.height();
                    a.jpeg = jpeg;
                    if (!atts.contains(a)) { // removed in the meantime
                        mc.getTextureManager().release(rl);
                    }
                });
            } catch (Exception e) {
                InGameMessenger.LOG.warn("Picture not prepared: {}", e.toString());
                a.failed = true;
                mc.execute(() -> remove(a));
            } finally {
                if (temp) {
                    deleteQuietly(file);
                }
            }
        }, "InGameMessenger picture");
        t.setDaemon(true);
        t.start();
    }

    static void remove(Att a) {
        atts.remove(a);
        Thumb t = a.thumb;
        if (t != null) {
            Minecraft.getInstance().getTextureManager().release(t.rl());
        }
    }

    static void clear() {
        for (Att a : List.copyOf(atts)) {
            remove(a);
        }
    }

    private static void deleteQuietly(Path f) {
        try {
            Files.deleteIfExists(f);
        } catch (Exception ignored) {
        }
    }

    // Upload progress
    private static int uploadId, albumId;
    static volatile boolean sending;
    static volatile int sendTotal, sendDone;
    static volatile long sendStep;
    private static volatile long sendStarted;

    /** 0..1: finished pictures plus an estimate for the current one, until the server shows them. */
    static float progress() {
        if (!sending || sendTotal <= 0) {
            return 0f;
        }
        float creep = 1f - (float) Math.exp(-(System.currentTimeMillis() - sendStep) / 1500.0);
        return Math.min(0.97f, (sendDone + 0.9f * creep) / sendTotal);
    }

    /** The server showed our pictures (or it timed out). */
    static void sent() {
        sending = false;
    }

    static void tick() {
        if (sending && System.currentTimeMillis() - sendStarted > 120_000) {
            sending = false;
        }
    }

    /** Sends the tray with this caption, as a reply to replyTo. */
    static void sendAlbum(String caption, int replyTo) {
        List<Att> list = List.copyOf(atts);
        atts.clear();
        if (list.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        sending = true;
        sendTotal = list.size();
        sendDone = 0;
        sendStarted = sendStep = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            try {
                long until = System.currentTimeMillis() + 30_000; // wait for pictures still being shrunk
                while (list.stream().anyMatch(a -> a.jpeg == null && !a.failed) && System.currentTimeMillis() < until) {
                    Thread.sleep(50);
                }
                List<Att> ready = list.stream().filter(a -> a.jpeg != null).toList();
                if (ready.isEmpty()) {
                    throw new IllegalStateException("nothing ready");
                }
                sendTotal = ready.size();
                if ("catbox".equals(host)) { // we upload, the server gets the links
                    StringBuilder data = new StringBuilder();
                    for (Att a : ready) {
                        String url = PictureHosts.toCatbox(a.jpeg); // from the player's connection: Catbox blocks data centers
                        data.append(data.length() > 0 ? "\n" : "").append(url).append(' ').append(a.w).append(' ').append(a.h);
                        sendDone++;
                        sendStep = System.currentTimeMillis();
                    }
                    mc.execute(() -> Net.toServer(new Packets.ImageLinks(data.toString(), caption, replyTo)));
                } else {
                    int album = ++albumId;
                    mc.execute(() -> Net.toServer(new Packets.AlbumStart(album, ready.size(), caption, replyTo)));
                    for (int i = 0; i < ready.size(); i++) {
                        Att a = ready.get(i);
                        int index = i;
                        mc.execute(() -> {
                            int id = ++uploadId;
                            Net.toServer(new Packets.ImageStart(id, a.jpeg.length, a.w, a.h, album, index));
                            for (int off = 0; off < a.jpeg.length; off += 30_000) {
                                byte[] part = java.util.Arrays.copyOfRange(a.jpeg, off, Math.min(a.jpeg.length, off + 30_000));
                                Net.toServer(new Packets.ImageChunk(id, off, part));
                            }
                        });
                        sendDone++;
                        sendStep = System.currentTimeMillis();
                    }
                }
            } catch (Exception e) {
                InGameMessenger.LOG.warn("Pictures not sent: {}", e.toString());
                sending = false;
                mc.execute(() -> mc.gui.setOverlayMessage(Component.translatable("ingamemessenger.image.failed"), false));
            } finally {
                mc.execute(() -> list.forEach(a -> {
                    Thumb th = a.thumb;
                    if (th != null) {
                        mc.getTextureManager().release(th.rl());
                    }
                }));
            }
        }, "InGameMessenger sending pictures");
        t.setDaemon(true);
        t.start();
    }

    // Clipboard. Minecraft only reads text from it, so images (Win+Shift+S, copied images or files) are read with
    // system tools: PowerShell on Windows, osascript on macOS, wl-paste / xclip on Linux.

    private static volatile boolean pasting;

    /** Ctrl+V with no text: looks for an image in the clipboard in the background. */
    static void paste() {
        if (!enabled || pasting) {
            return;
        }
        if (full()) {
            tooMany();
            return;
        }
        pasting = true;
        Thread t = new Thread(() -> {
            try {
                Object[] got = clipboardPicture();
                if (got != null) {
                    Minecraft.getInstance().execute(() -> add((Path) got[0], (boolean) got[1]));
                }
            } catch (Exception e) {
                InGameMessenger.LOG.warn("Clipboard picture not read: {}", e.toString());
            } finally {
                pasting = false;
            }
        }, "InGameMessenger clipboard");
        t.setDaemon(true);
        t.start();
    }

    /** {file, isTemporary} of the clipboard image, or null. */
    private static Object[] clipboardPicture() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path tmp = Files.createTempFile("ingamemessenger-paste", ".png");
        try {
            if (os.contains("win")) {
                String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing;"
                        + "$f=[Windows.Forms.Clipboard]::GetFileDropList();"
                        + "if($f -and $f.Count -gt 0){[Console]::Out.Write('FILE:'+$f[0]);exit};"
                        + "$i=[Windows.Forms.Clipboard]::GetImage();"
                        + "if($i){$i.Save('" + tmp.toAbsolutePath().toString().replace("'", "''") + "',[Drawing.Imaging.ImageFormat]::Png);[Console]::Out.Write('IMG')}";
                String out = run(List.of("powershell", "-NoProfile", "-NonInteractive", "-STA", "-Command", script), null);
                if (out.startsWith("FILE:")) {
                    Path file = Path.of(out.substring(5).trim());
                    String n = file.getFileName().toString().toLowerCase();
                    Files.deleteIfExists(tmp);
                    return n.matches(".*\\.(png|jpe?g|bmp|gif)") && Files.isRegularFile(file) ? new Object[]{file, false} : null;
                }
                return out.startsWith("IMG") && Files.size(tmp) > 0 ? new Object[]{tmp, true} : cleanup(tmp);
            }
            if (os.contains("mac")) {
                run(List.of("osascript", "-e", "set f to open for access POSIX file \"" + tmp.toAbsolutePath() + "\" with write permission",
                        "-e", "write (the clipboard as \u00ABclass PNGf\u00BB) to f", "-e", "close access f"), null);
                return Files.size(tmp) > 0 ? new Object[]{tmp, true} : cleanup(tmp);
            }
            for (List<String> cmd : List.of(List.of("wl-paste", "--no-newline", "--type", "image/png"),
                    List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-o"))) {
                try {
                    run(cmd, tmp);
                    if (Files.size(tmp) > 0) {
                        return new Object[]{tmp, true};
                    }
                } catch (Exception ignored) {
                }
            }
            return cleanup(tmp);
        } catch (Exception e) {
            cleanup(tmp);
            throw e;
        }
    }

    private static Object[] cleanup(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Runs a command for at most 10 s; output as text or into a file. */
    private static String run(List<String> cmd, Path outFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD);
        if (outFile != null) {
            pb.redirectOutput(outFile.toFile());
        }
        Process p = pb.start();
        p.getOutputStream().close();
        String out = outFile == null ? new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) : "";
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("timed out");
        }
        return out;
    }

    // Downloading

    private static final Map<String, CompletableFuture<byte[]>> waiting = new ConcurrentHashMap<>();

    /** Blocking (called on a loader thread): from a URL or from the server. */
    static byte[] fetch(String ref) throws Exception {
        if (PictureHosts.CATBOX_URL.matcher(ref).matches()) {
            return direct(ref);
        }
        try {
            return fromServer(ref);
        } catch (Exception e) {
            if (!ref.startsWith("https://")) {
                throw e;
            }
        }
        return direct(ref);
    }

    /** At most 4 MB. */
    private static byte[] direct(String ref) throws Exception {
        return PictureHosts.download(ref, 4 << 20);
    }

    private static byte[] fromServer(String ref) throws Exception {
        CompletableFuture<byte[]> f = waiting.computeIfAbsent(ref, k -> {
            CompletableFuture<byte[]> nf = new CompletableFuture<>();
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().getConnection() != null) {
                    Net.toServer(new Packets.ImageGet(ref));
                }
            });
            return nf;
        });
        try {
            byte[] b = f.get(45, TimeUnit.SECONDS);
            if (b.length == 0) {
                throw new IllegalStateException("gone");
            }
            return b;
        } finally {
            waiting.remove(ref, f);
        }
    }

    static void onData(Packets.ImageData d) {
        CompletableFuture<byte[]> f = waiting.get(d.key());
        if (f != null) {
            f.complete(d.data());
        }
    }

    /** JPEG or PNG to a GPU image, at most maxSide pixels. */
    static NativeImage decode(byte[] bytes, int maxSide) throws Exception {
        Pictures.Argb pic = Pictures.decode(bytes, maxSide);
        int w = pic.width(), h = pic.height();
        int[] px = pic.pixels();
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                //? if >=1.21.4 {
                /*img.setPixel(x, y, c);
                *///?} else {
                img.setPixelRGBA(x, y, c & 0xFF00FF00 | (c >> 16) & 0xFF | (c & 0xFF) << 16); // ARGB -> ABGR
                //?}
            }
        }
        return img;
    }

    // Screenshots

    record Shot(Path file, long modified) {
    }

    record Thumb(ResourceLocation rl, int w, int h) {
    }

    private static final Map<Path, Object> thumbs = new ConcurrentHashMap<>(); // Thumb or LOADING (failures stay LOADING)
    private static final Object LOADING = new Object();

    /** The newest n screenshots. */
    static List<Shot> shots(int n) {
        Path dir = Platform.get().gameDir().resolve("screenshots");
        List<Shot> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var files = Files.list(dir)) {
            files.filter(f -> {
                String s = f.getFileName().toString().toLowerCase();
                return s.endsWith(".png") || s.endsWith(".jpg");
            }).forEach(f -> {
                try {
                    out.add(new Shot(f, Files.getLastModifiedTime(f).toMillis()));
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        out.sort((a, b) -> Long.compare(b.modified(), a.modified()));
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    /** Preview of a screenshot, null while it's being made. */
    static Thumb thumb(Shot s) {
        Object t = thumbs.get(s.file());
        if (t instanceof Thumb th) {
            return th;
        }
        if (t == null && thumbs.putIfAbsent(s.file(), LOADING) == null) {
            Thread th = new Thread(() -> {
                try {
                    NativeImage img = decode(Files.readAllBytes(s.file()), 160);
                    Minecraft.getInstance().execute(() -> {
                        DynamicTexture dt = Compat.texture("pic", img);
                        Gfx.smooth(dt);
                        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("ingamemessenger",
                                "shot/" + Integer.toHexString(s.file().toString().hashCode()) + "_" + Long.toHexString(s.modified()));
                        Minecraft.getInstance().getTextureManager().register(rl, dt);
                        thumbs.put(s.file(), new Thumb(rl, img.getWidth(), img.getHeight()));
                    });
                } catch (Exception ignored) {
                    // stays LOADING so it isn't retried every frame
                }
            }, "InGameMessenger screenshot preview");
            th.setDaemon(true);
            th.start();
        }
        return null;
    }
}
