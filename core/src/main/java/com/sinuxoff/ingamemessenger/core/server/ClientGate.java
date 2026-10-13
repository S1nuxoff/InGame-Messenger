package com.sinuxoff.ingamemessenger.core.server;

import com.google.gson.JsonParser;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.*;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The platform as {@link ChatServer} sees it: packets reach only players whose client has the same protocol (they
 * said Hello). Everyone else gets stickers, voice messages, pictures, polls and private messages as plain chat lines,
 * in the server's own wording when the config has one.
 */
final class ClientGate implements ServerPlatform {
    private final ServerPlatform platform;
    private final Set<UUID> clients = ConcurrentHashMap.newKeySet();

    ClientGate(ServerPlatform platform) {
        this.platform = platform;
    }

    /** Returns false if the player was already known. */
    boolean add(UUID player) {
        return clients.add(player);
    }

    void remove(UUID player) {
        clients.remove(player);
    }

    boolean has(UUID player) {
        return clients.contains(player);
    }

    @Override
    public UUID idOf(String name) {
        return platform.idOf(name);
    }

    @Override
    public boolean mutedElsewhere(UUID id) {
        return platform.mutedElsewhere(id);
    }

    @Override
    public void send(UUID player, Packet packet) {
        if (clients.contains(player)) {
            platform.send(player, packet);
            return;
        }
        if (!platform.settings().plainLines()) {
            return;
        }
        if (packet instanceof Show p) {
            say(player, "ingamemessenger.plain.sticker", "<%s> [sticker]", p.name());
        } else if (packet instanceof VoiceShow p) {
            say(player, "ingamemessenger.plain.voice", "<%s> [voice message, %s s]",
                    p.name(), String.valueOf(Math.max(1, Math.round(p.durationMs() / 1000f))));
        } else if (packet instanceof ImageShow p) {
            int n = p.data().split("\n").length;
            if (n == 1) {
                say(player, "ingamemessenger.plain.picture", "<%s> [picture] %s", p.name(), p.caption());
            } else {
                say(player, "ingamemessenger.plain.pictures", "<%s> [%s pictures] %s", p.name(), String.valueOf(n), p.caption());
            }
        } else if (packet instanceof PollShow p) {
            say(player, "ingamemessenger.plain.poll", "<%s> [poll] %s", p.name(), question(p.json()));
        } else if (packet instanceof DmShow p) {
            if (player.equals(p.to())) {
                say(player, "ingamemessenger.plain.dm", "[%s -> you] %s", p.fromName(), p.text());
            }
        }
    }

    private static String question(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject().get("q").getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    @Override
    public Collection<ChatPlayer> online() {
        return platform.online();
    }

    @Override
    public String nameOf(UUID id) {
        return platform.nameOf(id);
    }

    @Override
    public boolean isOperator(UUID id) {
        return platform.isOperator(id);
    }

    @Override
    public void tell(UUID player, String translationKey) {
        platform.tell(player, translationKey);
    }

    @Override
    public void say(UUID player, String translationKey, String fallback, String... args) {
        String own = platform.settings().plainText(translationKey.substring(translationKey.lastIndexOf('.') + 1));
        platform.say(player, translationKey, own.isEmpty() ? fallback : own, args);
    }

    @Override
    public String modVersion() {
        return platform.modVersion();
    }

    @Override
    public void execute(Runnable task) {
        platform.execute(task);
    }

    @Override
    public Path worldDir() {
        return platform.worldDir();
    }

    @Override
    public Path configDir() {
        return platform.configDir();
    }

    @Override
    public ServerSettings settings() {
        return platform.settings();
    }

    @Override
    public void info(String message) {
        platform.info(message);
    }

    @Override
    public void warn(String message) {
        platform.warn(message);
    }
}
