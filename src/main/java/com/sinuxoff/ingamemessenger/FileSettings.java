package com.sinuxoff.ingamemessenger;

import com.sinuxoff.ingamemessenger.core.server.ChatServer;
import com.sinuxoff.ingamemessenger.core.storage.DatabaseConfig;
import com.sinuxoff.ingamemessenger.core.storage.StorageConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/ingamemessenger/server.toml. A small TOML subset: sections and key = value lines. Missing or invalid values
 * fall back to defaults. The file is rewritten with comments when it's created or a value is changed in game.
 */
public final class FileSettings implements ModSettings {
    private record Key(String section, String name, Object def, String comment) {
    }

    private static final Key TYPING = new Key("chat", "typing", true,
            "Show who's typing. Players can also turn it off for themselves.");
    private static final Key STICKER_COOLDOWN = new Key("stickers", "cooldownMs", 700,
            "Minimum time between two stickers from one player, in milliseconds.\n"
                    + "Packs go into the stickers/ and emoji/ folders next to this file (see README.txt there).");
    private static final Key VOICE_ON = new Key("voice", "enabled", true, "Allow voice messages.");
    private static final Key VOICE_MAX = new Key("voice", "maxSeconds", 60, "Maximum length of a voice message, in seconds (5-300).");
    private static final Key VOICE_KEEP = new Key("voice", "keepDays", 7, "Delete voice messages after this many days (0 = keep forever).");
    private static final Key IMAGES = new Key("images", "provider", "server", """
            Where pictures are stored:
              "server" - in this server's file storage (see [storage]); players download them from the server
              "imgbb"  - uploaded to ImgBB by the server (needs imgbbKey)
              "catbox" - players upload to catbox.moe themselves; the server only checks the links
              "off"    - no pictures""");
    private static final Key IMGBB_KEY = new Key("images", "imgbbKey", "", "ImgBB API key (https://api.imgbb.com). Stays on the server.");
    private static final Key IMAGE_KEEP = new Key("images", "keepDays", 30, "Delete pictures after this many days (0 = keep forever).");
    private static final Key IMAGE_MAX = new Key("images", "maxKb", 800,
            "Maximum picture size after compression, in KB (64-" + ChatServer.IMAGE_MAX_KB + ").");
    private static final Key PLAIN_ON = new Key("withoutMod", "plainLines", true, """
            Players without the mod can always join. These settings are about what they see.
            Show them stickers, voice messages, pictures, polls and private messages as plain chat lines.""");
    private static final Key INSTALL_HINT = new Key("withoutMod", "installHint", true,
            "Tell them once, a few seconds after joining, that this server uses InGame Messenger.");
    private static final Key TEXT_INSTALL = new Key("withoutMod", "installText", "", """
            Your own wording for those lines. Empty = the default text. Each %s is replaced, in this order:
              installText  - nothing
              stickerText  - player
              voiceText    - player, seconds
              pictureText  - player, caption
              picturesText - player, number of pictures, caption
              pollText     - player, question
              dmText       - sender, text
            Colors: & codes, e.g. "&e%s&7 sent a sticker".""");
    private static final Key TEXT_STICKER = new Key("withoutMod", "stickerText", "", "");
    private static final Key TEXT_VOICE = new Key("withoutMod", "voiceText", "", "");
    private static final Key TEXT_PICTURE = new Key("withoutMod", "pictureText", "", "");
    private static final Key TEXT_PICTURES = new Key("withoutMod", "picturesText", "", "");
    private static final Key TEXT_POLL = new Key("withoutMod", "pollText", "", "");
    private static final Key TEXT_DM = new Key("withoutMod", "dmText", "", "");
    private static final Key DB_TYPE = new Key("database", "type", "h2", """
            Where messages are stored. Changes need a server restart.
              "h2"         - a file in the world folder, nothing to set up
              "mysql", "mariadb", "postgresql" - an external database; its driver is downloaded on first start""");
    private static final Key DB_HOST = new Key("database", "host", "localhost", "");
    private static final Key DB_PORT = new Key("database", "port", 0, "0 = the default port (3306 for MySQL/MariaDB, 5432 for PostgreSQL)");
    private static final Key DB_NAME = new Key("database", "name", "ingamemessenger", "");
    private static final Key DB_USER = new Key("database", "user", "", "");
    private static final Key DB_PASSWORD = new Key("database", "password", "", "");
    private static final Key DB_PREFIX = new Key("database", "tablePrefix", "igm_", "Prefix for the table names.");
    private static final Key FILES_TYPE = new Key("storage", "type", "local", """
            Where voice messages and pictures are stored. Changes need a server restart.
              "local" - the world folder
              "s3"    - any S3-compatible storage: AWS S3, Cloudflare R2, Backblaze B2, MinIO, Wasabi...""");
    private static final Key S3_ENDPOINT = new Key("storage", "endpoint", "",
            "e.g. https://<account>.r2.cloudflarestorage.com or https://s3.eu-central-1.amazonaws.com");
    private static final Key S3_REGION = new Key("storage", "region", "auto", "\"auto\" for R2, the bucket's region for AWS.");
    private static final Key S3_BUCKET = new Key("storage", "bucket", "", "");
    private static final Key S3_ACCESS = new Key("storage", "accessKey", "", "");
    private static final Key S3_SECRET = new Key("storage", "secretKey", "", "");
    private static final Key S3_PATH_STYLE = new Key("storage", "pathStyle", true,
            "true: endpoint/bucket/key URLs (works almost everywhere); false: bucket.endpoint/key.");
    private static final Key S3_PREFIX = new Key("storage", "prefix", "", "Folder inside the bucket (empty = the root).");
    private static final Key[] ALL = {TYPING, STICKER_COOLDOWN, VOICE_ON, VOICE_MAX, VOICE_KEEP, IMAGES, IMGBB_KEY, IMAGE_KEEP, IMAGE_MAX,
            PLAIN_ON, INSTALL_HINT, TEXT_INSTALL, TEXT_STICKER, TEXT_VOICE, TEXT_PICTURE, TEXT_PICTURES, TEXT_POLL, TEXT_DM,
            DB_TYPE, DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, DB_PREFIX,
            FILES_TYPE, S3_ENDPOINT, S3_REGION, S3_BUCKET, S3_ACCESS, S3_SECRET, S3_PATH_STYLE, S3_PREFIX};

