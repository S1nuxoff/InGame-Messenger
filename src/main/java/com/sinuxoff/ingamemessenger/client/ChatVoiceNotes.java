package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Voice messages in the chat: two lines with a player (play/pause, waveform, duration). */
final class ChatVoiceNotes {
    private ChatVoiceNotes() {
    }

    /** waveform bars are 0..255. */
    record VoiceInfo(String key, int ms, byte[] wave) {
    }

    static final Map<GuiMessageTag, VoiceInfo> voiceOf = new IdentityHashMap<>();
    static VoiceInfo pendingVoice;
    /** Width of the player in a bubble. */
    static final int VOICE_BARS = 32, VOICE_W = 20 + VOICE_BARS * 3 + 6 + 26;

    static String voiceWord() {
        return Component.translatable("ingamemessenger.voice.word").getString();
    }

    static void addVoice(Packets.VoiceShow v) {
        UUID ch = ChatChannels.takeRoute();
        int hash = Protocol.print("voice:" + v.key());
        int mid = ChatModel.takeTag(v.sender(), hash);
        Minecraft mc = Minecraft.getInstance();
        boolean named = ch == null && ChatModel.named(v.sender(), false);
        MutableComponent text = Component.empty();
        if (ch == null) {
            ChatDates.liveDate(text, System.currentTimeMillis());
        }
        if (named) {
            text.append(ChatDecor.nameLine(v.name())).append("\n");
        }
        if (v.replyTo() >= 0) {
            text.append(ChatReplies.quoteLine(v.replyTo())).append("\n");
        }
        text.append(" \n \n ");
        GuiMessageTag tag = ChatModel.rememberNumbered(new ChatModel.Msg(v.sender(), v.name(), named, null, null, 0, true, System.currentTimeMillis(), mid, null),
                v.sender(), hash);
        voiceOf.put(tag, new VoiceInfo(v.key(), v.durationMs(), v.wave()));
        ChatModel.note(tag, v.name(), voiceWord(), v.replyTo());
        ChatRenderer.arrived();
        ChatDecor.spoke(v.name());
        if (v.replyTo() >= 0 && ChatReplies.isMine(v.replyTo()) && !v.name().equalsIgnoreCase(ChatDecor.myName())) {
            ChatModel.mentioned.add(tag);
            ChatReplies.notifyReply(v.name());
        }
        ChatChannels.postTo(mc.gui.getChat(), text, null, tag, ch);
    }

    /** Round play/pause button, waveform (played part highlighted), duration. */
    static void drawVoice(GuiGraphics g, VoiceInfo v, String who, int x, int y, float a) {
        var font = Minecraft.getInstance().font;
        boolean playing = VoiceClient.playing(v.key()), gone = VoiceClient.gone(v.key());
        int s = 17, by = y + 1;
        Panel.round(g, x, by, s, s, s / 2, (int) (a * 255) << 24 | (gone ? 0x4A4F5C : 0x7B68FF));
        int col = (int) (a * 255) << 24 | 0xFFFFFF, cx = x + s / 2, cy = by + s / 2;
        if (playing) {
            Panel.drawIcon(g, "pause", x + 4, by + 4, s - 8, col);
        } else if (VoiceClient.loading(v.key())) {
            int ph = (int) (System.currentTimeMillis() / 250 % 3);
            for (int i = 0; i < 3; i++) {
                g.fill(cx - 4 + i * 3, cy, cx - 3 + i * 3, cy + 1, i == ph ? col : (int) (a * 0x80) << 24 | 0xFFFFFF);
            }
        } else {
            Panel.drawIcon(g, "play", x + 4.5f, by + 4, s - 8, col);
        }
        int wx = x + s + 4;
        if (gone) {
            ChatDates.smallText(g, Component.translatable("ingamemessenger.voice.gone").getString(), wx, by + 6, (int) (a * 0xC0) << 24 | 0x8E95A6);
        } else {
            float played = VoiceClient.progress(v.key(), v.ms());
            for (int i = 0; i < VOICE_BARS; i++) {
                int from = i * v.wave().length / VOICE_BARS, to = Math.max(from + 1, (i + 1) * v.wave().length / VOICE_BARS);
                int peak = 0;
                for (int k = from; k < to && k < v.wave().length; k++) {
                    peak = Math.max(peak, v.wave()[k] & 0xFF);
                }
                int h = 2 + Math.round(peak / 255f * 12);
                boolean lit = (i + 0.5f) / VOICE_BARS <= played;
                int c = lit ? (int) (a * 255) << 24 | 0xFFFFFF : (int) (a * 0x70) << 24 | 0xC8CCD8;
                g.fill(wx + i * 3, cy - h / 2, wx + i * 3 + 2, cy - h / 2 + h, c);
            }
        }
        int secs = Math.round(v.ms() / 1000f);
        String len = secs / 60 + ":" + (secs % 60 < 10 ? "0" : "") + secs % 60;
        Gfx.text(g, font, len, wx + VOICE_BARS * 3 + 6, cy - 4, (int) (a * 255) << 24 | 0xB8BECC, false);
        ChatUi.voice(v.key(), who, v.ms(), x, by, VOICE_W, s);
    }
}
