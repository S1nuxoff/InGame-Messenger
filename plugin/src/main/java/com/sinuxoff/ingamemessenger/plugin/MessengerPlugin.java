package com.sinuxoff.ingamemessenger.plugin;

import com.google.gson.JsonObject;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.PacketType;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.hub.Hub;
import com.sinuxoff.ingamemessenger.core.server.FileSettings;
import com.sinuxoff.ingamemessenger.core.server.ModSettings;
import com.sinuxoff.ingamemessenger.core.storage.BlobStore;
import com.sinuxoff.ingamemessenger.core.storage.Store;
import com.sinuxoff.ingamemessenger.core.storage.Database;
import com.sinuxoff.ingamemessenger.core.storage.LocalBlobStore;
import com.sinuxoff.ingamemessenger.core.storage.S3BlobStore;
import com.sinuxoff.ingamemessenger.core.storage.Storage;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * InGame Messenger on Paper, Spigot, Purpur and Folia: the same chat's server as the mod, talking to the clients
 * through plugin messages.
 */
public final class MessengerPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    static final String ADMIN = "ingamemessenger.admin";
    static final String MUTE = "ingamemessenger.mute";
    private static final String WELCOME = channel(Packets.Welcome.TYPE);
    private static final Map<String, PacketType<?>> INCOMING = new HashMap<>();

    static {
        for (PacketType<?> t : com.sinuxoff.ingamemessenger.core.protocol.Protocol.ALL) {
            if (t.direction() == PacketType.Direction.TO_SERVER) {
                INCOMING.put(channel(t), t);
            }
        }
    }

    final Tasks tasks = new Tasks(this);
    final OtherMutes others = new OtherMutes(getLogger());
    /** The format of the last chat line (a chat plugin's, e.g. Essentials'): our plain lines take it too. */
    volatile String chatFormat;
    ModSettings settings;
    /** Null while the database couldn't be opened. */
    volatile Hub hub;
    /** Players past PlayerJoinEvent. */
    private final Set<UUID> joined = ConcurrentHashMap.newKeySet();
    private Method addChannel;
    private boolean decodeWarned;

    static String channel(PacketType<?> t) {
        return Protocol.NAMESPACE + ":" + t.id();
    }

    @Override
    public void onEnable() {
        settings = new FileSettings(getDataFolder().toPath().resolve("server.toml"), getLogger()::warning);
        BukkitPlatform platform = new BukkitPlatform(this, settings);
        Path data = platform.worldDir();
        Storage storage;
        try {
            Database db = Database.open(settings.database(), data, platform.configDir().resolve("libs"), getLogger()::info);
            BlobStore files = settings.storage().s3() ? new S3BlobStore(settings.storage()) : new LocalBlobStore(data);
            storage = Storage.threaded(new Store(db), files);
        } catch (Exception e) {
            getLogger().severe("InGame Messenger is OFF: can't open the " + settings.database().type() + " database (" + e
                    + "). Check [database] in " + platform.configDir().resolve("server.toml"));
            return;
        }
        Hub c = new Hub(platform, storage);
        hub = c;
        c.reloadStickers();

        for (PacketType<?> t : com.sinuxoff.ingamemessenger.core.protocol.Protocol.ALL) {
            if (t.direction() == PacketType.Direction.TO_CLIENT) {
                getServer().getMessenger().registerOutgoingPluginChannel(this, channel(t));
            } else {
                getServer().getMessenger().registerIncomingPluginChannel(this, channel(t), this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        var command = getCommand("messenger");
        if (command != null) {
            MessengerCommand handler = new MessengerCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        tasks.every(100, c::tick);
        tasks.everyAsync(40, others::refresh);
        // after /reload: the players already on get Welcome again
        for (Player p : Bukkit.getOnlinePlayers()) {
            joined.add(p.getUniqueId());
            c.joined(p.getUniqueId());
        }
    }

    @Override
    public void onDisable() {
        tasks.cancelAll();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        Hub c = hub;
        hub = null;
        if (c != null) {
            c.close();
        }
    }

    // Players

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Hub c = hub;
        if (c == null) {
            return;
        }
        UUID id = e.getPlayer().getUniqueId();
        joined.add(id);
        c.joined(id);
        system(PaperText.ON ? PaperText.join(e) : legacy(e.getJoinMessage()));
    }

    /** Some clients list their channels only after joining, too late for the first Welcome. */
    @EventHandler
    public void onChannel(PlayerRegisterChannelEvent e) {
        Hub c = hub;
        UUID id = e.getPlayer().getUniqueId();
        if (c != null && e.getChannel().equals(WELCOME) && joined.contains(id) && !c.hasClient(id)) {
            tasks.runHere(() -> c.joined(id));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        joined.remove(id);
        Hub c = hub;
        if (c != null) {
            system(PaperText.ON ? PaperText.quit(e) : legacy(e.getQuitMessage()));
            c.left(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        system(PaperText.ON ? PaperText.death(e) : legacy(e.getDeathMessage()));
    }

    /** A muted player's chat line doesn't go out (other plugins' mutes are theirs to stop). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChatMuted(AsyncPlayerChatEvent e) {
        Hub c = hub;
        if (c != null && !c.mayChat(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /** Nor /msg, /me and the like where the mute holds. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandMuted(PlayerCommandPreprocessEvent e) {
        Hub c = hub;
        if (c != null && !c.mayCommand(e.getPlayer().getUniqueId(), e.getMessage())) {
            e.setCancelled(true);
        }
    }

    /** A chat line going out: stored in the server's chat channel and sent to the players with the mod as our message. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        chatFormat = e.getFormat();
        Hub c = hub;
        if (c != null) {
            UUID id = e.getPlayer().getUniqueId();
            String name = e.getPlayer().getName(), text = e.getMessage();
            tasks.run(() -> c.chatLine(id, name, text));
        }
    }

    /** A join, leave or death line (as JSON): into the server chat's history. */
    private void system(String json) {
        Hub c = hub;
        if (c != null && json != null) {
            c.systemLine(json);
        }
    }

    /** Spigot has these lines only as text in the server's language. */
    private static String legacy(String text) {
        return text == null || text.isEmpty() ? null : ComponentSerializer.toString(TextComponent.fromLegacyText(text));
    }

    // Packets

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        Hub c = hub;
        PacketType<?> type = INCOMING.get(channel);
        if (c == null || type == null) {
            return;
        }
        Packet packet;
        try {
            packet = Protocol.decode(type, message);
        } catch (RuntimeException e) {
            if (!decodeWarned) {
                decodeWarned = true;
                getLogger().log(Level.WARNING, "Bad packet on " + channel + " from " + player.getName(), e);
            }
            return;
        }
        if (packet instanceof Packets.Hello) {
            listen(player);
        }
        UUID id = player.getUniqueId();
        tasks.runHere(() -> c.handle(id, packet));
    }

    /**
     * A client that says Hello has the mod, so it gets our packets even if its loader didn't list the channels in a
     * way the server saw.
     */
    private void listen(Player player) {
        Set<String> has = player.getListeningPluginChannels();
        for (PacketType<?> t : com.sinuxoff.ingamemessenger.core.protocol.Protocol.ALL) {
            String ch = channel(t);
            if (t.direction() == PacketType.Direction.TO_CLIENT && !has.contains(ch)) {
                try {
                    if (addChannel == null) {
                        addChannel = player.getClass().getMethod("addChannel", String.class);
                    }
                    addChannel.invoke(player, ch);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    getLogger().warning("Can't open the messenger's channels for " + player.getName() + ": " + e);
                    return;
                }
            }
        }
    }
}
