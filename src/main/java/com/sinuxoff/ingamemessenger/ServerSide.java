package com.sinuxoff.ingamemessenger;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.server.ChatServer;
import com.sinuxoff.ingamemessenger.core.server.ServerPlatform;
import com.sinuxoff.ingamemessenger.core.server.ServerSettings;
import com.sinuxoff.ingamemessenger.core.storage.BlobStore;
import com.sinuxoff.ingamemessenger.core.storage.ChatStore;
import com.sinuxoff.ingamemessenger.core.storage.Database;
import com.sinuxoff.ingamemessenger.core.storage.LocalBlobStore;
import com.sinuxoff.ingamemessenger.core.storage.S3BlobStore;
import com.sinuxoff.ingamemessenger.core.storage.Storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** {@link ServerPlatform} for a running Minecraft server. */
final class ServerSide implements ServerPlatform {
    /** The old chat server: no longer started (the test bots and test chat still refer to it). */
    static volatile ChatServer chat;
    /** Null while no server runs. */
    static volatile com.sinuxoff.ingamemessenger.core.hub.Hub hub;

    private final MinecraftServer server;

    private ServerSide(MinecraftServer server) {
        this.server = server;
    }

    static void start(MinecraftServer server) {
        ServerSide side = new ServerSide(server);
        ServerSettings s = side.settings();
        Path data = side.worldDir();
        Storage storage;
        try {
            Database db = Database.open(s.database(), data, Platform.get().configDir().resolve("libs"), InGameMessenger.LOG::info);
            BlobStore files = s.storage().s3() ? new S3BlobStore(s.storage()) : new LocalBlobStore(data);
            storage = Storage.threaded(new com.sinuxoff.ingamemessenger.core.storage.Store(db), files);
        } catch (Exception e) {
            InGameMessenger.LOG.error("InGame Messenger is OFF: can't open the {} database ({}). Check [database] in {}",
                    s.database().type(), e.toString(), Platform.get().configDir().resolve("server.toml"));
            return;
        }
        com.sinuxoff.ingamemessenger.core.hub.Hub h = new com.sinuxoff.ingamemessenger.core.hub.Hub(side, storage);
        hub = h;
        h.reloadStickers();
    }

    static void stop() {
        com.sinuxoff.ingamemessenger.core.hub.Hub h = hub;
        hub = null;
        if (h != null) {
            h.close();
        }
    }

    /** Text component as JSON, the way the history stores it. */
    static String json(MinecraftServer server, Component c) {
        return ServerCompat.toJson(c, server.registryAccess());
    }

    @Override
    public Collection<ChatPlayer> online() {
        List<ChatPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            out.add(new ChatPlayer(p.getUUID(), p.getGameProfile().name()));
        }
        return out;
    }

    /** Online players, test bots, then the server's profile cache. */
    @Override
    public String nameOf(UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            return p.getGameProfile().name();
        }
        String bot = FakeBots.nameOf(id);
        if (bot != null) {
            return bot;
        }
        return ServerCompat.knownName(server, id);
    }

    /** The display name: team colors and prefixes, a rank mod's look; without its click and hover. */
    @Override
    public String shownName(UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p == null) {
            return null;
        }
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        boolean[] styled = {false};
        p.getDisplayName().visit((style, part) -> {
            styled[0] |= style.getColor() != null || style.isBold() || style.isItalic();
            out.append(Component.literal(part).withStyle(style.withClickEvent(null).withHoverEvent(null).withInsertion(null)));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return !styled[0] && out.getString().equals(p.getGameProfile().name()) ? null : json(server, out);
    }

    @Override
    public boolean isOperator(UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p != null && ServerCompat.isOperator(p);
    }

    @Override
    public void send(UUID player, Packet packet) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) {
            Net.toPlayer(p, packet);
        }
    }

    @Override
    public void tell(UUID player, String translationKey) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) {
            p.sendSystemMessage(Component.translatable(translationKey));
        }
    }

    @Override
    public void say(UUID player, String translationKey, String fallback, String... args) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) {
            //? if >=1.19.4 {
            p.sendSystemMessage(Component.translatableWithFallback(translationKey, fallback, (Object[]) args)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            //?} else {
            /*// no fallback before 1.19.4: these lines are for players without the mod, who couldn't translate the key;
            // the key rides along as the insertion, so a client with the mod still knows the line
            p.sendSystemMessage(Component.literal(String.format(fallback, (Object[]) args))
                    .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withInsertion(translationKey)));
            *///?}
        }
    }

    /** "<name> body" the way the game shows chat, with the sender's display name (team colors, a rank mod's prefix). */
    @Override
    public void sayAs(UUID player, UUID sender, String senderName, String bodyKey, String bodyFallback, String... args) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p == null) {
            return;
        }
        ServerPlayer s = server.getPlayerList().getPlayer(sender);
        Component name = s != null ? s.getDisplayName() : Component.literal(senderName);
        //? if >=1.19.4 {
        Component body = Component.translatableWithFallback(bodyKey, bodyFallback, (Object[]) args);
        //?} else {
        /*Component body = Component.literal(String.format(bodyFallback, (Object[]) args));
        *///?}
        p.sendSystemMessage(Component.translatable("chat.type.text", name, body));
    }

    @Override
    public String modVersion() {
        return Platform.get().modVersion();
    }

    @Override
    public void execute(Runnable task) {
        server.execute(task);
    }

    @Override
    public Path worldDir() {
        return Platform.ensureDir(server.getWorldPath(LevelResource.ROOT).resolve(InGameMessenger.MOD_ID));
    }

    @Override
    public Path configDir() {
        return Platform.get().configDir();
    }

    @Override
    public ServerSettings settings() {
        return Platform.get().settings();
    }

    @Override
    public void info(String message) {
        InGameMessenger.LOG.info(message);
    }

    @Override
    public void warn(String message) {
        InGameMessenger.LOG.warn(message);
    }
}
