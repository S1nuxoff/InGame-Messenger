package com.sinuxoff.ingamemessenger.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Join, leave and death lines as Paper has them: components that each client shows in its own language. Only loaded
 * on Paper and its forks.
 */
final class PaperText {
    static final boolean ON = has();

    private PaperText() {
    }

    static String join(PlayerJoinEvent e) {
        return json(e.joinMessage());
    }

    static String quit(PlayerQuitEvent e) {
        return json(e.quitMessage());
    }

    static String death(PlayerDeathEvent e) {
        return json(e.deathMessage());
    }

    private static String json(Component c) {
        return c == null || c.equals(Component.empty()) ? null : GsonComponentSerializer.gson().serialize(c);
    }

    private static boolean has() {
        try {
            Class.forName("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
            PlayerJoinEvent.class.getMethod("joinMessage");
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }
}
