package com.sinuxoff.ingamemessenger.plugin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.server.ModSettings;
import com.sinuxoff.ingamemessenger.core.server.ServerPlatform;
import com.sinuxoff.ingamemessenger.core.server.ServerSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** {@link ServerPlatform} on the Bukkit API. Packets are plugin messages, the same bytes the mod sends. */
final class BukkitPlatform implements ServerPlatform {
    private final MessengerPlugin plugin;
    private final ModSettings settings;

    BukkitPlatform(MessengerPlugin plugin, ModSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @Override
    public Collection<ChatPlayer> online() {
        List<ChatPlayer> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(new ChatPlayer(p.getUniqueId(), p.getName()));
        }
        return out;
    }

    @Override
    public String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getName() : Bukkit.getOfflinePlayer(id).getName();
    }

    @Override
    public boolean isOperator(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null && (p.isOp() || p.hasPermission(MessengerPlugin.ADMIN));
    }

    @Override
    public UUID idOf(String name) {
        Player p = Bukkit.getPlayerExact(name);
        return p != null ? p.getUniqueId() : null;
    }

    @Override
    public boolean mutedElsewhere(UUID id) {
        return plugin.others.has(id);
    }

    @Override
    public void send(UUID player, Packet packet) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            // Bukkit drops it if the client hasn't registered the channel
            p.sendPluginMessage(plugin, MessengerPlugin.channel(packet.type()), Protocol.encode(packet));
        }
    }

    @Override
    public void tell(UUID player, String translationKey) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            p.spigot().sendMessage(new TranslatableComponent(translationKey));
        }
    }

    @Override
    public void say(UUID player, String translationKey, String fallback, String... args) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            TranslatableComponent text = new TranslatableComponent(translationKey, (Object[]) args);
            text.setFallback(fallback);
            text.setColor(ChatColor.GRAY);
            p.spigot().sendMessage(text);
        }
    }

    @Override
    @SuppressWarnings("deprecation") // getPluginMeta() is Paper-only
    public String modVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public void execute(Runnable task) {
        plugin.tasks.run(task);
    }

    /** ingamemessenger/ in the main world's folder, like the mod. */
    @Override
    public Path worldDir() {
        return dir(Bukkit.getWorlds().get(0).getWorldFolder().toPath().resolve("ingamemessenger"));
    }

    @Override
    public Path configDir() {
        return dir(plugin.getDataFolder().toPath());
    }

    @Override
    public ServerSettings settings() {
        return settings;
    }

    @Override
    public void info(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void warn(String message) {
        plugin.getLogger().warning(message);
    }

    private static Path dir(Path p) {
        try {
            return Files.createDirectories(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
