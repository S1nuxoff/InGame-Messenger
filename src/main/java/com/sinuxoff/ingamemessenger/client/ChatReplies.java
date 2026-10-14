package com.sinuxoff.ingamemessenger.client;

import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Proto;
import com.sinuxoff.ingamemessenger.core.text.ChatText;

/** Questions about a message by its id: its text, whether it's ours, what a reply to it quotes. */
final class ChatReplies {
    private ChatReplies() {
    }

    /** The text of a text message (for copying and editing), null for other kinds. */
    static String fullText(long id) {
        Messenger.Entry e = Messenger.byId(id);
        return e != null && "text".equals(e.kind) ? Messenger.body(e) : null;
    }

    /** Our own text message (editable). */
    static boolean isMineText(long id) {
        Messenger.Entry e = Messenger.byId(id);
        return e != null && e.mine() && "text".equals(e.kind) && ChatText.parseLocation(e.text()) == null;
    }

    static boolean isMineAny(long id) {
        Messenger.Entry e = Messenger.byId(id);
        return e != null && e.mine();
    }

    static void deleteMine(long id) {
        Net.toServer(new Proto.Delete(id));
    }

    /** {author (null for the server), start of the message}, or null if it isn't loaded. */
    static String[] replyInfo(long id) {
        Messenger.Entry e = Messenger.byId(id);
        if (e == null) {
            return null;
        }
        String what = "text".equals(e.kind) ? Messenger.body(e) : e.raw != null ? e.raw.getString() : "[" + e.kind + "]";
        return new String[]{e.name, ChatDecor.snippet(what)};
    }
}
