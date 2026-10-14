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

    /**
     * The display name (Essentials puts the rank in it); if it's just the name, the rank's prefix and suffix from
     * Vault (LuckPerms and other permission plugins give them there).
     */
    @Override
    @SuppressWarnings("deprecation") // legacy text: what Spigot has too
    public String shownName(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            return null;
        }
        String shown = p.getDisplayName();
        if (shown == null || ChatColor.stripColor(shown).equals(p.getName())) {
            String[] fix = vault(p);
            shown = fix == null ? shown : fix[0] + p.getName() + fix[1];
        }
        if (shown == null || shown.equals(p.getName())) {
            return null;
        }
        return net.md_5.bungee.chat.ComponentSerializer.toString(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(shown));
    }

    private boolean noVault;

    /** Prefix and suffix from Vault's chat service (color codes turned into §), or null. */
    private String[] vault(Player p) {
        if (noVault) {
            return null;
        }
        try {
            org.bukkit.plugin.Plugin v = Bukkit.getPluginManager().getPlugin("Vault");
            if (v == null || !v.isEnabled()) {
                return null;
            }
            Class<?> chat = v.getClass().getClassLoader().loadClass("net.milkbowl.vault.chat.Chat");
            var reg = Bukkit.getServicesManager().getRegistration(chat);
            if (reg == null) {
                return null;
            }
            Object c = reg.getProvider();
            String pre = (String) chat.getMethod("getPlayerPrefix", Player.class).invoke(c, p);
            String suf = (String) chat.getMethod("getPlayerSuffix", Player.class).invoke(c, p);
            return new String[]{ChatColor.translateAlternateColorCodes('&', pre == null ? "" : pre),
                    ChatColor.translateAlternateColorCodes('&', suf == null ? "" : suf)};
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            noVault = true;
            plugin.getLogger().warning("Can't read ranks from Vault (" + e + "); names show without them");
            return null;
        }
    }

    @Override
    @SuppressWarnings("deprecation") // the legacy text: what Spigot has too
    public String motd() {
        String m = Bukkit.getMotd();
        return m == null ? "" : m;
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

    /** In the format the chat's last line had (a chat plugin's), with the sender's display name (its rank). */
    @Override
    @SuppressWarnings("deprecation") // the Spigot chat API: what Spigot has too
    public void sayAs(UUID player, UUID sender, String senderName, String bodyKey, String bodyFallback, String... args) {
        Player p = Bukkit.getPlayer(player);
        if (p == null) {
            return;
        }
        Player s = Bukkit.getPlayer(sender);
        String name = s != null ? s.getDisplayName() : senderName;
        TranslatableComponent body = new TranslatableComponent(bodyKey, (Object[]) args);
        body.setFallback(bodyFallback);
        body.setColor(ChatColor.WHITE);
        String format = plugin.chatFormat;
        if (format != null) {
            try {
                String[] around = String.format(format, name, "\uE000").split("\uE000", -1);
                if (around.length == 2) {
                    java.util.List<net.md_5.bungee.api.chat.BaseComponent> line = new java.util.ArrayList<>();
                    java.util.Collections.addAll(line, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(around[0]));
                    line.add(body);
                    java.util.Collections.addAll(line, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(around[1]));
                    p.spigot().sendMessage(line.toArray(new net.md_5.bungee.api.chat.BaseComponent[0]));
                    return;
                }
            } catch (java.util.IllegalFormatException ignored) {
                // a format we can't fill: the game's own look below
            }
        }
        net.md_5.bungee.api.chat.TextComponent shown = new net.md_5.bungee.api.chat.TextComponent(
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(name));
        p.spigot().sendMessage(new TranslatableComponent("chat.type.text", shown, body));
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
