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
        UUID op = null;
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
    }

    @Test
    void joiningGetsWhatsOn() {
        s.joined(A);
        assertTrue(p.got(A, StickerIndex.class).get(0).json().contains("\"pack\""));
        assertEquals("server", p.got(A, Features.class).get(0).imageHost());
        assertEquals(1, p.got(A, ReadAll.class).size());
        assertEquals(1, p.got(A, DmChats.class).size());
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
        s.joined(A);
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
        s2.handle(A, new HistoryRequest(0));
        String json = p2.got(A, HistoryPage.class).get(0).json();
        assertTrue(json.contains("keep me") && json.contains("\"r\":\"2:Bob\""), json);
        s2.joined(A);
        assertTrue(p2.got(A, ReadAll.class).get(0).data().contains(C + " Cat " + id));
        assertTrue(p2.got(A, DmChats.class).get(0).data().startsWith(B + " Bob "));
        assertTrue(s2.chatLine(A, "Alice", "y", ChatServer.textJson("y")) > id);
    }
}
