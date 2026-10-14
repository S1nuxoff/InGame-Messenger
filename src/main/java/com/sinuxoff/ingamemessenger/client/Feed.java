package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Draws a channel's messages where the game's chat is: our own layout from the messages themselves (not from the
 * game's chat lines). Open: the selected channel, scrollable, older pages loaded at the top. Closed: what arrived in the
 * last seconds, from every channel, fading out like the game's chat.
 */
final class Feed {
    private Feed() {
    }

    private static final int AV = ChatRenderer.AV, FACE = 16, PAD = 8, PAD_V = 3, LINE = 10, GAP = 3, RUN_GAP = 7, DATE_H = 16;
    private static final int BUBBLE = 0x1E2333, BUBBLE_MINE = 0x33287A, BUBBLE_SYSTEM = 0x15181F, BUBBLE_ALPHA = 0xA8;
    /** How long a message stays on the closed chat. */
    private static final long SHOWN_MS = 10_000;

    /** Scroll from the bottom, in chat pixels: where it's going and where it is. */
    private static float target, at;
    private static long lastFrame;
    private static int unseen;
    private static boolean open;

    // Layout

    /** One message laid out: its height and what goes where. */
    private record Box(Messenger.Entry e, int h, boolean named, boolean face, boolean date, List<FormattedCharSequence> lines, int w,
                       boolean timeRow) {
    }

    /** One sender (a player, or the server for its lines), at most 2 minutes apart, the same day. */
    private static boolean sameRun(Messenger.Entry a, Messenger.Entry b) {
        if (a == null || b == null || Math.abs(a.time - b.time) >= 120_000 || ChatDates.dayOf(a.time) != ChatDates.dayOf(b.time)) {
            return false;
        }
        return !isPlayer(a) && !isPlayer(b) || isPlayer(a) && isPlayer(b) && a.sender.equals(b.sender);
    }

    /** The name over a run: the player's (with the server's look of it), or the server's own. */
    private static Component nameOf(Messenger.Entry e) {
        return isPlayer(e) ? ChatDecor.nameLine(e.name)
                : Component.literal(ServerIcon.name()).withStyle(st -> st.withBold(true).withColor(0xC8CCD8));
    }

    private static boolean isPlayer(Messenger.Entry e) {
        return e.sender != null && !"system".equals(e.kind) && !"line".equals(e.kind);
    }

    /** What a message shows as text (pictures, voice and polls in words until they're drawn as such). */
    private static Component textOf(Messenger.Entry e) {
        return switch (e.kind) {
            case "text" -> ChatDecor.highlightMentions(Component.literal(e.text()));
            case "voice" -> Component.literal("▶ " + (e.body.has("ms") ? Math.max(1, Math.round(e.body.get("ms").getAsInt() / 1000f)) + " s" : ""));
            case "images" -> Component.literal("[" + (e.body.has("i") ? e.body.getAsJsonArray("i").size() : 1) + " ▣] "
                    + (e.body.has("c") ? e.body.get("c").getAsString() : ""));
            case "poll" -> Component.literal("☰ " + (e.body.has("q") ? e.body.get("q").getAsString() : ""));
            default -> e.raw != null ? e.raw : Component.empty();
        };
    }

    /** Our own messages in a private chat show whether they were read. */
    private static boolean checks(Messenger.Entry e) {
        Messenger.Chan c = Messenger.channels.get(e.channel);
        return e.mine() && e.id > 0 && c != null && c.dm();
    }

    private static String timeOf(Messenger.Entry e) {
        var at = java.time.Instant.ofEpochMilli(e.time).atZone(java.time.ZoneId.systemDefault());
        String t = at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        return e.edited > 0 ? Component.translatable("ingamemessenger.edited").getString() + " " + t : t;
    }

    private static ChatVoiceNotes.VoiceInfo voiceOf(Messenger.Entry e) {
        byte[] wave;
        try {
            wave = java.util.HexFormat.of().parseHex(e.body.has("w") ? e.body.get("w").getAsString() : "");
        } catch (RuntimeException ex) {
            wave = new byte[0];
        }
        return new ChatVoiceNotes.VoiceInfo(e.body.has("k") ? e.body.get("k").getAsString() : "", e.body.has("ms") ? e.body.get("ms").getAsInt() : 0,
                wave);
    }

    private static ChatAlbums.AlbumInfo albumOf(Messenger.Entry e) {
        if (e.album == null) {
            List<ChatAlbums.Pic> pics = new ArrayList<>();
            if (e.body.has("i")) {
                for (var el : e.body.getAsJsonArray("i")) {
                    var o = el.getAsJsonObject();
                    pics.add(new ChatAlbums.Pic(o.get("k").getAsString(), o.get("w").getAsInt(), o.get("h").getAsInt()));
                }
            }
            e.album = ChatAlbums.album(pics, e.body.has("c") ? e.body.get("c").getAsString() : "");
        }
        return (ChatAlbums.AlbumInfo) e.album;
    }

    private static int stickerH() {
        return Prefs.stickerLines() * LINE;
    }

    /** Lays out a message; prev is the one above it (for the name, the face's run and the date). */
    private static Box box(Messenger.Entry e, Messenger.Entry prev, Messenger.Entry next, int chatW, Font font) {
        boolean date = prev == null || ChatDates.dayOf(prev.time) != ChatDates.dayOf(e.time);
        Messenger.Chan ch = Messenger.channels.get(e.channel);
        boolean right = e.mine() && Prefs.ownRight(); // ours on the right: no name, no face (like a phone's messenger)
        boolean named = !right && (date || !sameRun(prev, e)) && (ch == null || !ch.dm());
        boolean face = !right && !sameRun(e, next);
        // the server lays its lines out for the game's chat width: they get all of it
        int maxW = isPlayer(e) ? chatW - AV - 2 * PAD - 4 : chatW;
        if ("sticker".equals(e.kind)) {
            return new Box(e, (named ? LINE + 4 : 0) + stickerH() + (date ? DATE_H : 0), named, face, date, List.of(), stickerH(), false);
        }
        int timeW0 = ChatDates.timeWidth(timeOf(e) + (checks(e) ? " \u2713\u2713" : "")) + ChatDates.TIME_GAP;
        if ("voice".equals(e.kind)) {
            int w = Math.max(ChatVoiceNotes.VOICE_W + timeW0, named ? font.width(nameOf(e)) : 0);
            return new Box(e, (named ? LINE : 0) + 19 + 2 * PAD_V + (date ? DATE_H : 0), named, face, date, List.of(), w, false);
        }
        if ("images".equals(e.kind)) {
            ChatAlbums.AlbumInfo ai = albumOf(e);
            List<FormattedCharSequence> cap = ai.caption().isEmpty() ? List.of() : font.split(Component.literal(ai.caption()), ai.width());
            int w = Math.max(ai.width(), named ? font.width(nameOf(e)) : 0);
            int h = (named ? LINE : 0) + ai.height() + 2 * ChatAlbums.ALBUM_PAD + cap.size() * LINE + (cap.isEmpty() ? 0 : PAD_V)
                    + (date ? DATE_H : 0);
            return new Box(e, h, named, face, date, cap, w, false);
        }
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(textOf(e), maxW));
        if (lines.isEmpty()) {
            lines.add(FormattedCharSequence.EMPTY);
        }
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        if (named) {
            w = Math.max(w, font.width(nameOf(e)));
        }
        int timeW = ChatDates.timeWidth(timeOf(e) + (checks(e) ? " ✓✓" : "")) + ChatDates.TIME_GAP;
        int last = font.width(lines.get(lines.size() - 1));
        boolean timeRow = last + timeW > maxW;
        w = Math.max(w, timeRow ? timeW : last + timeW);
        int h = (named ? LINE : 0) + lines.size() * LINE + (timeRow ? LINE - 2 : 0) + 2 * PAD_V + (date ? DATE_H : 0);
        return new Box(e, h, named, face, date, lines, w, timeRow);
    }

    // Drawing

    private static void bubble(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb, float a) {
        int lift = Math.round(ChatMotion.shade * 0x0A);
        int c = Math.min(255, (rgb >> 16 & 0xFF) + lift) << 16 | Math.min(255, (rgb >> 8 & 0xFF) + lift) << 8 | Math.min(255, (rgb & 0xFF) + lift + 2);
        int alpha = Math.round(BUBBLE_ALPHA + ChatMotion.shade * (0xC4 - BUBBLE_ALPHA));
        Panel.round(g, x, y, w, h, Math.min(10, h / 2), (int) (a * alpha) << 24 | c);
    }

    /** Draws a message with its bottom at y; returns its top. */
    private static int draw(GuiGraphicsExtractor g, Box b, int y, int chatW, float a, Font font) {
        Messenger.Entry e = b.e();
        int top = y - b.h();
        if (b.date()) {
            ChatDates.drawDate(g, ChatDates.dayOf(e.time), top + 2, chatW, a);
        }
        int textCol = (int) (a * 255) << 24 | 0xFFFFFF;
        boolean right = e.mine() && Prefs.ownRight();
        if ("sticker".equals(e.kind)) {
            Library.Sticker st = Library.get(e.body.has("p") ? e.body.get("p").getAsString() : "", e.body.has("s") ? e.body.get("s").getAsString() : "");
            int sh = stickerH(), sw = st == null ? sh : Math.round(Math.min(sh * st.aspect(), sh * 1.6f));
            int sx = right ? chatW - 2 - sw : AV, sy = y - sh;
            if (b.named()) {
                Component name = ChatDecor.nameLine(e.name);
                int nw = font.width(name);
                bubble(g, sx - PAD, sy - LINE - 4 - PAD_V, nw + 2 * PAD, LINE + 2 * PAD_V - 2, BUBBLE, a);
                Gfx.text(g, font, name, sx, sy - LINE - 4, textCol, false);
            }
            if (st != null) {
                Library.draw(g, st, sx, sy, sw, sh, a);
            } else {
                Panel.round(g, sx, sy, sw, sh, 8, (int) (a * 0x80) << 24 | 0x1B1F2A);
            }
            String t = timeOf(e);
            int tw = ChatDates.timeWidth(t) + 8;
            Panel.round(g, sx + sw - tw - 2, y - 11, tw, 9, 4, (int) (a * 0x90) << 24);
            ChatDates.smallText(g, t, sx + sw - tw + 2, y - 9, (int) (a * 0xE0) << 24 | 0xE4E6EE);
        } else {
            int bw = b.w() + 2 * PAD, bh = b.h() - (b.date() ? DATE_H : 0);
            int bx = right ? chatW - 2 - bw : AV - PAD, by = y - bh;
            boolean me = !e.mine() && "text".equals(e.kind) && ChatDecor.mentionsMe(Component.literal(e.text()), e.name);
            bubble(g, bx, by, bw, bh, !isPlayer(e) ? BUBBLE_SYSTEM : me ? 0x332A5C : e.mine() ? BUBBLE_MINE : BUBBLE, e.nonce.isEmpty() ? a : a * 0.6f);
            int tx = bx + PAD, ty = by + PAD_V + 1;
            if (b.named()) {
                Gfx.text(g, font, nameOf(e), tx, ty, textCol, false);
                ty += LINE;
            }
            if ("voice".equals(e.kind)) {
                ChatVoiceNotes.drawVoice(g, voiceOf(e), e.name, tx, ty - 1, a);
            } else if ("images".equals(e.kind)) {
                ChatAlbums.AlbumInfo ai = albumOf(e);
                int ax = bx + (bw - ai.width()) / 2;
                ChatAlbums.drawAlbum(g, ai, e.name, ax, ty - 1 + ChatAlbums.ALBUM_PAD - PAD_V, a);
                ty += ai.height() + 2 * ChatAlbums.ALBUM_PAD - PAD_V;
            }
            for (FormattedCharSequence l : "voice".equals(e.kind) ? List.<FormattedCharSequence>of() : b.lines()) {
                Gfx.text(g, font, l, tx, ty, textCol, false);
                ChatDecor.drawInlineEmoji(g, font, l, tx, ty, a); // emoji over their blank places in the text
                ty += LINE;
            }
            String t = timeOf(e);
            boolean checks = checks(e);
            float timeX = bx + bw - PAD - ChatDates.timeWidth(t + (checks ? " ✓✓" : "")) + 2, timeY = y - PAD_V - 7;
            ChatDates.drawTime(g, t, timeX, timeY, a);
            if (checks) { // sent / read, after the time
                Messenger.Chan c = Messenger.channels.get(e.channel);
                boolean read = c != null && e.id <= c.peerRead;
                Panel.drawIcon(g, read ? "check2" : "check1", timeX + ChatDates.timeWidth(t + " ") - 1, timeY - 2.5f, 9,
                        (int) (a * 0xF0) << 24 | (read ? 0x6CC6FF : 0x8E95A6));
            }
        }
        if (b.face()) {
            if (isPlayer(e)) {
                ChatRenderer.drawFace(g, e.sender, e.name, 0, y - FACE, a);
            } else {
                ServerIcon.draw(g, 0, y - FACE, FACE, a);
            }
        }
        return top;
    }

    // The chat

    static boolean visible() {
        return open;
    }

    static boolean atBottom() {
        return at < 2;
    }

    static void toBottom() {
        target = 0;
        unseen = 0;
    }

    static void changed() {
    }

    /** A message came: counted on the down button if scrolled up in its channel. */
    static void arrived(Messenger.Chan c) {
        if (c.id == Messenger.current && open && target > 2) {
            unseen++;
        }
    }

    static void scroll(double lines) {
        target = Math.max(0, target + (float) lines * 12);
    }

    /** Draws the chat (in place of the game's), moved and scaled like it. */
    static void render(ChatComponent chat, GuiGraphicsExtractor g, int ticks, boolean focused) {
        open = focused;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
            return;
        }
        Font font = mc.font;
        float s = (float) Compat.chatScale(chat);
        int bottom = Mth.floor((g.guiHeight() - 40) / s);
        int areaH = ChatMotion.chatHeightPx(chat);
        int top = bottom - areaH;
        int chatW = (int) (Compat.chatWidth(chat) / s);
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        List<Messenger.Entry> list;
        if (focused) {
            Messenger.Chan c = Messenger.open();
            list = c == null ? List.of() : c.list;
        } else { // closed: what arrived lately, from every channel
            list = new ArrayList<>();
            for (Messenger.Chan c : Messenger.channels.values()) {
                for (Messenger.Entry e : c.list) {
                    if (e.arrived > now - SHOWN_MS) {
                        list.add(e);
                    }
                }
            }
            list.sort(Comparator.comparingLong(e -> e.arrived));
            target = at = 0;
        }

        ChatUi.clearHits();
        ChatUi.frame(s, 0, top, bottom, ChatMotion.shiftX(chat));
        Gfx.scissor(g, 0, Math.round(top * s + ChatMotion.shiftYNow), g.guiWidth(), Math.round(bottom * s + ChatMotion.shiftYNow) + 2);
        Gfx.push(g);
        Gfx.scale(g, s, s);
        Gfx.move(g, 4f, 0f);
        int y = bottom + Math.round(at);
        int total = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            Messenger.Entry e = list.get(i);
            Box b = box(e, i > 0 ? list.get(i - 1) : null, i + 1 < list.size() ? list.get(i + 1) : null, chatW, font);
            float a = 1f;
            if (!focused) {
                long age = now - e.arrived;
                a = (float) Mth.clamp((SHOWN_MS - age) / 1000.0, 0, 1) * (float) (mc.options.chatOpacity().get() * 0.9 + 0.1);
            }
            if (y - b.h() < bottom && a > 0.02f) {
                draw(g, b, y, chatW, a, font);
            }
            int step = b.h() + (i > 0 && sameRun(list.get(i - 1), e) ? GAP : RUN_GAP);
            y -= step;
            total += step;
            if (y < top - 200 && !focused) {
                break;
            }
        }
        Gfx.pop(g);
        Gfx.unscissor(g);

        if (focused) {
            float max = Math.max(0, total - areaH + 8);
            target = Math.min(target, max);
            at += (target - at) * (1f - (float) Math.exp(-dt * 16));
            if (Math.abs(target - at) < 0.3f) {
                at = target;
            }
            Messenger.Chan c = Messenger.open();
            if (c != null && at >= max - 4 && c.more) { // at the top: the page before
                Messenger.older();
            }
            if (atBottom()) {
                unseen = 0;
                if (c != null && (c.unread > 0 || c.newest() > c.lastRead)) {
                    Messenger.read();
                }
            }
        }
    }
}
