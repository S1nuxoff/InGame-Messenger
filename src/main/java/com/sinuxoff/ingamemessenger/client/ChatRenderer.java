package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.sinuxoff.ingamemessenger.core.text.ChatText;
import com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor;

import java.util.List;
import java.util.UUID;

/** Draws the messenger chat: bubbles, faces, times, reactions, quotes, stickers, the scroll-down button. */
final class ChatRenderer {
    private ChatRenderer() {
    }

    /** Text starts after the face. */
    public static final int AV = 29;
    private static final int FACE = 16, BUBBLE_PAD = 9, PAD_V = 3;
    private static final long SLIDE_MS = 220;
    /** Bubble color: translucent dark glass. */
    private static final int BUBBLE = 0x1E2333, BUBBLE_SYSTEM = 0x15181F, BUBBLE_MENTION = 0x332A5C, BUBBLE_ALPHA = 0xA8;

    /**
     * Line to start from: the bottom line of the message owning the lowest visible line, which may be below the view,
     * so a sticker scrolled partly out still shows its visible part.
     */
    private static int firstStart(List<GuiMessage.Line> lines, int scroll) {
        int b = Math.min(scroll, lines.size() - 1);
        while (b > 0 && b >= scroll - 40 && !lines.get(b).endOfEntry()) {
            b--;
        }
        return b < 0 ? 0 : b - scroll;
    }

    static final int TYPING_ROW = 12;

    /** Typing row under the newest message, next to the faces. */
    static void drawTyping(GuiGraphicsExtractor g) {
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        float cs = (float) Compat.chatScale(chat);
        int y = g.guiHeight() - ChatInput.BOTTOM - ChatInput.H - 6 - 9;
        String who = Typing.first();
        if (who != null && Prefs.seeTyping()) { // face in the face column, like a message
            var conn = Minecraft.getInstance().getConnection();
            UUID id = Ai.NAME.equals(who) && Ai.ID.equals(ChatChannels.channel) ? Ai.ID
                    : conn != null && conn.getPlayerInfo(who) != null ? conn.getPlayerInfo(who).getProfile().id() : null;
            int fs = Math.round(11 * cs);
            drawFace(g, id, who, Math.round(ChatMotion.shiftX(chat) + 4 * cs + (FACE * cs - fs) / 2f), y - 2, fs, 1f);
        }
        Typing.render(g, (int) (ChatMotion.shiftX(chat) + (4 + AV) * cs), y);
    }
    private static final int BUBBLE_MINE = 0x33287A; // our own messages are violet

    public static int textX(GuiMessageTag tag) {
        Integer x = ChatModel.textXOf.get(tag);
        return x != null ? x : AV;
    }

    /** Fade-in of a line's message, 1 = fully visible. */
    public static float alphaOf(GuiMessageTag tag) {
        return tag != null && tag == ChatMotion.newest ? ChatMotion.newestAlpha : 1f;
    }

    // Drawing

    private record Layout(float scale, int bottom, int lineH, int perPage, double opacity, int textOff) {
        /** Y of line j's text (Minecraft's own offset within the line). */
        int textY(int j) {
            return bottom - j * lineH + textOff;
        }
    }

    private static Layout layout(ChatComponent chat, GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        float scale = (float) Compat.chatScale(chat);
        double spacing = mc.options.chatLineSpacing().get();
        return new Layout(scale, Mth.floor((g.guiHeight() - 40) / scale), (int) (9.0 * (spacing + 1.0)),
                chat.getLinesPerPage(), mc.options.chatOpacity().get() * 0.9 + 0.1,
                (int) Math.round(-8.0 * (spacing + 1.0) + 4.0 * spacing));
    }

    private static boolean hidden() {
        return Minecraft.getInstance().options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN;
    }

    /** Message visibility from the chat's own fade-out, 0..1. */
    private static float fade(GuiMessage.Line line, int tickCount, boolean focused, double opacity) {
        int age = tickCount - line.addedTime();
        if (age >= 200 && !focused) {
            return 0;
        }
        return (float) ((focused ? 1.0 : timeFactor(age)) * opacity);
    }

