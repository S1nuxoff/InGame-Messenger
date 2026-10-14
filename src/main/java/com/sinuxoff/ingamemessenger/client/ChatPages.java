package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.text.ChatText;
import com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** History pages: the latest on join, older ones when scrolling up, built like live messages. */
final class ChatPages {
    private ChatPages() {
    }

    private static int oldestMid = Integer.MAX_VALUE;
    static boolean hasOlder = true, loading;

    static void reset() {
        oldestMid = Integer.MAX_VALUE;
        hasOlder = true;
        loading = false;
    }

    static void startHistory() {
        oldestMid = Integer.MAX_VALUE;
        hasOlder = true;
        loading = true;
        Net.toServer(new Packets.HistoryRequest(0));
    }

    /** Scrolled to the top: request the previous page. */
    static void wantOlder() {
        if (ChatChannels.channel != null) {
            ChatChannels.dmWantOlder();
            return;
        }
        if (loading || !hasOlder || oldestMid == Integer.MAX_VALUE) {
            return;
        }
        loading = true;
        Net.toServer(new Packets.HistoryRequest(oldestMid));
    }

    /**
     * Builds a page (oldest first) like live messages and inserts it above the existing ones, skipping duplicates.
     * Already faded in, with reactions; the view doesn't jump.
     */
    static void onHistory(Packets.HistoryPage page) {
        UUID ch = ChatChannels.takeRoute();
        try {
            buildHistory(page, ch);
        } finally {
            if (ch == null) {
                ChatReplies.jumpGoesOn();
            }
        }
    }

