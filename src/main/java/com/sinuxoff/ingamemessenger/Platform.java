package com.sinuxoff.ingamemessenger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;

import java.nio.file.Path;
import java.util.List;

/** What the mod needs from the mod loader. Each loader sets its implementation at startup. */
public interface Platform {
    /** e.g. "NeoForge 21.1.251". */
    String loader();

    /** The mod's own folder in the config folder (config/ingamemessenger/). */
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String modId);

    /** Installed mods as "Name version", given to Gemini as context. */
    List<String> mods();

    ModSettings settings();

    /** The server in this process (dedicated or integrated), or null. */
    MinecraftServer server();

    void sendToServer(Packet packet);

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
