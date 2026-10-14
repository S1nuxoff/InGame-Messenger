package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Message times and date separators. */
final class ChatDates {
    private ChatDates() {
    }

    private static java.time.ZoneId zone;

    /**
     * The player's time zone. Java's default, unless a loader switched it to UTC on start (NeoForge 20.6 does): then
     * the zone Java found at start, or on Windows the system's own (its name mapped by Java's tzmappings).
     */
    static java.time.ZoneId zone() {
        if (zone == null) {
            zone = findZone();
        }
        return zone;
    }

    private static java.time.ZoneId findZone() {
        java.time.ZoneId def = java.time.ZoneId.systemDefault();
        if (!def.normalized().equals(java.time.ZoneOffset.UTC)) {
            return def;
        }
        try {
            String prop = System.getProperty("user.timezone", "");
            if (!prop.isBlank()) {
                java.time.ZoneId z = java.time.ZoneId.of(prop);
                if (!z.normalized().equals(java.time.ZoneOffset.UTC)) {
                    return z;
                }
            }
        } catch (RuntimeException ignored) {
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return def;
        }
        try {
            Process p = new ProcessBuilder("reg", "query", "HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation", "/v",
                    "TimeZoneKeyName").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("TimeZoneKeyName\\s+REG_SZ\\s+(.+)").matcher(out);
            if (!m.find()) {
                return def;
            }
            String windows = m.group(1).strip();
            String found = null;
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("java.home"), "lib", "tzmappings"))) {
                String[] f = line.split(":");
                if (f.length >= 3 && f[0].equals(windows) && (found == null || f[1].equals("001"))) {
                    found = f[2];
                }
            }
            return found != null ? java.time.ZoneId.of(found) : def;
        } catch (Exception e) {
            return def;
        }
    }

    static long dayOf(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate().toEpochDay();
    }

    /** "Today", "Yesterday" or "8 October", in the game's language. */
    private static String dateLabel(long day) {
        long today = java.time.LocalDate.now(zone()).toEpochDay();
        if (day == today) {
            return Component.translatable("ingamemessenger.date.today").getString();
        }
        if (day == today - 1) {
            return Component.translatable("ingamemessenger.date.yesterday").getString();
        }
        //? if >=1.19.4 {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        //?} else {
        /*String lang = Minecraft.getInstance().getLanguageManager().getSelected().getCode();
        *///?}
        var locale = java.util.Locale.forLanguageTag(lang.replace('_', '-'));
        var date = java.time.LocalDate.ofEpochDay(day);
        String pattern = date.getYear() == java.time.LocalDate.now(zone()).getYear() ? "d MMMM" : "d MMMM yyyy";
        return date.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale));
    }

    /** The date on a small pill centered in the chat. */
    static void drawDate(GuiGraphicsExtractor g, long day, int y, int width, float a) {
        String label = dateLabel(day);
        int w = timeWidth(label) + 14, x = (width - w) / 2;
        Panel.round(g, x, y, w, 11, 5, (int) (a * 0xB0) << 24 | 0x161A23);
        smallText(g, label, x + 7, y + 3, (int) (a * 255) << 24 | 0xB8BECC);
    }
    /** "14:32", or "03.10 14:32" if not today. */
    static String when(long ms) {
        var at = java.time.Instant.ofEpochMilli(ms).atZone(zone());
        return at.toLocalDate().equals(java.time.LocalDate.now(zone())) ? at.format(HH_MM) : at.format(DAY_HH_MM);
    }

    static void small(GuiGraphicsExtractor g, String text, float x, float y, int color) {
        smallText(g, text, x, y, color);
    }

    static final int TIME_GAP = 6;
    static int timeWidth(String t) {
        return (int) Math.ceil(Minecraft.getInstance().font.width(t) * Panel.smallScale());
    }

    private static final java.time.format.DateTimeFormatter HH_MM = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    private static final java.time.format.DateTimeFormatter DAY_HH_MM = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm");

    static void drawTime(GuiGraphicsExtractor g, String time, float x, float y, float a) {
        smallText(g, time, x, y, (int) (a * 0xC0) << 24 | 0x8E95A6);
    }

    static void smallText(GuiGraphicsExtractor g, String text, float x, float y, int color) {
        Gfx.push(g);
        Gfx.move(g, x, y);
        float k = Panel.smallScale();
        Gfx.scale(g, k, k);
        Gfx.text(g, Minecraft.getInstance().font, text, 0, 0, color, false);
        Gfx.pop(g);
    }
}
