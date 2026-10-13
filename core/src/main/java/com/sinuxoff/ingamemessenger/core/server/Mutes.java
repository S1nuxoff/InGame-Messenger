package com.sinuxoff.ingamemessenger.core.server;

import com.sinuxoff.ingamemessenger.core.protocol.Packets.MuteState;
import com.sinuxoff.ingamemessenger.core.storage.ChatStore.Mute;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mutes, separately for the public chat and for private chats: a player muted in the chat can still write privately.
 * A mute from another plugin (Essentials, LiteBans, AdvancedBan...) counts as a mute in the chat.
 */
public final class Mutes {
    public static final long FOREVER = Long.MAX_VALUE;
    private static final Pattern TIME = Pattern.compile("(\\d{1,6})([smhdw])");

    /** Where a mute applies. */
    public enum Scope {
        CHAT, DM, ALL;

        static Scope parse(String s) {
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "chat" -> CHAT;
                case "dm", "pm", "private" -> DM;
                case "all" -> ALL;
                default -> null;
            };
        }

        boolean chat() {
            return this != DM;
        }

        boolean dm() {
            return this != CHAT;
        }
    }

    private final ChatServer server;
    private final Map<UUID, Mute> mutes = new ConcurrentHashMap<>();
    /** Last time a muted player was told so, to not repeat it on every try. */
    private final Map<UUID, Long> told = new ConcurrentHashMap<>();

    Mutes(ChatServer server) {
        this.server = server;
    }

    void load(List<Mute> stored) {
        for (Mute m : stored) {
            mutes.put(m.who(), m);
        }
    }

    /** Whether the player may write where pair points (null = the public chat); tells them if not. */
    boolean mayWrite(UUID who, String pair) {
        return mayWrite(who, pair, true);
    }

    /**
     * For the platform's chat event: false (and the player is told) while our mute holds them in the chat. Other
     * plugins' mutes are theirs to enforce on the chat line.
     */
    public boolean mayChat(UUID who) {
        return mayWrite(who, null, false);
    }

    /** Commands that write to someone privately, of the game and of the usual plugins (Essentials...). */
    private static final java.util.Set<String> PRIVATE = java.util.Set.of("msg", "tell", "w", "whisper", "m", "r", "reply", "pm", "dm",
            "emsg", "etell", "ewhisper", "em", "er", "ereply", "mail", "email", "emails", "message");
    /** Commands that write to everyone. */
    private static final java.util.Set<String> PUBLIC = java.util.Set.of("me", "eme", "action", "describe", "emote");

    /**
     * For the platform's command event: false (and the player is told) if the command writes where our mute holds
     * them: /msg and the like in private chats, /me in the chat. Players without the mod use these.
     */
    public boolean mayCommand(UUID who, String line) {
        String c = line.startsWith("/") ? line.substring(1) : line;
        int space = c.indexOf(' ');
        String name = (space < 0 ? c : c.substring(0, space)).toLowerCase(Locale.ROOT);
        name = name.substring(name.indexOf(':') + 1); // minecraft:msg, essentials:msg
        if (PRIVATE.contains(name)) {
            return mayWrite(who, "", false);
        }
        return !PUBLIC.contains(name) || mayWrite(who, null, false);
    }

    private boolean mayWrite(UUID who, String pair, boolean others) {
        long now = System.currentTimeMillis();
        Mute m = mutes.get(who);
        long until = m == null ? 0 : pair == null ? m.chatUntil() : m.dmUntil();
        boolean elsewhere = others && pair == null && until <= now && server.platform.mutedElsewhere(who);
        if (until <= now && !elsewhere) {
            return true;
        }
        Long last = told.get(who);
        if (last == null || now - last > 3000) {
            told.put(who, now);
            tell(who, pair, m, until, elsewhere, now);
        }
        return false;
    }

    /** On the server thread: this may be asked from the storage thread. */
    private void tell(UUID who, String pair, Mute m, long until, boolean elsewhere, long now) {
        server.platform.execute(() -> {
            String reason = m == null || m.reason().isEmpty() ? "" : m.reason();
            if (elsewhere || until == FOREVER) {
                server.platform.say(who, pair == null ? "ingamemessenger.mute.chat" : "ingamemessenger.mute.dm",
                        pair == null ? "You are muted in the chat. %s" : "You are muted in private chats. %s", reason);
            } else {
                server.platform.say(who, pair == null ? "ingamemessenger.mute.chat_for" : "ingamemessenger.mute.dm_for",
                        pair == null ? "You are muted in the chat for %s more. %s" : "You are muted in private chats for %s more. %s",
                        left(until - now), reason);
            }
        });
    }

    /** The player's mute state, for their client (on join and on every change). */
    MuteState state(UUID who) {
        long now = System.currentTimeMillis();
        Mute m = mutes.get(who);
        if (m == null) {
            return new MuteState(0, 0, "");
        }
        return new MuteState(m.chatUntil() > now ? m.chatUntil() : 0, m.dmUntil() > now ? m.dmUntil() : 0, m.reason());
    }

    /** Drops mutes that ran out and tells their players. */
    void tick(long now) {
        for (Mute m : new ArrayList<>(mutes.values())) {
            boolean chatOver = m.chatUntil() > 0 && m.chatUntil() <= now, dmOver = m.dmUntil() > 0 && m.dmUntil() <= now;
            if (chatOver || dmOver) {
                set(new Mute(m.who(), m.name(), chatOver ? 0 : m.chatUntil(), dmOver ? 0 : m.dmUntil(), m.reason(), m.by(), m.t()));
            }
        }
    }

    private void set(Mute m) {
        if (m.chatUntil() > 0 || m.dmUntil() > 0) {
            mutes.put(m.who(), m);
        } else {
            mutes.remove(m.who());
        }
        server.db("mute", () -> server.storage.chat.setMute(m));
        server.platform.execute(() -> server.platform.send(m.who(), state(m.who())));
    }

    // Commands: /messenger mute <player> [time] [chat|dm|all] [reason], unmute <player> [chat|dm|all], mutes

    /** Runs a mute command (args after "mute", "unmute" or "mutes"); returns the answer for the moderator. */
    public String command(String by, String what, List<String> args) {
        long now = System.currentTimeMillis();
        if (what.equals("mutes")) {
            StringBuilder b = new StringBuilder();
            for (Mute m : mutes.values()) {
                b.append("\n").append(m.name()).append(": ").append(describe(m, now));
            }
            return b.length() == 0 ? "nobody is muted" : "muted:" + b;
        }
        if (args.isEmpty()) {
            return what.equals("mute") ? "usage: mute <player> [10m|2h|3d|forever] [chat|dm|all] [reason]"
                    : "usage: unmute <player> [chat|dm|all]";
        }
        String name = args.get(0);
        UUID who = server.platform.idOf(name);
        if (who == null) {
            who = mutes.values().stream().filter(m -> m.name().equalsIgnoreCase(name)).map(Mute::who).findFirst().orElse(null);
        }
        if (who == null) {
            return "no player " + name + " online";
        }
        String shown = server.platform.nameOf(who) != null ? server.platform.nameOf(who) : name;
        int i = 1;
        long until = FOREVER;
        if (i < args.size() && time(args.get(i)) != null) {
            long ms = time(args.get(i++));
            until = ms == FOREVER ? FOREVER : now + ms;
        }
        Scope scope = what.equals("mute") ? Scope.CHAT : Scope.ALL;
        if (i < args.size() && Scope.parse(args.get(i)) != null) {
            scope = Scope.parse(args.get(i++));
        }
        Mute old = mutes.get(who);
        long chat = old == null ? 0 : old.chatUntil(), dm = old == null ? 0 : old.dmUntil();
        if (what.equals("unmute")) {
            if (old == null) {
                return shown + " isn't muted";
            }
            set(new Mute(who, shown, scope.chat() ? 0 : chat, scope.dm() ? 0 : dm, old.reason(), old.by(), old.t()));
            server.platform.say(who, "ingamemessenger.mute.lifted", "You can write again.");
            return shown + " unmuted" + (scope == Scope.ALL ? "" : " in " + scope.name().toLowerCase(Locale.ROOT));
        }
        String reason = String.join(" ", args.subList(i, args.size())).strip();
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        Mute m = new Mute(who, shown, scope.chat() ? until : chat, scope.dm() ? until : dm, reason, by, now);
        set(m);
        server.platform.info(by + " muted " + shown + ": " + describe(m, now));
        return shown + " muted: " + describe(m, now);
    }

    private static String describe(Mute m, long now) {
        List<String> parts = new ArrayList<>();
        if (m.chatUntil() > now) {
            parts.add("chat " + (m.chatUntil() == FOREVER ? "forever" : "for " + left(m.chatUntil() - now)));
        }
        if (m.dmUntil() > now) {
            parts.add("private " + (m.dmUntil() == FOREVER ? "forever" : "for " + left(m.dmUntil() - now)));
        }
        return String.join(", ", parts) + (m.reason().isEmpty() ? "" : " (" + m.reason() + ")") + ", by " + m.by();
    }

    /** "30s", "10m", "2h", "3d", "1w", combined like "1h30m"; "forever"/"perm" -> FOREVER; else null. */
    static Long time(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        if (t.equals("forever") || t.equals("perm") || t.equals("permanent")) {
            return FOREVER;
        }
        Matcher mt = TIME.matcher(t);
        long ms = 0;
        int end = 0;
        while (mt.find() && mt.start() == end) {
            long n = Long.parseLong(mt.group(1));
            ms += n * switch (mt.group(2)) {
                case "s" -> 1000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L;
            };
            end = mt.end();
        }
        return end == t.length() && ms > 0 ? ms : null;
    }

    /** Time left, rounded up: "45s", "12m", "3h 20m", "2d 4h". */
    static String left(long ms) {
        if (ms < 60_000) {
            return Math.max(1, (ms + 999) / 1000) + "s";
        }
        long m = (ms + 59_999) / 60_000, h = m / 60, d = h / 24;
        if (d > 0) {
            return d + "d" + (h % 24 > 0 ? " " + h % 24 + "h" : "");
        }
        if (h > 0) {
            return h + "h" + (m % 60 > 0 ? " " + m % 60 + "m" : "");
        }
        return m + "m";
    }
}
