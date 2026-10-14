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

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replies, edits and deletions: the quote above a reply, rebuilding an edited message, removing a deleted one
 * (replies to it then quote "deleted"), and jumping to a quoted message.
 */
final class ChatReplies {
    private ChatReplies() {
    }

    // A reply is sent as "↩<id> text" (stickers carry the id as a field). The prefix becomes a quote above the message:
    // the replied message's author and start, on a light strip with a colored bar.

    /** Per message: author and start (for quoting), and what it replies to. */
    static final Map<GuiMessageTag, String[]> infoOf = new IdentityHashMap<>();
    static final Map<GuiMessageTag, Integer> replyOf = new IdentityHashMap<>();
    /** Message just jumped to (highlighted briefly). */
    static int flashMid = -1;
    static long flashMs;
    // Edit and delete

    private static ChatModel.Msg msgOf(int mid) {
        GuiMessageTag tag = ChatModel.byMid.get(mid);
        return tag == null ? null : ChatModel.msgs.get(tag);
    }

    static boolean mine(ChatModel.Msg m) {
        return m != null && m.player() && m.name() != null && m.name().equalsIgnoreCase(ChatDecor.myName());
    }

    /** Full text of a text message, or null. */
    static String fullText(int mid) {
        GuiMessageTag tag = ChatModel.byMid.get(mid);
        return tag == null ? null : ChatModel.fullTextOf.get(tag);
    }

    /** Our own text message (editable). */
    static boolean isMineText(int mid) {
        ChatModel.Msg m = msgOf(mid);
        return mine(m) && !m.sticker() && fullText(mid) != null && !ChatLocations.locOf.containsKey(ChatModel.byMid.get(mid)) && !ChatAlbums.albumOf.containsKey(ChatModel.byMid.get(mid));
    }

    static boolean isMineAny(int mid) {
        return mine(msgOf(mid));
    }

    /** Id of the replied message, -1 if none. */
    static int replyOfMid(int mid) {
        GuiMessageTag tag = ChatModel.byMid.get(mid);
        Integer r = tag == null ? null : replyOf.get(tag);
        return r == null ? -1 : r;
    }

    static void deleteMine(int mid) {
        Net.toServer(new Packets.DeleteMsg(mid));
    }

    /** Rebuilds an edited message (name, quote and date kept) with "edited" next to the time. */
    static void onEdited(Packets.Edited e) {
        GuiMessageTag tag = ChatModel.byMid.get(e.id());
        ChatModel.Msg m = tag == null ? null : ChatModel.msgs.get(tag);
        if (m == null || m.sticker()) {
            return;
        }
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage> all = acc.ingamemessenger$allMessages();
        for (int i = 0; i < all.size(); i++) {
            GuiMessage old = all.get(i);
            if (old.tag() != tag) {
                continue;
            }
            MutableComponent out = Component.empty();
            if (ChatModel.datedOf.containsKey(tag)) {
                out.append(ChatDates.DATE_ROOM);
            }
            if (m.named()) {
                out.append(ChatDecor.nameLine(m.name())).append("\n");
            }
            String plain = e.text();
            int rt = ChatText.replyTarget(plain);
            String body = rt >= 0 ? plain.substring(ChatText.replyMarkLength(plain)) : plain;
            if (rt >= 0) {
                out.append(quoteLine(rt)).append("\n");
            }
            out.append(ChatDecor.highlightMentions(Component.literal(body))).append("\n ");
            all.set(i, Compat.withText(old, out, tag));
            ChatModel.editedSet.add(tag);
            ChatModel.fullTextOf.put(tag, body);
            infoOf.put(tag, new String[]{m.name(), ChatDecor.snippet(body)});
            relayout(chat);
            return;
        }
    }

    /** Deleted message ids (replies to them quote "deleted"). */
    static final java.util.Set<Integer> deletedMids = new java.util.HashSet<>();

    /** Removes a deleted message; replies to it now quote "deleted". */
    static void onDeleted(Packets.Deleted d) {
        String oldQuote = quoteLine(d.id()).getString();
        deletedMids.add(d.id());
        Component newQuote = quoteLine(d.id());
        GuiMessageTag tag = ChatModel.byMid.get(d.id());
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        List<GuiMessage> all = ((ChatComponentAccessor) chat).ingamemessenger$allMessages();
        boolean changed = false;
        for (int i = 0; i < all.size(); i++) {
            GuiMessage gm = all.get(i);
            Integer r = gm.tag() == null ? null : replyOf.get(gm.tag());
            if (r == null || r != d.id()) {
                continue;
            }
            MutableComponent out = Component.empty();
            boolean swapped = false;
            for (Component part : gm.content().getSiblings()) {
                if (!swapped && part.getString().equals(oldQuote)) {
                    out.append(newQuote);
                    swapped = true;
                } else {
                    out.append(part);
                }
            }
            if (swapped) {
                all.set(i, Compat.withText(gm, out));
                changed = true;
            }
        }
        if (tag == null) {
            if (changed) {
                relayout(chat);
            }
            return;
        }
        all.removeIf(gm -> gm.tag() == tag);
        ChatModel.reactions.remove(d.id());
        relayout(chat);
    }

