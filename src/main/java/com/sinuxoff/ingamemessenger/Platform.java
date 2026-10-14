package com.sinuxoff.ingamemessenger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.server.ModSettings;

import java.nio.file.Path;
import java.util.List;

/** What the mod needs from the mod loader. Each loader sets its implementation at startup. */
public interface Platform {
    /** e.g. "NeoForge 21.1.251". */
    String loader();

    /** This mod's version. */
    String modVersion();

    /** The mod's own folder in the config folder (config/ingamemessenger/). */
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String modId);

    ModSettings settings();

    /** The server in this process (dedicated or integrated), or null. */
    MinecraftServer server();

    /** Dropped if the server has no channel for it (it doesn't have the mod). */
    void sendToServer(Packet packet);

    /** Dropped if the player's client has no channel for it. */
    void sendToPlayer(ServerPlayer player, Packet packet);

    static Path ensureDir(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            InGameMessenger.LOG.warn("Can't create {}", dir, e);
        }
        return dir;
    }

    static Platform get() {
        Platform p = Holder.current;
        if (p == null) {
            throw new IllegalStateException("InGame Messenger: no platform yet");
        }
        return p;
    }

    static void set(Platform p) {
        Holder.current = p;
    }

    final class Holder {
        private static volatile Platform current;

        private Holder() {
        }
    }
}
