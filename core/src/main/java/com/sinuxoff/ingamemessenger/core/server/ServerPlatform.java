package com.sinuxoff.ingamemessenger.core.server;

import com.sinuxoff.ingamemessenger.core.protocol.Packet;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

/** What {@link ChatServer} needs from the server it runs on (NeoForge, Fabric, Paper...). */
public interface ServerPlatform {
    record ChatPlayer(UUID id, String name) {
    }

    Collection<ChatPlayer> online();

    /** Name of an online or known player, or null. */
    String nameOf(UUID id);

    boolean isOperator(UUID id);

    /** Does nothing if the player is offline. */
    void send(UUID player, Packet packet);

    /** Sends a translated system line to the player. */
    void tell(UUID player, String translationKey);

    /** Runs the task on the server thread. */
    void execute(Runnable task);

    /** The mod's data folder inside the world. */
    Path worldDir();

    /** The mod's config folder (sticker and emoji packs live here). */
    Path configDir();

    ServerSettings settings();

    void info(String message);

    void warn(String message);
}
