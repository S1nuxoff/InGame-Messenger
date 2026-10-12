package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Text styling: name colors, @mentions (and the bell for ours), inline emoji, recent speakers for the @ list. */
final class ChatDecor {
    private ChatDecor() {
    }

    private static final int[] NAME_COLORS = {0xFF6E6E, 0xFFAE5C, 0xB58BFF, 0x63D07E, 0x5BC8E8, 0x5C9DFF, 0xFF7EB6};
    /** @name in a message: light blue, ours gold. */
    static final int MENTION = 0x6CC6FF, MENTION_ME = 0xFFC94D;
    private static final java.util.regex.Pattern AT = java.util.regex.Pattern.compile("@([A-Za-z0-9_]{1,16})");

    static int nameColor(String name) {
        return NAME_COLORS[Math.floorMod(name.hashCode(), NAME_COLORS.length)];
    }

    static MutableComponent nameLine(String name) {
        return Component.literal(name).withStyle(s -> s.withColor(nameColor(name)).withBold(true));
    }

    /** The text without its first n characters, styles kept. */
    static Component dropStart(Component text, int n) {
        MutableComponent out = Component.empty();
        int[] left = {n};
        text.visit((style, part) -> {
            if (left[0] >= part.length()) {
                left[0] -= part.length();
            } else {
                out.append(Component.literal(part.substring(left[0])).withStyle(style));
                left[0] = 0;
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    /** Start of a message for a reply quote: one line, without emoji codes. */
    static String snippet(String text) {
        String s = text.replaceAll(":[A-Za-z0-9_\\-]{1,64}/[A-Za-z0-9_\\-]{1,64}:", "").replace('\n', ' ').strip();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    // Player card

    static int colorOf(String name) {
        return nameColor(name);
    }

    static boolean online(UUID id, String name) {
        var conn = Minecraft.getInstance().getConnection();
        return conn != null && (id != null && conn.getPlayerInfo(id) != null || name != null && conn.getPlayerInfo(name) != null);
    }

    static Long lastSeen(String name) {
        return ChatModel.lastSeenAt.get(name.toLowerCase());
    }

    // Mentions

    static String myName() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getGameProfile().getName();
    }

    /** True if someone else's message mentions us. */
    static boolean mentionsMe(Component text, String sender) {
        String me = myName();
        if (me == null || me.equalsIgnoreCase(sender)) {
            return false;
        }
        var m = AT.matcher(text.getString());
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(me)) {
                return true;
            }
        }
        return false;
    }

    /** Same text with @names colored (ours gold and bold); links and other styles kept. */
    static Component highlightMentions(Component text) {
        String plain = text.getString();
        if (!plain.contains("@") && !plain.contains(":")) {
            return text;
        }
        String me = myName();
        MutableComponent out = Component.empty();
        text.visit((style, part) -> {
            var m = DECOR.matcher(part);
            int at = 0;
            while (m.find()) {
                if (m.start() > at) {
                    out.append(Component.literal(part.substring(at, m.start())).withStyle(style));
                }
                if (m.group(1) != null) { // @name
                    boolean mine = m.group(1).equalsIgnoreCase(me);
                    out.append(Component.literal(m.group()).withStyle(style.withColor(mine ? MENTION_ME : MENTION).withBold(mine)));
                } else { // :pack/id: inline emoji: a blank of its width, drawn over later
                    out.append(Component.literal(EMOJI_CHAR).withStyle(Compat.font(style, EMOJI_FONT)
                            .withInsertion(EMOJI_MARK + m.group(2) + "/" + m.group(3))));
                }
                at = m.end();
            }
            if (at < part.length()) {
                out.append(Component.literal(part.substring(at)).withStyle(style));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    // Inline emoji

    /** Inline emoji are written ":pack/id:"; in the text they become a blank glyph of the emoji's width. */
    private static final String EMOJI_CHAR = "\uE000", EMOJI_MARK = "ingamemessenger:emoji:";
    private static final net.minecraft.resources.ResourceLocation EMOJI_FONT =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ingamemessenger", "emoji");
    private static final java.util.regex.Pattern DECOR =
            java.util.regex.Pattern.compile("@([A-Za-z0-9_]{1,16})|:([A-Za-z0-9_\\-]{1,64})/([A-Za-z0-9_\\-]{1,64}):");

    static String emojiCode(Library.Sticker s) {
        return ":" + s.pack() + "/" + s.id() + ":";
    }

    /** Draws a line's inline emoji (animated) over their blanks. */
    public static void drawInlineEmoji(GuiGraphics g, net.minecraft.client.gui.Font font,
                                       net.minecraft.util.FormattedCharSequence text, int x, int y, float alpha) {
        float[] adv = {0};
        List<Object[]> found = new ArrayList<>();
        text.accept((index, style, cp) -> {
            String ch = new String(Character.toChars(cp));
            if (cp == 0xE000 && style.getInsertion() != null && style.getInsertion().startsWith(EMOJI_MARK)) {
                found.add(new Object[]{adv[0], style.getInsertion().substring(EMOJI_MARK.length())});
            }
            adv[0] += font.getSplitter().stringWidth(net.minecraft.util.FormattedCharSequence.forward(ch, style));
            return true;
        });
        for (Object[] f : found) {
            ChatUi.emoji(g, (String) f[1], x + (float) f[0] + 1, y - 1, 9, alpha);
        }
    }

    /** Players who wrote recently, latest last (they're listed first in the @ list). */
    private static final java.util.LinkedHashSet<String> speakers = new java.util.LinkedHashSet<>();

    static void spoke(String name) {
        speakers.remove(name);
        speakers.add(name);
        while (speakers.size() > 50) {
            speakers.remove(speakers.iterator().next());
        }
    }

    /** Higher = more recent, -1 = not recently. */
    static int lately(String name) {
        int i = 0, found = -1;
        for (String s : speakers) {
            if (s.equalsIgnoreCase(name)) {
                found = i;
            }
            i++;
        }
        return found;
    }

    /** We were mentioned: a bell and the name above the hotbar. */
    static void notifyMention(String who) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, 0.9f));
        mc.gui.setOverlayMessage(Component.translatable("ingamemessenger.mention", who).withStyle(s -> s.withColor(MENTION_ME)), false);
    }
}
