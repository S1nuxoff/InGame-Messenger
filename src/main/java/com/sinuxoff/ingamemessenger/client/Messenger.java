package com.sinuxoff.ingamemessenger.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Proto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the client knows of this server's messenger while on it: spaces, the channels it sees, and of each the
 * messages loaded so far (history comes from the server a page at a time), unread counts, read marks and who's typing.
 * Nothing is kept on disk; leaving the server forgets it all.
 */
public final class Messenger {
    private Messenger() {
    }

    /** One message, or one line of the game's chat shown as it came (raw). */
    static final class Entry {
        long id; // the server's; 0 for a line of the game's chat or one still being sent
        long channel;
        UUID sender;
        String name;
        String kind; // text, sticker, voice, images, poll, system, line
        JsonObject body = new JsonObject();
        long replyTo;
        boolean replyGone;
        long time, edited;
        String reactions = "";
        String pollCounts = "";
        int myVote;
        Component raw; // "line": the game's own text
        String nonce = ""; // ours, until the server's copy comes back
        /** When it arrived live (for the fade on the closed chat); 0 for history. */
        long arrived;
        /** The pictures' layout, made when first drawn. */
        Object album;
        /** Lines of the game's chat joined in this one (a command's answer). */
        int lines = 1;

        String text() {
            return body.has("t") ? body.get("t").getAsString() : "";
        }

        boolean mine() {
            return sender != null && sender.equals(me);
        }
    }

    /** A channel the player sees. */
    static final class Chan {
        long id, space;
        String kind = "text", name = "", topic = "";
        int pos;
        boolean game;
        long perms;
        UUID with; // private chats: the other player
        String withName = "";
        final List<Entry> list = new ArrayList<>();
        boolean loaded, loading, more = true;
        int unread;
        long lastRead, peerRead;
        final Map<UUID, Long> typing = new HashMap<>();
        final Map<UUID, String> typingNames = new HashMap<>();

        boolean dm() {
            return space == 0;
        }

        String title() {
            return dm() ? withName : name;
        }

