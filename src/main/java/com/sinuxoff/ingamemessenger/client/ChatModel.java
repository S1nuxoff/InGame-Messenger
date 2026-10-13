package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.server.ChatServer;
import com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side message model: each chat line's tag mapped to its message (sender, sticker, server id...), server ids
 * matched to messages, reactions, and per-message extras (quote, date, full text, picture, poll, voice, location).
 */
final class ChatModel {
    private ChatModel() {
    }

    /** Face used for messages not about a player (server, mods). */
    private static final ItemStack SYSTEM_ICON = new ItemStack(Items.COMMAND_BLOCK);
    /** Messages that mention us (tinted bubble). */
    static final java.util.Set<GuiMessageTag> mentioned = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** Shown in place of a face: a player head (by id or name) or an item. */
    record Icon(UUID face, String name, ItemStack item) {
    }

    /**
     * A rebuilt message. who null = not a player; named = shows the name line; sticker = pack, id and height in lines;
     * spacer = ends with the empty line; mid = server id (-1 = none, no reactions); icon for non-player messages.
     */
    record Msg(UUID sender, String name, boolean named, String pack, String id, int lines, boolean spacer, long createdMs,
               int mid, Icon icon) {
        boolean sticker() {
            return pack != null;
        }

        boolean player() {
            return name != null && icon == null;
        }

        Msg withMid(int n) {
            return new Msg(sender, name, named, pack, id, lines, spacer, createdMs, n, icon);
        }
    }

    record Reaction(String emoji, List<String> names) {
        boolean mine() {
            var p = Minecraft.getInstance().player;
            return p != null && names.contains(p.getGameProfile().getName());
        }
    }

    static final Map<GuiMessageTag, Msg> msgs = new IdentityHashMap<>();
    private static final Deque<GuiMessageTag> order = new ArrayDeque<>();
    /** By server id: messages, and reactions (only messages that have some). */
    static final Map<Integer, GuiMessageTag> byMid = new HashMap<>();
    static final Map<Integer, List<Reaction>> reactions = new HashMap<>();

    /** Server ids that arrived before their messages: (id, sender, hash, time). */
    private record Pending(int id, UUID sender, int hash, long ms) {
    }

    private static final Deque<Pending> pending = new ArrayDeque<>();

    /** Messages that arrived before their ids: (tag, sender, hash, time). */
    private record Waiting(GuiMessageTag tag, UUID sender, int hash, long ms) {
    }

    private static final Deque<Waiting> waiting = new ArrayDeque<>();

    private static boolean sameMessage(UUID s1, int h1, UUID s2, int h2) {
        return s1 != null && s1.equals(s2) && (h1 == h2 || h1 == 0 || h2 == 0);
    }

    /** Last player message: sender and time, to group consecutive messages. */
    static UUID lastSender;
    private static long lastMs;
    /** Whether the current group already showed the name (stickers don't; the next text does). */
    private static boolean runNamed;

    /** Lines the chat keeps (vanilla keeps 100). */
    public static final int MAX_KEPT = 3000, MAX_LINES = 30000;

    static GuiMessageTag remember(Msg m) {
        GuiMessageTag tag = new GuiMessageTag(0x000000, null, null, m.sticker() ? "Sticker" : "Chat");
        msgs.put(tag, m);
        order.addLast(tag);
        if (m.mid() >= 0) {
            byMid.put(m.mid(), tag);
        }
        while (order.size() > MAX_KEPT) {
            Msg old = msgs.remove(order.removeFirst());
            if (old != null && old.mid() >= 0) {
                byMid.remove(old.mid());
                reactions.remove(old.mid());
            }
        }
        return tag;
    }

    // Server ids and reactions

