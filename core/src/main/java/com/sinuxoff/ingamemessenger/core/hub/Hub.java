package com.sinuxoff.ingamemessenger.core.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Proto;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.server.PollBook;
import com.sinuxoff.ingamemessenger.core.server.ServerPlatform;
import com.sinuxoff.ingamemessenger.core.server.StickerStore;
import com.sinuxoff.ingamemessenger.core.storage.Store;
import com.sinuxoff.ingamemessenger.core.storage.Store.Channel;
import com.sinuxoff.ingamemessenger.core.storage.Store.Message;
import com.sinuxoff.ingamemessenger.core.storage.Store.Role;
import com.sinuxoff.ingamemessenger.core.storage.Store.Space;
import com.sinuxoff.ingamemessenger.core.storage.Storage;
import com.sinuxoff.ingamemessenger.core.text.ChatText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * The messenger's server: spaces, channels, permissions and messages. Validates what players send, stores it and
 * delivers it to whoever sees the channel. Platform-independent: the mod and the plugin give it a {@link ServerPlatform}.
 * <p>
 * Packets are handled on the server thread; the database is used on the storage thread, and what comes back from it is
 * sent from the server thread again ({@link ServerPlatform#execute}). The small parts of the model (spaces, channels,
 * roles, members) are kept in memory too.
 */
public final class Hub {
    public static final int MAX_TEXT = 2000;
    private static final Pattern EMOJI = Pattern.compile("[0-" + (Protocol.BUILT_IN_REACTIONS - 1) + "]|[A-Za-z0-9_\\-]{1,64}/[A-Za-z0-9_\\-]{1,64}");

    final ServerPlatform platform;
    final Storage storage;
    final Store store;
    final StickerStore stickers;
    final Moderation moderation;
    final Voices voices;
    final Albums albums;
    private final PollBook polls = new PollBook();

    // the model, as in the database
    private final Map<Long, Space> spaces = new ConcurrentHashMap<>();
    private final Map<Long, Channel> channels = new ConcurrentHashMap<>();
    private final Map<Long, List<UUID>> dmPlayers = new ConcurrentHashMap<>();
    private final Map<String, Long> dmOf = new ConcurrentHashMap<>();
    private final Map<Long, Set<UUID>> members = new ConcurrentHashMap<>();
    private final Map<Long, Role> roles = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> memberRoles = new ConcurrentHashMap<>();
    private final Map<Long, List<Store.Override>> overrides = new ConcurrentHashMap<>();
    private final AtomicLong nextMessage = new AtomicLong(1), nextChannel = new AtomicLong(1), nextSpace = new AtomicLong(2),
            nextRole = new AtomicLong(1);

    /** Players whose client speaks our protocol. */
    private final Set<UUID> clients = ConcurrentHashMap.newKeySet();
    /** Players who joined but haven't said Hello -> when. */
    private final Map<UUID, Long> unanswered = new ConcurrentHashMap<>();
    /** Online players' shown names as last sent ("" = just the name). */
    private final Map<UUID, String> shown = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    /** For the cooldowns; tests move it on. */
    java.util.function.LongSupplier clock = System::currentTimeMillis;

    /** Creates the tables and the server's own space if needed. Blocks until the database is ready. */
    public Hub(ServerPlatform platform, Storage storage) {
        this.platform = platform;
        this.storage = storage;
        this.store = storage.store;
        this.stickers = new StickerStore(platform.configDir(), platform::info);
        this.moderation = new Moderation(this);
        CompletableFuture.runAsync(() -> {
            try {
                load();
            } catch (Exception e) {
                throw new IllegalStateException("Can't open the chat database: " + e, e);
            }
        }, storage.db).join();
        this.voices = new Voices(this);
        this.albums = new Albums(this);
    }

    private void load() throws Exception {
        store.init();
        long now = System.currentTimeMillis();
        if (store.space(Store.MAIN) == null) { // a new database: the server's space, its chat, everyone's role
            store.addSpace(new Space(Store.MAIN, "main", "Server", null, null, true, now));
            store.addChannel(new Channel(1, Store.MAIN, "text", "general", "", 0, true, now));
            store.addRole(new Role(1, Store.MAIN, "@everyone", 0, 0, Perms.EVERYONE));
        }
        store.allSpaces().forEach(s -> spaces.put(s.id(), s));
        store.allChannels().forEach(c -> channels.put(c.id(), c));
        store.channelMembers().forEach((ch, players) -> {
            dmPlayers.put(ch, players);
            if (players.size() == 2) {
                dmOf.put(pair(players.get(0), players.get(1)), ch);
            }
        });
        store.members().forEach((sp, players) -> members.put(sp, ConcurrentHashMap.newKeySet()));
        store.members().forEach((sp, players) -> members.get(sp).addAll(players));
        store.roles().forEach(r -> roles.put(r.id(), r));
        memberRoles.putAll(store.memberRoles());
        store.overrides().forEach(o -> overrides.computeIfAbsent(o.channelId(), k -> new ArrayList<>()).add(o));
        nextMessage.set(store.maxId("messages") + 1);
        nextChannel.set(store.maxId("channels") + 1);
        nextSpace.set(Math.max(2, store.maxId("spaces") + 1));
        nextRole.set(store.maxId("roles") + 1);
        moderation.load(store.mutes());
    }

    /** Runs a task on the storage thread; failures are logged. */
    void db(String what, Task task) {
        storage.db.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                platform.warn("Chat storage: " + what + " failed: " + e);
            }
        });
    }

    interface Task {
        void run() throws Exception;
    }

    /** Rescans the sticker folders in the background, then sends the new index to everyone. */
    public void reloadStickers() {
        Thread t = new Thread(() -> {
            stickers.load();
            platform.execute(() -> clients.forEach(c -> platform.send(c, new Packets.StickerIndex(stickers.index()))));
        }, "InGameMessenger stickers");
        t.setDaemon(true);
        t.start();
    }

    public StickerStore stickers() {
        return stickers;
    }

    public Moderation moderation() {
        return moderation;
    }

    public void close() {
        storage.close();
    }

    /** Called every few seconds: sweeps old files, drops stale uploads, ends mutes, sends changed ranks. */
    public void tick() {
        tick(System.currentTimeMillis());
    }

    void tick(long now) {
        voices.tick(now);
        albums.tick(now);
        moderation.tick(now);
        names(false);
        unanswered.forEach((id, t) -> {
            if (now - t > 5000 && unanswered.remove(id) != null && platform.settings().installHint()) {
                platform.say(id, "ingamemessenger.plain.install",
                        "This server uses InGame Messenger. Install the mod to see stickers, voice messages, pictures and polls.");
            }
        });
    }

    // The model

    static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** The channel the game's chat goes to: the server's general chat. */
    public long gameChat() {
        for (Channel c : channels.values()) {
            if (c.gameChat()) {
                return c.id();
            }
        }
        return 1;
    }

    Channel channel(long id) {
        return channels.get(id);
    }

    private long everyoneRole(long space) {
        return roles.values().stream().filter(r -> r.spaceId() == space && r.pos() == 0).mapToLong(Role::id).findFirst().orElse(0);
    }

    /** What the player may do in the channel (0 = can't even see it). */
    public long perms(UUID player, long channelId) {
        Channel ch = channels.get(channelId);
        if (ch == null) {
            return 0;
        }
        if (ch.dm()) {
            List<UUID> two = dmPlayers.get(ch.id());
            return two != null && two.contains(player) ? Perms.DM : 0;
        }
        Space s = spaces.get(ch.spaceId());
        if (s == null) {
            return 0;
        }
        if (s.id() == Store.MAIN && platform.isOperator(player) || player.equals(s.owner())) {
            return Perms.ALL;
        }
        if (s.id() != Store.MAIN && !members.getOrDefault(s.id(), Set.of()).contains(player)) {
            return 0;
        }
        long everyone = everyoneRole(s.id());
        List<Long> mine = new ArrayList<>();
        List<Role> mineRoles = new ArrayList<>();
        for (long r : memberRoles.getOrDefault(player, List.of())) {
            Role role = roles.get(r);
            if (role != null && role.spaceId() == s.id()) {
                mine.add(r);
                mineRoles.add(role);
            }
        }
        Role base = roles.get(everyone);
        long p = Perms.base(base == null ? Perms.EVERYONE : base.perms(), mineRoles);
        return Perms.inChannel(p, everyone, mine, player, overrides.getOrDefault(ch.id(), List.of()));
    }

    /** Online players with our client who see the channel. */
    List<UUID> viewers(long channelId) {
        List<UUID> out = new ArrayList<>();
        for (UUID id : clients) {
            if (Perms.has(perms(id, channelId), Perms.VIEW)) {
                out.add(id);
            }
        }
        return out;
    }

    void send(UUID player, Packet p) {
        if (clients.contains(player)) {
            platform.send(player, p);
        }
    }

    private void sendViewers(long channelId, Packet p) {
        for (UUID id : viewers(channelId)) {
            platform.send(id, p);
        }
    }

    /** A channel as its viewer gets it: with their permissions and, for a private chat, the other player. */
    JsonObject channelJson(Channel c, UUID viewer) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.id());
        o.addProperty("space", c.spaceId());
        o.addProperty("kind", c.kind());
        o.addProperty("name", c.name());
        if (!c.topic().isEmpty()) {
            o.addProperty("topic", c.topic());
        }
        o.addProperty("pos", c.pos());
        if (c.gameChat()) {
            o.addProperty("game", true);
        }
        o.addProperty("perms", perms(viewer, c.id()));
        if (c.dm()) {
            for (UUID u : dmPlayers.getOrDefault(c.id(), List.of())) {
                if (!u.equals(viewer)) {
                    o.addProperty("with", u.toString());
                    String n = platform.nameOf(u);
                    o.addProperty("withName", n == null ? "?" : n);
                }
            }
        }
        return o;
    }

    // Players coming and going

    /** A player joined: the client gets Welcome and, if it has the mod, answers with Hello. */
    public void joined(UUID id) {
        unanswered.put(id, System.currentTimeMillis());
        platform.send(id, new Packets.Welcome(Protocol.VERSION, platform.modVersion()));
    }

    public boolean hasClient(UUID id) {
        return clients.contains(id);
    }

    public void left(UUID id) {
        clients.remove(id);
        unanswered.remove(id);
        shown.remove(id);
        voices.left(id);
        albums.left(id);
    }

    private void hello(UUID id, String name, Packets.Hello h) {
        unanswered.remove(id);
        if (!h.protocol().equals(Protocol.VERSION)) {
            platform.info(name + " has InGame Messenger " + h.version() + ", the server " + platform.modVersion() + ": plain chat for them");
            return;
        }
        if (!clients.add(id)) {
            return;
        }
        long now = System.currentTimeMillis();
        members.computeIfAbsent(Store.MAIN, k -> ConcurrentHashMap.newKeySet()).add(id);
        String look = platform.shownName(id);
        db("player", () -> {
            store.seePlayer(id, name, look, now);
            store.addMember(Store.MAIN, id, now);
        });
        names(false);
        snapshot(id);
    }

    /** Everything the client needs to start: spaces, channels, unread counts, read marks, features, mutes, names. */
    private void snapshot(UUID id) {
        List<Channel> mine = new ArrayList<>();
        for (Channel c : channels.values()) {
            if (Perms.has(perms(id, c.id()), Perms.VIEW)) {
                mine.add(c);
            }
        }
        List<Long> ids = mine.stream().map(Channel::id).toList();
        db("snapshot", () -> {
            Map<Long, Integer> unread = store.unread(id, ids);
            Map<Long, Long> reads = store.readMarks(id);
            Map<Long, Long> peers = new HashMap<>();
            for (Channel c : mine) {
                if (c.dm()) {
                    store.readMarksOf(c.id()).forEach((p, mark) -> {
                        if (!p.equals(id)) {
                            peers.put(c.id(), mark);
                        }
                    });
                }
            }
            Set<UUID> others = new HashSet<>();
            mine.stream().filter(Channel::dm).forEach(c -> others.addAll(dmPlayers.getOrDefault(c.id(), List.of())));
            String names = store.shownNames(others);
            platform.execute(() -> {
                JsonObject o = new JsonObject();
                o.addProperty("me", id.toString());
                o.addProperty("game", gameChat());
                JsonArray sp = new JsonArray();
                for (Space s : spaces.values()) {
                    if (s.id() == Store.MAIN || members.getOrDefault(s.id(), Set.of()).contains(id)) {
                        JsonObject so = new JsonObject();
                        so.addProperty("id", s.id());
                        so.addProperty("kind", s.kind());
                        so.addProperty("name", s.name());
                        if (s.icon() != null) {
                            so.addProperty("icon", s.icon());
                        }
                        so.addProperty("public", s.isPublic());
                        sp.add(so);
                    }
                }
                o.add("spaces", sp);
                JsonArray ch = new JsonArray();
                mine.forEach(c -> ch.add(channelJson(c, id)));
                o.add("channels", ch);
                o.add("unread", map(unread));
                o.add("reads", map(reads));
                o.add("peerReads", map(peers));
                o.add("features", features());
                o.add("mutes", moderation.json(id));
                send(id, new Proto.Snapshot(o.toString()));
                send(id, new Packets.StickerIndex(stickers.index()));
                send(id, new Packets.Names(namesNow(names)));
            });
        });
    }

    private static JsonObject map(Map<Long, ? extends Number> m) {
        JsonObject o = new JsonObject();
        m.forEach((k, v) -> o.addProperty(String.valueOf(k), v));
        return o;
    }

    JsonObject features() {
        var s = platform.settings();
        JsonObject f = new JsonObject();
        f.addProperty("typing", s.typing());
        f.addProperty("voice", s.voiceOn());
        f.addProperty("voiceMax", s.voiceMaxSeconds());
        f.addProperty("images", albums.provider());
        f.addProperty("imageMaxKb", albums.maxKb());
        return f;
    }

    /** Settings changed: everyone gets their snapshot again. */
    public void featuresChanged() {
        clients.forEach(this::snapshot);
    }

    // Shown names (ranks)

    /** Sends the online players' shown names that changed to everyone, and remembers them for the history. */
    void names(boolean all) {
        List<UUID> changed = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ServerPlatform.ChatPlayer p : platform.online()) {
            String look = platform.shownName(p.id());
            String was = shown.put(p.id(), look == null ? "" : look);
            if (all || !(look == null ? "" : look).equals(was)) {
                changed.add(p.id());
                db("player", () -> store.seePlayer(p.id(), p.name(), look, now));
            }
        }
        if (!changed.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (UUID id : changed) {
                String name = platform.nameOf(id);
                if (name != null) {
                    b.append(b.length() > 0 ? "\n" : "").append(id).append('\t').append(name).append('\t').append(shown.getOrDefault(id, ""));
                }
            }
            clients.forEach(c -> platform.send(c, new Packets.Names(b.toString())));
        }
    }

    /** The given names plus everyone online with a rank. */
    private String namesNow(String stored) {
        StringBuilder b = new StringBuilder(stored);
        shown.forEach((id, look) -> {
            String name = platform.nameOf(id);
            if (!look.isEmpty() && name != null) {
                b.append(b.length() > 0 ? "\n" : "").append(id).append('\t').append(name).append('\t').append(look);
            }
        });
        return b.toString();
    }

    // Packets

    public void handle(UUID player, Packet packet) {
        String name = platform.nameOf(player);
        if (name == null) {
            return;
        }
        if (packet instanceof Packets.Hello h) {
            hello(player, name, h);
            return;
        }
        if (!clients.contains(player)) {
            return;
        }
        // (no pattern switch: the core also builds for Java 17)
        if (packet instanceof Proto.History p) {
            history(player, p);
        } else if (packet instanceof Proto.Post p) {
            post(player, name, p);
        } else if (packet instanceof Proto.Edit p) {
            edit(player, p);
        } else if (packet instanceof Proto.Delete p) {
            delete(player, p);
        } else if (packet instanceof Proto.React p) {
            react(player, name, p);
        } else if (packet instanceof Proto.Read p) {
            read(player, p);
        } else if (packet instanceof Proto.Typing p) {
            typing(player, name, p);
        } else if (packet instanceof Proto.Vote p) {
            vote(player, p);
        } else if (packet instanceof Proto.Find p) {
            find(player, p);
        } else if (packet instanceof Proto.OpenDm p) {
            openDm(player, p.player());
        } else if (packet instanceof Proto.VoiceUp p) {
            voices.start(player, p);
        } else if (packet instanceof Packets.VoiceChunk p) {
            voices.chunk(player, name, p);
        } else if (packet instanceof Packets.VoiceGet p) {
            voices.get(player, p);
        } else if (packet instanceof Proto.AlbumUp p) {
            albums.album(player, p);
        } else if (packet instanceof Packets.ImageStart p) {
            albums.start(player, p);
        } else if (packet instanceof Packets.ImageChunk p) {
            albums.chunk(player, name, p);
        } else if (packet instanceof Proto.LinksUp p) {
            albums.links(player, name, p);
        } else if (packet instanceof Packets.ImageGet p) {
            albums.get(player, p);
        } else if (packet instanceof Packets.StickerGet p) {
            byte[] data = stickers.image(p.pack(), p.id(), p.thumb());
            send(player, new Packets.StickerData(p.pack(), p.id(), p.thumb(), data == null ? new byte[0] : data));
        }
        // anything else is an old client's packet: ignored
    }

    /** True at most once per ms for this player and action. */
    boolean cooled(UUID who, String what, long ms) {
        long now = clock.getAsLong();
        String key = who + what;
        Long last = cooldowns.get(key);
        if (last != null && now - last < ms) {
            return false;
        }
        cooldowns.put(key, now);
        return true;
    }

    private void history(UUID who, Proto.History h) {
        if (!Perms.has(perms(who, h.channel()), Perms.VIEW) || !cooled(who, "history" + h.channel(), 200)) {
            return;
        }
        db("history page", () -> {
            Store.Page page = store.page(h.channel(), h.before());
            List<Long> ids = page.messages().stream().map(Message::id).toList();
            Map<Long, String> rx = store.reactions(ids);
            List<Long> replies = page.messages().stream().map(Message::replyTo).filter(r -> r > 0).toList();
            List<Long> gone = store.deletedOf(replies);
            JsonArray arr = new JsonArray();
            Set<UUID> senders = new HashSet<>();
            for (Message m : page.messages()) {
                JsonObject o = Store.json(m);
                if (rx.containsKey(m.id())) {
                    o.addProperty("rx", rx.get(m.id()));
                }
                if (gone.contains(m.replyTo())) {
                    o.addProperty("rd", true);
                }
                if ("poll".equals(m.kind())) {
                    o.addProperty("pc", store.pollCounts(m.id(), PollBook.options(m.body())));
                    o.addProperty("pm", store.vote(m.id(), who));
                }
                if (m.sender() != null) {
                    senders.add(m.sender());
                }
                arr.add(o);
            }
            String names = store.shownNames(senders);
            platform.execute(() -> {
                if (!names.isEmpty()) {
                    send(who, new Packets.Names(names));
                }
                send(who, new Proto.Page(h.channel(), h.before(), page.more(), arr.toString()));
            });
        });
    }

    /** Whether the player may post this in the channel (tells them if muted). */
    boolean mayPost(UUID who, long channelId, long need) {
        long p = perms(who, channelId);
        if (!Perms.has(p, Perms.VIEW | need)) {
            return false;
        }
        Channel ch = channels.get(channelId);
        return moderation.mayWrite(who, ch);
    }

    private void post(UUID who, String name, Proto.Post p) {
        Channel ch = channels.get(p.channel());
        if (ch == null) {
            return;
        }
        switch (p.kind()) {
            case "text" -> {
                String text = p.body().strip();
                // the game's chat takes text through the game's chat (so plugins, logs and bridges see it)
                if (ch.gameChat() || !ChatText.isPlainText(text, MAX_TEXT) || !mayPost(who, ch.id(), Perms.SEND) || !cooled(who, "post", 250)) {
                    return;
                }
                JsonObject b = new JsonObject();
                b.addProperty("t", text);
                deliver(who, name, ch, "text", b.toString(), text, p.replyTo(), p.nonce());
            }
            case "sticker" -> {
                JsonObject b;
                try {
                    b = JsonParser.parseString(p.body()).getAsJsonObject();
                } catch (RuntimeException e) {
                    return;
                }
                String pack = b.has("p") ? b.get("p").getAsString() : "", id = b.has("s") ? b.get("s").getAsString() : "";
                if (stickers.image(pack, id, true) == null || !mayPost(who, ch.id(), Perms.SEND)
                        || !cooled(who, "sticker", platform.settings().stickerCooldownMs())) {
                    return;
                }
                JsonObject clean = new JsonObject();
                clean.addProperty("p", pack);
                clean.addProperty("s", id);
                deliver(who, name, ch, "sticker", clean.toString(), null, p.replyTo(), p.nonce());
            }
            case "poll" -> {
                String json;
                try {
                    JsonObject b = JsonParser.parseString(p.body()).getAsJsonObject();
                    StringBuilder opts = new StringBuilder();
                    b.getAsJsonArray("o").forEach(e -> opts.append(e.getAsString().replace('\n', ' ')).append('\n'));
                    json = mayPost(who, ch.id(), Perms.SEND) ? polls.compose(who, b.get("q").getAsString(), opts.toString(),
                            b.has("m") && b.get("m").getAsBoolean(), clock.getAsLong()) : null;
                } catch (RuntimeException e) {
                    return;
                }
                if (json != null) {
                    String q = JsonParser.parseString(json).getAsJsonObject().get("q").getAsString();
                    deliver(who, name, ch, "poll", json, q, p.replyTo(), p.nonce());
                }
            }
            default -> {
            }
        }
    }

    /**
     * Stores a message and sends it to whoever sees the channel; in the game's chat, players without the mod get a
     * plain line. Returns the message. Also used by voice notes and pictures.
     */
    Message deliver(UUID who, String name, Channel ch, String kind, String body, String plain, long replyTo, String nonce) {
        long id = nextMessage.getAndIncrement();
        long reply = replyTo > 0 && replyTo < id ? replyTo : 0;
        Message m = new Message(id, ch.id(), who, name, kind, body, reply, System.currentTimeMillis(), 0, false);
        db("saving message " + id, () -> store.addMessage(m, plain));
        String json = Store.json(m).toString();
        for (UUID v : viewers(ch.id())) {
            platform.send(v, new Proto.Msg(json, v.equals(who) && nonce != null ? nonce : ""));
        }
        if (ch.gameChat() && !kind.equals("text") && platform.settings().plainLines()) {
            plainLine(who, name, kind, body, plain);
        }
        return m;
    }

    /** A sticker, voice message, picture or poll in the game's chat, for players without the mod. */
    private void plainLine(UUID who, String name, String kind, String body, String plain) {
        for (ServerPlatform.ChatPlayer p : platform.online()) {
            if (clients.contains(p.id())) {
                continue;
            }
            String own = platform.settings().plainText(kind);
            if (!own.isEmpty()) {
                platform.say(p.id(), "ingamemessenger.plain." + kind, own, name);
                continue;
            }
            switch (kind) {
                case "sticker" -> platform.sayAs(p.id(), who, name, "ingamemessenger.plain.sticker_body", "[sticker]");
                case "voice" -> {
                    int ms = JsonParser.parseString(body).getAsJsonObject().get("ms").getAsInt();
                    platform.sayAs(p.id(), who, name, "ingamemessenger.plain.voice_body", "[voice message, %s s]",
                            String.valueOf(Math.max(1, Math.round(ms / 1000f))));
                }
                case "images" -> {
                    JsonObject b = JsonParser.parseString(body).getAsJsonObject();
                    int n = b.getAsJsonArray("i").size();
                    String caption = b.has("c") ? b.get("c").getAsString() : "";
                    if (n == 1) {
                        platform.sayAs(p.id(), who, name, "ingamemessenger.plain.picture_body", "[picture] %s", caption);
                    } else {
                        platform.sayAs(p.id(), who, name, "ingamemessenger.plain.pictures_body", "[%s pictures] %s", String.valueOf(n), caption);
                    }
                }
                case "poll" -> platform.sayAs(p.id(), who, name, "ingamemessenger.plain.poll_body", "[poll] %s", plain == null ? "" : plain);
                default -> {
                }
            }
        }
    }

    // The game's chat

    /**
     * A chat line of the game, about to be sent to everyone: stored in the game's chat channel and sent to the
     * players with the mod as our message. The platform keeps the game's own copy from them (see {@link #hasClient}).
     * Returns the message id.
     */
    public long chatLine(UUID who, String name, String text) {
        Channel ch = channels.get(gameChat());
        JsonObject b = new JsonObject();
        b.addProperty("t", text);
        return deliver(who, name, ch, "text", b.toString(), text, 0, null).id();
    }

    /** A line of the server for everyone (a join, a death, a broadcast) as component JSON: kept in the game chat's history. */
    public void systemLine(String componentJson) {
        long id = nextMessage.getAndIncrement();
        JsonObject b = new JsonObject();
        b.addProperty("c", componentJson);
        Message m = new Message(id, gameChat(), null, null, "system", b.toString(), 0, System.currentTimeMillis(), 0, false);
        db("saving server line " + id, () -> store.addMessage(m, null));
    }

    /** Whether the player may write in the game's chat (our mute; other plugins' mutes are theirs to enforce there). */
    public boolean mayChat(UUID who) {
        return moderation.mayChat(who, channels.get(gameChat()));
    }

    /** Whether a command writes where the player is muted (/msg in private chats, /me in the chat). */
    public boolean mayCommand(UUID who, String line) {
        return moderation.mayCommand(who, line, channels.get(gameChat()));
    }

    // Editing, deleting, reactions, polls

    private void edit(UUID who, Proto.Edit e) {
        String text = e.text().strip();
        if (!ChatText.isPlainText(text, MAX_TEXT)) {
            return;
        }
        db("edit", () -> {
            Message m = store.message(e.id());
            if (m == null || m.deleted() || !"text".equals(m.kind()) || !who.equals(m.sender())) {
                return;
            }
            long now = System.currentTimeMillis();
            JsonObject b = new JsonObject();
            b.addProperty("t", text);
            store.editMessage(m.id(), b.toString(), text, now);
            platform.execute(() -> {
                if (moderation.mayWrite(who, channels.get(m.channelId()))) {
                    sendViewers(m.channelId(), new Proto.Edited(m.id(), m.channelId(), b.toString(), now));
                }
            });
        });
    }

    private void delete(UUID who, Proto.Delete d) {
        db("delete", () -> {
            Message m = store.message(d.id());
            if (m == null || m.deleted()) {
                return;
            }
            platform.execute(() -> {
                long p = perms(who, m.channelId());
                if (!Perms.has(p, Perms.VIEW) || !who.equals(m.sender()) && !Perms.has(p, Perms.MANAGE_MESSAGES)) {
                    return;
                }
                db("delete", () -> {
                    store.deleteMessage(m.id());
                    if ("images".equals(m.kind())) {
                        albums.forget(m.body());
                    }
                });
                sendViewers(m.channelId(), new Proto.Deleted(m.id(), m.channelId()));
            });
        });
    }

    private void react(UUID who, String name, Proto.React r) {
        if (!EMOJI.matcher(r.emoji()).matches() || !cooled(who, "react", 150)) {
            return;
        }
        db("reaction", () -> {
            Message m = store.message(r.id());
            if (m == null || m.deleted()) {
                return;
            }
            platform.execute(() -> {
                if (!mayPost(who, m.channelId(), Perms.REACT)) {
                    return;
                }
                db("reaction", () -> {
                    store.toggleReaction(m.id(), who, name, r.emoji(), System.currentTimeMillis());
                    String now = store.reactions(List.of(m.id())).getOrDefault(m.id(), "");
                    platform.execute(() -> sendViewers(m.channelId(), new Proto.Reactions(m.id(), m.channelId(), now)));
                });
            });
        });
    }

    private void vote(UUID who, Proto.Vote v) {
        db("vote", () -> {
            Message m = store.message(v.id());
            if (m == null || m.deleted() || !"poll".equals(m.kind())) {
                return;
            }
            int options = PollBook.options(m.body());
            if (v.option() < 0 || v.option() >= options) {
                return;
            }
            platform.execute(() -> {
                if (!Perms.has(perms(who, m.channelId()), Perms.VIEW)) {
                    return;
                }
                db("vote", () -> {
                    int bits = PollBook.toggle(store.vote(m.id(), who), v.option(), PollBook.multi(m.body()));
                    store.setVote(m.id(), who, bits);
                    String counts = store.pollCounts(m.id(), options);
                    platform.execute(() -> sendViewers(m.channelId(), new Proto.PollState(m.id(), m.channelId(), counts, who, bits)));
                });
            });
        });
    }

    // Read marks and typing

    private void read(UUID who, Proto.Read r) {
        if (!Perms.has(perms(who, r.channel()), Perms.VIEW) || r.upTo() <= 0 || r.upTo() >= nextMessage.get()) {
            return;
        }
        db("read mark", () -> {
            if (store.markRead(who, r.channel(), r.upTo())) {
                Channel ch = channels.get(r.channel());
                if (ch != null && ch.dm()) { // the other one's "read" checks
                    platform.execute(() -> sendViewers(r.channel(), new Proto.ReadMark(r.channel(), who, r.upTo())));
                }
            }
        });
    }

    private void typing(UUID who, String name, Proto.Typing t) {
        if (!platform.settings().typing() || !Perms.has(perms(who, t.channel()), Perms.SEND) || !cooled(who, "typing", 800)) {
            return;
        }
        for (UUID v : viewers(t.channel())) {
            if (!v.equals(who)) {
                platform.send(v, new Proto.TypingAt(t.channel(), who, name));
            }
        }
    }

    // Search

    private void find(UUID who, Proto.Find f) {
        if (!cooled(who, "find", 300)) {
            return;
        }
        List<Long> where = new ArrayList<>();
        for (Channel c : channels.values()) {
            if ((f.channel() == 0 || c.id() == f.channel()) && Perms.has(perms(who, c.id()), Perms.VIEW)) {
                where.add(c.id());
            }
        }
        db("search", () -> {
            JsonArray arr = new JsonArray();
            for (Message m : store.search(where, f.query(), 40)) {
                JsonObject o = Store.json(m);
                arr.add(o);
            }
            platform.execute(() -> send(who, new Proto.Found(f.query(), arr.toString())));
        });
    }

    // Private chats

    /** The private chat of two players: found, or made and announced to both. */
    public long openDm(UUID who, UUID other) {
        if (other.equals(who) || platform.nameOf(other) == null) {
            return 0;
        }
        String key = pair(who, other);
        Long have = dmOf.get(key);
        if (have != null) {
            send(who, new Proto.ChannelUp(channelJson(channels.get(have), who).toString()));
            return have;
        }
        long id = nextChannel.getAndIncrement();
        Channel ch = new Channel(id, 0, "dm", "", "", 0, false, System.currentTimeMillis());
        channels.put(id, ch);
        dmPlayers.put(id, List.of(who, other));
        dmOf.put(key, id);
        db("private chat", () -> {
            store.addChannel(ch);
            store.addChannelMember(id, who);
            store.addChannelMember(id, other);
        });
        for (UUID u : List.of(who, other)) {
            send(u, new Proto.ChannelUp(channelJson(ch, u).toString()));
        }
        return id;
    }

    // For the test bots and the platforms

    public Collection<Channel> channels() {
        return channels.values();
    }
}
