package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public and private chats. Minecraft's chat holds one list of messages: the selected chat. The others are kept in
 * their own lists with unread counts; switching swaps the lists. Also private chat pages, read marks and the chat list.
 */
final class ChatChannels {
    private ChatChannels() {
    }

    static UUID channel; // null = public chat
    /** The server's own chat: everything that isn't from a player (command answers, joins, deaths, broadcasts), and
     * where commands are typed. */
    static final UUID SERVER = new UUID(0, 0x5E);
    static List<GuiMessage> commonStash = new ArrayList<>();
    static final Map<UUID, List<GuiMessage>> stash = new HashMap<>();
    /** Open private chat tabs and their names, oldest first. */
    static final java.util.LinkedHashMap<UUID, String> dmTabs = new java.util.LinkedHashMap<>();
    static final Map<UUID, Integer> dmUnread = new HashMap<>();
    static int commonUnread;
    static final Map<UUID, Integer> dmPeerRead = new HashMap<>(), dmReadSent = new HashMap<>(), dmOldest = new HashMap<>();
    static final Map<UUID, Boolean> dmMore = new HashMap<>();
    static final java.util.Set<UUID> dmLoaded = new java.util.HashSet<>(), dmLoading = new java.util.HashSet<>();

    static UUID channel() {
        return channel;
    }

    /** The chat the server named for the next message (Route); null = public. */
    private static UUID routeNext;
    /** Chat of each private message. */
    static final Map<GuiMessageTag, UUID> chanOf = new IdentityHashMap<>();

    static void onRoute(Packets.Route r) {
        routeNext = r.other();
    }

    static UUID takeRoute() {
        UUID r = routeNext;
        routeNext = null;
        return r;
    }

    static void post(ChatComponent chat, Component text, MessageSignature sig, GuiMessageTag tag) {
        postTo(chat, text, sig, tag, null);
    }