    /** Rounded translucent bubble. */
    private static void bubble(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb, float a) {
        int r = Math.min(11, h / 2); // a single line becomes a pill
        // lighter and more opaque over the open chat's shade, so they stand out
        int lift = Math.round(ChatMotion.shade * 0x0A);
        int c = Math.min(255, (rgb >> 16 & 0xFF) + lift) << 16 | Math.min(255, (rgb >> 8 & 0xFF) + lift) << 8 | Math.min(255, (rgb & 0xFF) + lift + 2);
        int alpha = Math.round(BUBBLE_ALPHA + ChatMotion.shade * (0xC4 - BUBBLE_ALPHA));
        Panel.round(g, x, y, w, h, r, (int) (a * alpha) << 24 | c);
    }

    /**
     * Runs before Minecraft draws the chat text: bubbles, faces, reactions, and the newest message's slide. Walks the
     * messages from the newest up; a face goes next to the last message of a group. Leaves the chat area clipped for
     * Minecraft's text until {@link #render}. With the chat open, records hit areas for {@link ChatUi}.
     */
    public static void renderBubbles(ChatComponent chat, GuiGraphicsExtractor g, int tickCount, boolean focused) {
        ChatMotion.slide = 0;
        ChatMotion.newest = null;
        ChatMotion.newestAlpha = 1f;
        ChatMotion.clipTop = ChatMotion.clipBottom = -1;
        ChatUi.clearHits();
        if (hidden()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        roomForBubbles(mc);
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = acc.ingamemessenger$lines();
        int scroll = acc.ingamemessenger$scroll();
        Layout L = layout(chat, g);

        // the newest message slides in from below (unless scrolled up)
        if (scroll == 0 && !lines.isEmpty() && lines.get(0).tag() != null && ChatModel.msgs.containsKey(lines.get(0).tag())) {
            GuiMessageTag tag = lines.get(0).tag();
            ChatModel.Msg m = ChatModel.msgs.get(tag);
            float t = Math.max(0f, Math.min(1f, (System.currentTimeMillis() - m.createdMs()) / (float) SLIDE_MS));
            if (t < 1f) {
                float e = 1 - (1 - t) * (1 - t) * (1 - t);
                int n = 0;
                while (n < lines.size() && lines.get(n).tag() == tag) {
                    n++;
                }
                ChatMotion.slide = (1 - e) * n * L.lineH();
                ChatMotion.newest = tag;
                ChatMotion.newestAlpha = e;
            }
        }

        // smooth scrolling (see scrollPos)
        long nowMs = System.currentTimeMillis();
        float dt = ChatMotion.lastFrameMs == 0 ? 0 : Math.min(0.1f, (nowMs - ChatMotion.lastFrameMs) / 1000f);
        ChatMotion.lastFrameMs = nowMs;
        int maxScroll = Math.max(0, lines.size() - L.perPage());
        if (!focused) {
            ChatMotion.scrollPos = ChatMotion.scrollTarget = ChatMotion.scrollFrac = 0;
            ChatMotion.scrollApplied = scroll;
        } else {
            if (scroll != ChatMotion.scrollApplied) { // scroll changed elsewhere: jump
                float d = scroll - (ChatMotion.scrollApplied < 0 ? 0 : ChatMotion.scrollApplied);
                ChatMotion.scrollPos += d;
                ChatMotion.scrollTarget += d;
            }
            ChatMotion.scrollTarget = Math.max(0, Math.min(ChatMotion.scrollTarget, maxScroll));
            ChatMotion.scrollPos += (ChatMotion.scrollTarget - ChatMotion.scrollPos) * (1f - (float) Math.exp(-dt * 16));
            if (Math.abs(ChatMotion.scrollTarget - ChatMotion.scrollPos) < 0.01f) {
                ChatMotion.scrollPos = ChatMotion.scrollTarget;
            }
            ChatMotion.scrollPos = Math.max(0, Math.min(ChatMotion.scrollPos, maxScroll));
            int whole = (int) Math.floor(ChatMotion.scrollPos);
            ChatMotion.scrollFrac = ChatMotion.scrollPos - whole;
            if (whole != scroll) {
                acc.ingamemessenger$setScroll(whole);
                scroll = whole;
            }
            ChatMotion.scrollApplied = whole;
            ChatMotion.slide += ChatMotion.scrollFrac * L.lineH(); // fractional part as a pixel offset, one extra line on top
        }

        int top = L.bottom() - L.perPage() * L.lineH();
        ChatMotion.clipTop = Math.round(top * L.scale() + ChatMotion.shiftYNow);
        ChatMotion.clipBottom = Math.round(L.bottom() * L.scale() + ChatMotion.shiftYNow) + 2;
        Gfx.scissor(g, 0, ChatMotion.clipTop, g.guiWidth(), ChatMotion.clipBottom);
        // slim scroll bar inside the chat area (vanilla's is hidden)
        if (focused && lines.size() > L.perPage()) {
            Gfx.push(g);
            Gfx.scale(g, L.scale(), L.scale());
            int areaH = L.perPage() * L.lineH(), total = lines.size() - L.perPage();
            int barH = Math.max(10, areaH * L.perPage() / lines.size());
            float pos = Math.max(0f, Math.min(1f, ChatMotion.scrollPos / total));
            int barX = (int) (Compat.chatWidth(chat) / L.scale()) + 10;
            Panel.round(g, barX, Math.round(L.bottom() - barH - pos * (areaH - barH)), 3, barH, 1, 0x58FFFFFF);
            Gfx.pop(g);
        }
        Gfx.push(g);
        Gfx.scale(g, L.scale(), L.scale());
        Gfx.move(g, 4f, ChatMotion.slide);
        ChatUi.frame(L.scale(), ChatMotion.slide, top, L.bottom(), ChatMotion.shiftX(chat));
        int chatW = (int) (Compat.chatWidth(chat) / L.scale());
        ChatModel.textXOf.clear();
        if (focused && scroll >= lines.size() - L.perPage() - 2) {
            ChatPages.wantOlder();
        }
        int j = firstStart(lines, scroll), shown = L.perPage() + ChatMotion.extraLines();
        // sender of the message below, also from below the view, so a group's face stays next to its last message
        // while scrolling instead of jumping to the lowest visible one
        UUID below = null;
        long belowMs = 0;
        if (j + scroll > 0) {
            GuiMessage.Line under = lines.get(j + scroll - 1);
            ChatModel.Msg um = under.tag() == null ? null : ChatModel.msgs.get(under.tag());
            below = um != null && um.player() ? um.sender() : null;
            belowMs = um != null ? um.createdMs() : 0;
        }
        while (j + scroll < lines.size() && j < shown) {
            int start = j, maxW = 0;
            GuiMessage.Line first = lines.get(j + scroll);
            do {
                maxW = Math.max(maxW, mc.font.width(lines.get(j + scroll).content()));
                j++;
            } while (j + scroll < lines.size() && j < shown && !lines.get(j + scroll).endOfEntry());
            ChatModel.Msg m = first.tag() == null ? null : ChatModel.msgs.get(first.tag());
            UUID sender = m != null && m.player() ? m.sender() : null;
            // face next to the last message of a group (one sender, at most 2 minutes apart)
            boolean faceHere = m != null && m.player() && (sender == null || !sender.equals(below)
                    || belowMs - m.createdMs() > 120_000);
            below = sender;
            belowMs = m != null ? m.createdMs() : 0;
            float a = fade(first, tickCount, focused, L.opacity()) * alphaOf(first.tag());
            if (a < 0.02f) {
                continue;
            }
            // content lines (without the spacer and the reaction row), and the bubble around their text
            List<ChatModel.Reaction> rs = m != null && m.mid() >= 0 ? ChatModel.reactions.get(m.mid()) : null;
            int reactLine = start + (m != null && m.spacer() ? 1 : 0);
            // a text message's reactions sit inside its bubble under the text; a sticker's below it.
            // pictures always have a bubble; without caption or reactions the time sits on them
            ChatAlbums.AlbumInfo ai = first.tag() == null ? null : ChatAlbums.albumOf.get(first.tag());
            boolean timeOnPics = ai != null && ai.caption().isEmpty() && rs == null;
            boolean inside = rs != null && m.player() && !m.sticker();
            int bottomLine = reactLine + (rs != null && !inside ? 1 : 0), topLine = j - 1;
            // date separator above a day's first message, centered
            Long dateDay = first.tag() == null ? null : ChatModel.datedOf.get(first.tag());
            if (dateDay != null && topLine >= bottomLine) {
                if (!ChatModel.hiddenDate.contains(first.tag())) { // centered in the gap between this bubble and the one above
                    ChatDates.drawDate(g, dateDay, L.textY(topLine) - 2, chatW, a);
                }
                topLine -= Math.min(ChatDates.DATE_LINES, topLine - bottomLine + 1);
            }
            if (bottomLine > topLine) {
                continue;
            }
            int textTop = L.textY(topLine) - 1, textBottom = L.textY(bottomLine) + 8 + (inside ? 3 : 0);
            int bx = AV - BUBBLE_PAD, bottomY = textBottom + PAD_V;
            if (scroll == 0 && first.tag() != null && first.tag() == lines.get(0).tag()) { // newest message: gap below its bubble
                ChatMotion.newestGap = L.bottom() - bottomY;
            }
            if (m == null) { // not ours (arrived before the mod took over): leave it as is
                bubble(g, -4, textTop - PAD_V, maxW + 2 * BUBBLE_PAD - 2, textBottom - textTop + 2 * PAD_V, BUBBLE_SYSTEM, a);
                continue;
            }
            // time at the bottom right, after the last line or the reactions
            String time = (ChatModel.editedSet.contains(first.tag()) ? Component.translatable("ingamemessenger.edited").getString() + " " : "") + ChatDates.time(m);
            boolean checks = m != null && m.player() && m.mid() >= 0 && ChatReplies.mine(m);
            String timeOnly = time;
            if (checks) { // room for the read checks after the time
                time = time + " \u2713\u2713";
            }
            boolean mineRight = Prefs.ownRight() && ChatReplies.mine(m);
            int lastW = inside ? reactionsWidth(rs) : mc.font.width(lines.get(bottomLine + scroll).content());
            ChatVoiceNotes.VoiceInfo vi = ChatVoiceNotes.voiceOf.get(first.tag());
            if (vi != null) { // room for the voice player
                maxW = Math.max(maxW, ChatVoiceNotes.VOICE_W);
                if (!inside) {
                    lastW = ChatVoiceNotes.VOICE_W;
                }
            }
            ChatPolls.PollInfo pli = ChatPolls.pollOf.get(first.tag());
            if (pli != null) { // room for the poll options; the time goes on the count line
                maxW = Math.max(maxW, ChatPolls.POLL_W);
            }
            ChatText.Location li = ChatLocations.locOf.get(first.tag());
            if (li != null) { // room for the location card
                maxW = Math.max(maxW, ChatLocations.LOC_W);
                if (!inside) {
                    lastW = ChatLocations.LOC_W;
                }
            }
            int bw = Math.max(maxW, lastW + ChatDates.TIME_GAP + ChatDates.timeWidth(time)) + 2 * BUBBLE_PAD;
            if (ai != null) { // bubble around the pictures, or the caption if wider
                bw = Math.max(bw, ai.width() + 2 * ChatAlbums.ALBUM_PAD);
            }
            if (mineRight) { // ours, on the right edge
                bx = chatW - 2 - bw;
            }
            int tx = bx + BUBBLE_PAD;
            // smaller, on the last line's baseline (centered in the reaction row if there is one)
            float timeX = bx + bw - BUBBLE_PAD - ChatDates.timeWidth(time) + 2, timeY = inside ? textBottom - 7.1f : textBottom - 6.25f;
            if (!m.player()) { // join, server, mod...: plain bubble with an icon as face
                bubble(g, bx, textTop - PAD_V, bw, textBottom - textTop + 2 * PAD_V, BUBBLE_SYSTEM, a);
                ChatDates.drawTime(g, time, timeX, timeY, a);
                ChatModel.Icon icon = m.icon();
                if (icon.item() != null) {
                    if (a > 0.35f) {
                        Gfx.item(g, icon.item(), 0, bottomY - FACE);
                    }
                } else {
                    drawFace(g, icon.face(), icon.name(), 0, bottomY - FACE, a);
                    ChatUi.face(icon.face(), icon.name(), 0, bottomY - FACE, FACE, FACE);
                }
                continue;
            }
            int bubbleTop = textTop - PAD_V, bubbleRight;
            if (m.sticker()) {
                Library.Sticker st = Library.get(m.pack(), m.id());
                int[] size = stickerSize(st, m, L);
                if (mineRight) {
                    tx = chatW - 2 - size[0];
                    bx = tx - BUBBLE_PAD;
                }
                bubbleTop = textBottom - size[1];
                bubbleRight = tx + size[0];
                if (m.named()) { // just the name, on a small bubble
                    int ny = L.textY(topLine) - 1;
                    int nw = mc.font.width(lines.get(topLine + scroll).content());
                    bubble(g, bx, ny - PAD_V, nw + 2 * BUBBLE_PAD, 9 + 2 * PAD_V, BUBBLE, a);
                    ChatUi.face(m.sender(), m.name(), tx, ny, nw, 9);
                }
            } else {
                bubble(g, bx, bubbleTop, bw, textBottom - textTop + 2 * PAD_V,
                        ChatModel.mentioned.contains(first.tag()) ? BUBBLE_MENTION : ChatReplies.mine(m) ? BUBBLE_MINE : BUBBLE, a);
                if (!timeOnPics) {
                    ChatDates.drawTime(g, timeOnly, timeX, timeY, a);
                    if (checks) {
                        ChatReads.drawChecks(g, m.mid(), timeX + ChatDates.timeWidth(timeOnly + " "), timeY, a);
                    }
                }
                bubbleRight = bx + bw;
                if (m.named()) {
                    ChatUi.face(m.sender(), m.name(), tx, L.textY(topLine) - 1, mc.font.width(lines.get(topLine + scroll).content()), 9);
                }
            }
            ChatModel.textXOf.put(first.tag(), tx);
            if (vi != null) {
                int vt = topLine - (m.named() ? 1 : 0) - (ChatReplies.replyOf.containsKey(first.tag()) ? 1 : 0);
                if (vt >= bottomLine) {
                    ChatVoiceNotes.drawVoice(g, vi, m.name(), tx, L.textY(vt) - 1, a);
                }
            }
            if (ai != null) {
                // from the message's real top line, which may be above the view
                int realTop = j - 1;
                while (realTop + 1 + scroll < lines.size() && lines.get(realTop + 1 + scroll).tag() == first.tag()) {
                    realTop++;
                }
                int at = realTop - (dateDay != null ? ChatDates.DATE_LINES : 0) - (m.named() ? 1 : 0) - (ChatReplies.replyOf.containsKey(first.tag()) ? 1 : 0);
                int ax = bx + (bw - ai.width()) / 2, ay = L.textY(at) - 1;
                ChatAlbums.drawAlbum(g, ai, m.name(), ax, ay, a);
                if (timeOnPics) { // time on a small dark pill over the last picture's corner
                    int tw = ChatDates.timeWidth(time) + 8, px = ax + ai.width() - tw - 3, py = ay + ai.height() - 12;
                    Panel.round(g, px, py, tw, 9, 4, (int) (a * 0x90) << 24);
                    ChatDates.smallText(g, timeOnly, px + 4, py + 2, (int) (a * 0xE0) << 24 | 0xE4E6EE);
                    if (checks) {
                        ChatReads.drawChecks(g, m.mid(), px + 4 + ChatDates.timeWidth(timeOnly + " "), py + 2, a);
                    }
                }
            }
            if (pli != null) { // from the bottom: count line, options above it
                int foot = reactLine + (rs != null ? 1 : 0);
                ChatPolls.drawPoll(g, pli, tx, L.textY(foot) - 1, L.lineH(), a);
            }
            if (li != null) {
                int lt = topLine - (m.named() ? 1 : 0) - (ChatReplies.replyOf.containsKey(first.tag()) ? 1 : 0);
                if (lt >= bottomLine) {
                    ChatLocations.drawLoc(g, li, tx, L.textY(lt) - 1, a);
                }
            }
            Integer quoted = ChatReplies.replyOf.get(first.tag());
            if (quoted != null && topLine - (m.named() ? 1 : 0) >= bottomLine) {
                int ql = topLine - (m.named() ? 1 : 0), qy = L.textY(ql) - 1;
                int qw = mc.font.width(lines.get(ql + scroll).content());
                if (m.sticker()) { // stickers have no bubble, so the quote gets a small one
                    bubble(g, bx, qy - PAD_V, qw + 2 * BUBBLE_PAD, 9 + 2 * PAD_V, BUBBLE, a);
                }
                Panel.round(g, tx - 3, qy - 1, qw + 6, 11, 4, (int) (a * 0x26) << 24 | 0xFFFFFF);
                String[] qi = ChatReplies.replyInfo(quoted);
                g.fill(tx - 2, qy, tx, qy + 9, (int) (a * 255) << 24 | (qi != null && qi[0] != null ? ChatDecor.nameColor(qi[0]) : 0x8E95A6));
                ChatUi.quote(quoted, tx - 3, qy - 1, qw + 6, 11);
            }
            if (m.mid() >= 0 && m.mid() == ChatReplies.flashMid && System.currentTimeMillis() - ChatReplies.flashMs < 1400) { // jumped to
                float f = 1f - (System.currentTimeMillis() - ChatReplies.flashMs) / 1400f;
                Panel.round(g, bx - 2, bubbleTop - 2, bubbleRight - bx + 4, bottomY - bubbleTop + 4, 10, (int) (f * 0x50) << 24 | 0x7B68FF);
            }
            if (m.mid() >= 0) {
                ChatUi.bubble(m.mid(), bx, bubbleTop, bubbleRight, bottomY);
            }
            if (rs != null) {
                if (inside) {
                    drawReactions(g, rs, m.mid(), tx, L.textY(reactLine) + 1, a);
                } else {
                    drawReactions(g, rs, m.mid(), bx, L.textY(reactLine) + 3, a);
                }
            }
            if (faceHere && !mineRight) { // no face next to our own right-side messages
                drawFace(g, m.sender(), m.name(), 0, bottomY - FACE, a);
                ChatUi.face(m.sender(), m.name(), 0, bottomY - FACE, FACE, FACE);
            }
        }
        if (ChatMotion.scrollPos < 0.5f) {
            unseen = 0;
            if (focused) {
                ChatReads.iHaveRead();
            }
        }
        Gfx.unscissor(g); // the button below reaches past the message area
        if (focused && ChatMotion.scrollPos > 1.5f) {
            // bottom level with the input's bottom on screen: undo the line slide exactly, fractions included
            float want = (g.guiHeight() - ChatInput.BOTTOM - ChatInput.H - 6 - ChatMotion.shiftYNow) / L.scale() - DOWN;
            int iy = (int) Math.floor(want);
            float dy = -ChatMotion.slide + (want - iy);
            Gfx.push(g);
            Gfx.move(g, 0f, dy);
            drawDown(g, chatW - 4 - DOWN, iy, dy, Math.min(1f, (ChatMotion.scrollPos - 1.5f) / 2f));
            Gfx.pop(g);
        }
        Gfx.pop(g);
    }

    // Scroll-down button

    /** Messages received while scrolled up (shown on the button). */
    private static int unseen;
    private static final int DOWN = 18;

    static void arrived() {
        if (ChatMotion.scrollTarget > 0.5f) {
            unseen++;
        }
    }

    /** Round arrow button with the new message count, bottom right of the chat. */
    private static void drawDown(GuiGraphicsExtractor g, int x, int y, float dy, float a) {
        boolean over = ChatUi.over(x, y + dy, DOWN, DOWN);
        Panel.round(g, x - 1, y - 1, DOWN + 2, DOWN + 2, DOWN / 2 + 1, (int) (a * 0x40) << 24 | 0xFFFFFF);
        Panel.round(g, x, y, DOWN, DOWN, DOWN / 2, (int) (a * 0xF0) << 24 | (over ? 0x2A3040 : 0x1B1F2A));
        Panel.drawIcon(g, "down", x + 4, y + 4.5f, DOWN - 8, (int) (a * 255) << 24 | 0xE4E6EE);
        if (unseen > 0) {
            String n = unseen > 99 ? "99+" : String.valueOf(unseen);
            var font = Minecraft.getInstance().font;
            int w = Math.max(11, font.width(n) + 6);
            int px = x + DOWN / 2 - w / 2, py = y - 8;
            Panel.round(g, px, py, w, 11, 5, (int) (a * 255) << 24 | 0x7B68FF);
            Gfx.text(g, font, n, px + (w - font.width(n)) / 2 + 1, py + 2, (int) (a * 255) << 24 | 0xFFFFFF, false);
        }
        ChatUi.down(x, y + dy - (unseen > 0 ? 8 : 0), DOWN, DOWN + (unseen > 0 ? 8 : 0));
    }

    /** Scrolls smoothly to the newest message. */
    static void toBottom() {
        ChatMotion.scrollTarget = 0;
        unseen = 0;
    }

    private static int pillW(ChatModel.Reaction r) {
        return 4 + 9 + 3 + Minecraft.getInstance().font.width(String.valueOf(r.names().size())) + 5;
    }

    private static int reactionsWidth(List<ChatModel.Reaction> rs) {
        int w = 0;
        for (ChatModel.Reaction r : rs) {
            w += pillW(r) + 3;
        }
        return w - 3;
    }

    /** Reaction pills (emoji and count), ours in the accent color. */
    private static void drawReactions(GuiGraphicsExtractor g, List<ChatModel.Reaction> rs, int mid, int x, int y, float a) {
        var font = Minecraft.getInstance().font;
        for (ChatModel.Reaction r : rs) {
            String n = String.valueOf(r.names().size());
            int w = pillW(r), h = 11;
            int bg = r.mine() ? (int) (a * 0xE0) << 24 | 0x5B4BD6 : (int) (a * 0x24) << 24 | 0xFFFFFF;
            Panel.round(g, x, y, w, h, 5, bg);
            ChatUi.emoji(g, r.emoji(), x + 4, y + 1, 9, a);
            Gfx.text(g, font, n, x + 16, y + 2, (int) (a * 255) << 24 | (r.mine() ? 0xFFFFFF : 0xC8CCD8), false);
            ChatUi.pill(mid, r, x, y, w, h);
            x += w + 3;
        }
    }

    /** Draws a Minecraft chat line clipped to the chat area. */
    public static int clipped(GuiGraphicsExtractor g, java.util.function.IntSupplier draw) {
        if (ChatMotion.clipTop < 0 || ChatMotion.slide == 0) {
            return draw.getAsInt();
        }
        Gfx.scissor(g, 0, ChatMotion.clipTop, g.guiWidth(), ChatMotion.clipBottom);
        try {
            return draw.getAsInt();
        } finally {
            Gfx.unscissor(g);
        }
    }

    /** Bubbles need some space between lines: raises the chat line spacing to 0.25 once. */
    private static boolean spacingChecked;

    private static void roomForBubbles(Minecraft mc) {
        if (spacingChecked) {
            return;
        }
        spacingChecked = true;
        if (!Prefs.spacingSet() && mc.options.chatLineSpacing().get() < 0.2) {
            mc.options.chatLineSpacing().set(0.25);
            mc.options.save();
        }
        Prefs.markSpacingSet();
    }

    static void drawFace(GuiGraphicsExtractor g, UUID id, String name, int x, int y, float alpha) {
        drawFace(g, id, name, x, y, FACE, alpha);
    }

    static void drawFace(GuiGraphicsExtractor g, UUID id, String name, int x, int y, int size, float alpha) {
        if (Ai.ID.equals(id)) {
            Panel.round(g, x, y, size, size, Math.max(2, size / 3), (int) (alpha * 255) << 24 | 0xF4F6FB);
            Panel.drawIcon(g, "ai_logo", x + size * 0.08f, y + size * 0.08f, size * 0.84f, (int) (alpha * 255) << 24 | 0xFFFFFF);
            return;
        }
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : id != null ? conn.getPlayerInfo(id) : name != null ? conn.getPlayerInfo(name) : null;
        UUID skinId = id != null ? id : UUID.nameUUIDFromBytes(String.valueOf(name).getBytes());
        //? if >=1.20.2 {
        var face = Compat.skinTexture(info != null ? info.getSkin() : DefaultPlayerSkin.get(skinId));
        //?} else {
        /*var face = info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(skinId);
        *///?}
        // face and hat layer, with rounded corners
        float r = size * 0.3f, k = 1 / 64f;
        Gfx.pixelated(face);
        Library.roundedTex(g, face, x, y, size, size, r, 8 * k, 8 * k, 16 * k, 16 * k, alpha);
        Library.roundedTex(g, face, x, y, size, size, r, 40 * k, 8 * k, 48 * k, 16 * k, alpha);
    }

    /** {w, h} of a sticker in the chat: its own shape, within the size setting's box. */
    private static int[] stickerSize(Library.Sticker st, ChatModel.Msg m, Layout L) {
        boolean above = m.named() || m.mid() >= 0 && ChatModel.byMid.containsKey(m.mid()) && ChatReplies.replyOf.containsKey(ChatModel.byMid.get(m.mid()));
            int boxH = m.lines() * L.lineH() - 2 - (above ? 6 : 0); // gap below the name or quote bubble
        if (st == null) {
            return new int[]{boxH, boxH};
        }
            int fullH = Prefs.stickerLines() * L.lineH() - 2;
        int w = Math.round(Math.min(boxH * st.aspect(), fullH * ChatMessages.MAX_ASPECT));
        int h = Math.min(boxH, Math.round(w / st.aspect()));
        return new int[]{Math.round(h * st.aspect()), h};
    }

    /** After Minecraft draws the chat: stickers over their blank lines, right of the face. */
    public static void render(ChatComponent chat, GuiGraphicsExtractor g, int tickCount, boolean focused) {
        if (ChatModel.msgs.isEmpty() || hidden()) {
            return;
        }
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = acc.ingamemessenger$lines();
        int scroll = acc.ingamemessenger$scroll();
        Layout L = layout(chat, g);
        int top = L.bottom() - L.perPage() * L.lineH();
        Gfx.scissor(g, 0, Math.round(top * L.scale() + ChatMotion.shiftYNow), g.guiWidth(), Math.round(L.bottom() * L.scale() + ChatMotion.shiftYNow) + 2);
        Gfx.push(g);
        Gfx.scale(g, L.scale(), L.scale());
        Gfx.move(g, 4f, ChatMotion.slide, 60f);
        for (int j = firstStart(lines, scroll); j + scroll < lines.size() && j < L.perPage() + ChatMotion.extraLines(); j++) {
            GuiMessage.Line line = lines.get(j + scroll);
            if (line == null || !line.endOfEntry() || line.tag() == null) {
                continue;
            }
            ChatModel.Msg m = ChatModel.msgs.get(line.tag());
            if (m == null || !m.sticker()) {
                continue;
            }
            float a = fade(line, tickCount, focused, L.opacity()) * alphaOf(line.tag());
            if (a < 0.02f) {
                continue;
            }
            Library.Sticker st = Library.get(m.pack(), m.id());
            if (st == null) {
                continue;
            }
            int bottomLine = j + (m.spacer() ? 1 : 0) + (ChatModel.reactionRow(line.tag()) ? 1 : 0);
            int bottomY = L.textY(bottomLine) + 8;
            int[] size = stickerSize(st, m, L);
            int sx = textX(line.tag());
            if (Library.IMG.equals(m.pack())) { // picture: click opens it (or a placeholder if deleted)
                if (Library.failed(st)) {
                    Panel.round(g, sx, bottomY - size[1], size[0], size[1], 8, (int) (a * 0xC0) << 24 | 0x1B1F2A);
                    ChatDates.smallText(g, Component.translatable("ingamemessenger.image.gone").getString(), sx + 8, bottomY - size[1] / 2f - 3,
                            (int) (a * 0xC0) << 24 | 0x8E95A6);
                } else if (!Library.ready(st)) { // loading: dark placeholder with spinning dots
                    int px = sx, py = bottomY - size[1];
                    Panel.round(g, px, py, size[0], size[1], 8, (int) (a * 0xC0) << 24 | 0x1B1F2A);
                    int ph = (int) (System.currentTimeMillis() / 300 % 3), cx = px + size[0] / 2, cy = py + size[1] / 2;
                    for (int i = 0; i < 3; i++) {
                        Panel.round(g, cx - 9 + i * 7, cy - 2, 4, 4, 2, (int) (a * (i == ph ? 0xF0 : 0x60)) << 24 | 0xC8CCD8);
                    }
                } else {
                    Library.draw(g, st, sx, bottomY - size[1], size[0], size[1], a);
                }
                ChatUi.picture(st, m.name(), sx, bottomY - size[1], size[0], size[1]);
            } else {
                Library.draw(g, st, sx, bottomY - size[1], size[0], size[1], a);
            }
            // time on a small dark pill over the sticker's bottom right corner
            String time = ChatDates.time(m);
            int tw = ChatDates.timeWidth(time) + 8, tx = sx + size[0] - tw - 2, ty = bottomY - 11;
            Panel.round(g, tx, ty, tw, 9, 4, (int) (a * 0x90) << 24);
            ChatDates.smallText(g, time, tx + 4, ty + 2, (int) (a * 0xE0) << 24 | 0xE4E6EE);
        }
        Gfx.pop(g);
        Gfx.unscissor(g);
    }

    private static double timeFactor(int age) {
        double d = 1.0 - age / 200.0;
        d = Mth.clamp(d * 10.0, 0.0, 1.0);
        return d * d;
    }
}
