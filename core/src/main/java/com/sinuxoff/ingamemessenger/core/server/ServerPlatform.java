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

    /** The online player with this name (any case), or null. */
    default UUID idOf(String name) {
        for (ChatPlayer p : online()) {
            if (p.name().equalsIgnoreCase(name)) {
                return p.id();
            }
        }
        return null;
    }

    /**
     * How the server shows an online player's name (a rank, colors) as component JSON, or null if it's just the name.
     * Clients put it over the player's bubbles.
     */
    default String shownName(UUID id) {
        return null;
    }

    /** Whether another plugin or mod (Essentials, LiteBans...) has the player muted: then they're muted in the chat. */
    default boolean mutedElsewhere(UUID id) {
        return false;
    }

    /** Does nothing if the player is offline. */
    void send(UUID player, Packet packet);

    /** Sends a translated system line to the player. */
    void tell(UUID player, String translationKey);

    /** Sends a translated line with arguments; clients without the mod see the English fallback. */
    void say(UUID player, String translationKey, String fallback, String... args);

    /**
     * A line for a player without the mod, as if sender had written body in the chat: in the server's own look of
     * chat lines (rank, name colors). body is translated where possible, else its English fallback.
     */
    default void sayAs(UUID player, UUID sender, String senderName, String bodyKey, String bodyFallback, String... args) {
        String[] all = new String[args.length + 1];
        all[0] = senderName;
        System.arraycopy(args, 0, all, 1, args.length);
        say(player, bodyKey, "<%s> " + bodyFallback, all);
    }

    /** The mod's version, e.g. "1.0.0". */
    String modVersion();

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
