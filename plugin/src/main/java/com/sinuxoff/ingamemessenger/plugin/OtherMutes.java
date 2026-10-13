package com.sinuxoff.ingamemessenger.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Who is muted by Essentials, LiteBans or AdvancedBan: then they're muted in our chat too (stickers, voice,
 * pictures...). Looked up off the main thread every couple of seconds, since LiteBans asks its database.
 */
final class OtherMutes {
    private final Logger log;
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    private final Set<String> broken = ConcurrentHashMap.newKeySet();

    OtherMutes(Logger log) {
        this.log = log;
    }

    boolean has(UUID id) {
        return muted.contains(id);
    }

    void refresh() {
        Set<UUID> now = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (essentials(p) || liteBans(p) || advancedBan(p)) {
                now.add(p.getUniqueId());
            }
        }
        muted.retainAll(now);
        muted.addAll(now);
    }

    private boolean essentials(Player p) {
        Plugin ess = enabled("Essentials");
        if (ess == null) {
            return false;
        }
        try {
            Object user = ess.getClass().getMethod("getUser", UUID.class).invoke(ess, p.getUniqueId());
            return user != null && (Boolean) user.getClass().getMethod("isMuted").invoke(user);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return failed("Essentials", e);
        }
    }

    private boolean liteBans(Player p) {
        Plugin lb = enabled("LiteBans");
        if (lb == null) {
            return false;
        }
        try {
            Class<?> db = lb.getClass().getClassLoader().loadClass("litebans.api.Database");
            Object api = db.getMethod("get").invoke(null);
            InetSocketAddress addr = p.getAddress();
            String ip = addr == null || addr.getAddress() == null ? null : addr.getAddress().getHostAddress();
            return (Boolean) db.getMethod("isPlayerMuted", UUID.class, String.class).invoke(api, p.getUniqueId(), ip);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return failed("LiteBans", e);
        }
    }

    private boolean advancedBan(Player p) {
        Plugin ab = enabled("AdvancedBan");
        if (ab == null) {
            return false;
        }
        try {
            ClassLoader cl = ab.getClass().getClassLoader();
            Class<?> ids = cl.loadClass("me.leoko.advancedban.manager.UUIDManager");
            Object uuid = ids.getMethod("getUUID", String.class).invoke(ids.getMethod("get").invoke(null), p.getName());
            Class<?> pm = cl.loadClass("me.leoko.advancedban.manager.PunishmentManager");
            return (Boolean) pm.getMethod("isMuted", String.class).invoke(pm.getMethod("get").invoke(null), String.valueOf(uuid));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return failed("AdvancedBan", e);
        }
    }

    private static Plugin enabled(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled() ? p : null;
    }

    /** Said once per plugin: its version may have changed its API. */
    private boolean failed(String plugin, Exception e) {
        if (broken.add(plugin)) {
            log.warning("Can't read " + plugin + "'s mutes (" + e + "); its muted players can still post stickers and pictures");
        }
        return false;
    }
}
