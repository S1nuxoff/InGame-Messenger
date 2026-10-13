package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.server.ChatServer;
import com.sinuxoff.ingamemessenger.core.text.ChatText;
import com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor;

import java.util.UUID;

/** Incoming live messages: player text and server lines rebuilt in the messenger look, stickers, the Gemini chat. */
final class ChatMessages {
    private ChatMessages() {
    }

    /**
     * Rebuilds an incoming message: "<name> text" gets the name above and the face beside, anything else an icon,
     * followed by the spacer line. True if rebuilt (the original must then be dropped).
     */
    public static boolean transform(ChatComponent chat, Component content, MessageSignature sig, GuiMessageTag tag) {
        if (tag != null && ChatModel.msgs.containsKey(tag)) {
            return false; // already ours
        }
        long now = System.currentTimeMillis();
        if (content.getContents() instanceof TranslatableContents tc && "chat.type.text".equals(tc.getKey()) && tc.getArgs().length >= 2) {
            Object a0 = tc.getArgs()[0], a1 = tc.getArgs()[1];
            Component nameC = a0 instanceof Component c ? c : Component.literal(String.valueOf(a0));
            Component text = a1 instanceof Component c ? c : Component.literal(String.valueOf(a1));
            showText(chat, ChatModel.uuidOf(nameC), nameC.getString(), text, now, sig, null, Protocol.print(text.getString()));
        } else {
            ChatModel.lastSender = null;
            GuiMessageTag st = ChatModel.rememberNumbered(new ChatModel.Msg(null, null, false, null, null, 0, true, now, ChatModel.takeTag(ChatServer.SYSTEM, 0),
                    ChatModel.systemIcon(content)), ChatServer.SYSTEM, 0);
            MutableComponent sys = Component.empty();
            ChatDates.liveDate(sys, now);
            ChatModel.note(st, null, ChatDecor.snippet(content.getString()), -1);
            ChatChannels.postTo(chat, sys.append(content).append("\n "), sig, st, ChatChannels.SERVER);
        }
        return true;
    }

    /** Player text: name (in the public chat), reply quote, text or location card. ch null = public chat. */
    static void showText(ChatComponent chat, UUID id, String name, Component text, long now, MessageSignature sig, UUID ch, int hash) {
        boolean named = ch == null && ChatModel.named(id, false);
        MutableComponent out = Component.empty();
        if (ch == null) {
            ChatDates.liveDate(out, now);
        }
        if (named) {
            out.append(ChatDecor.nameLine(name)).append("\n");
        }
        String plain = text.getString();
        int rt = ChatText.replyTarget(plain);
        Component body = rt >= 0 ? ChatDecor.dropStart(text, ChatText.replyMarkLength(plain)) : text;
        if (rt >= 0) {
            out.append(ChatReplies.quoteLine(rt)).append("\n");
        }
        boolean me = ChatDecor.mentionsMe(body, name);
        boolean answersMe = rt >= 0 && ChatReplies.isMine(rt) && !name.equalsIgnoreCase(ChatDecor.myName());
        ChatDecor.spoke(name);
        ChatText.Location loc = ChatText.parseLocation(body.getString());
        if (loc != null) { // location card, two lines
            out.append(" \n \n ");
        } else {
            out.append(ChatDecor.highlightMentions(body)).append("\n ");
        }
        int mid = ChatModel.takeTag(id, hash);
        GuiMessageTag mt = ChatModel.rememberNumbered(new ChatModel.Msg(id, name, named, null, null, 0, true, now, mid, null), id, hash);
        if (loc != null) {
            ChatLocations.locOf.put(mt, loc);
        }
        ChatRenderer.arrived();
        ChatModel.pendingFull = loc != null ? loc.coords() : body.getString();
        ChatModel.note(mt, name, loc != null ? ChatLocations.locWord() : ChatDecor.snippet(body.getString()), rt);
        if (me) {
            ChatModel.mentioned.add(mt);
            ChatDecor.notifyMention(name);
        } else if (answersMe) {
            ChatModel.mentioned.add(mt);
            ChatReplies.notifyReply(name);
        }
        ChatChannels.postTo(chat, out, sig, mt, ch);
    }

