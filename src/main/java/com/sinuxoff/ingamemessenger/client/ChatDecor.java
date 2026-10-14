package com.sinuxoff.ingamemessenger.client;

import com.sinuxoff.ingamemessenger.InGameMessenger;
import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
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

    /** How the server shows players' names (a rank, colors), as it said (Names), by lower-case name. */
    private static final java.util.Map<String, Component> shownNames = new java.util.HashMap<>();

    /** Names packet: "uuid \t name \t component JSON" per line. */
    static void onNames(String data) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null || data.isEmpty()) {
            return;
        }
        for (String line : data.split("\n")) {
            String[] f = line.split("\t", 3);
            if (f.length < 3) {
                continue;
            }
            String key = f[1].toLowerCase(java.util.Locale.ROOT);
            if (f[2].isEmpty()) { // just the name now
                shownNames.remove(key);
                continue;
            }
            try {
                shownNames.put(key, com.sinuxoff.ingamemessenger.ServerCompat.fromJson(f[2], conn.registryAccess()));
            } catch (RuntimeException ignored) {
                shownNames.remove(key); // not a component this version reads: our look
            }
        }
    }

    static void forgetServerNames() {
        shownNames.clear();
    }

    /** The widest a name line gets; a longer rank is cut, the name itself never. */
    private static final int NAME_MAX_W = 200;

    /** The name over a bubble: the server's look of it if it has one, else in our color. */
    static MutableComponent nameLine(String name) {
        Component shown = shownNames.get(name.toLowerCase(java.util.Locale.ROOT));
        MutableComponent bold = Component.empty().withStyle(s -> s.withBold(true));
        if (shown == null) {
            return bold.append(Component.literal(name).withStyle(s -> s.withColor(nameColor(name))));
        }
        var font = Minecraft.getInstance().font;
        if (Smooth.width(font, bold.copy().append(shown)) <= NAME_MAX_W) {
            return bold.append(shown);
        }
        // "[Rank] Name suffix": the name stays whole, then the suffix if there's room, the rank gets what's left
        String plain = shown.getString();
        int at = plain.toLowerCase(java.util.Locale.ROOT).lastIndexOf(name.toLowerCase(java.util.Locale.ROOT));
        if (at < 0) {
            return bold.append(cut(slice(shown, 0, plain.length()), NAME_MAX_W - Smooth.width(font, bold.copy().append("\u2026")), true));
        }
        MutableComponent nick = slice(shown, at, at + name.length());
        MutableComponent rest = slice(shown, at + name.length(), plain.length());
        int nickW = Smooth.width(font, bold.copy().append(nick));
        if (Smooth.width(font, bold.copy().append(nick).append(rest)) > NAME_MAX_W) {
            rest = Component.empty();
        }
        int room = NAME_MAX_W - Smooth.width(font, bold.copy().append(nick).append(rest));
        MutableComponent rank = slice(shown, 0, at);
        if (Smooth.width(font, bold.copy().append(rank)) > room) {
            rank = cut(rank, room - Smooth.width(font, bold.copy().append("\u2026 ")), false);
            rank = rank.getString().isBlank() ? Component.empty() : rank.append("\u2026 ");
        }
        return bold.append(rank).append(nick).append(rest);
    }

    /** Characters from..to of text, styles kept. */
    private static MutableComponent slice(Component text, int from, int to) {
        MutableComponent out = Component.empty();
        int[] at = {0};
        text.visit((style, part) -> {
            int a = Math.max(from, at[0]), b = Math.min(to, at[0] + part.length());
            if (a < b) {
                out.append(Component.literal(part.substring(a - at[0], b - at[0])).withStyle(style));
            }
            at[0] += part.length();
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    /** As much of text (bold) as fits in maxW pixels, plus "…" if ellipsis. */
    private static MutableComponent cut(MutableComponent text, int maxW, boolean ellipsis) {
        var font = Minecraft.getInstance().font;
        MutableComponent out = Component.empty();
        int[] used = {0};
        boolean[] full = {false};
        text.visit((style, part) -> {
            for (int i = 0; i < part.length() && !full[0]; ) {
                int n = Character.charCount(part.codePointAt(i));
                Component ch = Component.literal(part.substring(i, i + n)).withStyle(style.withBold(true));
                int w = Smooth.width(font, ch);
                if (used[0] + w > maxW) {
                    full[0] = true;
                    break;
                }
                out.append(Component.literal(part.substring(i, i + n)).withStyle(style));
                used[0] += w;
                i += n;
            }
            return full[0] ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return ellipsis && full[0] ? out.append("\u2026") : out;
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
        return p == null ? null : p.getGameProfile().name();
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
    private static final java.util.regex.Pattern URL = java.util.regex.Pattern.compile("(?i)\\bhttps?://[^\\s<>\"]+[^\\s<>\".,;:!?)\\]'}]");

    /** Addresses in a player's text: underlined links that open (after the game's question). */
    static Component links(Component text) {
        String plain = text.getString();
        if (!plain.contains("://")) {
            return text;
        }
        MutableComponent out = Component.empty();
        text.visit((style, part) -> {
            var m = URL.matcher(part);
            int at = 0;
            while (m.find()) {
                if (m.start() > at) {
                    out.append(Component.literal(part.substring(at, m.start())).withStyle(style));
                }
                net.minecraft.network.chat.Style link;
                try {
                    link = style.withColor(0x6CC6FF).withUnderlined(true).withClickEvent(Compat.openUrl(m.group()))
                            .withHoverEvent(Compat.showText(Component.literal(m.group())));
                } catch (IllegalArgumentException bad) { // not an address after all
                    link = style;
                }
                out.append(Component.literal(m.group()).withStyle(link));
                at = m.end();
            }
            if (at < part.length()) {
                out.append(Component.literal(part.substring(at)).withStyle(style));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    /** An emoji's blank place (its insertion is ours, not something to put in the input). */
    static boolean isEmojiMark(String insertion) {
        return insertion != null && insertion.startsWith(EMOJI_MARK);
    }

    /** Message text in the smooth font when the player chose it (emoji keep theirs). */
    static Component smooth(Component text) {
        return Smooth.text(text);
    }

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
    private static final net.minecraft.resources.Identifier EMOJI_FONT =
            InGameMessenger.id("emoji");
    private static final java.util.regex.Pattern DECOR =
            java.util.regex.Pattern.compile("@([A-Za-z0-9_]{1,16})|:([A-Za-z0-9_\\-]{1,64})/([A-Za-z0-9_\\-]{1,64}):");

    static String emojiCode(Library.Sticker s) {
        return ":" + s.pack() + "/" + s.id() + ":";
    }

    /** Draws a line's inline emoji (animated) over their blanks. */
    public static void drawInlineEmoji(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
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
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL/*? if >=1.19.3 {*/.value()/*?}*/, 1.2f, 0.9f));
        mc.gui.hud.setOverlayMessage(Component.translatable("ingamemessenger.mention", who).withStyle(s -> s.withColor(MENTION_ME)), false);
    }
}