        long newest() {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).id > 0) {
                    return list.get(i).id;
                }
            }
            return 0;
        }

        long oldest() {
            for (Entry e : list) {
                if (e.id > 0) {
                    return e.id;
                }
            }
            return 0;
        }
    }

    /** icon: base64 PNG, or null. */
    record Space(long id, String kind, String name, boolean isPublic, String icon) {
    }

    static UUID me;
    static long game;
    static final Map<Long, Space> spaces = new LinkedHashMap<>();
    static final Map<Long, Chan> channels = new LinkedHashMap<>();
    /** The open channel. */
    static long current;
    static JsonArray mutes = new JsonArray();
    /** OpenDm sent: open that private chat when it comes. */
    private static UUID dmWanted;
    private static int nextNonce;

    static boolean on() {
        return Session.on();
    }

    static Chan open() {
        return channels.get(current);
    }

    static Chan gameChat() {
        return channels.get(game);
    }

    static void reset() {
        spaces.clear();
        channels.clear();
        current = game = 0;
        me = null;
        mutes = new JsonArray();
        dmWanted = null;
    }

    /** The channels for the list column: the server's (by position), then private chats (newest first). */
    static List<Chan> ordered() {
        List<Chan> out = new ArrayList<>();
        channels.values().stream().filter(c -> !c.dm()).sorted(Comparator.comparingLong((Chan c) -> c.space).thenComparingInt(c -> c.pos)
                .thenComparingLong(c -> c.id)).forEach(out::add);
        channels.values().stream().filter(Chan::dm).sorted(Comparator.comparingLong(Chan::newest).reversed().thenComparingLong(c -> -c.id))
                .forEach(out::add);
        return out;
    }

    // From the server

    static void handle(Packet p) {
        if (p instanceof Proto.Snapshot s) {
            onSnapshot(JsonParser.parseString(s.json()).getAsJsonObject());
        } else if (p instanceof Proto.Msg m) {
            onMsg(JsonParser.parseString(m.json()).getAsJsonObject(), m.nonce());
        } else if (p instanceof Proto.Page pg) {
            onPage(pg);
        } else if (p instanceof Proto.Edited e) {
            Entry x = find(e.channel(), e.id());
            if (x != null) {
                x.body = JsonParser.parseString(e.body()).getAsJsonObject();
                x.edited = e.at();
                Feed.changed();
            }
        } else if (p instanceof Proto.Deleted d) {
            Chan c = channels.get(d.channel());
            if (c != null && c.list.removeIf(x -> x.id == d.id())) {
                c.list.forEach(x -> x.replyGone |= x.replyTo == d.id());
                Feed.changed();
            }
        } else if (p instanceof Proto.Reactions r) {
            Entry x = find(r.channel(), r.id());
            if (x != null) {
                x.reactions = r.data();
                Feed.changed();
            }
        } else if (p instanceof Proto.ReadMark r) {
            Chan c = channels.get(r.channel());
            if (c != null && !r.player().equals(me)) {
                c.peerRead = Math.max(c.peerRead, r.upTo());
            }
        } else if (p instanceof Proto.TypingAt t) {
            Chan c = channels.get(t.channel());
            if (c != null) {
                c.typing.put(t.who(), System.currentTimeMillis());
                c.typingNames.put(t.who(), t.name());
            }
        } else if (p instanceof Proto.PollState s) {
            Entry x = find(s.channel(), s.id());
            if (x != null) {
                x.pollCounts = s.counts();
                if (s.voter().equals(me)) {
                    x.myVote = s.bits();
                }
                Feed.changed();
            }
        } else if (p instanceof Proto.ChannelUp u) {
            Chan c = channel(JsonParser.parseString(u.json()).getAsJsonObject());
            if (c.dm() && c.with != null && c.with.equals(dmWanted)) {
                dmWanted = null;
                select(c.id);
            }
        } else if (p instanceof Proto.ChannelGone g) {
            channels.remove(g.id());
            if (current == g.id()) {
                select(game);
            }
        } else if (p instanceof Proto.Found f) {
            List<Entry> out = new ArrayList<>();
            for (var el : JsonParser.parseString(f.json()).getAsJsonArray()) {
                out.add(entry(el.getAsJsonObject()));
            }
            ChatInput.onFound(f.query(), out);
        } else if (p instanceof Proto.Mutes m) {
            mutes = JsonParser.parseString(m.json()).getAsJsonArray();
        }
    }

    /** A loaded message by its id, in any channel; null if not loaded. */
    static Entry byId(long id) {
        if (id <= 0) {
            return null;
        }
        for (Chan c : channels.values()) {
            for (Entry e : c.list) {
                if (e.id == id) {
                    return e;
                }
            }
        }
        return null;
    }

    /** The message a message answers (its field, or in the game's chat the mark at the start of its text); 0 if none. */
    static long replyOf(Entry e) {
        if (e.replyTo > 0) {
            return e.replyTo;
        }
        return "text".equals(e.kind) ? Math.max(0, com.sinuxoff.ingamemessenger.core.text.ChatText.replyTarget(e.text())) : 0;
    }

    /** The text without the reply mark at its start. */
    static String body(Entry e) {
        String t = e.text();
        return com.sinuxoff.ingamemessenger.core.text.ChatText.replyTarget(t) >= 0 ? t.substring(com.sinuxoff.ingamemessenger.core.text.ChatText.replyMarkLength(t)).stripLeading() : t;
    }

    private static Entry find(long channel, long id) {
        Chan c = channels.get(channel);
        if (c == null) {
            return null;
        }
        for (Entry e : c.list) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    private static void onSnapshot(JsonObject o) {
        long was = current;
        List<Entry> early = new ArrayList<>(); // the game's lines that came before (the server's greeting...)
        Chan oldGame = gameChat();
        if (oldGame != null) {
            oldGame.list.stream().filter(e -> "line".equals(e.kind)).forEach(early::add);
        }
        reset();
        me = UUID.fromString(o.get("me").getAsString());
        game = o.get("game").getAsLong();
        for (JsonElement e : o.getAsJsonArray("spaces")) {
            JsonObject s = e.getAsJsonObject();
            spaces.put(s.get("id").getAsLong(), new Space(s.get("id").getAsLong(), s.get("kind").getAsString(), s.get("name").getAsString(),
                    s.get("public").getAsBoolean(), s.has("icon") ? s.get("icon").getAsString() : null));
        }
        for (JsonElement e : o.getAsJsonArray("channels")) {
            channel(e.getAsJsonObject());
        }
        o.getAsJsonObject("unread").entrySet().forEach(e -> {
            Chan c = channels.get(Long.parseLong(e.getKey()));
            if (c != null) {
                c.unread = e.getValue().getAsInt();
            }
        });
        o.getAsJsonObject("reads").entrySet().forEach(e -> {
            Chan c = channels.get(Long.parseLong(e.getKey()));
            if (c != null) {
                c.lastRead = e.getValue().getAsLong();
            }
        });
        o.getAsJsonObject("peerReads").entrySet().forEach(e -> {
            Chan c = channels.get(Long.parseLong(e.getKey()));
            if (c != null) {
                c.peerRead = e.getValue().getAsLong();
            }
        });
        mutes = o.getAsJsonArray("mutes");
        JsonObject f = o.getAsJsonObject("features");
        String images = f.get("images").getAsString();
        Packets.Features features = new Packets.Features(f.get("typing").getAsBoolean(), f.get("voice").getAsBoolean(), f.get("voiceMax").getAsInt(),
                !images.equals("off"), f.get("imageMaxKb").getAsInt(), images);
        Typing.onFeatures(features);
        VoiceClient.onFeatures(features);
        Images.onFeatures(features);
        Chan g = gameChat();
        if (g != null) {
            g.list.addAll(early);
            if (Session.pendingEarly != null) {
                Session.pendingEarly.forEach(Messenger::line);
                Session.pendingEarly = null;
            }
        }
        select(channels.containsKey(was) ? was : game);
        if (g != null && g.id != current) {
            history(g); // the closed chat shows the game's chat
        }
    }

    /** A channel from the server, new or changed. */
    private static Chan channel(JsonObject o) {
        long id = o.get("id").getAsLong();
        Chan c = channels.computeIfAbsent(id, k -> new Chan());
        c.id = id;
        c.space = o.get("space").getAsLong();
        c.kind = o.get("kind").getAsString();
        c.name = o.get("name").getAsString();
        c.topic = o.has("topic") ? o.get("topic").getAsString() : "";
        c.pos = o.get("pos").getAsInt();
        c.game = o.has("game") && o.get("game").getAsBoolean();
        c.perms = o.get("perms").getAsLong();
        if (o.has("with")) {
            c.with = UUID.fromString(o.get("with").getAsString());
            c.withName = o.get("withName").getAsString();
        }
        return c;
    }

    /** A message from its JSON (Store.json on the server, plus a page's extras). */
    private static Entry entry(JsonObject o) {
        Entry e = new Entry();
        e.id = o.get("i").getAsLong();
        e.channel = o.get("ch").getAsLong();
        if (o.has("u")) {
            e.sender = UUID.fromString(o.get("u").getAsString());
            e.name = o.get("n").getAsString();
        }
        e.kind = o.get("k").getAsString();
        try {
            e.body = JsonParser.parseString(o.get("b").getAsString()).getAsJsonObject();
        } catch (RuntimeException ex) {
            e.body = new JsonObject();
        }
        e.replyTo = o.has("r") ? o.get("r").getAsLong() : 0;
        e.time = o.get("t").getAsLong();
        e.edited = o.has("e") ? o.get("e").getAsLong() : 0;
        e.reactions = o.has("rx") ? o.get("rx").getAsString() : "";
        e.replyGone = o.has("rd");
        e.pollCounts = o.has("pc") ? o.get("pc").getAsString() : "";
        e.myVote = o.has("pm") ? o.get("pm").getAsInt() : 0;
        if ("system".equals(e.kind) && e.body.has("c")) {
            var conn = Minecraft.getInstance().getConnection();
            try {
                e.raw = conn == null ? Component.literal("") : com.sinuxoff.ingamemessenger.ServerCompat.fromJson(e.body.get("c").getAsString(),
                        conn.registryAccess());
            } catch (RuntimeException ex) {
                e.raw = Component.literal("");
            }
        }
        return e;
    }

    private static void onMsg(JsonObject o, String nonce) {
        Entry e = entry(o);
        Chan c = channels.get(e.channel);
        if (c == null) {
            return;
        }
        e.arrived = System.currentTimeMillis();
        boolean replaced = false;
        if (!nonce.isEmpty()) { // ours: in place of the one being sent
            for (int i = 0; i < c.list.size(); i++) {
                if (nonce.equals(c.list.get(i).nonce)) {
                    c.list.set(i, e);
                    replaced = true;
                    break;
                }
            }
        }
        if (!replaced) {
            c.list.add(e);
        }
        c.typing.remove(e.sender);
        if (e.mine() && "images".equals(e.kind)) {
            Images.sent(); // our pictures arrived: the progress bar goes
        }
        if (!e.mine() && !(c.id == current && Feed.atBottom() && Feed.visible())) {
            c.unread++;
            if (c.dm() && c.id != current) {
                ping();
            }
        }
        Feed.arrived(c);
    }

    private static void ping() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 0.8f));
    }

    private static void onPage(Proto.Page p) {
        Chan c = channels.get(p.channel());
        if (c == null) {
            return;
        }
        c.loading = false;
        c.loaded = true;
        c.more = p.more();
        List<Entry> older = new ArrayList<>();
        for (JsonElement el : JsonParser.parseString(p.json()).getAsJsonArray()) {
            Entry e = entry(el.getAsJsonObject());
            if (find(c.id, e.id) == null) {
                older.add(e);
            }
        }
        c.list.addAll(0, older); // a page is older than anything loaded (what's already here was skipped)
        Feed.changed();
    }

    /** A line the game's chat got (the server's or a mod's): into the game chat, as it is. */
    static void line(Component content) {
        Chan c = gameChat();
        if (c == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Entry last = c.list.isEmpty() ? null : c.list.get(c.list.size() - 1);
        if (last != null && "line".equals(last.kind) && now - last.arrived < 150 && last.lines < 60) { // a command's answer: one block
            last.raw = Component.empty().append(last.raw).append("\n").append(content);
            last.lines++;
            last.arrived = now;
            CommandAnswer.line(last);
            return;
        }
        Entry e = new Entry();
        e.channel = c.id;
        e.kind = "line";
        e.raw = content;
        e.time = e.arrived = System.currentTimeMillis();
        c.list.add(e);
        if (c.list.size() > 2000) {
            c.list.remove(0);
        }
        CommandAnswer.line(e);
        Feed.arrived(c);
    }

    // To the server

    /** Opens a channel: its history is fetched the first time. */
    static void select(long id) {
        Chan c = channels.get(id);
        if (c == null) {
            return;
        }
        current = id;
        Feed.toBottom();
        if (!c.loaded) {
            history(c);
        }
    }

    static void history(Chan c) {
        if (c.loading || c.loaded && !c.more) {
            return;
        }
        c.loading = true;
        Net.toServer(new Proto.History(c.id, c.loaded ? c.oldest() : 0));
    }

    /** Scrolled to the top: the page before. */
    static void older() {
        Chan c = open();
        if (c != null && c.loaded && c.more && c.oldest() > 0) {
            history(c);
        }
    }

    /** Read up to the newest message of the open channel. */
    static void read() {
        Chan c = open();
        if (c == null) {
            return;
        }
        c.unread = 0;
        long newest = c.newest();
        if (newest > c.lastRead) {
            c.lastRead = newest;
            Net.toServer(new Proto.Read(c.id, newest));
        }
    }

    private static String nonce() {
        return Long.toString(System.nanoTime(), 36) + (nextNonce++);
    }

    /** A message being sent, shown at once (the server's copy replaces it). */
    /** Muted where we'd send: says so instead (the server would refuse it anyway). */
    static boolean muted() {
        if (Muted.here() <= 0) {
            return false;
        }
        Minecraft.getInstance().gui.hud.setOverlayMessage(Muted.hint().copy().withStyle(s -> s.withColor(0xE0707A)), false);
        return true;
    }

    /** Our messages the server never confirmed (refused, or lost): gone after a while instead of hanging there. */
    static void dropStale(Chan c) {
        long now = System.currentTimeMillis();
        for (int i = c.list.size() - 1, seen = 0; i >= 0 && seen < 30; i--, seen++) {
            Entry e = c.list.get(i);
            if (!e.nonce.isEmpty() && now - e.arrived > 15_000) {
                c.list.remove(i);
            }
        }
    }

    private static void pending(Chan c, String kind, JsonObject body, String nonce, long replyTo) {
        Entry e = new Entry();
        e.channel = c.id;
        e.sender = me;
        var p = Minecraft.getInstance().player;
        e.name = p == null ? "" : p.getGameProfile().name();
        e.kind = kind;
        e.body = body;
        e.nonce = nonce;
        e.replyTo = replyTo;
        e.time = e.arrived = System.currentTimeMillis();
        c.list.add(e);
        Feed.arrived(c);
    }

    /**
     * Sends text to the open channel. False for the game's chat: that text goes through the game (so plugins and
     * bridges see it), and comes back as our message.
     */
    static boolean sendText(String text, long replyTo) {
        Chan c = open();
        if (c != null && muted()) {
            return true;
        }
        if (c == null || c.game) {
            return false;
        }
        String n = nonce();
        JsonObject b = new JsonObject();
        b.addProperty("t", text);
        pending(c, "text", b, n, replyTo);
        Net.toServer(new Proto.Post(c.id, n, "text", text, replyTo));
        return true;
    }

    static void sendPoll(String question, List<String> options, boolean multi, long replyTo) {
        Chan c = open();
        if (c == null || muted()) {
            return;
        }
        String n = nonce();
        JsonObject b = new JsonObject();
        b.addProperty("q", question);
        var arr = new com.google.gson.JsonArray();
        options.forEach(arr::add);
        b.add("o", arr);
        if (multi) {
            b.addProperty("m", true);
        }
        pending(c, "poll", b, n, replyTo);
        Net.toServer(new Proto.Post(c.id, n, "poll", b.toString(), replyTo));
    }

    static void sendSticker(String pack, String id, long replyTo) {
        Chan c = open();
        if (c == null || muted()) {
            return;
        }
        String n = nonce();
        JsonObject b = new JsonObject();
        b.addProperty("p", pack);
        b.addProperty("s", id);
        pending(c, "sticker", b, n, replyTo);
        Net.toServer(new Proto.Post(c.id, n, "sticker", b.toString(), replyTo));
    }

    static void typing() {
        Chan c = open();
        if (c != null) {
            Net.toServer(new Proto.Typing(c.id));
        }
    }

    /** The private chat with a player: opened when the server has it. */
    static void openDm(UUID player) {
        for (Chan c : channels.values()) {
            if (c.dm() && player.equals(c.with)) {
                select(c.id);
                return;
            }
        }
        dmWanted = player;
        Net.toServer(new Proto.OpenDm(player));
    }

    /** Who is typing in the open channel (seen in the last 4 seconds). */
    static List<String> typingHere() {
        Chan c = open();
        List<String> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        c.typing.forEach((id, t) -> {
            if (now - t < 4000 && !id.equals(me)) {
                out.add(c.typingNames.getOrDefault(id, "?"));
            }
        });
        return out;
    }
}
