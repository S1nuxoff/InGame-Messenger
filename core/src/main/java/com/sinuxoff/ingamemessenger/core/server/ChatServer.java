package com.sinuxoff.ingamemessenger.core.server;

import com.google.gson.JsonObject;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.*;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.storage.ChatStore;
import com.sinuxoff.ingamemessenger.core.storage.ChatStore.Message;
import com.sinuxoff.ingamemessenger.core.storage.Storage;
import com.sinuxoff.ingamemessenger.core.text.ChatText;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Platform-independent chat server. Validates what players send ({@link #handle}), assigns message ids, stores
 * messages and delivers them to everyone or to the two players of a private chat.
 * <p>
 * Each player has an open chat (ChatTarget); what they send goes there. Private messages are sent to both players,
 * each preceded by a Route packet naming the other one, so the client puts it in the right chat.
 * <p>
 * Packet handling runs on the server thread. Everything that touches the database runs on the storage thread and
 * comes back to the server thread ({@link ServerPlatform#execute}) to send packets.
 */
public final class ChatServer {
    /** Sender id used for system messages (joins, deaths...). */
    public static final UUID SYSTEM = new UUID(0, 0);
    private static final UUID NONE = new UUID(0, 0);
    private static final Pattern EMOJI = Pattern.compile("[0-" + (Protocol.BUILT_IN_REACTIONS - 1) + "]|[A-Za-z0-9_\\-]{1,64}/[A-Za-z0-9_\\-]{1,64}");
    /** Hard limit for pictures, in KB, regardless of config. */
    public static final int IMAGE_MAX_KB = ImageInbox.HARD_MAX_KB;
    /** Max length of a text message, edit or private message. */
    public static final int MAX_TEXT = 300;

    /** Hooks for the test bots. */
    public interface Listener {
        default void publicMessage(int id, UUID sender) {
        }

        default void privateMessage(UUID from, String fromName, UUID to, String toName, String text) {
        }
    }

    /** Database work that may fail. */
    interface Task {
        void run() throws Exception;
    }

    /** Sends only to players with a matching client, see {@link ClientGate}. */
    final ClientGate platform;
    /** The platform itself, for Welcome. */
    private final ServerPlatform direct;
    final Storage storage;
    final PollBook polls = new PollBook();
    final Dms dms;
    final VoiceInbox voice;
    final ImageInbox images;
    final StickerStore stickers;
    private final AtomicInteger nextId = new AtomicInteger(1);
    /** Messages that aren't stored (test chat), with their reactions kept in memory. */
    private final Set<Integer> unstored = ConcurrentHashMap.newKeySet();
    private final Reactions unstoredReactions = new Reactions();
    private Listener listener = new Listener() {
    };
    /** Player -> the other player of their open private chat (absent = public chat). */
    private final Map<UUID, UUID> target = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSticker = new HashMap<>(), lastReact = new HashMap<>(), lastHistory = new HashMap<>(),
            lastTyping = new HashMap<>(), lastSearch = new HashMap<>();
    /** Players who joined but haven't said Hello -> when they joined. */
    private final Map<UUID, Long> unanswered = new ConcurrentHashMap<>();
    /** While set, messages are not stored (test chat). */
    private boolean quiet;

    /** Creates the tables if needed. Blocks until the database is ready. */
    public ChatServer(ServerPlatform platform, Storage storage) {
        this.direct = platform;
        this.platform = new ClientGate(platform);
        this.storage = storage;
        CompletableFuture.runAsync(() -> {
            try {
                storage.chat.init();
                nextId.set(storage.chat.maxId() + 1);
            } catch (Exception e) {
                throw new IllegalStateException("Can't open the chat database: " + e, e);
            }
        }, storage.db).join();
        dms = new Dms(this);
        voice = new VoiceInbox(this);
        images = new ImageInbox(this);
        stickers = new StickerStore(platform.configDir(), platform::info);
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

    /** Rescans the sticker folders in the background, then sends the new index to everyone. */
    public void reloadStickers() {
        Thread t = new Thread(() -> {
            stickers.load();
            platform.execute(() -> sendAll(new StickerIndex(stickers.index())));
        }, "InGameMessenger stickers");
        t.setDaemon(true);
        t.start();
    }

    public StickerStore stickers() {
        return stickers;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void close() {
        storage.close();
    }

    /** Called every few seconds: sweeps old files, drops stale uploads. */
    public void tick() {
        tick(System.currentTimeMillis());
    }

    void tick(long now) {
        voice.tick(now);
        images.tick(now);
        unanswered.forEach((id, t) -> {
            if (now - t > 5000 && unanswered.remove(id) != null && platform.settings().installHint()) {
                platform.say(id, "ingamemessenger.plain.install",
                        "This server uses InGame Messenger. Install the mod to see stickers, voice messages, pictures and polls.");
            }
        });
    }

    // Platform events

    /** A player joined: the client gets Welcome and, if it has the mod, answers with Hello. */
    public void joined(UUID id) {
        unanswered.put(id, System.currentTimeMillis());
        direct.send(id, new Welcome(Protocol.VERSION, platform.modVersion()));
    }

    private void hello(UUID id, String name, Hello h) {
        unanswered.remove(id);
        if (!h.protocol().equals(Protocol.VERSION)) {
            platform.info(name + " has InGame Messenger " + h.version() + ", the server " + platform.modVersion()
                    + ": plain chat for them");
            return;
        }
        if (platform.add(id)) {
            ready(id);
        }
    }

    /** Whether the player's client speaks our protocol. */
    public boolean hasClient(UUID id) {
        return platform.has(id);
    }

    private void ready(UUID id) {
        // Features first: the client turns the messenger on with it, and drops what it would send before
        platform.send(id, features());
        platform.send(id, new StickerIndex(stickers.index()));
        db("read marks", () -> {
            String marks = storage.chat.readMarks();
            platform.execute(() -> platform.send(id, new ReadAll(marks)));
        });
        dms.joined(id);
    }

    public synchronized void left(UUID id, String name) {
        platform.remove(id);
        unanswered.remove(id);
        lastSticker.remove(id);
        lastReact.remove(id);
        lastHistory.remove(id);
        lastTyping.remove(id);
        lastSearch.remove(id);
        target.remove(id);
        typing(id, name, false);
    }

    /** A regular chat line about to be broadcast: its id goes out first. Returns the id. */
    public int chatLine(UUID who, String name, String plain, String textJson) {
        int id = tag(who, Protocol.print(plain), null);
        store(new Message(id, System.currentTimeMillis(), "t", who, name, textJson, null, null, -1, null, false));
        typing(who, name, false);
        return id;
    }

    /** A system message (join, death...) about to be broadcast. */
    public void systemMessage(String textJson) {
        int id = tag(SYSTEM, 0, null);
        store(new Message(id, System.currentTimeMillis(), "y", null, null, textJson, null, null, -1, null, false));
    }

    /** Server features for clients; resend after a setting changes. */
    public Features features() {
        ServerSettings s = platform.settings();
        return new Features(s.typing(), s.voiceOn(), s.voiceMaxSeconds(), !images.provider().equals("off"), images.maxKb(),
                images.provider());
    }

    public void featuresChanged() {
        sendAll(features());
    }

    /** Messages sent inside r are not stored (test chat). */
    public synchronized void quietly(Runnable r) {
        boolean was = quiet;
        quiet = true;
        try {
            r.run();
        } finally {
            quiet = was;
        }
    }

    public void handle(UUID player, Packet packet) {
        String name = platform.nameOf(player);
        if (name == null) {
            return;
        }
        if (packet instanceof Hello h) {
            hello(player, name, h);
            return;
        }
        if (!platform.has(player)) {
            return;
        }
        switch (packet) {
            case Send p -> sticker(player, name, p);
            case StickerGet p -> {
                byte[] data = stickers.image(p.pack(), p.id(), p.thumb());
                platform.send(player, new StickerData(p.pack(), p.id(), p.thumb(), data == null ? new byte[0] : data));
            }
            case React p -> {
                if (EMOJI.matcher(p.emoji()).matches() && cooled(lastReact, player, 150)) {
                    react(player, name, p.id(), p.emoji(), true);
                }
            }
            case EditMsg p -> edit(player, p);
            case DeleteMsg p -> delete(player, p);
            case ChatTarget p -> {
                if (p.other().equals(NONE) || p.other().equals(player)) {
                    target.remove(player);
                } else {
                    target.put(player, p.other());
                }
            }
            case Typing p -> typingFrom(player, name, p.on());
            case HistoryRequest p -> {
                if (cooled(lastHistory, player, 250)) {
                    db("history page", () -> {
                        ChatStore.Page page = storage.chat.page(p.before(), player, null);
                        platform.execute(() -> platform.send(player, new HistoryPage(page.more(), page.json())));
                    });
                }
            }
            case SearchReq p -> {
                if (cooled(lastSearch, player, 250)) {
                    db("search", () -> {
                        String found = storage.chat.search(p.query(), 40);
                        platform.execute(() -> platform.send(player, new SearchRes(p.query(), found)));
                    });
                }
            }
            case ReadUpTo p -> markRead(player, name, p.mid());
            case PollCreate p -> poll(player, name, p);
            case PollVote p -> vote(player, p);
            case DmSend p -> dms.send(player, name, p);
            case DmHistoryReq p -> dms.history(player, p);
            case DmRead p -> dms.read(player, p.other(), p.upTo());
            case VoiceStart p -> voice.start(player, p);
            case VoiceChunk p -> voice.chunk(player, name, p);
            case VoiceGet p -> voice.get(player, p);
            case AlbumStart p -> images.album(player, p);
            case ImageStart p -> images.start(player, p);
            case ImageChunk p -> images.chunk(player, name, p);
            case ImageLinks p -> images.links(player, name, p);
            case ImageGet p -> images.get(player, p);
            default -> platform.warn("Unexpected packet from " + name + ": " + packet.type().id());
        }
    }

    private synchronized boolean cooled(Map<UUID, Long> last, UUID who, long ms) {
        long now = System.currentTimeMillis();
        Long l = last.get(who);
        if (l != null && now - l < ms) {
            return false;
        }
        last.put(who, now);
        return true;
    }

    // Delivery

    /** Null for the public chat, otherwise the player's private pair. */
    String audience(UUID who) {
        UUID t = target.get(who);
        return t == null ? null : ChatStore.pair(who, t);
    }

    void sendAll(Packet p) {
        for (ServerPlatform.ChatPlayer pl : platform.online()) {
            platform.send(pl.id(), p);
        }
    }

    /** To everyone (pair == null) or to both players of the pair, with a Route first if route is set. */
    void sendTo(String pair, Packet p, boolean route) {
        if (pair == null) {
            sendAll(p);
            return;
        }
        UUID[] two = ChatStore.both(pair);
        for (int i = 0; i < 2; i++) {
            if (route) {
                platform.send(two[i], new Route(two[1 - i]));
            }
            platform.send(two[i], p);
        }
    }

    /** Sends on the server thread to whoever sees the message. */
    private void sendAbout(Message m, Packet p) {
        platform.execute(() -> sendTo(m.pair(), p, false));
    }

    private static boolean canSee(Message m, UUID who) {
        return m.pair() == null || m.pair().contains(who.toString());
    }

    // Ids and posting

    /**
     * Assigns the next id and announces it (Tag) to whoever will see the message, right before the message itself.
     * hash is {@link Protocol#print} of the message.
     */
    public synchronized int tag(UUID sender, int hash, String pair) {
        int id = nextId.getAndIncrement();
        sendTo(pair, new Tag(id, sender, hash), false);
        if (pair == null && sender != null && !sender.equals(SYSTEM)) {
            listener.publicMessage(id, sender);
        }
        return id;
    }

    /** Stores a message, unless in quiet mode. */
    synchronized void store(Message m) {
        if (quiet) {
            unstored.add(m.id());
            unstoredReactions.open(m.id());
            return;
        }
        db("saving message " + m.id(), () -> storage.chat.insert(m));
    }

    /** Posts a sticker, voice message, picture or poll (built by make from the id) to the sender's open chat. */
    int post(UUID who, String name, String print, java.util.function.IntFunction<Message> make, Packet show) {
        String aud = audience(who);
        int id = tag(who, Protocol.print(print), aud);
        Message m = make.apply(id);
        store(new Message(m.id(), m.t(), m.kind(), m.who(), m.name(), m.json(), m.pack(), m.sticker(), m.replyTo(), aud, false));
        sendTo(aud, show, true);
        typing(who, name, false);
        return id;
    }

    static Message message(int id, String kind, UUID who, String name, String json, int replyTo) {
        return new Message(id, System.currentTimeMillis(), kind, who, name, json, null, null, replyTo, null, false);
    }

    // Stickers

    private void sticker(UUID who, String name, Send s) {
        if (stickers.image(s.pack(), s.id(), true) == null || !cooled(lastSticker, who, platform.settings().stickerCooldownMs())) {
            return;
        }
        platform.info("<" + name + "> [sticker " + s.pack() + "/" + s.id() + "]");
        int replyTo = Math.max(-1, s.replyTo());
        post(who, name, s.pack() + "/" + s.id(),
                id -> new Message(id, System.currentTimeMillis(), "s", who, name, null, s.pack(), s.id(), replyTo, null, false),
                new Show(who, name, s.pack(), s.id(), replyTo));
    }

    // Reactions

    /** Toggles a reaction and notifies whoever sees the message. Also used by the test bots. */
    public void reactAs(UUID who, String name, int id, String emoji) {
        react(who, name, id, emoji, false);
    }

    private void react(UUID who, String name, int id, String emoji, boolean checkVisible) {
        if (unstored.contains(id)) {
            String now = unstoredReactions.toggle(id, who, name, emoji);
            if (now != null) {
                sendAll(new Packets.Reactions(id, unstoredReactions.data(id)));
            }
            return;
        }
        db("reaction", () -> {
            Message m = storage.chat.get(id);
            if (m == null || checkVisible && !canSee(m, who)) {
                return;
            }
            String had = storage.chat.reaction(id, who);
            storage.chat.setReaction(id, who, name, emoji.equals(had) ? "" : emoji, System.currentTimeMillis());
            sendAbout(m, new Packets.Reactions(id, storage.chat.reactions(id)));
        });
    }

    // Edit and delete

    /** Plain text as a text component JSON. */
    static String textJson(String plain) {
        JsonObject o = new JsonObject();
        o.addProperty("text", plain);
        return o.toString();
    }

    private void edit(UUID who, EditMsg e) {
        String text = e.text().strip();
        if (!ChatText.isPlainText(text, MAX_TEXT)) {
            return;
        }
        db("edit", () -> {
            Message m = storage.chat.get(e.id());
            if (m == null || !"t".equals(m.kind()) || !who.equals(m.who())) {
                return;
            }
            storage.chat.edit(e.id(), textJson(text));
            sendAbout(m, new Edited(e.id(), text));
        });
    }

    private void delete(UUID who, DeleteMsg d) {
        boolean op = platform.isOperator(who);
        if (unstored.contains(d.id())) { // test chat: operators only
            if (op) {
                sendAll(new Deleted(d.id()));
            }
            return;
        }
        db("delete", () -> {
            Message m = storage.chat.get(d.id());
            if (m == null || !canSee(m, who) || !who.equals(m.who()) && !op) {
                return;
            }
            storage.chat.delete(d.id());
            ChatStore.imageRefs(m).forEach(images::forget);
            sendAbout(m, new Deleted(d.id()));
        });
    }

    // Typing

    private void typingFrom(UUID who, String name, boolean on) {
        if (!platform.settings().typing()) {
            return;
        }
        if (!on || cooled(lastTyping, who, 800)) { // "stopped" is never throttled
            typing(who, name, on);
        }
    }

    /** Notifies the players of the chat the sender is in. */
    public void typing(UUID who, String name, boolean on) {
        TypingState st = new TypingState(who, name, on);
        UUID other = target.get(who);
        if (other != null) {
            platform.send(other, new Route(who));
            platform.send(other, st);
            return;
        }
        for (ServerPlatform.ChatPlayer p : platform.online()) {
            if (!p.id().equals(who)) {
                platform.send(p.id(), st);
            }
        }
    }

    // Read marks

    /** Marks the public chat as read up to mid and tells everyone. */
    public void markRead(UUID who, String name, int mid) {
        if (mid < 0 || mid >= nextId.get()) {
            return;
        }
        db("read mark", () -> {
            if (storage.chat.markRead(who, name, mid)) {
                platform.execute(() -> sendAll(new ReadState(who, name, mid)));
            }
        });
    }

    /** Used by the test bots. */
    public void privateMessage(UUID from, String fromName, UUID to, String toName, String text) {
        dms.deliver(from, fromName, to, toName, text);
    }

    public void markDmRead(UUID reader, UUID other, int upTo) {
        dms.read(reader, other, upTo);
    }

    // Polls

    private void poll(UUID who, String name, PollCreate c) {
        String json = polls.compose(who, c.question(), c.options(), c.multi(), System.currentTimeMillis());
        if (json == null) {
            return;
        }
        int replyTo = Math.max(-1, c.replyTo());
        platform.info("<" + name + "> [poll] " + c.question().strip());
        post(who, name, "poll:" + json, id -> message(id, "p", who, name, json, replyTo), new PollShow(who, name, json, replyTo));
    }

    private void vote(UUID who, PollVote v) {
        db("vote", () -> {
            Message m = storage.chat.get(v.id());
            if (m == null || !"p".equals(m.kind()) || !canSee(m, who)) {
                return;
            }
            int options = ChatStore.pollOptions(m.json());
            if (v.option() < 0 || v.option() >= options) {
                return;
            }
            int bits = PollBook.toggle(storage.chat.vote(v.id(), who), v.option(), ChatStore.pollMulti(m.json()));
            storage.chat.setVote(v.id(), who, bits);
            sendAbout(m, new PollState(v.id(), storage.chat.pollCounts(v.id(), options), who, bits));
        });
    }

    Listener listener() {
        return listener;
    }
}