    private static void buildHistory(Packets.HistoryPage page, UUID ch) {
        if (ch == null) {
            loading = false;
            hasOlder = page.more();
        } else {
            ChatChannels.dmLoading.remove(ch);
            ChatChannels.dmMore.put(ch, page.more());
        }
        Minecraft mc = Minecraft.getInstance();
        var conn = mc.getConnection();
        if (conn == null) {
            return;
        }
        ChatComponent chat = mc.gui.hud.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        boolean active = java.util.Objects.equals(ch, ChatChannels.channel);
        List<GuiMessage> all = active ? acc.ingamemessenger$allMessages() : ch == null ? ChatChannels.commonStash : ChatChannels.stash.computeIfAbsent(ch, k -> new ArrayList<>());
        java.util.Set<Integer> present = new java.util.HashSet<>();
        List<GuiMessage> known = new ArrayList<>(all);
        if (ch == null) { // its server lines may be in the server chat already
            known.addAll(ChatChannels.SERVER.equals(ChatChannels.channel) ? acc.ingamemessenger$allMessages()
                    : ChatChannels.stash.getOrDefault(ChatChannels.SERVER, List.of()));
        }
        for (GuiMessage gm : known) {
            ChatModel.Msg m = gm.tag() == null ? null : ChatModel.msgs.get(gm.tag());
            if (m != null && m.mid() >= 0) {
                present.add(m.mid());
            }
        }
        List<GuiMessage> built = new ArrayList<>(), serverBuilt = new ArrayList<>();
        long pageDay = Long.MIN_VALUE, serverDay = Long.MIN_VALUE;
        int tick = mc.gui.hud.getGuiTicks() - 400;
        UUID runSender = null;
        long runMs = 0;
        boolean runHasName = false;
        com.google.gson.JsonArray arr;
        try {
            arr = com.google.gson.JsonParser.parseString(page.json()).getAsJsonArray();
        } catch (RuntimeException e) {
            InGameMessenger.LOG.warn("Bad chat history page", e);
            return;
        }
        for (var el : arr) {
            try {
                com.google.gson.JsonObject o = el.getAsJsonObject();
                int id = o.get("i").getAsInt();
                if (ch == null) {
                    oldestMid = Math.min(oldestMid, id);
                } else {
                    ChatChannels.dmOldest.merge(ch, id, Math::min);
                }
                if (present.contains(id)) {
                    continue;
                }
                long t = o.get("t").getAsLong();
                String kind = o.get("k").getAsString();
                UUID who = o.has("u") ? UUID.fromString(o.get("u").getAsString()) : null;
                String name = o.has("n") ? o.get("n").getAsString() : null;
                // the public history's server lines go to the server chat, with their own dates, not breaking the
                // players' runs
                boolean toServer = ch == null && name == null;
                long keepDay = pageDay;
                UUID keepRun = runSender;
                long keepMs = runMs;
                boolean keepHasName = runHasName;
                if (toServer) {
                    pageDay = serverDay;
                }
                MutableComponent out = Component.empty();
                ChatModel.Msg m;
                boolean me = false;
                int rt = -1;
                String snip;
                ChatModel.pendingDay = Long.MIN_VALUE;
                ChatModel.pendingFull = null;
                ChatLocations.pendingLoc = null;
                ChatAlbums.pendingAlbum = null;
                ChatPolls.pendingPoll = null;
                long day = ChatDates.dayOf(t);
                if (day != pageDay) {
                    out.append(ChatDates.DATE_ROOM);
                    ChatModel.pendingDay = day;
                }
                pageDay = day;
                ChatModel.pendingEdited = o.has("ed");
                boolean same = who != null && who.equals(runSender) && t - runMs < 120_000;
                if (!same) {
                    runHasName = ch != null; // no names in private chats
                }
                runSender = who;
                runMs = t;
                if (kind.equals("t") && name != null) {
                    boolean named = !runHasName;
                    runHasName = true;
                    if (named) {
                        out.append(ChatDecor.nameLine(name)).append("\n");
                    }
                    Component body = com.sinuxoff.ingamemessenger.ServerCompat.fromJson(o.get("c").getAsString(), conn.registryAccess());
                    String plain = body.getString();
                    rt = ChatText.replyTarget(plain);
                    if (rt >= 0 && o.has("rd")) {
                        ChatReplies.deletedMids.add(rt);
                    }
                    if (rt >= 0) {
                        body = ChatDecor.dropStart(body, ChatText.replyMarkLength(plain));
                        out.append(ChatReplies.quoteLine(rt)).append("\n");
                    }
                    me = ChatDecor.mentionsMe(body, name) || rt >= 0 && ChatReplies.isMine(rt) && !name.equalsIgnoreCase(ChatDecor.myName());
                    snip = ChatDecor.snippet(body.getString());
                    ChatText.Location loc = ChatText.parseLocation(body.getString());
                    if (loc != null) {
                        snip = ChatLocations.locWord();
                        ChatModel.pendingFull = loc.coords();
                        ChatLocations.pendingLoc = loc;
                        out.append(" \n \n ");
                    } else {
                        ChatModel.pendingFull = body.getString();
                        out.append(ChatDecor.highlightMentions(body)).append("\n ");
                    }
                    m = new ChatModel.Msg(who, name, named, null, null, 0, true, t, id, null);
                } else if (kind.equals("p") && name != null) {
                    ChatPolls.PollInfo pi = ChatPolls.parsePoll(id, o.get("c").getAsString());
                    if (o.has("pc")) {
                        pi.counts(o.get("pc").getAsString());
                    }
                    pi.mine = o.has("pm") ? o.get("pm").getAsInt() : 0;
                    boolean named = !runHasName;
                    runHasName = true;
                    if (named) {
                        out.append(ChatDecor.nameLine(name)).append("\n");
                    }
                    rt = o.has("rt") ? o.get("rt").getAsInt() : -1;
                    if (rt >= 0 && o.has("rd")) {
                        ChatReplies.deletedMids.add(rt);
                    }
                    if (rt >= 0) {
                        out.append(ChatReplies.quoteLine(rt)).append("\n");
                    }
                    ChatPolls.appendPoll(out, pi);
                    snip = ChatPolls.pollWord(pi);
                    ChatPolls.pendingPoll = pi;
                    m = new ChatModel.Msg(who, name, named, null, null, 0, true, t, id, null);
                } else if (kind.equals("i") && name != null) {
                    com.google.gson.JsonObject im = com.google.gson.JsonParser.parseString(o.get("c").getAsString()).getAsJsonObject();
                    List<ChatAlbums.Pic> pics = im.has("ref")
                            ? List.of(new ChatAlbums.Pic(im.get("ref").getAsString(), im.get("w").getAsInt(), im.get("h").getAsInt()))
                            : ChatAlbums.parsePics(im.get("data").getAsString());
                    boolean named = !runHasName;
                    runHasName = true;
                    if (named) {
                        out.append(ChatDecor.nameLine(name)).append("\n");
                    }
                    rt = o.has("rt") ? o.get("rt").getAsInt() : -1;
                    if (rt >= 0 && o.has("rd")) {
                        ChatReplies.deletedMids.add(rt);
                    }
                    if (rt >= 0) {
                        out.append(ChatReplies.quoteLine(rt)).append("\n");
                    }
                    ChatAlbums.AlbumInfo ai = ChatAlbums.album(pics, im.has("text") ? im.get("text").getAsString() : "");
                    ChatAlbums.appendAlbum(out, ai);
                    snip = ChatAlbums.albumWord(ai);
                    ChatModel.pendingFull = ai.caption().isEmpty() ? null : ai.caption();
                    ChatAlbums.pendingAlbum = ai;
                    m = new ChatModel.Msg(who, name, named, null, null, 0, true, t, id, null);
                } else if (kind.equals("v") && name != null) {
                    com.google.gson.JsonObject v = com.google.gson.JsonParser.parseString(o.get("c").getAsString()).getAsJsonObject();
                    boolean named = !runHasName;
                    runHasName = true;
                    if (named) {
                        out.append(ChatDecor.nameLine(name)).append("\n");
                    }
                    rt = o.has("rt") ? o.get("rt").getAsInt() : -1;
                    if (rt >= 0 && o.has("rd")) {
                        ChatReplies.deletedMids.add(rt);
                    }
                    if (rt >= 0) {
                        out.append(ChatReplies.quoteLine(rt)).append("\n");
                    }
                    out.append(" \n \n ");
                    snip = ChatVoiceNotes.voiceWord();
                    m = new ChatModel.Msg(who, name, named, null, null, 0, true, t, id, null);
                    ChatVoiceNotes.pendingVoice = new ChatVoiceNotes.VoiceInfo(v.get("key").getAsString(), v.get("ms").getAsInt(),
                            java.util.HexFormat.of().parseHex(v.get("wave").getAsString()));
                } else if ((kind.equals("s") || kind.equals("i")) && name != null) { // sticker or single picture
                    if (Prefs.isMuted(who)) {
                        continue;
                    }
                    String pack, sid;
                    if (kind.equals("i")) {
                        com.google.gson.JsonObject im = com.google.gson.JsonParser.parseString(o.get("c").getAsString()).getAsJsonObject();
                        pack = Library.IMG;
                        sid = im.get("ref").getAsString();
                        Library.image(sid, im.get("w").getAsInt(), im.get("h").getAsInt());
                    } else {
                        pack = o.get("p").getAsString();
                        sid = o.get("s").getAsString();
                    }
                    int lines = ChatMessages.stickerLines(Library.get(pack, sid));
                    rt = o.has("rt") ? o.get("rt").getAsInt() : -1;
                    if (rt >= 0 && o.has("rd")) {
                        ChatReplies.deletedMids.add(rt);
                    }
                    if (rt >= 0) {
                        out.append(ChatReplies.quoteLine(rt)).append("\n");
                    }
                    snip = kind.equals("i") ? ChatAlbums.imageWord() : ChatMessages.stickerWord();
                    out.append(" ");
                    for (int i = 1; i < lines; i++) {
                        out.append("\n ");
                    }
                    out.append("\n ");
                    m = new ChatModel.Msg(who, name, false, pack, sid, lines, true, t, id, null);
                } else {
                    runSender = null;
                    Component content = com.sinuxoff.ingamemessenger.ServerCompat.fromJson(o.get("c").getAsString(), conn.registryAccess());
                    // we may have got it live before the messenger was on (our own join line), without its id: this
                    // one takes its place
                    String text = content.getString().strip();
                    all.removeIf(gm -> (gm.tag() == null || ChatModel.msgs.get(gm.tag()) == null) && gm.content().getString().strip().equals(text));
                    snip = ChatDecor.snippet(content.getString());
                    out.append(content).append("\n ");
                    m = new ChatModel.Msg(null, null, false, null, null, 0, true, t, id, ChatModel.systemIcon(content));
                }
                List<ChatModel.Reaction> rs = ChatModel.parseReactions(o.has("r") ? o.get("r").getAsString() : null);
                if (!rs.isEmpty()) {
                    ChatModel.reactions.put(id, rs);
                }
                GuiMessageTag ht = ChatModel.remember(m);
                if (ch != null) {
                    ChatChannels.chanOf.put(ht, ch);
                }
                if (ChatVoiceNotes.pendingVoice != null) {
                    ChatVoiceNotes.voiceOf.put(ht, ChatVoiceNotes.pendingVoice);
                    ChatVoiceNotes.pendingVoice = null;
                }
                if (ChatLocations.pendingLoc != null) {
                    ChatLocations.locOf.put(ht, ChatLocations.pendingLoc);
                    ChatLocations.pendingLoc = null;
                }
                if (ChatAlbums.pendingAlbum != null) {
                    ChatAlbums.albumOf.put(ht, ChatAlbums.pendingAlbum);
                    ChatAlbums.pendingAlbum = null;
                }
                if (ChatPolls.pendingPoll != null) {
                    ChatPolls.pollOf.put(ht, ChatPolls.pendingPoll);
                    ChatPolls.pendingPoll = null;
                }
                ChatModel.note(ht, m.player() ? m.name() : null, snip, rt);
                if (me) {
                    ChatModel.mentioned.add(ht);
                }
                (toServer ? serverBuilt : built).add(Compat.message(tick, out, null, ht));
                if (toServer) {
                    serverDay = pageDay;
                    pageDay = keepDay;
                    runSender = keepRun;
                    runMs = keepMs;
                    runHasName = keepHasName;
                }
            } catch (RuntimeException e) {
                InGameMessenger.LOG.warn("Bad chat history entry: {}", e.toString());
            }
        }
        if (!serverBuilt.isEmpty()) {
            boolean shown = ChatChannels.SERVER.equals(ChatChannels.channel);
            merge(shown ? acc.ingamemessenger$allMessages() : ChatChannels.stash.computeIfAbsent(ChatChannels.SERVER, k -> new ArrayList<>()),
                    serverBuilt);
            if (shown) {
                chat.rescaleChat();
            }
        }
        if (built.isEmpty()) {
            return;
        }
        merge(all, built);
        if (!active) {
            return;
        }
        int scroll = acc.ingamemessenger$scroll();
        chat.rescaleChat();
        acc.ingamemessenger$setScroll(Math.max(0, Math.min(scroll, acc.ingamemessenger$lines().size() - 1)));
    }

    /** Puts a history page (oldest first) below the messages a chat already has. */
    private static void merge(List<GuiMessage> all, List<GuiMessage> built) {
        // no repeated date separator if the next message is on the same day
        if (!all.isEmpty()) {
            GuiMessage after = all.get(all.size() - 1);
            ChatModel.Msg newestBuilt = ChatModel.msgs.get(built.get(built.size() - 1).tag());
            Long d = ChatModel.datedOf.get(after.tag());
            if (d != null && newestBuilt != null && d == ChatDates.dayOf(newestBuilt.createdMs())) {
                ChatModel.hiddenDate.add(after.tag());
            }
        }
        for (int i = built.size() - 1; i >= 0; i--) { // the chat stores newest first
            all.add(built.get(i));
        }
        while (all.size() > ChatModel.MAX_KEPT) {
            all.remove(all.size() - 1);
        }
    }
}
