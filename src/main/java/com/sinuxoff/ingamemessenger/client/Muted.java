package com.sinuxoff.ingamemessenger.client;

import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Our mute on this server: the input says so where it holds, instead of sending into nothing. */
final class Muted {
    private Muted() {
    }

    private static volatile long chatUntil, dmUntil;
    private static volatile String reason = "";

    static void on(Packets.MuteState m) {
        chatUntil = m.chatUntil();
        dmUntil = m.dmUntil();
        reason = m.reason();
    }

    static void reset() {
        chatUntil = dmUntil = 0;
        reason = "";
    }

    /** Until when we're muted in the open chat, 0 if we aren't (commands and Gemini aren't muted). */
    static long here() {
        UUID ch = ChatChannels.channel();
        long until = ch == null ? chatUntil : ch.equals(Ai.ID) || ch.equals(ChatChannels.SERVER) ? 0 : dmUntil;
        return until > System.currentTimeMillis() ? until : 0;
    }

    /** "Muted until 18:30 · spam" for the empty input. */
    static Component hint() {
        long until = here();
        ZoneId zone = ZoneId.systemDefault();
        var at = Instant.ofEpochMilli(until).atZone(zone);
        String when = at.format(DateTimeFormatter.ofPattern(at.toLocalDate().equals(LocalDate.now(zone)) ? "HH:mm" : "dd.MM HH:mm"));
        Component text = until == Long.MAX_VALUE ? Component.translatable("ingamemessenger.muted.forever")
                : Component.translatable("ingamemessenger.muted.until", when);
        return reason.isBlank() ? text : text.copy().append(" · " + reason);
    }
}
