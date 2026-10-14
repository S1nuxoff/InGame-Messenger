package com.sinuxoff.ingamemessenger.client;

import com.sinuxoff.ingamemessenger.InGameMessenger;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The smooth font (Inter) for the messenger's text, when the player turns it on. Its glyphs are made for each whole
 * screen scale (font/smooth_N.json), so they land on screen pixels 1:1 instead of being stretched; bold text uses the
 * bold face instead of the game's shifted copy. Text that sets its own font (emoji) keeps it.
 */
public final class Smooth {
    private Smooth() {
    }

    private static final int MAX = 8;
    private static final Identifier[] REGULAR = new Identifier[MAX + 1], BOLD = new Identifier[MAX + 1];

    static {
        for (int n = 1; n <= MAX; n++) {
            REGULAR[n] = InGameMessenger.id("smooth_" + n);
            BOLD[n] = InGameMessenger.id("smooth_bold_" + n);
        }
    }

    /** Screen pixels per GUI pixel of the text being drawn now (the chat's own scale while it draws); 0 = the window's. */
    static float scale;

    public static boolean on() {
        return Prefs.smoothFont();
    }

    private static int level() {
        float s = scale > 0 ? scale : (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        return Math.max(1, Math.min(MAX, Math.round(s)));
    }

    private static Style style(Style st, int n) {
        if (!Compat.defaultFont(st)) {
            return st;
        }
        return st.isBold() ? Compat.font(st, BOLD[n]).withBold(false) : Compat.font(st, REGULAR[n]);
    }

    public static FormattedCharSequence seq(FormattedCharSequence text) {
        if (!on()) {
            return text;
        }
        int n = level(); // taken now: the text may be laid out later, after the scale changed back
        return sink -> text.accept((i, st, cp) -> sink.accept(i, style(st, n), cp));
    }

    public static FormattedCharSequence seq(String text) {
        return seq(FormattedCharSequence.forward(text, Style.EMPTY));
    }

    static FormattedCharSequence seq(Component text) {
        return seq(text.getVisualOrderText());
    }

    /** For wrapping (Font.split): the same text with the smooth font in its styles. */
    static Component text(Component text) {
        if (!on()) {
            return text;
        }
        int n = level();
        MutableComponent out = Component.empty();
        text.visit((st, part) -> {
            out.append(Component.literal(part).withStyle(style(st, n)));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public static int width(Font font, String text) {
        return on() ? font.width(seq(text)) : font.width(text);
    }

    static int width(Font font, Component text) {
        return on() ? font.width(seq(text)) : font.width(text);
    }

    static int width(Font font, FormattedCharSequence text) {
        return on() ? font.width(seq(text)) : font.width(text);
    }

    /** The longest start of text (or end, with fromEnd) that fits in w. */
    public static String cut(Font font, String text, int w, boolean fromEnd) {
        if (!on()) {
            return font.plainSubstrByWidth(text, w, fromEnd);
        }
        int n = level();
        float used = 0;
        if (fromEnd) {
            int i = text.length();
            while (i > 0) {
                int cp = text.codePointBefore(i);
                used += glyph(font, cp, n);
                if (used > w) {
                    break;
                }
                i -= Character.charCount(cp);
            }
            return text.substring(i);
        }
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            used += glyph(font, cp, n);
            if (used > w) {
                break;
            }
            i += Character.charCount(cp);
        }
        return text.substring(0, i);
    }

    public static String cut(Font font, String text, int w) {
        return cut(font, text, w, false);
    }

    private static float glyph(Font font, int cp, int n) {
        return font.width(FormattedCharSequence.forward(new String(Character.toChars(cp)), style(Style.EMPTY, n)));
    }
}
