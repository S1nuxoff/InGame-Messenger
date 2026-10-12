package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Message times and date separators. */
final class ChatDates {
    private ChatDates() {
    }

    static long dayOf(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay();
    }

    /** A date separator above a day's first message takes two lines. */
    static final int DATE_LINES = 2;
    static final String DATE_ROOM = " \n \n";

    static void liveDate(MutableComponent out, long ms) {
        long day = dayOf(ms);
        if (day != ChatModel.lastLiveDay) {
            out.append(DATE_ROOM);
            ChatModel.pendingDay = day;
        }
        ChatModel.lastLiveDay = day;
    }

    /** "Today", "Yesterday" or "8 October", in the game's language. */
    private static String dateLabel(long day) {
        long today = java.time.LocalDate.now().toEpochDay();
        if (day == today) {
            return Component.translatable("ingamemessenger.date.today").getString();
        }
        if (day == today - 1) {
            return Component.translatable("ingamemessenger.date.yesterday").getString();
        }
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        var locale = java.util.Locale.forLanguageTag(lang.replace('_', '-'));
        var date = java.time.LocalDate.ofEpochDay(day);
        String pattern = date.getYear() == java.time.LocalDate.now().getYear() ? "d MMMM" : "d MMMM yyyy";
        return date.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale));
    }

    /** The date on a small pill centered in the chat. */
    static void drawDate(GuiGraphics g, long day, int y, int width, float a) {
        String label = dateLabel(day);
        int w = timeWidth(label) + 14, x = (width - w) / 2;
        Panel.round(g, x, y, w, 11, 5, (int) (a * 0xB0) << 24 | 0x161A23);
        smallText(g, label, x + 7, y + 3, (int) (a * 255) << 24 | 0xB8BECC);
    }
    /** "14:32", or "03.10 14:32" if not today. */
    static String when(long ms) {
        var at = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault());
        return at.toLocalDate().equals(java.time.LocalDate.now()) ? at.format(HH_MM) : at.format(DAY_HH_MM);
    }

    static void small(GuiGraphics g, String text, float x, float y, int color) {
        smallText(g, text, x, y, color);
    }

    static final int TIME_GAP = 6;
    static int timeWidth(String t) {
        return (int) Math.ceil(Minecraft.getInstance().font.width(t) * Panel.smallScale());
    }

    private static final java.time.format.DateTimeFormatter HH_MM = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    private static final java.time.format.DateTimeFormatter DAY_HH_MM = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /** Local time of a message: "14:32", or "03.10 14:32" if not today. */
    static String time(ChatModel.Msg m) {
        var at = java.time.Instant.ofEpochMilli(m.createdMs()).atZone(java.time.ZoneId.systemDefault());
        return at.toLocalDate().equals(java.time.LocalDate.now()) ? at.format(HH_MM) : at.format(DAY_HH_MM);
    }

    static void drawTime(GuiGraphics g, String time, float x, float y, float a) {
        smallText(g, time, x, y, (int) (a * 0xC0) << 24 | 0x8E95A6);
    }

    static void smallText(GuiGraphics g, String text, float x, float y, int color) {
        Gfx.push(g);
        Gfx.move(g, x, y);
        float k = Panel.smallScale();
        Gfx.scale(g, k, k);
        Gfx.text(g, Minecraft.getInstance().font, text, 0, 0, color, false);
        Gfx.pop(g);
    }
}