    /** Skipped if the sender is muted. */
    static void add(Packets.Show s) {
        addPicture(s, Protocol.print(s.pack() + "/" + s.id()), stickerWord());
    }
    /** Our own voice message to Gemini, played from the local copy. */
    static void aiVoice(String key, int ms, byte[] wave) {
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        MutableComponent text = Component.empty();
        text.append(" \n \n ");
        GuiMessageTag tag = ChatModel.remember(new ChatModel.Msg(p.getUUID(), p.getGameProfile().getName(), false, null, null, 0, true, System.currentTimeMillis(),
                -1, null));
        ChatVoiceNotes.voiceOf.put(tag, new ChatVoiceNotes.VoiceInfo(key, ms, wave));
        ChatModel.note(tag, p.getGameProfile().getName(), ChatVoiceNotes.voiceWord(), -1);
        ChatChannels.postTo(Minecraft.getInstance().gui.getChat(), text, null, tag, Ai.ID);
    }

    /** Clears the Gemini chat and its memory, then greets again. */
    static void clearAiChat() {
        Ai.forget();
        ChatChannels.stash.remove(Ai.ID);
        ChatChannels.dmUnread.remove(Ai.ID);
        if (Ai.ID.equals(ChatChannels.channel)) {
            ChatComponent chat = Minecraft.getInstance().gui.getChat();
            ((ChatComponentAccessor) chat).ingamemessenger$allMessages().clear();
            chat.rescaleChat();
        }
        Ai.reset();
        Ai.opened();
    }

    /** Gemini chat message (stays on this computer). */
    static void aiMessage(UUID who, String name, String text) {
        MutableComponent out = Component.empty();
        out.append(Ai.ID.equals(who) ? markdown(text) : Component.literal(text)).append("\n ");
        GuiMessageTag tag = ChatModel.remember(new ChatModel.Msg(who, name, false, null, null, 0, true, System.currentTimeMillis(), -1, null));
        ChatModel.pendingFull = text;
        ChatModel.note(tag, name, ChatDecor.snippet(text), -1);
        ChatChannels.postTo(Minecraft.getInstance().gui.getChat(), out, null, tag, Ai.ID);
    }

    /** Markdown subset to Minecraft styles: **bold**, *italic*, _italic_, `code` (grey), "- " and "* " as "• ". */
    static MutableComponent markdown(String text) {
        MutableComponent out = Component.empty();
        for (ChatText.Span sp : ChatText.markdown(text)) {
            out.append(Component.literal(sp.text()).withStyle(s -> {
                s = s.withBold(sp.bold() ? Boolean.TRUE : null).withItalic(sp.italic() ? Boolean.TRUE : null);
                return sp.code() ? s.withColor(0xB8C4D8) : s;
            }));
        }
        return out;
    }

    private static void addPicture(Packets.Show s, int hash, String word) {
        UUID ch = ChatChannels.takeRoute();
        int mid = ChatModel.takeTag(s.sender(), hash);
        if (Prefs.isMuted(s.sender())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int lines = stickerLines(Library.get(s.pack(), s.id()));
        boolean named = ch == null && ChatModel.named(s.sender(), true);
        boolean spacer = true;
        MutableComponent text = Component.empty();
        if (ch == null) {
            ChatDates.liveDate(text, System.currentTimeMillis());
        }
        if (named) {
            text.append(ChatDecor.nameLine(s.name())).append("\n");
        }
        if (s.replyTo() >= 0) {
            text.append(ChatReplies.quoteLine(s.replyTo())).append("\n");
        }
        text.append(" ");
        for (int i = 1; i < lines; i++) {
            text.append("\n ");
        }
        if (spacer) {
            text.append("\n ");
        }
        GuiMessageTag tag = ChatModel.rememberNumbered(new ChatModel.Msg(s.sender(), s.name(), named, s.pack(), s.id(), lines, spacer, System.currentTimeMillis(),
                spacer ? mid : -1, null), spacer ? s.sender() : null, hash);
        ChatModel.note(tag, s.name(), word, s.replyTo());
        ChatRenderer.arrived();
        if (s.replyTo() >= 0 && ChatReplies.isMine(s.replyTo()) && !s.name().equalsIgnoreCase(ChatDecor.myName())) {
            ChatReplies.notifyReply(s.name());
        }
        ChatChannels.postTo(mc.gui.getChat(), text, null, tag, ch);
    }

    static String stickerWord() {
        return Component.translatable("ingamemessenger.reply.sticker").getString();
    }

    /** Max sticker width relative to its height; wider ones are shown smaller. */
    static final float MAX_ASPECT = 1.1f;

    /** Lines a sticker takes: the size setting, less for wide ones. */
    static int stickerLines(Library.Sticker st) {
        if (st != null && Library.isEmoji(st.pack())) {
            return Math.max(3, Prefs.stickerLines() / 3); // emoji are small
        }
        int lines = Prefs.stickerLines();
        if (st != null && st.aspect() > MAX_ASPECT) {
            lines = Math.max(2, Math.round(lines * MAX_ASPECT / st.aspect()));
        }
        return lines;
    }
}