    /** Relayout after a change, keeping the scroll position. */
    private static void relayout(ChatComponent chat) {
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        int scroll = acc.ingamemessenger$scroll();
        chat.rescaleChat();
        acc.ingamemessenger$setScroll(Math.max(0, Math.min(scroll, Math.max(0, acc.ingamemessenger$lines().size() - 1))));
    }

    /** {author or null for system, text} of message mid, or null if not loaded. */
    static String[] replyInfo(int mid) {
        GuiMessageTag tag = ChatModel.byMid.get(mid);
        return tag == null ? null : infoOf.get(tag);
    }

    static boolean isMine(int mid) {
        String[] i = replyInfo(mid);
        return i != null && i[0] != null && i[0].equalsIgnoreCase(ChatDecor.myName());
    }

    /** Quote line: the author (in their color) and the start of the message, cut to fit. */
    static Component quoteLine(int target) {
        Minecraft mc = Minecraft.getInstance();
        if (deletedMids.contains(target)) {
            return Component.literal("  ").append(Component.translatable("ingamemessenger.reply.deleted").withStyle(s -> s.withColor(0x8E95A6).withItalic(true)));
        }
        String[] i = replyInfo(target);
        String who = i == null ? Component.translatable("ingamemessenger.reply.unknown").getString()
                : i[0] == null ? Component.translatable("ingamemessenger.reply.server").getString() : i[0];
        int room = (int) (Compat.chatWidth(mc.gui.hud.getChat()) / Math.max(0.1, Compat.chatScale(mc.gui.hud.getChat()))) - ChatRenderer.AV - 40;
        MutableComponent name = Component.literal(who).withStyle(s -> s.withColor(i != null && i[0] != null ? ChatDecor.nameColor(who) : 0xC8CCD8));
        int left = room - mc.font.width(name) - 14;
        String text = i == null ? "" : i[1];
        String cut = mc.font.plainSubstrByWidth(text, Math.max(0, left));
        if (cut.length() < text.length()) {
            cut = mc.font.plainSubstrByWidth(text, Math.max(0, left - mc.font.width("…"))) + "…";
        }
        return Component.literal("  ").append(name).append(Component.literal("  " + cut).withStyle(s -> s.withColor(0x9AA1B2)));
    }

    /** Someone replied to us: a bell and the name above the hotbar. */
    static void notifyReply(String who) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL/*? if >=1.19.3 {*/.value()/*?}*/, 1.2f, 0.9f));
        mc.gui.hud.setOverlayMessage(Component.translatable("ingamemessenger.replied", who).withStyle(s -> s.withColor(ChatDecor.MENTION_ME)), false);
    }

    /** Message to jump to once loaded (older pages are requested until it is), or -1. */
    private static int pendingJump = -1, jumpPages;

    /** Scrolls to message mid and highlights it, loading older pages if needed. */
    static void jumpTo(int mid) {
        ChatChannels.openChannel(null, null); // search results are in the public chat
        if (ChatModel.byMid.containsKey(mid)) {
            pendingJump = -1;
            scrollTo(mid);
            return;
        }
        pendingJump = mid;
        jumpPages = 0;
        ChatPages.wantOlder();
    }

    /** After a page loads: jump if the message is there, otherwise request the next page (at most 60). */
    static void jumpGoesOn() {
        if (pendingJump < 0) {
            return;
        }
        if (ChatModel.byMid.containsKey(pendingJump)) {
            int m = pendingJump;
            pendingJump = -1;
            scrollTo(m);
        } else if (ChatPages.hasOlder && ++jumpPages < 60) {
            ChatPages.wantOlder();
        } else {
            pendingJump = -1;
        }
    }

    static void scrollTo(int mid) {
        GuiMessageTag tag = ChatModel.byMid.get(mid);
        if (tag == null) {
            return;
        }
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = acc.ingamemessenger$lines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).tag() == tag) {
                int per = chat.getLinesPerPage();
                acc.ingamemessenger$setScroll(Math.max(0, Math.min(i - per / 3, lines.size() - per)));
                flashMid = mid;
                flashMs = System.currentTimeMillis();
                return;
            }
        }
    }
}
