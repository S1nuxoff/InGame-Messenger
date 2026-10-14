package com.sinuxoff.ingamemessenger.core.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sinuxoff.ingamemessenger.core.protocol.Proto;
import com.sinuxoff.ingamemessenger.core.storage.Store;
import com.sinuxoff.ingamemessenger.core.storage.Store.Channel;
import com.sinuxoff.ingamemessenger.core.storage.Store.Mute;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mutes: in a whole space ("space"), in one channel ("channel") or in every private chat ("dm"), for a time or for
 * good. A mute from another plugin (Essentials, LiteBans, AdvancedBan...) counts as a mute in the server's space.
 */
public final class Moderation {
    public static final long FOREVER = Long.MAX_VALUE;
    private static final Pattern TIME = Pattern.compile("(\\d{1,6})([smhdw])");
    /** Commands that write to someone privately, of the game and of the usual plugins (Essentials...). */
    private static final Set<String> PRIVATE = Set.of("msg", "tell", "w", "whisper", "m", "r", "reply", "pm", "dm", "emsg", "etell", "ewhisper",
            "em", "er", "ereply", "mail", "email", "emails", "message");
    /** Commands that write to everyone. */
    private static final Set<String> PUBLIC = Set.of("me", "eme", "action", "describe", "emote");

    private final Hub hub;
    /** player -> their mutes. */
    private final Map<UUID, List<Mute>> mutes = new ConcurrentHashMap<>();
    /** Last time a muted player was told so, to not repeat it on every try. */
    private final Map<UUID, Long> told = new ConcurrentHashMap<>();

    Moderation(Hub hub) {
        this.hub = hub;
    }

    void load(List<Mute> stored) {
        for (Mute m : stored) {
            mutes.computeIfAbsent(m.player(), k -> new ArrayList<>()).add(m);
        }
    }

    /** The mute that holds the player in this channel now, or null. */
    private Mute holding(UUID who, Channel ch) {
        long now = System.currentTimeMillis();
        for (Mute m : mutes.getOrDefault(who, List.of())) {
            if (m.until() <= now) {
                continue;
            }
            if (ch.dm() ? m.scope().equals("dm") : m.scope().equals("space") && m.scopeId() == ch.spaceId()
                    || m.scope().equals("channel") && m.scopeId() == ch.id()) {
                return m;
            }
        }
        return null;
    }

    /** Whether the player may write in the channel; tells them if not. Other plugins' mutes count in the server's space. */
    boolean mayWrite(UUID who, Channel ch) {
        return may(who, ch, true);
    }

    /** For the platform's chat event: our mutes only (other plugins stop the line themselves). */
    boolean mayChat(UUID who, Channel gameChat) {
        return may(who, gameChat, false);
    }

    private boolean may(UUID who, Channel ch, boolean others) {
        if (ch == null) {
            return false;
        }
        Mute m = holding(who, ch);
        boolean elsewhere = m == null && others && !ch.dm() && ch.spaceId() == Store.MAIN && hub.platform.mutedElsewhere(who);
        if (m == null && !elsewhere) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = told.get(who);
        if (last == null || now - last > 3000) {
            told.put(who, now);
            hub.platform.execute(() -> tell(who, ch.dm(), m, now));
        }
        return false;
    }

    private void tell(UUID who, boolean dm, Mute m, long now) {
        String reason = m == null ? "" : m.reason();
        if (m == null || m.until() == FOREVER) {
            hub.platform.say(who, dm ? "ingamemessenger.mute.dm" : "ingamemessenger.mute.chat",
                    dm ? "You are muted in private chats. %s" : "You are muted in the chat. %s", reason);
        } else {
            hub.platform.say(who, dm ? "ingamemessenger.mute.dm_for" : "ingamemessenger.mute.chat_for",
                    dm ? "You are muted in private chats for %s more. %s" : "You are muted in the chat for %s more. %s", left(m.until() - now), reason);
        }
    }

    /**
     * For the platform's command event: false (and the player is told) if the command writes where our mute holds them:
     * /msg and the like in private chats, /me in the chat. Players without the mod use these.
     */
    boolean mayCommand(UUID who, String line, Channel gameChat) {
        String c = line.startsWith("/") ? line.substring(1) : line;
        int space = c.indexOf(' ');
        String name = (space < 0 ? c : c.substring(0, space)).toLowerCase(Locale.ROOT);
        name = name.substring(name.indexOf(':') + 1); // minecraft:msg, essentials:msg
        if (PRIVATE.contains(name)) {
            return may(who, new Channel(0, 0, "dm", "", "", 0, false, 0), false);
        }
        return !PUBLIC.contains(name) || may(who, gameChat, false);
    }

