package com.sinuxoff.ingamemessenger.core.server;

import com.sinuxoff.ingamemessenger.core.protocol.Packets.*;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.storage.ChatStore;
import com.sinuxoff.ingamemessenger.core.text.ChatText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/** Private messages: regular messages marked with the pair, visible only to the two players. */
final class Dms {
    private final ChatServer server;
    private final Map<UUID, Long> lastSent = new HashMap<>();

    Dms(ChatServer server) {
        this.server = server;
    }

    void send(UUID from, String fromName, DmSend s) {
        String text = s.text().strip();
        long now = System.currentTimeMillis();
        synchronized (this) {
            Long last = lastSent.get(from);
            if (!ChatText.isPlainText(text, ChatServer.MAX_TEXT) || s.to().equals(from) || last != null && now - last < 300) {
                return;
            }
        }
        String toName = server.platform.nameOf(s.to());
        if (toName == null) {
            return;
        }
        synchronized (this) {
            lastSent.put(from, now);
        }
        deliver(from, fromName, s.to(), toName, text);
    }

    /** Also used by the test bots. */
    void deliver(UUID from, String fromName, UUID to, String toName, String text) {
        String pair = ChatStore.pair(from, to);
        int id = server.tag(from, Protocol.print("dm:" + text), pair);
        long now = System.currentTimeMillis();
        server.store(new ChatStore.Message(id, now, "t", from, fromName, ChatServer.textJson(text), null, null, -1, pair, false));
        server.db("private read mark", () -> server.storage.chat.markDmRead(from, to, id));
        server.platform.info("<" + fromName + " -> " + toName + "> " + text);
        server.sendTo(pair, new DmShow(id, now, from, fromName, to, toName, text), true);
        server.listener().privateMessage(from, fromName, to, toName, text);
    }

    void history(UUID who, DmHistoryReq r) {
        server.db("private history page", () -> {
            ChatStore.Page page = server.storage.chat.page(r.before(), who, ChatStore.pair(who, r.other()));
            server.platform.execute(() -> {
                server.platform.send(who, new Route(r.other()));
                server.platform.send(who, new HistoryPage(page.more(), page.json()));
            });
        });
    }

    /** Marks the chat as read and tells the other side (for the read checks). */
    void read(UUID reader, UUID other, int upTo) {
        server.db("private read mark", () -> {
            int newest = server.storage.chat.newestInPair(ChatStore.pair(reader, other));
            int to = Math.min(upTo, newest);
            if (newest > 0 && server.storage.chat.markDmRead(reader, other, to)) {
                server.platform.execute(() -> server.platform.send(other, new DmReadState(reader, to)));
            }
        });
    }

    /** Sends the player their private chats, one "uuid name newestId unread peerReadUpTo" per line. */
    void joined(UUID who) {
        server.db("private chats", () -> {
            List<ChatStore.DmChat> chats = server.storage.chat.dmChats(who);
            server.platform.execute(() -> {
                StringJoiner out = new StringJoiner("\n");
                for (ChatStore.DmChat c : chats) {
                    String name = server.platform.nameOf(c.other());
                    if (name != null) {
                        out.add(c.other() + " " + name + " " + c.newest() + " " + c.unread() + " " + c.peerReadUpTo());
                    }
                }
                server.platform.send(who, new DmChats(out.toString()));
            });
        });
    }
}
