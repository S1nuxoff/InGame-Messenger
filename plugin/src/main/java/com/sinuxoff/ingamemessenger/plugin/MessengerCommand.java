package com.sinuxoff.ingamemessenger.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import com.sinuxoff.ingamemessenger.core.server.ChatServer;

import java.util.List;

/** /messenger for operators: reload, typing, voice. The same as the mod's command. */
final class MessengerCommand implements TabExecutor {
    private final MessengerPlugin plugin;

    MessengerCommand(MessengerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String what = run(args);
        if (what == null) {
            return false; // shows the usage
        }
        sender.sendMessage("InGame Messenger: " + what);
        return true;
    }

    private String run(String[] a) {
        ChatServer chat = plugin.chat;
        // test servers: who has the messenger
        if (a.length == 1 && a[0].equals("clients") && Boolean.getBoolean("ingamemessenger.dev") && chat != null) {
            StringBuilder b = new StringBuilder();
            for (var p : org.bukkit.Bukkit.getOnlinePlayers()) {
                b.append(b.length() > 0 ? ", " : "").append(p.getName())
                        .append(chat.hasClient(p.getUniqueId()) ? ": messenger" : ": plain chat");
            }
            return b.length() > 0 ? b.toString() : "nobody on";
        }
        if (a.length == 1 && a[0].equals("reload")) {
            plugin.settings.reload();
            if (chat != null) {
                chat.featuresChanged();
                chat.reloadStickers();
            }
            return "config and stickers reloaded";
        }
        if (a.length == 2 && a[0].equals("typing") && bool(a[1]) != null) {
            boolean on = bool(a[1]);
            plugin.settings.setTyping(on);
            return changed("typing indicator " + (on ? "on" : "off"));
        }
        if (a.length == 2 && a[0].equals("voice") && bool(a[1]) != null) {
            boolean on = bool(a[1]);
            plugin.settings.setVoiceOn(on);
            return changed("voice messages " + (on ? "on" : "off"));
        }
        if (a.length == 3 && a[0].equals("voice") && a[1].equals("length")) {
            Integer s = number(a[2], 5, 300);
            if (s != null) {
                plugin.settings.setVoiceMaxSeconds(s);
                return changed("voice messages up to " + s + " s");
            }
        }
        if (a.length == 3 && a[0].equals("voice") && a[1].equals("keep")) {
            Integer d = number(a[2], 0, 3650);
            if (d != null) {
                plugin.settings.setVoiceKeepDays(d);
                return changed(d == 0 ? "voice messages kept forever" : "voice messages kept " + d + " days");
            }
        }
        return null;
    }

    private String changed(String what) {
        ChatServer chat = plugin.chat;
        if (chat != null) {
            chat.featuresChanged();
        }
        return what;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] a) {
        List<String> options = switch (a.length) {
            case 1 -> List.of("reload", "typing", "voice");
            case 2 -> a[0].equals("typing") ? List.of("true", "false")
                    : a[0].equals("voice") ? List.of("true", "false", "length", "keep") : List.of();
            default -> List.of();
        };
        String last = a.length == 0 ? "" : a[a.length - 1];
        return options.stream().filter(o -> o.startsWith(last)).toList();
    }

    private static Boolean bool(String s) {
        return s.equals("true") ? Boolean.TRUE : s.equals("false") ? Boolean.FALSE : null;
    }

    private static Integer number(String s, int min, int max) {
        try {
            int n = Integer.parseInt(s);
            return n >= min && n <= max ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