    /** The player's mutes, for their client: [{"scope", "id", "until", "reason"}]. */
    JsonArray json(UUID who) {
        long now = System.currentTimeMillis();
        JsonArray a = new JsonArray();
        for (Mute m : mutes.getOrDefault(who, List.of())) {
            if (m.until() > now) {
                JsonObject o = new JsonObject();
                o.addProperty("scope", m.scope());
                o.addProperty("id", m.scopeId());
                o.addProperty("until", m.until());
                o.addProperty("reason", m.reason());
                a.add(o);
            }
        }
        return a;
    }

    /** Drops mutes that ran out and tells their players. */
    void tick(long now) {
        mutes.forEach((who, list) -> {
            List<Mute> over = list.stream().filter(m -> m.until() <= now).toList();
            if (!over.isEmpty()) {
                list.removeAll(over);
                over.forEach(m -> hub.db("mute", () -> hub.store.unmute(m.player(), m.scope(), m.scopeId())));
                changed(who);
            }
        });
    }

    private void changed(UUID who) {
        hub.platform.execute(() -> hub.send(who, new Proto.Mutes(json(who).toString())));
    }

    private void set(Mute m) {
        List<Mute> list = mutes.computeIfAbsent(m.player(), k -> new ArrayList<>());
        list.removeIf(x -> x.scope().equals(m.scope()) && x.scopeId() == m.scopeId());
        list.add(m);
        hub.db("mute", () -> hub.store.setMute(m));
        changed(m.player());
    }

    private boolean lift(UUID who, String scope, long id) {
        List<Mute> list = mutes.getOrDefault(who, new ArrayList<>());
        boolean had = list.removeIf(x -> x.scope().equals(scope) && x.scopeId() == id);
        if (had) {
            hub.db("mute", () -> hub.store.unmute(who, scope, id));
            changed(who);
        }
        return had;
    }

    // Commands: mute <player> [time] [chat|dm|all] [reason], unmute <player> [chat|dm|all], mutes

    /** Runs a mute command (args after "mute", "unmute" or "mutes"); returns the answer for the moderator. */
    public String command(String by, String what, List<String> args) {
        long now = System.currentTimeMillis();
        if (what.equals("mutes")) {
            StringBuilder b = new StringBuilder();
            mutes.forEach((who, list) -> list.stream().filter(m -> m.until() > now)
                    .forEach(m -> b.append("\n").append(m.name()).append(": ").append(describe(m, now))));
            return b.length() == 0 ? "nobody is muted" : "muted:" + b;
        }
        if (args.isEmpty()) {
            return what.equals("mute") ? "usage: mute <player> [10m|2h|3d|forever] [chat|dm|all] [reason]" : "usage: unmute <player> [chat|dm|all]";
        }
        String name = args.get(0);
        UUID who = hub.platform.idOf(name);
        if (who == null) {
            who = mutes.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(m -> m.name().equalsIgnoreCase(name))).map(Map.Entry::getKey)
                    .findFirst().orElse(null);
        }
        if (who == null) {
            return "no player " + name + " online";
        }
        String shownName = hub.platform.nameOf(who) != null ? hub.platform.nameOf(who) : name;
        int i = 1;
        long until = FOREVER;
        if (i < args.size() && time(args.get(i)) != null) {
            long ms = time(args.get(i++));
            until = ms == FOREVER ? FOREVER : now + ms;
        }
        String where = what.equals("mute") ? "chat" : "all";
        if (i < args.size() && List.of("chat", "dm", "all").contains(args.get(i).toLowerCase(Locale.ROOT))) {
            where = args.get(i++).toLowerCase(Locale.ROOT);
        }
        boolean chat = !where.equals("dm"), dm = !where.equals("chat");
        if (what.equals("unmute")) {
            boolean a = chat && lift(who, "space", Store.MAIN), b = dm && lift(who, "dm", 0);
            if (!a && !b) {
                return shownName + " isn't muted";
            }
            hub.platform.say(who, "ingamemessenger.mute.lifted", "You can write again.");
            return shownName + " unmuted" + (where.equals("all") ? "" : " in " + where);
        }
        String reason = String.join(" ", args.subList(i, args.size())).strip();
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        List<Mute> made = new ArrayList<>();
        if (chat) {
            made.add(new Mute(who, shownName, "space", Store.MAIN, until, reason, by, now));
        }
        if (dm) {
            made.add(new Mute(who, shownName, "dm", 0, until, reason, by, now));
        }
        made.forEach(this::set);
        String said = String.join(", ", made.stream().map(m -> describe(m, now)).toList());
        hub.platform.info(by + " muted " + shownName + ": " + said);
        return shownName + " muted: " + said;
    }

    private static String describe(Mute m, long now) {
        String where = m.scope().equals("dm") ? "private" : m.scope().equals("space") ? "chat" : "channel " + m.scopeId();
        return where + " " + (m.until() == FOREVER ? "forever" : "for " + left(m.until() - now)) + (m.reason().isEmpty() ? "" : " (" + m.reason()
                + ")") + ", by " + m.by();
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
