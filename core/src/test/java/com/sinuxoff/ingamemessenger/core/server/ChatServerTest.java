package com.sinuxoff.ingamemessenger.core.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.*;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.storage.ChatStore;
import com.sinuxoff.ingamemessenger.core.storage.Database;
import com.sinuxoff.ingamemessenger.core.storage.DatabaseConfig;
import com.sinuxoff.ingamemessenger.core.storage.LocalBlobStore;
import com.sinuxoff.ingamemessenger.core.storage.Storage;
import com.sinuxoff.ingamemessenger.core.storage.StorageConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatServerTest {
    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    /** A server with three players online; it remembers every packet each got. */
    static final class FakePlatform implements ServerPlatform, ServerSettings {
        final Path dir;
        final Map<UUID, String> online = new LinkedHashMap<>(Map.of(A, "Alice", B, "Bob", C, "Cat"));
        final Map<UUID, List<Packet>> got = new HashMap<>();
        final List<String> told = new ArrayList<>();
        final List<String> said = new ArrayList<>(), fallbacks = new ArrayList<>();
        boolean plainLines = true;
        UUID op = null;
        final java.util.Set<UUID> mutedElsewhere = new java.util.HashSet<>();
        final Map<UUID, String> shown = new HashMap<>();
        String provider = "server";

        FakePlatform(Path dir) {
            this.dir = dir;
        }

        List<Packet> got(UUID who) {
            return got.computeIfAbsent(who, k -> new ArrayList<>());
        }

        <T extends Packet> List<T> got(UUID who, Class<T> type) {
            return got(who).stream().filter(type::isInstance).map(type::cast).toList();
        }

        void clear() {
            got.clear();
        }

        @Override
        public Collection<ChatPlayer> online() {
            List<ChatPlayer> out = new ArrayList<>();
            online.forEach((k, v) -> out.add(new ChatPlayer(k, v)));
            return out;
        }

        @Override
        public String nameOf(UUID id) {
            return online.get(id);
        }

        @Override
        public boolean isOperator(UUID id) {
            return id.equals(op);
        }

        @Override
        public String shownName(UUID id) {
            return shown.get(id);
        }

        @Override
        public boolean mutedElsewhere(UUID id) {
            return mutedElsewhere.contains(id);
        }

        @Override
        public void send(UUID player, Packet packet) {
            if (online.containsKey(player)) {
                Protocol.decode(packet.type(), Protocol.encode(packet)); // (it must also survive the wire)
                got(player).add(packet);
            }
        }

        @Override
        public void tell(UUID player, String translationKey) {
            told.add(player + " " + translationKey);
        }

        @Override
        public void say(UUID player, String translationKey, String fallback, String... args) {
            said.add(player + " " + translationKey + " " + String.join(" ", args));
            fallbacks.add(fallback);
        }

        @Override
        public String modVersion() {
            return "test";
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public Path worldDir() {
            return dir;
        }

        @Override
        public Path configDir() {
            return dir.resolve("config");
        }

        @Override
        public ServerSettings settings() {
            return this;
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public int stickerCooldownMs() {
            return 0;
        }

        @Override
        public boolean typing() {
            return true;
        }

        @Override
        public boolean voiceOn() {
            return true;
        }

        @Override
        public int voiceMaxSeconds() {
            return 60;
        }

        @Override
        public int voiceKeepDays() {
            return 7;
        }

        @Override
        public String imageProvider() {
            return provider;
        }

        @Override
        public String imgbbKey() {
            return "";
        }

        @Override
        public int imageKeepDays() {
            return 30;
        }

        @Override
        public int imageMaxKb() {
            return 800;
        }

        @Override
        public boolean plainLines() {
            return plainLines;
        }

        @Override
        public boolean installHint() {
            return true;
        }

        @Override
        public String plainText(String line) {
            return line.equals("sticker") ? "{%s sent a sticker}" : "";
        }

        @Override
        public DatabaseConfig database() {
            return DatabaseConfig.h2();
        }

        @Override
        public StorageConfig storage() {
            return StorageConfig.local();
        }
    }

    /** Synchronous storage on an H2 file in dir, so a second server sees the same data. */
    static ChatServer server(FakePlatform p, Path dir) throws Exception {
        Database db = Database.open(DatabaseConfig.h2(), dir, dir, msg -> {
        });
        return new ChatServer(p, Storage.inline(new ChatStore(db), new LocalBlobStore(dir)));
    }

    /** The players' clients answer Welcome. */
    static void hello(ChatServer s, UUID... players) {
        for (UUID u : players) {
            s.handle(u, new Hello(Protocol.VERSION, "test"));
        }
    }

    @TempDir
    Path dir;
    FakePlatform p;
    ChatServer s;

    @BeforeEach
    void open() throws Exception {
        p = new FakePlatform(dir);
        java.nio.file.Files.createDirectories(dir.resolve("config/stickers/pack"));
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", dir.resolve("config/stickers/pack/x.png").toFile());
        s = server(p, dir);
        s.stickers().load();
        hello(s, A, B, C);
        p.clear();
    }

    static final UUID D = new UUID(0, 4);

    @Test
    void joiningIsAHandshake() {
        p.online.put(D, "Dan");
        s.joined(D);
        assertEquals(Protocol.VERSION, p.got(D, Welcome.class).get(0).protocol());
        assertEquals(1, p.got(D).size(), "nothing else until Hello");
        hello(s, D);
        assertEquals(Features.class, p.got(D).get(1).getClass(), "first: it turns the client on");
        assertTrue(p.got(D, StickerIndex.class).get(0).json().contains("\"pack\""));
        assertEquals("server", p.got(D, Features.class).get(0).imageHost());
        assertEquals(1, p.got(D, ReadAll.class).size());
        assertEquals(1, p.got(D, DmChats.class).size());
        assertTrue(s.hasClient(D));
    }

    @Test
    void aClientMaySayHelloFirst() {
        p.online.put(D, "Dan");
        hello(s, D);
        assertEquals(List.of(Welcome.class, Features.class), p.got(D).stream().limit(2).map(Object::getClass).toList());
        p.clear();
        hello(s, D);
        assertTrue(p.got(D).isEmpty(), "once");
    }

    @Test
    void playersWithoutTheModGetPlainLines() {
        p.online.put(D, "Dan");
        s.joined(D);
        s.handle(D, new Hello("1", "0.1")); // another version counts as none
        s.handle(D, new Send("pack", "x", -1));
        assertTrue(p.got(A, Show.class).isEmpty(), "their packets are ignored");
        s.handle(A, new Send("pack", "x", -1));
        s.handle(A, new PollCreate("Lunch?", "yes\nno", false, -1));
        s.handle(A, new ChatTarget(D));
        s.handle(A, new DmSend(D, "psst"));
        assertEquals(List.of(Welcome.class), p.got(D).stream().map(Object::getClass).toList());
        assertEquals(List.of(D + " ingamemessenger.plain.sticker Alice", D + " ingamemessenger.plain.poll_body Alice Lunch?",
                D + " ingamemessenger.plain.dm Alice psst"), p.said);
        assertFalse(s.hasClient(D));
        assertEquals("{%s sent a sticker}", p.fallbacks.get(0), "the server's own wording");
        assertEquals("<%s> [poll] %s", p.fallbacks.get(1), "else like a chat line of the sender's");
        p.said.clear();
        p.plainLines = false;
        s.handle(A, new Send("pack", "x", -1));
        assertTrue(p.said.isEmpty(), "turned off");
    }

    @Test
    void playersWithoutTheModAreToldAboutIt() {
        p.online.put(D, "Dan");
        s.joined(D);
        s.tick(System.currentTimeMillis() + 1000);
        assertTrue(p.said.isEmpty(), "not before 5 seconds");
        s.tick(System.currentTimeMillis() + 6000);
        s.tick(System.currentTimeMillis() + 12000);
        assertEquals(List.of(D + " ingamemessenger.plain.install "), p.said, "once");
        p.said.clear();
        s.joined(A);
        hello(s, A);
        s.tick(System.currentTimeMillis() + 6000);
        assertTrue(p.said.isEmpty(), "not those who have it");
    }

    @Test
    void aStickerGoesToEveryone() {
        s.handle(A, new Send("pack", "x", -1));
        for (UUID u : List.of(A, B, C)) {
            Tag t = p.got(u, Tag.class).get(0);
            assertEquals(A, t.sender());
            assertEquals(Protocol.print("pack/x"), t.hash());
            assertEquals("x", p.got(u, Show.class).get(0).id());
            assertTrue(p.got(u, Route.class).isEmpty(), "everyone's: no route");
        }
        s.handle(A, new Send("../etc", "x", -1));
        s.handle(A, new Send("pack", "nope", -1));
        assertEquals(1, p.got(B, Show.class).size(), "unknown stickers are refused");
    }

    @Test
    void aPrivateChatReachesOnlyTheTwo() {
        s.handle(A, new ChatTarget(B));
        s.handle(A, new Send("pack", "x", -1));
        assertEquals(B, p.got(A, Route.class).get(0).other());
        assertEquals(A, p.got(B, Route.class).get(0).other());
        assertEquals(1, p.got(B, Show.class).size());
        assertTrue(p.got(C).isEmpty(), "the third sees nothing");
        // typing in it: only the other one, after a Route
        p.clear();
        s.handle(A, new Typing(true));
        assertEquals(List.of(Route.class, TypingState.class), p.got(B).stream().map(Object::getClass).toList());
        assertTrue(p.got(C).isEmpty());
        // back to everyone's
        s.handle(A, new ChatTarget(new UUID(0, 0)));
        p.clear();
        s.handle(A, new Typing(false)); // (a stop always goes through; a start again so soon wouldn't: a flood)
        assertEquals(1, p.got(C, TypingState.class).size());
    }

    @Test
    void reactionsEditsAndDeletions() throws Exception {
        int id = s.chatLine(A, "Alice", "hello", ChatServer.textJson("hello"));
        s.handle(B, new React(id, "1"));
        assertEquals("1:Bob", p.got(C, Packets.Reactions.class).get(0).data());
        s.handle(B, new React(id, "99"));
        assertEquals(1, p.got(C, Packets.Reactions.class).size(), "not an emoji");
        s.handle(B, new EditMsg(id, "hacked"));
        assertTrue(p.got(C, Edited.class).isEmpty(), "not theirs");
        s.handle(A, new EditMsg(id, "hello all"));
        assertEquals("hello all", p.got(C, Edited.class).get(0).text());
        s.handle(B, new DeleteMsg(id));
        assertTrue(p.got(C, Deleted.class).isEmpty(), "not theirs, not an operator");
        p.op = B;
        s.handle(B, new DeleteMsg(id));
        assertEquals(id, p.got(C, Deleted.class).get(0).id());
        assertNull(s.storage.chat.get(id));
    }

    @Test
    void privateMessagesReadsAndTheList() throws Exception {
        s.handle(A, new DmSend(B, "hi Bob"));
        DmShow show = p.got(B, DmShow.class).get(0);
        assertEquals("hi Bob", show.text());
        assertTrue(p.got(C).isEmpty());
        s.handle(B, new DmRead(A, Integer.MAX_VALUE));
        assertEquals(show.id(), p.got(A, DmReadState.class).get(0).upTo());
        p.clear();
        s.left(A, "Alice"); // and back
        hello(s, A);
        String chats = p.got(A, DmChats.class).get(0).data();
        assertEquals(B + " Bob " + show.id() + " 0 " + show.id(), chats);
        s.handle(A, new DmHistoryReq(B, 0));
        HistoryPage page = p.got(A, HistoryPage.class).get(0);
        assertTrue(page.json().contains("hi Bob"));
        s.handle(A, new DmSend(A, "me"));
        s.handle(A, new DmSend(new UUID(9, 9), "nobody"));
        assertEquals(1, s.storage.chat.dmChats(A).size(), "not to oneself, not to someone unknown");
    }

    @Test
    void readsOfTheCommonChat() {
        int id = s.chatLine(A, "Alice", "x", ChatServer.textJson("x"));
        s.handle(B, new ReadUpTo(id));
        assertEquals(id, p.got(C, ReadState.class).get(0).mid());
        s.handle(B, new ReadUpTo(id + 100));
        assertEquals(1, p.got(C, ReadState.class).size(), "not a message yet");
    }

    @Test
    void polls() {
        s.handle(A, new PollCreate("Best?", "a\nb", false, -1));
        PollShow show = p.got(C, PollShow.class).get(0);
        int id = p.got(C, Tag.class).get(0).id();
        assertTrue(show.json().contains("Best?"));
        s.handle(B, new PollVote(id, 1));
        PollState st = p.got(C, PollState.class).get(0);
        assertEquals("0,1;1", st.counts());
        assertEquals(2, st.bits());
    }

    @Test
    void searchAndHistory() {
        s.chatLine(A, "Alice", "diamonds at spawn", ChatServer.textJson("diamonds at spawn"));
        s.handle(B, new SearchReq("diamond"));
        assertTrue(p.got(B, SearchRes.class).get(0).data().contains("diamonds at spawn"));
        s.handle(B, new HistoryRequest(0));
        assertTrue(p.got(B, HistoryPage.class).get(0).json().contains("diamonds"));
    }

    @Test
    void aVoiceMessageInPieces() {
        byte[] file = new byte[50_000];
        file[0] = 'M';
        s.handle(A, new VoiceStart(1, file.length, 5000, new byte[48], -1));
        s.handle(A, new VoiceChunk(1, 0, java.util.Arrays.copyOfRange(file, 0, 30_000)));
        assertTrue(p.got(B, VoiceShow.class).isEmpty(), "not all there yet");
        s.handle(A, new VoiceChunk(1, 30_000, java.util.Arrays.copyOfRange(file, 30_000, 50_000)));
        VoiceShow show = p.got(B, VoiceShow.class).get(0);
        assertEquals(5000, show.durationMs());
        s.handle(C, new VoiceGet(show.key()));
        assertArrayEquals(file, p.got(C, VoiceData.class).get(0).data());
        s.handle(C, new VoiceGet("../../secret"));
        assertEquals(0, p.got(C, VoiceData.class).get(1).data().length);
        s.handle(A, new VoiceStart(2, 10_000_000, 5000, new byte[48], -1));
        s.handle(A, new VoiceChunk(2, 0, new byte[10]));
        assertEquals(1, p.got(B, VoiceShow.class).size(), "too big for its length: refused");
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void picturesKeptOnTheServer() throws Exception {
        byte[] img = png();
        s.handle(A, new AlbumStart(7, 2, "look", -1));
        s.handle(A, new ImageStart(1, img.length, 20, 10, 7, 0));
        s.handle(A, new ImageChunk(1, 0, img));
        assertTrue(p.got(B, ImageShow.class).isEmpty(), "waits for the second");
        s.handle(A, new ImageStart(2, 3, 20, 10, 7, 1));
        s.handle(A, new ImageChunk(2, 0, new byte[]{1, 2, 3})); // not a picture: its slot fails
        ImageShow show = p.got(B, ImageShow.class).get(0);
        assertEquals("look", show.caption());
        String[] line = show.data().split(" ");
        assertEquals(1, show.data().split("\n").length);
        assertEquals("20", line[1]);
        s.handle(C, new ImageGet(line[0]));
        assertArrayEquals(img, p.got(C, ImageData.class).get(0).data());
        // its message deleted: the file goes too
        int id = p.got(B, Tag.class).get(0).id();
        s.handle(A, new DeleteMsg(id));
        s.handle(C, new ImageGet(line[0]));
        assertEquals(0, p.got(C, ImageData.class).get(1).data().length);
    }

    @Test
    void picturesThatAllFail() throws Exception {
        s.handle(A, new AlbumStart(1, 1, "", -1));
        s.handle(A, new ImageStart(1, 3, 20, 10, 1, 0));
        s.handle(A, new ImageChunk(1, 0, new byte[]{1, 2, 3}));
        assertTrue(p.got(B, ImageShow.class).isEmpty());
        assertEquals(List.of(A + " ingamemessenger.image.failed"), p.told);
    }

    @Test
    void picturesOff() throws Exception {
        p.provider = "nonsense";
        assertFalse(s.features().images());
        s.handle(A, new AlbumStart(1, 1, "", -1));
        byte[] img = png();
        s.handle(A, new ImageStart(1, img.length, 20, 10, 1, 0));
        s.handle(A, new ImageChunk(1, 0, img));
        assertTrue(p.got(B, ImageShow.class).isEmpty());
    }

    @Test
    void theTestBotsHearAndAnswer() {
        List<String> heard = new ArrayList<>();
        s.setListener(new ChatServer.Listener() {
            @Override
            public void publicMessage(int id, UUID sender) {
                heard.add("public " + id);
            }

            @Override
            public void privateMessage(UUID from, String fromName, UUID to, String toName, String text) {
                heard.add(fromName + "->" + toName + " " + text);
            }
        });
        int id = s.chatLine(A, "Alice", "x", ChatServer.textJson("x"));
        s.systemMessage(ChatServer.textJson("Bob joined"));
        s.handle(A, new DmSend(B, "psst"));
        assertEquals(List.of("public " + id, "Alice->Bob psst"), heard);
        s.quietly(() -> s.systemMessage(ChatServer.textJson("made up")));
        s.handle(B, new SearchReq("made up"));
        assertEquals("", p.got(B, SearchRes.class).get(0).data(), "quiet: not in the history");
    }

    @Test
    void everythingSurvivesARestart() throws Exception {
        int id = s.chatLine(A, "Alice", "keep me", ChatServer.textJson("keep me"));
        s.handle(B, new React(id, "2"));
        s.handle(C, new ReadUpTo(id));
        s.handle(A, new DmSend(B, "later"));
        s.close();
        FakePlatform p2 = new FakePlatform(dir);
        ChatServer s2 = server(p2, dir);
        hello(s2, A);
        s2.handle(A, new HistoryRequest(0));
        String json = p2.got(A, HistoryPage.class).get(0).json();
        assertTrue(json.contains("keep me") && json.contains("\"r\":\"2:Bob\""), json);
        assertTrue(p2.got(A, ReadAll.class).get(0).data().contains(C + " Cat " + id));
        assertTrue(p2.got(A, DmChats.class).get(0).data().startsWith(B + " Bob "));
        assertTrue(s2.chatLine(A, "Alice", "y", ChatServer.textJson("y")) > id);
    }

    // Mutes

    static List<String> args(String... a) {
        return List.of(a);
    }

    @Test
    void aChatMuteKeepsPrivateChats() {
        assertTrue(s.mutes().command("Mod", "mute", args("bob", "10m", "spam", "links")).startsWith("Bob muted"));
        MuteState st = p.got(B, MuteState.class).get(0);
        assertTrue(st.chatUntil() > System.currentTimeMillis() && st.dmUntil() == 0);
        assertEquals("spam links", st.reason());
        s.handle(B, new Send("pack", "x", -1));
        s.handle(B, new PollCreate("Lunch?", "yes\nno", false, -1));
        assertTrue(p.got(A, Show.class).isEmpty() && p.got(A, PollShow.class).isEmpty(), "nothing in the chat");
        assertTrue(p.said.get(0).startsWith(B + " ingamemessenger.mute.chat_for 10m"), p.said.toString());
        assertEquals(1, p.said.size(), "told once, not on every try");
        assertFalse(s.mutes().mayChat(B), "the chat line itself is stopped by the platform");
        s.handle(B, new DmSend(A, "sorry"));
        assertEquals("sorry", p.got(A, DmShow.class).get(0).text(), "private chats still work");
        assertTrue(s.mutes().mayChat(A));
    }

    @Test
    void aMuteEverywhereAndUnmute() {
        s.mutes().command("Mod", "mute", args("Bob", "all"));
        s.handle(B, new DmSend(A, "hi"));
        assertTrue(p.got(A, DmShow.class).isEmpty());
        assertTrue(p.said.get(0).startsWith(B + " ingamemessenger.mute.dm "), "forever: no time left shown");
        s.mutes().command("Mod", "unmute", args("Bob", "dm"));
        s.handle(B, new DmSend(A, "hi"));
        assertEquals(1, p.got(A, DmShow.class).size(), "private chats back");
        s.handle(B, new Send("pack", "x", -1));
        assertTrue(p.got(A, Show.class).isEmpty(), "still muted in the chat");
        assertEquals("Bob unmuted", s.mutes().command("Mod", "unmute", args("bob")));
        s.handle(B, new Send("pack", "x", -1));
        assertEquals(1, p.got(A, Show.class).size());
        assertEquals("Bob isn't muted", s.mutes().command("Mod", "unmute", args("bob")));
        MuteState last = p.got(B, MuteState.class).get(p.got(B, MuteState.class).size() - 1);
        assertEquals(0, last.chatUntil() + last.dmUntil());
    }

    @Test
    void aMuteRunsOut() {
        s.mutes().command("Mod", "mute", args("Bob", "1m"));
        s.tick(System.currentTimeMillis() + 61_000);
        MuteState last = p.got(B, MuteState.class).get(p.got(B, MuteState.class).size() - 1);
        assertEquals(0, last.chatUntil());
        assertTrue(s.mutes().mayChat(B));
        assertEquals("nobody is muted", s.mutes().command("Mod", "mutes", args()));
    }

    @Test
    void anotherPluginsMuteCountsInTheChat() {
        p.mutedElsewhere.add(B);
        s.handle(B, new Send("pack", "x", -1));
        assertTrue(p.got(A, Show.class).isEmpty(), "no stickers around Essentials' /mute");
        assertTrue(s.mutes().mayChat(B), "the chat line is the other plugin's to stop");
        s.handle(B, new DmSend(A, "hi"));
        assertEquals(1, p.got(A, DmShow.class).size());
    }

    @Test
    void mutedPlayersCantEditOrReact() {
        int id = s.chatLine(B, "Bob", "hello", ChatServer.textJson("hello"));
        int other = s.chatLine(A, "Alice", "hey", ChatServer.textJson("hey"));
        s.mutes().command("Mod", "mute", args("Bob"));
        s.handle(B, new EditMsg(id, "now with a link"));
        s.handle(B, new React(other, "1"));
        assertTrue(p.got(A, Edited.class).isEmpty());
        assertTrue(p.got(A, Packets.Reactions.class).isEmpty());
    }

    @Test
    void mutesOutliveARestart() throws Exception {
        s.mutes().command("Mod", "mute", args("Bob", "2h", "dm", "rude"));
        s.close();
        FakePlatform p2 = new FakePlatform(dir);
        ChatServer s2 = server(p2, dir);
        hello(s2, B);
        MuteState st = p2.got(B, MuteState.class).get(0);
        assertTrue(st.dmUntil() > System.currentTimeMillis() && st.chatUntil() == 0);
        assertEquals("rude", st.reason());
        assertTrue(s2.mutes().command("Mod", "mutes", args()).contains("Bob: private for 2h (rude), by Mod"));
        s2.close();
    }

    @Test
    void muteTimes() {
        assertEquals(5_400_000L, Mutes.time("1h30m"));
        assertEquals(Mutes.FOREVER, Mutes.time("forever"));
        assertNull(Mutes.time("chat"));
        assertNull(Mutes.time("10x"));
        assertEquals("no player Zed online", s.mutes().command("Mod", "mute", args("Zed")));
    }

    // Shown names

    static final String ADMIN = "{\"text\":\"[Admin] Bob\",\"color\":\"red\"}";

    @Test
    void namesAsTheServerShowsThem() throws Exception {
        p.shown.put(B, ADMIN);
        p.online.put(D, "Dan");
        s.joined(D);
        hello(s, D);
        assertEquals(B + "\tBob\t" + ADMIN, p.got(D, Names.class).get(p.got(D, Names.class).size() - 1).data(), "on join: the ranks of those online");
        p.clear();
        s.tick(System.currentTimeMillis());
        assertTrue(p.got(A, Names.class).isEmpty(), "nothing new");
        p.shown.put(B, "{\"text\":\"[Mod] Bob\"}");
        s.tick(System.currentTimeMillis());
        assertEquals(B + "\tBob\t{\"text\":\"[Mod] Bob\"}", p.got(A, Names.class).get(0).data(), "a new rank goes to everyone");
        // Bob writes and leaves: his rank still comes with the history
        s.chatLine(B, "Bob", "hi", ChatServer.textJson("hi"));
        p.online.remove(B);
        s.left(B, "Bob");
        p.clear();
        s.handle(A, new HistoryRequest(0));
        List<Packet> got = p.got(A);
        assertEquals(Names.class, got.get(0).getClass(), "before the page");
        assertEquals(B + "\tBob\t{\"text\":\"[Mod] Bob\"}", ((Names) got.get(0)).data());
        assertEquals(HistoryPage.class, got.get(1).getClass());
    }
}
