package com.sinuxoff.ingamemessenger.core.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sinuxoff.ingamemessenger.core.server.PollBook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatStoreTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static int dbs;

    private ChatStore store;

    @BeforeEach
    void open() throws Exception {
        store = new ChatStore(Database.memory("t" + dbs++));
        store.init();
    }

    @AfterEach
    void close() {
        store.close();
    }

    private static String text(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("text", t);
        return o.toString();
    }

    private static ChatStore.Message msg(int id, String kind, UUID who, String name, String json, int replyTo, String pair) {
        return new ChatStore.Message(id, id * 1000L, kind, who, name, json, null, null, replyTo, pair, false);
    }

    private JsonArray page(int before, UUID viewer, String pair) throws Exception {
        return JsonParser.parseString(store.page(before, viewer, pair).json()).getAsJsonArray();
    }

    @Test
    void messagesReactionsPollsAndDeletion() throws Exception {
        store.insert(msg(1, "t", A, "Alice", text("hello there"), -1, null));
        store.insert(msg(2, "t", B, "Bob", text("↩1 hi Alice"), -1, null));
        store.setReaction(1, B, "Bob", "1", 10);
        store.edit(1, text("hello everyone"));
        String poll = new PollBook().compose(A, "Best?", "x\ny", false, 0);
        store.insert(msg(3, "p", A, "Alice", poll, -1, null));
        store.setVote(3, B, PollBook.toggle(0, 1, false));
        String ab = ChatStore.pair(A, B);
        store.insert(msg(4, "t", A, "Alice", text("secret"), -1, ab));
        store.delete(1);

        assertNull(store.get(1), "deleted");
        assertEquals(4, store.maxId(), "deleted ids still count");
        JsonArray everyone = page(0, B, null);
        assertEquals(2, everyone.size(), "the private message isn't public");
        JsonObject reply = everyone.get(0).getAsJsonObject();
        assertEquals(2, reply.get("i").getAsInt());
        assertEquals(1, reply.get("rd").getAsInt(), "the replied message was deleted");
        JsonObject pl = everyone.get(1).getAsJsonObject();
        assertEquals("0,1;1", pl.get("pc").getAsString());
        assertEquals(2, pl.get("pm").getAsInt());
        assertEquals(1, page(0, A, ab).size());
        assertEquals(ab, store.get(4).pair());
    }

    @Test
    void reactionsKeepTheirOrder() throws Exception {
        store.insert(msg(1, "t", A, "Alice", text("x"), -1, null));
        store.setReaction(1, A, "Alice", "1", 1);
        store.setReaction(1, B, "Bob", "1", 2);
        store.setReaction(1, C, "Cat", "pack/x", 3);
        assertEquals("1:Alice,Bob;pack/x:Cat", store.reactions(1));
        store.setReaction(1, A, "Alice", "", 4);
        store.setReaction(1, B, "Bob", "2", 5);
        assertEquals("pack/x:Cat;2:Bob", store.reactions(1));
        assertEquals("2", store.reaction(1, B));
        assertNull(store.reaction(1, A));
    }

    @Test
    void pagesGoBackInTime() throws Exception {
        for (int i = 1; i <= 120; i++) {
            store.insert(msg(i, "t", A, "Alice", text("m" + i), -1, null));
        }
        var newest = store.page(0, A, null);
        JsonArray a = JsonParser.parseString(newest.json()).getAsJsonArray();
        assertEquals(ChatStore.PAGE, a.size());
        assertTrue(newest.more());
        assertEquals(71, a.get(0).getAsJsonObject().get("i").getAsInt());
        assertEquals(120, a.get(49).getAsJsonObject().get("i").getAsInt());
        var oldest = store.page(21, A, null);
        assertFalse(oldest.more());
        assertEquals(20, JsonParser.parseString(oldest.json()).getAsJsonArray().size());
    }

    @Test
    void searchFindsWordsNotMarks() throws Exception {
        store.insert(msg(1, "t", A, "Alice", "{\"text\":\"↩5 \",\"extra\":[{\"text\":\"the Diamond mine 100%\"}]}", -1, null));
        store.insert(msg(2, "i", B, "Bob", "{\"data\":\"https://files.catbox.moe/abcd.jpg 10 10\",\"text\":\"diamonds!\"}", -1, null));
        store.insert(msg(3, "t", A, "Alice", text("diamond secret"), -1, ChatStore.pair(A, B)));
        String found = store.search("DIAMOND", 10);
        String[] lines = found.split("\n");
        assertEquals(2, lines.length, found);
        assertTrue(lines[0].startsWith("2\t"));
        assertTrue(lines[1].endsWith("\tthe Diamond mine 100%"), lines[1]);
        assertEquals("", store.search("d", 10), "too short");
        assertEquals("1\t1000\tAlice\tthe Diamond mine 100%", store.search("100%", 10), "% is matched literally");
        assertEquals("", store.search("1_0", 10), "_ is matched literally");
        assertEquals(List.of("https://files.catbox.moe/abcd.jpg"), ChatStore.imageRefs(store.get(2)));
    }

    @Test
    void readMarks() throws Exception {
        assertTrue(store.markRead(A, "Alice", 5));
        assertFalse(store.markRead(A, "Alice", 4), "only forward");
        assertTrue(store.markRead(B, "Bob", 9));
        String all = store.readMarks();
        assertTrue(all.contains(A + " Alice 5") && all.contains(B + " Bob 9"), all);
    }

    @Test
    void privateChats() throws Exception {
        String ab = ChatStore.pair(A, B), ac = ChatStore.pair(A, C);
        store.insert(msg(3, "t", B, "Bob", text("1"), -1, ab));
        store.insert(msg(4, "t", A, "Alice", text("2"), -1, ab));
        store.insert(msg(7, "t", B, "Bob", text("3"), -1, ab));
        store.insert(msg(9, "t", C, "Cat", text("4"), -1, ac));
        assertTrue(store.markDmRead(A, B, 3));
        assertFalse(store.markDmRead(A, B, 2));
        store.markDmRead(B, A, 4);
        assertEquals(7, store.newestInPair(ab));
        List<ChatStore.DmChat> chats = store.dmChats(A);
        assertEquals(List.of(new ChatStore.DmChat(C, 9, 1, 0), new ChatStore.DmChat(B, 7, 1, 4)), chats);
    }

    @Test
    void files() throws Exception {
        store.addFile("voice/a", "voice", 100);
        store.addFile("voice/b", "voice", 300);
        store.addFile("images/c", "images", 100);
        assertEquals(List.of("voice/a"), store.filesBefore("voice", 200));
        store.removeFile("voice/a");
        assertEquals(List.of(), store.filesBefore("voice", 200));
    }

    @Test
    void anH2FileSurvivesAReopen(@TempDir Path dir) throws Exception {
        ChatStore s = new ChatStore(Database.open(DatabaseConfig.h2(), dir, dir, m -> {
        }));
        s.init();
        s.insert(msg(5, "t", A, "Alice", text("kept"), -1, null));
        s.close();
        ChatStore back = new ChatStore(Database.open(DatabaseConfig.h2(), dir, dir, m -> {
        }));
        back.init();
        assertEquals(5, back.maxId());
        assertEquals("Alice", back.get(5).name());
        back.close();
    }

    @Test
    void pollToggle() {
        assertEquals(1, PollBook.toggle(0, 0, false));
        assertEquals(2, PollBook.toggle(1, 1, false), "single choice replaces");
        assertEquals(0, PollBook.toggle(2, 1, false), "again: removed");
        assertEquals(5, PollBook.toggle(1, 2, true), "multiple choice adds");
    }
}