    /**
     * Adds a message of chat ch (null = public) to Minecraft's chat if it's the one shown, otherwise to its waiting
     * list as unread (with a notification and sound for private messages from others).
     */
    static void postTo(ChatComponent chat, Component text, MessageSignature sig, GuiMessageTag tag, UUID ch) {
        Minecraft mc = Minecraft.getInstance();
        if (ch != null && !ch.equals(SERVER)) {
            chanOf.put(tag, ch);
            if (!ch.equals(Ai.ID)) {
                dmTabs.putIfAbsent(ch, nameOf(ch));
            }
        }
        ChatModel.Msg m = ChatModel.msgs.get(tag);
        boolean mineMsg = m != null && ChatReplies.mine(m);
        if (java.util.Objects.equals(ch, channel)) {
            Compat.addToChat(chat, text, sig, tag);
        } else if (ch == null) {
            commonStash.add(0, Compat.message(mc.gui.getGuiTicks(), text, sig, tag));
            commonUnread++;
        } else {
            stash.computeIfAbsent(ch, k -> new ArrayList<>()).add(0, Compat.message(mc.gui.getGuiTicks(), text, sig, tag));
            if (!mineMsg) {
                dmUnread.merge(ch, 1, Integer::sum);
            }
        }
        if (ch != null && !ch.equals(SERVER) && !mineMsg && (!ch.equals(channel) || !(ChatMotion.chatOpen(mc)))) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME/*? if >=1.19.3 {*/.value()/*?}*/, 1.4f, 0.8f));
        }
    }

    /** Name from the tab list or the private chat tabs. */
    static String nameOf(UUID id) {
        var conn = Minecraft.getInstance().getConnection();
        var info = conn == null ? null : conn.getPlayerInfo(id);
        if (info != null) {
            return info.getProfile().getName();
        }
        String n = dmTabs.get(id);
        return n != null ? n : "?";
    }

    static boolean isDm(int mid) {
        GuiMessageTag t = ChatModel.byMid.get(mid);
        return t != null && chanOf.containsKey(t);
    }

    /** Shows the public chat (peer null) or the private chat with peer. */
    static void openChannel(UUID peer, String name) {
        Minecraft mc = Minecraft.getInstance();
        var me = mc.player;
        if (java.util.Objects.equals(peer, channel) || peer != null && me != null && peer.equals(me.getUUID())) {
            return;
        }
        // a command isn't a message, nor a message a command: the input is cleared going in or out of the server
        // chat (unless a "/" just brought us in)
        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen cs) {
            var input = ((com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor) cs).ingamemessenger$input();
            if (SERVER.equals(channel) || SERVER.equals(peer) && !input.getValue().startsWith("/")) {
                input.setValue("");
            }
        }
        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage> all = acc.ingamemessenger$allMessages();
        List<GuiMessage> cur = new ArrayList<>(all);
        if (channel == null) {
            commonStash = cur;
        } else {
            stash.put(channel, cur);
        }
        all.clear();
        if (peer == null) {
            all.addAll(commonStash);
            commonStash = new ArrayList<>();
            commonUnread = 0;
        } else {
            if (!peer.equals(Ai.ID) && !peer.equals(SERVER)) {
                dmTabs.putIfAbsent(peer, name);
            }
            List<GuiMessage> waiting = stash.remove(peer);
            if (waiting != null) {
                all.addAll(waiting);
            }
            dmUnread.remove(peer);
        }
        channel = peer;
        Net.toServer(
                new Packets.ChatTarget(peer == null || peer.equals(Ai.ID) || peer.equals(SERVER) ? new UUID(0, 0) : peer));
        chat.rescaleChat();
        acc.ingamemessenger$setScroll(0);
        ChatMotion.scrollPos = ChatMotion.scrollTarget = ChatMotion.scrollFrac = 0;
        if (Ai.ID.equals(peer)) {
            Ai.opened();
        } else if (peer != null && !peer.equals(SERVER) && dmLoaded.add(peer)) {
            dmLoading.add(peer);
            Net.toServer(new Packets.DmHistoryReq(peer, 0));
        }
    }

    /** Closes a private chat tab (its messages stay on the server). */
    static void closeTab(UUID peer) {
        if (peer.equals(channel)) {
            openChannel(null, null);
        }
        dmTabs.remove(peer);
        stash.remove(peer);
        dmLoaded.remove(peer);
        dmUnread.remove(peer);
    }

    static void resetChannels() {
        channel = null;
        routeNext = null;
        Ai.reset();
        commonStash = new ArrayList<>();
        stash.clear();
        dmTabs.clear();
        dmUnread.clear();
        commonUnread = 0;
        dmPeerRead.clear();
        dmReadSent.clear();
        dmOldest.clear();
        dmMore.clear();
        dmLoaded.clear();
        dmLoading.clear();
    }

    static void onDmShow(Packets.DmShow v) {
        takeRoute();
        var me = Minecraft.getInstance().player;
        if (me == null) {
            return;
        }
        UUID peer = v.from().equals(me.getUUID()) ? v.to() : v.from();
        dmTabs.putIfAbsent(peer, v.from().equals(me.getUUID()) ? v.toName() : v.fromName());
        ChatMessages.showText(Minecraft.getInstance().gui.getChat(), v.from(), v.fromName(), Component.literal(v.text()), System.currentTimeMillis(), null, peer,
                Protocol.print("dm:" + v.text()));
    }

    static void dmWantOlder() {
        UUID peer = channel;
        if (peer == null || dmLoading.contains(peer) || !dmMore.getOrDefault(peer, false) || !dmOldest.containsKey(peer)) {
            return;
        }
        dmLoading.add(peer);
        Net.toServer(new Packets.DmHistoryReq(peer, dmOldest.get(peer)));
    }

    static void onDmReadState(Packets.DmReadState r) {
        dmPeerRead.merge(r.reader(), r.upTo(), Math::max);
    }

    /** On join: our private chats (tabs for unread ones and the most recent few). */
    static void onDmChats(Packets.DmChats c) {
        int shown = 0;
        for (String line : c.data().split("\n")) {
            String[] f = line.split(" ");
            if (f.length != 5) {
                continue;
            }
            try {
                UUID id = UUID.fromString(f[0]);
                int unread = Integer.parseInt(f[3]);
                dmPeerRead.merge(id, Integer.parseInt(f[4]), Math::max);
                if (unread > 0 || shown < 3) {
                    dmTabs.putIfAbsent(id, f[1]);
                    shown++;
                }
                if (unread > 0) {
                    dmUnread.put(id, unread);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Private chat scrolled to the bottom: mark it read. */
    static void dmHaveRead() {
        ChatComponentAccessor acc = (ChatComponentAccessor) Minecraft.getInstance().gui.getChat();
        int newest = 0;
        for (GuiMessage gm : acc.ingamemessenger$allMessages()) {
            ChatModel.Msg m = gm.tag() == null ? null : ChatModel.msgs.get(gm.tag());
            if (m != null && m.mid() >= 0) {
                newest = Math.max(newest, m.mid());
            }
        }
        if (newest > dmReadSent.getOrDefault(channel, 0)) {
            dmReadSent.put(channel, newest);
            Net.toServer(new Packets.DmRead(channel, newest));
        }
    }
}
