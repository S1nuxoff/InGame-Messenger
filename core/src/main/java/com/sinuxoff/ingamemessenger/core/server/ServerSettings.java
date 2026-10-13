package com.sinuxoff.ingamemessenger.core.server;

import com.sinuxoff.ingamemessenger.core.storage.DatabaseConfig;
import com.sinuxoff.ingamemessenger.core.storage.StorageConfig;

/** Server config, read by each platform from config/ingamemessenger/server.toml. */
public interface ServerSettings {
    /** Show who's typing. */
    boolean typing();

    /** Minimum time between two stickers from one player. */
    int stickerCooldownMs();

    boolean voiceOn();

    int voiceMaxSeconds();

    /** 0 = keep forever. */
    int voiceKeepDays();

    /** "server", "imgbb", "catbox" or "off". */
    String imageProvider();

    String imgbbKey();

    /** 0 = keep forever. */
    int imageKeepDays();

    int imageMaxKb();

    /** Show stickers, voice messages, pictures, polls and private messages to players without the mod as chat lines. */
    boolean plainLines();

    /** Tell players without the mod, once, that the server uses it. */
    boolean installHint();

    /** The server's own text for a plain line ("install", "sticker", "voice", ...), or "" for the default. */
    String plainText(String line);

    DatabaseConfig database();

    StorageConfig storage();
}
