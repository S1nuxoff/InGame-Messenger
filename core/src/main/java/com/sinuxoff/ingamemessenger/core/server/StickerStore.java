package com.sinuxoff.ingamemessenger.core.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sinuxoff.ingamemessenger.core.image.StickerImages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Sticker and emoji packs from the server's config folder: {@code stickers/<pack>/} and {@code emoji/<pack>/}, one
 * image per sticker. Everything is converted once on load and kept in memory; clients get the index on join and
 * request images as they need them.
 */
public final class StickerStore {
    public static final Pattern ID = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");
    private static final Pattern EXT = Pattern.compile("(?i).+\\.(png|jpe?g|gif)$");
    private static final int MAX_PACKS = 200, MAX_PER_PACK = 200;

    private record Images(byte[] full, byte[] thumb) {
    }

    private final Path dir;
    private final Consumer<String> log;
    private volatile String index = emptyIndex();
    private volatile Map<String, Images> images = Map.of();
    private volatile int version;

    public StickerStore(Path configDir, Consumer<String> log) {
        this.dir = configDir;
        this.log = log;
    }

    /** The index JSON clients get: packs, their stickers and animation layout. */
    public String index() {
        return index;
    }

    /** Null if there's no such sticker. */
    public byte[] image(String pack, String id, boolean thumb) {
        Images i = images.get(pack + "/" + id);
        return i == null ? null : thumb ? i.thumb() : i.full();
    }

    public List<String[]> allStickers() {
        List<String[]> out = new ArrayList<>();
        for (String k : images.keySet()) {
            out.add(k.split("/", 2));
        }
        return out;
    }

    /** Rescans both folders. Slow for big packs: call it off the server thread. */
    public synchronized void load() {
        Map<String, Images> found = new HashMap<>();
        JsonArray packs = new JsonArray();
        Set<String> packIds = new HashSet<>();
        int stickers = 0;
        for (String kind : new String[]{"stickers", "emoji"}) {
            Path root = dir.resolve(kind);
            ensureFolder(root, kind);
            for (Path packDir : sorted(root)) {
                if (!Files.isDirectory(packDir) || packs.size() >= MAX_PACKS) {
                    continue;
                }
                String packId = unique(cleanId(packDir.getFileName().toString()), packIds);
                JsonObject pack = readPack(packDir, packId, kind.equals("emoji"), found);
                if (pack != null) {
                    packs.add(pack);
                    stickers += pack.getAsJsonArray("stickers").size();
                }
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", ++version);
        root.add("packs", packs);
        images = found;
        index = root.toString();
        log.accept("Stickers: " + packs.size() + " packs, " + stickers + " stickers");
    }

    private JsonObject readPack(Path packDir, String packId, boolean emoji, Map<String, Images> found) {
        JsonObject meta = new JsonObject();
        Path metaFile = packDir.resolve("pack.json");
        if (Files.exists(metaFile)) {
            try {
                meta = JsonParser.parseString(Files.readString(metaFile, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                log.accept("Stickers: " + metaFile + " is not valid JSON, ignored");
            }
        }
        JsonArray list = new JsonArray();
        Set<String> ids = new HashSet<>();
        for (Path file : sorted(packDir)) {
            String name = file.getFileName().toString();
            if (!EXT.matcher(name).matches() || !Files.isRegularFile(file) || list.size() >= MAX_PER_PACK) {
                continue;
            }
            try {
                StickerImages.Converted c = StickerImages.convert(Files.readAllBytes(file));
                if (c == null) {
                    log.accept("Stickers: can't read " + file);
                    continue;
                }
                String id = unique(cleanId(name.substring(0, name.lastIndexOf('.'))), ids);
                JsonObject s = new JsonObject();
                s.addProperty("id", id);
                s.addProperty("w", c.frameW());
                s.addProperty("h", c.frameH());
                s.addProperty("thumb", true);
                if (c.frames() > 1) {
                    s.addProperty("frames", c.frames());
                    s.addProperty("fps", c.fps());
                    s.addProperty("cols", c.cols());
                }
                list.add(s);
                found.put(packId + "/" + id, new Images(c.png(), c.thumb()));
            } catch (IOException | RuntimeException e) {
                log.accept("Stickers: can't read " + file + ": " + e);
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        JsonObject pack = new JsonObject();
        pack.addProperty("id", packId);
        pack.addProperty("title", meta.has("title") ? meta.get("title").getAsString() : packDir.getFileName().toString());
        pack.addProperty("emoji", emoji);
        pack.add("stickers", list);
        return pack;
    }

    private static List<Path> sorted(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (var s = Files.list(folder)) {
            return s.sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString())).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static String cleanId(String name) {
        String id = name.replaceAll("[^A-Za-z0-9_\\-]", "_");
        id = id.length() > 60 ? id.substring(0, 60) : id;
        return id.isEmpty() ? "_" : id;
    }

    private static String unique(String id, Set<String> taken) {
        String out = id;
        for (int n = 2; !taken.add(out.toLowerCase(Locale.ROOT)); n++) {
            out = id + "_" + n;
        }
        return out;
    }

    private void ensureFolder(Path root, String kind) {
        try {
            Files.createDirectories(root);
            Path readme = root.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, readme(kind), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.accept("Stickers: can't create " + root + ": " + e);
        }
    }

    private static String readme(String kind) {
        boolean emoji = kind.equals("emoji");
        return """
                InGame Messenger: %s

                Each folder here is one pack. Put the images in it, one file per %s:

                  %s/
                    my-pack/
                      pack.json        (optional) {"title": "My pack"}
                      001.png
                      002.gif

                Supported: PNG and JPG (still), GIF (animated). PNG with transparency looks best.
                Recommended size: %s. Bigger images are scaled down.
                Files are sorted by name; the first one is the pack's icon.

                After adding or changing packs, run /messenger reload (or restart the server).
                """.formatted(emoji ? "emoji packs" : "sticker packs", emoji ? "emoji" : "sticker", kind,
                emoji ? "100x100, square" : "512x512");
    }

    private static String emptyIndex() {
        return "{\"version\":0,\"packs\":[]}";
    }
}