    /** Assigns an id to a waiting message, or keeps it for the message still on its way. */
    static void onTag(Packets.Tag t) {
        long now = System.currentTimeMillis();
        waiting.removeIf(w -> now - w.ms() > (ChatServer.SYSTEM.equals(w.sender()) ? 1500 : 5000));
        for (Iterator<Waiting> it = waiting.iterator(); it.hasNext(); ) {
            Waiting w = it.next();
            if (sameMessage(w.sender(), w.hash(), t.sender(), t.hash())) {
                it.remove();
                Msg m = msgs.get(w.tag());
                if (m != null) {
                    msgs.put(w.tag(), m.withMid(t.id()));
                    byMid.put(t.id(), w.tag());
                }
                return;
            }
        }
        pending.addLast(new Pending(t.id(), t.sender(), t.hash(), now));
        while (pending.size() > 32) {
            pending.removeFirst();
        }
    }

    /** The id that arrived for this message, -1 if none yet. Old entries are dropped. */
    static int takeTag(UUID sender, int hash) {
        long now = System.currentTimeMillis();
        pending.removeIf(p -> now - p.ms() > 5000);
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (sameMessage(sender, hash, p.sender(), p.hash())) {
                it.remove();
                return p.id();
            }
        }
        return -1;
    }

    /** Registers a message and waits for its id if it hasn't arrived. */
    static GuiMessageTag rememberNumbered(Msg m, UUID sender, int hash) {
        GuiMessageTag tag = remember(m);
        if (m.mid() < 0 && sender != null) {
            waiting.addLast(new Waiting(tag, sender, hash, System.currentTimeMillis()));
            while (waiting.size() > 32) {
                waiting.removeFirst();
            }
        }
        return tag;
    }

    static void onReactions(Packets.Reactions r) {
        if (!byMid.containsKey(r.id())) {
            return;
        }
        boolean had = reactions.containsKey(r.id());
        List<Reaction> list = parseReactions(r.data());
        if (list.isEmpty()) {
            reactions.remove(r.id());
        } else {
            reactions.put(r.id(), list);
        }
        if (had != !list.isEmpty()) { // the reaction row appears or disappears: relayout, keeping the scroll
            ChatComponent chat = Minecraft.getInstance().gui.getChat();
            ChatComponentAccessor acc = (ChatComponentAccessor) chat;
            int scroll = acc.ingamemessenger$scroll();
            chat.rescaleChat();
            acc.ingamemessenger$setScroll(Math.max(0, Math.min(scroll, acc.ingamemessenger$lines().size() - 1)));
        }
    }

    static List<Reaction> parseReactions(String data) {
        List<Reaction> list = new ArrayList<>();
        if (data != null && !data.isEmpty()) {
            for (String part : data.split(";")) {
                int c = part.indexOf(':');
                if (c > 0) {
                    list.add(new Reaction(part.substring(0, c), Arrays.asList(part.substring(c + 1).split(","))));
                }
            }
        }
        return list;
    }

    /** True if the message has a reaction row (one more line when wrapping). */
    public static boolean reactionRow(GuiMessageTag tag) {
        Msg m = tag == null ? null : msgs.get(tag);
        return m != null && m.mid() >= 0 && reactions.containsKey(m.mid());
    }

    static void react(int mid, String emoji) {
        Prefs.usedReaction(emoji);
        Net.toServer(new Packets.React(mid, emoji));
    }

    // Building messages

    /** Shows the name on the first text of a group (one sender, at most 2 minutes apart). */
    static boolean named(UUID sender, boolean sticker) {
        long now = System.currentTimeMillis();
        boolean same = sender != null && sender.equals(lastSender) && now - lastMs < 120_000;
        lastSender = sender;
        lastMs = now;
        if (!same) {
            runNamed = false;
        }
        if (sticker || runNamed) {
            return false;
        }
        runNamed = true;
        return true;
    }

    /** The player a name refers to (from its hover, else the tab list), or null. */
    static UUID uuidOf(Component name) {
        HoverEvent hover = name.getStyle().getHoverEvent();
        if (hover != null) {
            return com.sinuxoff.ingamemessenger.ServerCompat.hoverPlayer(hover);
        }
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo p = conn == null ? null : conn.getPlayerInfo(name.getString());
        return p == null ? null : p.getProfile().getId();
    }

    /** Icon for a non-player message: the advancement's item, the player it's about, or a command block. */
    static Icon systemIcon(Component content) {
        if (content.getContents() instanceof TranslatableContents tc && tc.getArgs().length >= 1 && tc.getArgs()[0] instanceof Component who) {
            if (tc.getKey().startsWith("chat.type.advancement.") && tc.getArgs().length >= 2 && tc.getArgs()[1] instanceof Component adv) {
                ItemStack item = advancementIcon(adv.getString());
                if (item != null) {
                    return new Icon(null, null, item);
                }
            }
            UUID id = uuidOf(who);
            if (id != null) {
                return new Icon(id, who.getString(), null);
            }
        }
        return new Icon(null, null, SYSTEM_ICON);
    }

    /** Item of the advancement with this title ("[Stone Age]"), if the client knows it. */
    private static ItemStack advancementIcon(String title) {
        String t = title.startsWith("[") && title.endsWith("]") ? title.substring(1, title.length() - 1) : title;
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            return null;
        }
        for (var display : Compat.advancements(conn)) {
            if (Compat.title(display).getString().equals(t)) {
                return Compat.icon(display);
            }
        }
        return null;
    }

    static void note(GuiMessageTag tag, String name, String snippet, int replyTo) {
        ChatReplies.infoOf.put(tag, new String[]{name, snippet});
        if (replyTo >= 0) {
            ChatReplies.replyOf.put(tag, replyTo);
        }
        if (pendingDay != Long.MIN_VALUE) {
            datedOf.put(tag, pendingDay);
            pendingDay = Long.MIN_VALUE;
        }
        if (pendingFull != null) {
            fullTextOf.put(tag, pendingFull);
            pendingFull = null;
        }
        if (pendingEdited) {
            editedSet.add(tag);
            pendingEdited = false;
        }
        Msg m = msgs.get(tag);
        if (name != null && m != null) {
            lastSeenAt.merge(name.toLowerCase(), m.createdMs(), Math::max);
        }
    }

    // Dates, own messages on the right, edits and deletions

    /** Messages starting a new day (day number), and those whose date separator is hidden. */
    static final Map<GuiMessageTag, Long> datedOf = new IdentityHashMap<>();
    static final java.util.Set<GuiMessageTag> hiddenDate = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** Full text of a text message (emoji as codes, no reply prefix), for copying and editing. */
    static final Map<GuiMessageTag, String> fullTextOf = new IdentityHashMap<>();
    static final java.util.Set<GuiMessageTag> editedSet = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** Last message time per player (lower-case name). */
    static final Map<String, Long> lastSeenAt = new HashMap<>();
    /** Text x of each message this frame (after the face, or in a right-side bubble). */
    static final Map<GuiMessageTag, Integer> textXOf = new IdentityHashMap<>();
    static long pendingDay = Long.MIN_VALUE, lastLiveDay = Long.MIN_VALUE;
    static String pendingFull;
    static boolean pendingEdited;

    /** Another server: its message ids mean other messages. */
    static void reset() {
        mentioned.clear();
        msgs.clear();
        order.clear();
        byMid.clear();
        reactions.clear();
        pending.clear();
        waiting.clear();
        lastSender = null;
        lastMs = 0;
        runNamed = false;
        datedOf.clear();
        hiddenDate.clear();
        fullTextOf.clear();
        editedSet.clear();
        lastSeenAt.clear();
        textXOf.clear();
        pendingDay = lastLiveDay = Long.MIN_VALUE;
        pendingFull = null;
        pendingEdited = false;
        ChatAlbums.albumOf.clear();
        ChatPolls.pollOf.clear();
        ChatVoiceNotes.voiceOf.clear();
        ChatLocations.locOf.clear();
        ChatReplies.infoOf.clear();
        ChatReplies.replyOf.clear();
        ChatReplies.deletedMids.clear();
        ChatReplies.flashMid = -1;
    }

    /** True if the line's text is shifted right of the face. */
    public static boolean shifted(GuiMessageTag tag) {
        return tag != null && msgs.containsKey(tag);
    }
}