    private final Path file;
    private final Map<Key, Object> values = new LinkedHashMap<>();

    public FileSettings(Path file) {
        this.file = file;
        reload();
    }

    @Override
    public synchronized void reload() {
        for (Key k : ALL) {
            values.put(k, k.def());
        }
        if (!Files.exists(file) || read() < ALL.length) {
            save(); // new file, or new keys added in an update
        }
    }

    /** Returns how many known keys the file has. */
    private int read() {
        String section = "";
        int found = 0;
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).strip();
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String name = line.substring(0, eq).strip(), value = line.substring(eq + 1).strip();
                for (Key k : ALL) {
                    if (k.section().equals(section) && k.name().equals(name)) {
                        found++;
                        Object v = parse(k, value);
                        if (v != null) {
                            values.put(k, v);
                        } else {
                            InGameMessenger.LOG.warn("{}: invalid value for [{}] {}, using the default", file, section, name);
                        }
                    }
                }
            }
        } catch (IOException e) {
            InGameMessenger.LOG.warn("Can't read {}, using defaults: {}", file, e.toString());
            return ALL.length; // don't overwrite a file that couldn't be read
        }
        return found;
    }

    private static Object parse(Key k, String v) {
        try {
            if (k.def() instanceof Boolean) {
                return v.equals("true") ? Boolean.TRUE : v.equals("false") ? Boolean.FALSE : null;
            }
            if (k.def() instanceof Integer) {
                return Integer.parseInt(v);
            }
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                return v.substring(1, v.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private synchronized void save() {
        StringBuilder b = new StringBuilder("# InGame Messenger server config. Apply changes with /messenger reload.\n");
        String section = "";
        for (Key k : ALL) {
            if (!k.section().equals(section)) {
                section = k.section();
                b.append("\n[").append(section).append("]\n");
            }
            if (!k.comment().isEmpty()) {
                for (String c : k.comment().split("\n")) {
                    b.append("# ").append(c).append('\n');
                }
            }
            Object v = values.get(k);
            b.append(k.name()).append(" = ").append(v instanceof String s ? '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"' : v)
                    .append("\n\n");
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, b.toString().stripTrailing() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            InGameMessenger.LOG.warn("Can't write {}: {}", file, e.toString());
        }
    }

    private synchronized String str(Key k) {
        return (String) values.get(k);
    }

    private synchronized int num(Key k, int min, int max) {
        return Math.max(min, Math.min(max, (Integer) values.get(k)));
    }

    private synchronized boolean bool(Key k) {
        return (Boolean) values.get(k);
    }

    private synchronized void set(Key k, Object v) {
        values.put(k, v);
        save();
    }

    @Override
    public boolean typing() {
        return bool(TYPING);
    }

    @Override
    public int stickerCooldownMs() {
        return num(STICKER_COOLDOWN, 0, 60_000);
    }

    @Override
    public boolean voiceOn() {
        return bool(VOICE_ON);
    }

    @Override
    public int voiceMaxSeconds() {
        return num(VOICE_MAX, 5, 300);
    }

    @Override
    public int voiceKeepDays() {
        return num(VOICE_KEEP, 0, 3650);
    }

    @Override
    public String imageProvider() {
        return str(IMAGES);
    }

    @Override
    public String imgbbKey() {
        return str(IMGBB_KEY);
    }

    @Override
    public int imageKeepDays() {
        return num(IMAGE_KEEP, 0, 3650);
    }

    @Override
    public int imageMaxKb() {
        return num(IMAGE_MAX, 64, ChatServer.IMAGE_MAX_KB);
    }

    @Override
    public boolean plainLines() {
        return bool(PLAIN_ON);
    }

    @Override
    public boolean installHint() {
        return bool(INSTALL_HINT);
    }

    @Override
    public String plainText(String line) {
        return colors(switch (line) {
            case "install" -> str(TEXT_INSTALL);
            case "sticker" -> str(TEXT_STICKER);
            case "voice" -> str(TEXT_VOICE);
            case "picture" -> str(TEXT_PICTURE);
            case "pictures" -> str(TEXT_PICTURES);
            case "poll" -> str(TEXT_POLL);
            case "dm" -> str(TEXT_DM);
            default -> "";
        });
    }

    private static String colors(String text) {
        return text.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    @Override
    public DatabaseConfig database() {
        return new DatabaseConfig(str(DB_TYPE), str(DB_HOST), num(DB_PORT, 0, 65535), str(DB_NAME), str(DB_USER), str(DB_PASSWORD),
                str(DB_PREFIX));
    }

    @Override
    public StorageConfig storage() {
        return new StorageConfig(str(FILES_TYPE), str(S3_ENDPOINT), str(S3_REGION), str(S3_BUCKET), str(S3_ACCESS), str(S3_SECRET),
                bool(S3_PATH_STYLE), str(S3_PREFIX));
    }

    @Override
    public void setTyping(boolean on) {
        set(TYPING, on);
    }

    @Override
    public void setVoiceOn(boolean on) {
        set(VOICE_ON, on);
    }

    @Override
    public void setVoiceMaxSeconds(int seconds) {
        set(VOICE_MAX, seconds);
    }

    @Override
    public void setVoiceKeepDays(int days) {
        set(VOICE_KEEP, days);
    }
}
