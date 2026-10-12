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

    DatabaseConfig database();

    StorageConfig storage();
}
