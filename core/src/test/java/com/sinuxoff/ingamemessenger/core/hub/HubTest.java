package com.sinuxoff.ingamemessenger.core.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Proto;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.server.ServerPlatform;
import com.sinuxoff.ingamemessenger.core.server.ServerSettings;
import com.sinuxoff.ingamemessenger.core.storage.Database;
import com.sinuxoff.ingamemessenger.core.storage.DatabaseConfig;
import com.sinuxoff.ingamemessenger.core.storage.LocalBlobStore;
import com.sinuxoff.ingamemessenger.core.storage.Storage;
import com.sinuxoff.ingamemessenger.core.storage.StorageConfig;
import com.sinuxoff.ingamemessenger.core.storage.Store;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HubTest {
    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);

    /** A server with players online (Dan without the mod); it remembers every packet and line each got. */
    static final class FakePlatform implements ServerPlatform, ServerSettings {
        final Path dir;
        final Map<UUID, String> online = new LinkedHashMap<>(Map.of(A, "Alice", B, "Bob", C, "Cat", D, "Dan"));
        final Map<UUID, List<Packet>> got = new HashMap<>();
        final List<String> said = new ArrayList<>();
        final Set<UUID> ops = new HashSet<>(), mutedElsewhere = new HashSet<>();

        FakePlatform(Path dir) {
            this.dir = dir;
        }

        List<Packet> got(UUID who) {
            return got.computeIfAbsent(who, k -> new ArrayList<>());
        }

        <T extends Packet> List<T> got(UUID who, Class<T> type) {
            return got(who).stream().filter(type::isInstance).map(type::cast).toList();
        }

        <T extends Packet> T last(UUID who, Class<T> type) {
            List<T> l = got(who, type);
            return l.isEmpty() ? null : l.get(l.size() - 1);
        }

        void clear() {
            got.clear();
            said.clear();
        }

        public Collection<ChatPlayer> online() {
            List<ChatPlayer> out = new ArrayList<>();
            online.forEach((k, v) -> out.add(new ChatPlayer(k, v)));
            return out;
        }

        public String nameOf(UUID id) {
            return online.get(id);
        }

        public boolean isOperator(UUID id) {
            return ops.contains(id);
        }

        public boolean mutedElsewhere(UUID id) {
            return mutedElsewhere.contains(id);
        }

        public void send(UUID player, Packet packet) {
            if (online.containsKey(player)) {
                Protocol.decode(packet.type(), Protocol.encode(packet)); // it must also survive the wire
                got(player).add(packet);
            }
        }

        public void tell(UUID player, String key) {
            said.add(player + " " + key);
        }

        public void say(UUID player, String key, String fallback, String... args) {
            said.add(player + " " + key + " " + String.join(" ", args));
        }

        public void sayAs(UUID player, UUID sender, String senderName, String key, String fallback, String... args) {
            said.add(player + " " + senderName + ": " + key + " " + String.join(" ", args));
        }

        public String modVersion() {
            return "test";
        }

        public void execute(Runnable task) {
            task.run();
        }

        public Path worldDir() {
            return dir;
        }

        public Path configDir() {
            return dir.resolve("config");
        }

        public ServerSettings settings() {
            return this;
        }

        public void info(String message) {
        }

        public void warn(String message) {
            throw new AssertionError("warning: " + message);
        }

        public int stickerCooldownMs() {
            return 0;
        }

        public boolean typing() {
            return true;
        }

        public boolean voiceOn() {
            return true;
        }

        public int voiceMaxSeconds() {
            return 60;
        }

        public int voiceKeepDays() {
            return 7;
        }

        public String imageProvider() {
            return "server";
        }

        public String imgbbKey() {
            return "";
        }

        public int imageKeepDays() {
            return 30;
        }

        public int imageMaxKb() {
            return 800;
        }

        public boolean plainLines() {
            return true;
        }

        public boolean installHint() {
            return true;
        }

        public String plainText(String line) {
            return "";
        }

        public DatabaseConfig database() {
            return DatabaseConfig.h2();
        }

        public StorageConfig storage() {
            return StorageConfig.local();
        }
    }

    static Hub hub(FakePlatform p, Path dir) throws Exception {
        Database db = Database.open(DatabaseConfig.h2(), dir, dir, msg -> {
        });
        return new Hub(p, Storage.inline(new Store(db), new LocalBlobStore(dir)));
    }

    /** The players' clients answer Welcome. */
    static void hello(Hub h, UUID... players) {
        for (UUID u : players) {
            h.handle(u, new Packets.Hello(Protocol.VERSION, "test"));
        }
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @TempDir
    Path dir;
    FakePlatform p;
    Hub h;
    long general;

    @BeforeEach
    void open() throws Exception {
        p = new FakePlatform(dir);
        java.nio.file.Files.createDirectories(dir.resolve("config/stickers/pack"));
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", dir.resolve("config/stickers/pack/x.png").toFile());
        h = hub(p, dir);
        long[] t = {0};
        h.clock = () -> t[0] += 10_000; // no cooldowns between the steps of a test
        h.stickers().load();
        hello(h, A, B, C);
        general = h.gameChat();
        p.clear();
    }

    @Test
    void joiningGivesTheServersSpaceAndItsChat() {
        p.online.put(new UUID(0, 9), "Eve");
        UUID e = new UUID(0, 9);
        h.joined(e);
        assertEquals(Protocol.VERSION, p.got(e, Packets.Welcome.class).get(0).protocol());
        hello(h, e);
        JsonObject snap = json(p.last(e, Proto.Snapshot.class).json());
        assertEquals(general, snap.get("game").getAsLong());
        assertEquals(1, snap.getAsJsonArray("spaces").size());
        JsonObject ch = snap.getAsJsonArray("channels").get(0).getAsJsonObject();
        assertTrue(ch.get("game").getAsBoolean());
        assertTrue(Perms.has(ch.get("perms").getAsLong(), Perms.VIEW | Perms.SEND | Perms.REACT));
        assertFalse(Perms.has(ch.get("perms").getAsLong(), Perms.MANAGE_MESSAGES), "not an op");
        assertEquals(1, p.got(e, Packets.StickerIndex.class).size());
        assertTrue(h.hasClient(e));
    }

    @Test
    void theGamesChatComesAsOurMessages() {
        long id = h.chatLine(A, "Alice", "hi all");
        JsonObject m = json(p.last(B, Proto.Msg.class).json());
        assertEquals(id, m.get("i").getAsLong());
        assertEquals(general, m.get("ch").getAsLong());
        assertEquals("Alice", m.get("n").getAsString());
        assertEquals("hi all", json(m.get("b").getAsString()).get("t").getAsString());
        assertNotNull(p.last(A, Proto.Msg.class), "the sender too: it gets no copy from the game");
        assertTrue(p.got(D).isEmpty(), "Dan without the mod reads the game's own line");
        h.handle(C, new Proto.History(general, 0));
        Proto.Page page = p.last(C, Proto.Page.class);
        assertEquals(1, JsonParser.parseString(page.json()).getAsJsonArray().size());
        assertFalse(page.more());
    }

    @Test
    void serverLinesGoIntoTheHistoryOnly() {
        h.systemLine("{\"text\":\"Bob joined\"}");
        assertTrue(p.got(A).isEmpty(), "the game's own line is what clients show live");
        h.handle(A, new Proto.History(general, 0));
        JsonObject m = JsonParser.parseString(p.last(A, Proto.Page.class).json()).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("system", m.get("k").getAsString());
        assertFalse(m.has("u"));
    }

    @Test
    void textInTheGamesChatMustComeThroughTheGame() {
        h.handle(A, new Proto.Post(general, "n1", "text", "hey", 0));
        assertTrue(p.got(B).isEmpty());
    }

    @Test
    void stickersInTheGamesChatAndAPlainLineForThoseWithoutTheMod() {
        h.handle(A, new Proto.Post(general, "n1", "sticker", "{\"p\":\"pack\",\"s\":\"x\"}", 0));
        assertEquals("n1", p.last(A, Proto.Msg.class).nonce(), "the sender knows its own");
        assertEquals("", p.last(B, Proto.Msg.class).nonce());
        assertEquals(List.of(D + " Alice: ingamemessenger.plain.sticker_body "), p.said);
        h.handle(A, new Proto.Post(general, "n2", "sticker", "{\"p\":\"pack\",\"s\":\"nope\"}", 0));
        assertEquals(1, p.got(B, Proto.Msg.class).size(), "no such sticker");
    }

    @Test
    void privateChats() {
        h.handle(A, new Proto.OpenDm(B));
        long dm = json(p.last(A, Proto.ChannelUp.class).json()).get("id").getAsLong();
        JsonObject forBob = json(p.last(B, Proto.ChannelUp.class).json());
        assertEquals(A.toString(), forBob.get("with").getAsString());
        assertEquals("Alice", forBob.get("withName").getAsString());
        p.clear();
        h.handle(A, new Proto.OpenDm(B));
        assertEquals(dm, json(p.last(A, Proto.ChannelUp.class).json()).get("id").getAsLong(), "the same one");
        h.handle(A, new Proto.Post(dm, "n", "text", "psst", 0));
        assertEquals("psst", json(json(p.last(B, Proto.Msg.class).json()).get("b").getAsString()).get("t").getAsString());
        assertTrue(p.got(C, Proto.Msg.class).isEmpty(), "Cat can't see it");
        h.handle(C, new Proto.History(dm, 0));
        assertTrue(p.got(C, Proto.Page.class).isEmpty());
        h.handle(C, new Proto.Post(dm, "n", "text", "me too", 0));
        assertEquals(1, p.got(B, Proto.Msg.class).size(), "nor write there");
        // read marks: Bob reads, Alice sees the checks
        long id = json(p.last(B, Proto.Msg.class).json()).get("i").getAsLong();
        h.handle(B, new Proto.Read(dm, id));
        Proto.ReadMark r = p.last(A, Proto.ReadMark.class);
        assertEquals(B, r.player());
        assertEquals(id, r.upTo());
    }

    @Test
    void unreadCountsComeWithTheSnapshot() {
        h.handle(A, new Proto.OpenDm(B));
        long dm = json(p.last(A, Proto.ChannelUp.class).json()).get("id").getAsLong();
        h.handle(A, new Proto.Post(dm, "n", "text", "one", 0));
        h.handle(A, new Proto.Post(dm, "n", "text", "two", 0));
        h.chatLine(C, "Cat", "hello");
        h.left(B);
        p.clear();
        hello(h, B);
        JsonObject snap = json(p.last(B, Proto.Snapshot.class).json());
        assertEquals(2, snap.getAsJsonObject("unread").get(String.valueOf(dm)).getAsInt());
        assertEquals(1, snap.getAsJsonObject("unread").get(String.valueOf(general)).getAsInt());
        assertEquals(2, snap.getAsJsonArray("channels").size(), "the chat and the private one");
    }

    @Test
    void editDeleteReactVote() {
        h.handle(A, new Proto.OpenDm(B));
        long dm = json(p.last(A, Proto.ChannelUp.class).json()).get("id").getAsLong();
        h.handle(A, new Proto.Post(dm, "n", "text", "helo", 0));
        long id = json(p.last(B, Proto.Msg.class).json()).get("i").getAsLong();
        h.handle(B, new Proto.Edit(id, "hacked"));
        assertTrue(p.got(A, Proto.Edited.class).isEmpty(), "only the author edits");
        h.handle(A, new Proto.Edit(id, "hello"));
        assertEquals("hello", json(p.last(B, Proto.Edited.class).body()).get("t").getAsString());
        h.handle(B, new Proto.React(id, "1"));
        assertEquals("1:Bob", p.last(A, Proto.Reactions.class).data());
        h.handle(B, new Proto.React(id, "1"));
        assertEquals("", p.last(A, Proto.Reactions.class).data(), "taken back");
        h.handle(B, new Proto.Delete(id));
        assertTrue(p.got(A, Proto.Deleted.class).isEmpty(), "not Bob's");
        h.handle(A, new Proto.Delete(id));
        assertEquals(id, p.last(B, Proto.Deleted.class).id());
        h.handle(A, new Proto.Post(dm, "n", "poll", "{\"q\":\"Lunch?\",\"o\":[\"yes\",\"no\"]}", 0));
        long poll = json(p.last(B, Proto.Msg.class).json()).get("i").getAsLong();
        h.handle(B, new Proto.Vote(poll, 1));
        assertEquals("0,1;1", p.last(A, Proto.PollState.class).counts());
        h.handle(B, new Proto.History(dm, 0));
        JsonArray page = JsonParser.parseString(p.last(B, Proto.Page.class).json()).getAsJsonArray();
        assertEquals(1, page.size(), "the deleted one isn't there");
        assertEquals(2, page.get(0).getAsJsonObject().get("pm").getAsInt(), "Bob's vote");
    }

    @Test
    void opsModerateTheServersChannels() {
        long id = h.chatLine(A, "Alice", "spam");
        h.handle(B, new Proto.Delete(id));
        assertTrue(p.got(A, Proto.Deleted.class).isEmpty());
        p.ops.add(B);
        h.handle(B, new Proto.Delete(id));
        assertEquals(id, p.last(A, Proto.Deleted.class).id());
    }

    @Test
    void mutesHoldInTheChatButNotInPrivate() {
        assertTrue(h.moderation().command("Mod", "mute", List.of("bob", "10m", "spam")).startsWith("Bob muted"));
        JsonArray mutes = JsonParser.parseString(p.last(B, Proto.Mutes.class).json()).getAsJsonArray();
        assertEquals("space", mutes.get(0).getAsJsonObject().get("scope").getAsString());
        assertFalse(h.mayChat(B));
        h.handle(B, new Proto.Post(general, "n", "sticker", "{\"p\":\"pack\",\"s\":\"x\"}", 0));
        assertTrue(p.got(A, Proto.Msg.class).isEmpty());
        assertTrue(p.said.stream().anyMatch(s -> s.startsWith(B + " ingamemessenger.mute.chat_for 10m")), p.said.toString());
        h.handle(B, new Proto.OpenDm(A));
        long dm = json(p.last(B, Proto.ChannelUp.class).json()).get("id").getAsLong();
        h.handle(B, new Proto.Post(dm, "n", "text", "sorry", 0));
        assertEquals(1, p.got(A, Proto.Msg.class).size(), "private chats still work");
        h.moderation().command("Mod", "mute", List.of("bob", "dm"));
        h.handle(B, new Proto.Post(dm, "n", "text", "again", 0));
        assertEquals(1, p.got(A, Proto.Msg.class).size());
        assertFalse(h.mayCommand(B, "/msg Alice hi"));
        assertTrue(h.mayCommand(B, "/home"));
        assertEquals("Bob unmuted", h.moderation().command("Mod", "unmute", List.of("bob")));
        assertTrue(h.mayChat(B));
    }

    @Test
    void anotherPluginsMuteCountsInTheServersChannels() {
        p.mutedElsewhere.add(B);
        h.handle(B, new Proto.Post(general, "n", "sticker", "{\"p\":\"pack\",\"s\":\"x\"}", 0));
        assertTrue(p.got(A, Proto.Msg.class).isEmpty());
        assertTrue(h.mayChat(B), "the line itself is the other plugin's to stop");
    }

    @Test
    void searchOnlyWhereYouSee() {
        h.chatLine(A, "Alice", "the diamond shop is open");
        h.handle(A, new Proto.OpenDm(B));
        long dm = json(p.last(A, Proto.ChannelUp.class).json()).get("id").getAsLong();
        h.handle(A, new Proto.Post(dm, "n", "text", "secret diamond stash", 0));
        h.handle(C, new Proto.Find("diamond", 0));
        assertEquals(1, JsonParser.parseString(p.last(C, Proto.Found.class).json()).getAsJsonArray().size());
        h.handle(B, new Proto.Find("diamond", 0));
        assertEquals(2, JsonParser.parseString(p.last(B, Proto.Found.class).json()).getAsJsonArray().size());
    }

    @Test
    void everythingOutlivesARestart() throws Exception {
        h.handle(A, new Proto.OpenDm(B));
        long dm = json(p.last(A, Proto.ChannelUp.class).json()).get("id").getAsLong();
        h.handle(A, new Proto.Post(dm, "n", "text", "remember me", 0));
        long first = json(p.last(B, Proto.Msg.class).json()).get("i").getAsLong();
        h.moderation().command("Mod", "mute", List.of("cat", "2h"));
        h.close();
        FakePlatform p2 = new FakePlatform(dir);
        Hub h2 = hub(p2, dir);
        hello(h2, B, C);
        h2.handle(B, new Proto.History(dm, 0));
        assertEquals(1, JsonParser.parseString(p2.last(B, Proto.Page.class).json()).getAsJsonArray().size());
        assertFalse(h2.mayChat(C));
        h2.chatLine(A, "Alice", "next");
        assertTrue(json(p2.last(B, Proto.Msg.class).json()).get("i").getAsLong() > first, "ids go on");
        h2.close();
    }

    @Test
    void channelPermissions() {
        Store.Role everyone = new Store.Role(1, 1, "@everyone", 0, 0, Perms.EVERYONE);
        Store.Role mod = new Store.Role(2, 1, "Mod", 0, 1, Perms.MANAGE_MESSAGES);
        long base = Perms.base(everyone.perms(), List.of());
        List<Store.Override> staffOnly = List.of(new Store.Override(5, "r:1", 0, Perms.VIEW), new Store.Override(5, "r:2", Perms.VIEW, 0));
        assertEquals(0, Perms.inChannel(base, 1, List.of(), A, staffOnly), "everyone loses sight of it");
        long modBase = Perms.base(everyone.perms(), List.of(mod));
        assertTrue(Perms.has(Perms.inChannel(modBase, 1, List.of(2L), A, staffOnly), Perms.VIEW | Perms.MANAGE_MESSAGES));
        List<Store.Override> readOnly = List.of(new Store.Override(5, "r:1", 0, Perms.SEND), new Store.Override(5, "p:" + B, Perms.SEND, 0));
        assertFalse(Perms.has(Perms.inChannel(base, 1, List.of(), A, readOnly), Perms.SEND));
        assertTrue(Perms.has(Perms.inChannel(base, 1, List.of(), B, readOnly), Perms.SEND), "a player's own override wins");
        assertEquals(Perms.ALL, Perms.base(Perms.ADMIN, List.of()));
        assertEquals(5_400_000L, Moderation.time("1h30m"));
        assertEquals("2h", Moderation.left(7_200_000L));
    }
}
