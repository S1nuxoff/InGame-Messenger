package com.sinuxoff.ingamemessenger.client;

import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Our mute on this server: the input says so where it holds, instead of sending into nothing. */
final class Muted {
    private Muted() {
    }

    private static volatile String reason = "";

    /** Until when we're muted in the open channel, 0 if we aren't (commands aren't muted). */
    static long here() {
        Messenger.Chan c = Messenger.open();
        if (c == null) {
            return 0;
        }
        long now = System.currentTimeMillis(), until = 0;
        String why = "";
        for (var el : Messenger.mutes) {
            var m = el.getAsJsonObject();
            String scope = m.get("scope").getAsString();
            long id = m.get("id").getAsLong(), u = m.get("until").getAsLong();
            boolean holds = c.dm() ? scope.equals("dm") : scope.equals("space") && id == c.space || scope.equals("channel") && id == c.id;
            if (holds && u > now && u > until) {
                until = u;
                why = m.has("reason") ? m.get("reason").getAsString() : "";
            }
        }
        reason = why;
        return until;
    }

    /** "Muted until 18:30 · spam" for the empty input. */
    static Component hint() {
        long until = here();
        ZoneId zone = ChatDates.zone();
        var at = Instant.ofEpochMilli(until).atZone(zone);
        String when = at.format(DateTimeFormatter.ofPattern(at.toLocalDate().equals(LocalDate.now(zone)) ? "HH:mm" : "dd.MM HH:mm"));
        Component text = until == Long.MAX_VALUE ? Component.translatable("ingamemessenger.muted.forever")
                : Component.translatable("ingamemessenger.muted.until", when);
        return reason.isBlank() ? text : text.copy().append(" · " + reason);
    }
}
