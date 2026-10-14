package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor;

/**
 * The chat input and everything around it: rounded field with the sticker and attach buttons, send/microphone button,
 * character counter, chat list column, search bar, reply/edit bar, picture tray, @mention list, mini player.
 */
public final class ChatInput {
    /** Field height, distance from the bottom, max width. */
    static final int H = 18, BOTTOM = 10, MAX_W = 280;
    static final int SEND = 14;
    private static final int LIMIT = 256;

    private ChatInput() {
    }

    static int fieldW(int sw) {
        int w = Math.min(MAX_W + 40, Compat.chatWidth(Minecraft.getInstance().gui.hud.getChat()) + 26);
        return Math.max(190, Math.min(sw - fieldX(sw) - 8, w));
    }

    /** Left aligned with the chat, next to the chat list. */
    static int fieldX(int sw) {
        return Math.max(4, Math.round(ChatMotion.shiftX(Minecraft.getInstance().gui.hud.getChat())) + 2);
    }

    static int fieldY(int sh) {
        return sh - BOTTOM - H;
    }

    /** Y of the input text (Minecraft draws it from the top of its box). */
    static int textY(int sh) {
        return fieldY(sh) + (H - 8) / 2;
    }

    private static int sendX(int sw) {
        return fieldX(sw) + fieldW(sw) - 3 - SEND;
    }

    private static int sendY(int sh) {
        return fieldY(sh) + (H - SEND) / 2;
    }

    /** Sticker button: at the end of the field, before the send button. */
    static int smileX(int sw) {
        return sendX(sw) - Panel.BTN - 6;
    }

    /** Location button, before the sticker button. */
    static int pinX(int sw) {
        return smileX(sw) - Panel.BTN - 6;
    }

    private static int pinY(int sh) {
        return fieldY(sh) + (H - Panel.BTN) / 2;
    }

    private static boolean pinShown() {
        return !VoiceClient.recording() && editId < 0 && !Ai.ID.equals(ChatChannels.channel()) && !ChatChannels.SERVER.equals(ChatChannels.channel());
    }

    static boolean overPin(double mx, double my, int sw, int sh) {
        return pinShown() && mx >= pinX(sw) - 2 && mx < pinX(sw) + Panel.BTN + 2 && my >= pinY(sh) - 2 && my < pinY(sh) + Panel.BTN + 2;
    }

    static int inputX(int sw) {
        return fieldX(sw) + 11 + SEARCH_ROOM;
    }

    // Chat list column next to the open chat, like Discord's: the public chat, then private chats (face and unread
    // count), then "+" to start one. The selected chat has a bar on its side; hovering shows the name.

    private record Item(long channel, String name, int y) {
    }

    private static final java.util.List<Item> items = new java.util.ArrayList<>();
    private static int sideX, plusY = -1;
    private static boolean picking;
    private static final int ITEM = 22, ICON = 18;

    private static void drawTabs(GuiGraphicsExtractor g, int sw, int sh, int mx, int my) {
        var mc = Minecraft.getInstance();
        var font = mc.font;
        float open = ChatMotion.openness();
        items.clear();
        sideX = Prefs.chatRight() ? Math.round(ChatMotion.shiftX(mc.gui.hud.getChat())) - ChatMotion.SIDEBAR : 0;
        int top = 8, foot = footTop(sh), bottom = foot - 4;
        int cx = sideX + (ChatMotion.SIDEBAR - ICON) / 2;
        Gfx.push(g);
        Gfx.raise(g, 360);
        // thin line on the chat side
        int lx = Prefs.chatRight() ? sideX : sideX + ChatMotion.SIDEBAR - 1;
        g.fill(lx, 6, lx + 1, sh - 6, Math.round(open * 0x38) << 24 | 0xFFFFFF);
        drawFoot(g, sh, mx, my, cx);
        if (searching) { // searching: only a close button at the top
            boolean overX = mx >= sideX && mx < sideX + ChatMotion.SIDEBAR && my >= top && my < top + ITEM;
            Panel.round(g, cx, top + 2, ICON, ICON, ICON / 2, overX ? 0xFF3A4258 : 0xFF2A3040);
            Gfx.text(g, font, "\u2715", cx + (ICON - font.width("\u2715")) / 2 + 1, top + 7, 0xFFF2F3F7, false);
            plusY = -1;
            if (overX) {
                glassBox(g, sideX + ChatMotion.SIDEBAR + 3, top + 4, font.width(Component.translatable("ingamemessenger.search.close")) + 12, 14);
                Gfx.text(g, font, Component.translatable("ingamemessenger.search.close"), sideX + ChatMotion.SIDEBAR + 9, top + 7, 0xFFF2F3F7, false);
            }
            Gfx.pop(g);
            return;
        }
        java.util.List<Messenger.Chan> want = Messenger.ordered();
        int y = top + 2;
        Item hover = null;
        boolean sep = false;
        for (Messenger.Chan c : want) {
            if (y + ITEM > bottom - ITEM) {
                break;
            }
            if (c.dm() && !sep) { // between the server's channels and the private chats
                sep = true;
                g.fill(cx + 3, y + 1, cx + ICON - 3, y + 2, 0x30FFFFFF);
                y += 4;
            }
            boolean on = c.id == Messenger.current;
            boolean over = mx >= sideX && mx < sideX + ChatMotion.SIDEBAR && my >= y && my < y + ITEM;
            if (on) {
                Panel.round(g, cx - 2, y, ICON + 4, ICON + 4, 7, 0x605B4BD6);
                Panel.round(g, sideX, y + 5, 3, ICON - 6, 1, 0xFFFFFFFF);
            } else if (over) {
                Panel.round(g, cx - 2, y, ICON + 4, ICON + 4, 7, 0x30FFFFFF);
            }
            String title = c.game ? Component.translatable("ingamemessenger.dm.common").getString() : c.dm() ? c.withName : "#" + c.name;
            if (c.dm()) {
                ChatRenderer.drawFace(g, c.with, c.withName, cx + 1, y + 3, ICON - 2, 1f);
            } else {
                Panel.round(g, cx, y + 2, ICON, ICON, 6, on ? 0xFF7B68FF : 0xFF2A3040);
                if (c.game) {
                    Panel.drawIcon(g, "chats", cx + 3, y + 5, ICON - 6, 0xFFFFFFFF);
                } else {
                    Gfx.text(g, font, "#", cx + (ICON - font.width("#")) / 2 + 1, y + 7, 0xFFFFFFFF, false);
                }
            }
            if (c.unread > 0) {
                String badge = c.unread > 99 ? "99+" : String.valueOf(c.unread);
                float bwf = Math.max(9, (float) Math.ceil(font.width(badge) * Panel.smallScale()) + 4);
                int bw = Math.round(bwf), bx = cx + ICON - bw + 4, by = y;
                Panel.round(g, bx, by, bw, 9, 4, 0xFFE0475A);
                ChatDates.small(g, badge, bx + (bw - (float) Math.ceil(font.width(badge) * Panel.smallScale())) / 2f, by + 2, 0xFFFFFFFF);
            }
            if (over) {
                hover = new Item(c.id, title, y);
            }
            items.add(new Item(c.id, title, y));
            y += ITEM;
        }
        plusY = y + ITEM <= bottom ? y : -1;
        if (plusY >= 0) {
            boolean over = mx >= sideX && mx < sideX + ChatMotion.SIDEBAR && my >= plusY && my < plusY + ITEM;
            Panel.round(g, cx, plusY + 2, ICON, ICON, ICON / 2, picking || over ? 0xFF3A4258 : 0xFF2A3040);
            int py = plusY + 2;
            g.fill(cx + 5, py + 8, cx + 13, py + 10, 0xFF9BE7A6);
            g.fill(cx + 8, py + 5, cx + 10, py + 13, 0xFF9BE7A6);
            if (over && !picking) {
                hover = new Item(0, Component.translatable("ingamemessenger.dm.new_chat").getString(), plusY);
            }
        }
        Item footHover = footHover(sh, mx, my);
        if (footHover != null) {
            hover = footHover;
        }
        if (hover != null && !picking) { // name next to the column
            String n = hover.name();
            int tw = font.width(n) + 12, tx = Prefs.chatRight() ? sideX - tw - 3 : sideX + ChatMotion.SIDEBAR + 3;
            glassBox(g, tx, hover.y() + 4, tw, 14);
            Gfx.text(g, font, n, tx + 6, hover.y() + 7, 0xFFF2F3F7, false);
        }
        if (picking) {
            drawPicker(g, mx, my);
        }
        Gfx.pop(g);
    }

    // column footer: settings (level with the input), search above it, and in the Gemini chat a clear button above that

    private static int gearY(int sh) {
        return fieldY(sh) + (H - ITEM) / 2;
    }

    private static int footTop(int sh) {
        return gearY(sh) - ITEM * (Ai.ID.equals(ChatChannels.channel()) ? 2 : 1);
    }

    private static void drawFoot(GuiGraphicsExtractor g, int sh, int mx, int my, int cx) {
        int gy = gearY(sh), sy = gy - ITEM, by = sy - ITEM;
        boolean inSide = mx >= sideX && mx < sideX + ChatMotion.SIDEBAR;
        Panel.drawIcon(g, "gear", cx + 3, gy + 5, ICON - 6, inSide && my >= gy && my < gy + ITEM ? 0xFFFFFFFF : 0xB3C8CCD8);
        Panel.drawIcon(g, "search", cx + 3, sy + 5, ICON - 6, searching ? 0xFF9C8CFF : inSide && my >= sy && my < sy + ITEM ? 0xFFFFFFFF : 0xB3C8CCD8);
        if (Ai.ID.equals(ChatChannels.channel())) {
            boolean armed = System.currentTimeMillis() - binArmed < 3000;
            Panel.drawIcon(g, "bin", cx + 3, by + 5, ICON - 6, armed ? 0xFFFF6E6E : inSide && my >= by && my < by + ITEM ? 0xFFFFFFFF : 0xB3C8CCD8);
        }
    }

    /** Footer button under the mouse, as an item (for its tooltip). */
    private static Item footHover(int sh, double mx, double my) {
        if (mx < sideX || mx >= sideX + ChatMotion.SIDEBAR) {
            return null;
        }
        int gy = gearY(sh), sy = gy - ITEM, by = sy - ITEM;
        if (my >= gy && my < gy + ITEM) {
            return new Item(0, Component.translatable("ingamemessenger.screen.title").getString(), gy);
        }
        if (my >= sy && my < sy + ITEM) {
            return new Item(0, Component.translatable("ingamemessenger.search.hint").getString(), sy);
        }
        if (Ai.ID.equals(ChatChannels.channel()) && my >= by && my < by + ITEM) {
            boolean armed = System.currentTimeMillis() - binArmed < 3000;
            return new Item(0, Component.translatable(armed ? "ingamemessenger.ai.clear_sure" : "ingamemessenger.ai.clear_chat").getString(), by);
        }
        return null;
    }

    private static boolean clickFoot(ChatScreen screen, double mx, double my) {
        int sh = screen.height;
        int gy = gearY(sh), sy = gy - ITEM, by = sy - ITEM;
        if (my >= gy && my < gy + ITEM) {
            Minecraft.getInstance().gui.setScreen(new SettingsScreen(screen));
            return true;
        }
        if (my >= sy && my < sy + ITEM) {
            toggleSearch(screen);
            return true;
        }
        if (Ai.ID.equals(ChatChannels.channel()) && my >= by && my < by + ITEM) {
            if (System.currentTimeMillis() - binArmed < 3000) {
                binArmed = 0;
                ChatMessages.clearAiChat();
            } else {
                binArmed = System.currentTimeMillis();
            }
            return true;
        }
        return false;
    }

    /** Players to message (online, not us). */
    private static java.util.List<com.mojang.authlib.GameProfile> candidates() {
        var mc = Minecraft.getInstance();
        java.util.List<com.mojang.authlib.GameProfile> out = new java.util.ArrayList<>();
        if (mc.getConnection() == null || mc.player == null) {
            return out;
        }
        for (var info : mc.getConnection().getOnlinePlayers()) {
            if (!info.getProfile().id().equals(mc.player.getUUID())) {
                out.add(info.getProfile());
            }
        }
        out.sort((a, b) -> com.sinuxoff.ingamemessenger.ServerCompat.name(a).compareToIgnoreCase(com.sinuxoff.ingamemessenger.ServerCompat.name(b)));
        return out.size() > 14 ? out.subList(0, 14) : out;
    }

    private static int[] pickerRect() {
        int n = Math.max(1, candidates().size()), h = n * 14 + 6;
        int x = Prefs.chatRight() ? sideX - 134 : sideX + ChatMotion.SIDEBAR + 4;
        int y = Math.max(4, Math.min(plusY, Minecraft.getInstance().getWindow().getGuiScaledHeight() - h - 4));
        return new int[]{x, y, 130, h};
    }

    private static void drawPicker(GuiGraphicsExtractor g, int mx, int my) {
        var font = Minecraft.getInstance().font;
        int[] r = pickerRect();
        glassBox(g, r[0], r[1], r[2], r[3]);
        var list = candidates();
        if (list.isEmpty()) {
            Gfx.text(g, font, Component.translatable("ingamemessenger.dm.nobody"), r[0] + 8, r[1] + 6, 0xFF8E95A6, false);
        }
        for (int i = 0; i < list.size(); i++) {
            int iy = r[1] + 3 + i * 14;
            boolean over = mx >= r[0] && mx < r[0] + r[2] && my >= iy && my < iy + 14;
            if (over) {
                Panel.round(g, r[0] + 3, iy, r[2] - 6, 14, 4, 0x1CFFFFFF);
            }
            ChatRenderer.drawFace(g, com.sinuxoff.ingamemessenger.ServerCompat.id(list.get(i)), com.sinuxoff.ingamemessenger.ServerCompat.name(list.get(i)), r[0] + 7, iy + 3, 8, 1f);
            Gfx.text(g, font, com.sinuxoff.ingamemessenger.ServerCompat.name(list.get(i)), r[0] + 19, iy + 3, over ? 0xFFFFFFFF : 0xFFC8CCD8, false);
        }
    }

    /** Clicks on the column (left opens, right closes a private chat), "+" and the player list. True if ours. */
    static boolean clickTabs(ChatScreen screen, double mx, double my, int button) {
        if (picking) {
            picking = false;
            int[] r = pickerRect();
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                var list = candidates();
                int i = (int) Math.floor((my - r[1] - 3) / 14);
                if (i >= 0 && i < list.size()) {
                    Messenger.openDm(com.sinuxoff.ingamemessenger.ServerCompat.id(list.get(i)));
                }
                return true;
            }
        }
        if (mx < sideX || mx >= sideX + ChatMotion.SIDEBAR) {
            return false;
        }
        if (button == 0 && clickFoot(screen, mx, my)) {
            return true;
        }
        if (searching) {
            if (button == 0 && my >= 8 && my < 8 + ITEM) {
                stopSearch(screen);
            }
            return true;
        }
        for (Item it : items) {
            if (my >= it.y() && my < it.y() + ITEM) {
                if (button == 0) {
                    Messenger.select(it.channel());
                }
                return true;
            }
        }
        if (plusY >= 0 && my >= plusY && my < plusY + ITEM && button == 0) {
            picking = true;
            return true;
        }
        return false;
    }

    // Sending

    /** Sends the input like Enter. The chat closes afterwards unless "keep open" is on (then the input just clears). */
    static void sendLine(ChatScreen screen) {
        var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
        String v = input.getValue();
        if (!Prefs.keepOpen()) {
            Compat.pressEnter(screen);
            return;
        }
        screen.handleChatInput(v, true);
        input.setValue("");
    }

    /** Enter with "keep open": sent here, true so Minecraft doesn't close the chat. */
    static boolean enter(ChatScreen screen, int key) {
        if ((key != com.mojang.blaze3d.platform.InputConstants.KEY_RETURN && key != com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) || !Prefs.keepOpen() || searching) {
            return false;
        }
        sendLine(screen);
        return true;
    }

    // close button at the chat's top right

    private static int[] closeBtn() {
        var mc = Minecraft.getInstance();
        var chat = mc.gui.hud.getChat();
        int right = Math.round(ChatMotion.shiftX(chat)) + 2 + Compat.chatWidth(chat) + 14;
        return new int[]{right - 14, 6, 12, 12};
    }

    private static void drawClose(GuiGraphicsExtractor g, int mx, int my) {
        if (ChatMotion.openness() < 0.05f || searching) {
            return;
        }
        int[] b = closeBtn();
        boolean over = mx >= b[0] - 2 && mx < b[0] + b[2] + 2 && my >= b[1] - 2 && my < b[1] + b[3] + 2;
        Panel.round(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 8, over ? 0xC0E0475A : 0x901B1F2A);
        var font = Minecraft.getInstance().font;
        Gfx.text(g, font, "\u2715", b[0] + (b[2] - font.width("\u2715")) / 2 + 1, b[1] + 2, over ? 0xFFFFFFFF : 0xFFC8CCD8, false);
    }

    private static boolean clickClose(double mx, double my) {
        if (ChatMotion.openness() < 0.05f || searching) {
            return false;
        }
        int[] b = closeBtn();
        if (mx >= b[0] - 2 && mx < b[0] + b[2] + 2 && my >= b[1] - 2 && my < b[1] + b[3] + 2) {
            Minecraft.getInstance().gui.setScreen(null);
            return true;
        }
        return false;
    }

    // Bar above the open chat: its name (and face), search and settings. Search (or Ctrl+F) turns the bar into a search
    // field over the whole server history; results drop down below, newest first. Click or Enter jumps to the message,
    // Esc closes the search.

    private static final int SEARCH_ROOM = 0, RES_H = 21, RES_SHOWN = 7, HEAD_H = 16;

    record Found(int id, long t, String name, String bit) {
    }

    private static boolean searching;
    private static String query = "", sentQuery = "", shownQuery = "";
    private static long typedMs;
    private static java.util.List<Found> found = java.util.List.of();
    private static int foundSel, foundTop;

    public static boolean searching() {
        return searching;
    }

    static void toggleSearch(ChatScreen screen) {
        if (searching) {
            stopSearch(screen);
            return;
        }
        searching = true;
        query = sentQuery = shownQuery = "";
        found = java.util.List.of();
        foundSel = foundTop = 0;
    }

    static void stopSearch(ChatScreen screen) {
        searching = false;
        found = java.util.List.of();
        query = "";
    }

    static void onSearch(Packets.SearchRes r) {
        if (!searching || !r.query().equals(sentQuery)) {
            return;
        }
        java.util.List<Found> out = new java.util.ArrayList<>();
        for (String line : r.data().split("\n")) {
            String[] f = line.split("\t", 4);
            if (f.length == 4) {
                try {
                    out.add(new Found(Integer.parseInt(f[0]), Long.parseLong(f[1]), f[2], f[3]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        found = out;
        shownQuery = r.query();
        foundSel = foundTop = 0;
    }

    /** While searching: sends the query once typing pauses. */
    private static void searchTick() {
        String q = query.strip();
        long now = System.currentTimeMillis();
        if (!q.equals(sentQuery)) {
            if (typedMs == 0) {
                typedMs = now;
            }
            if (now - typedMs >= 220) {
                sentQuery = q;
                typedMs = 0;
                if (q.length() >= 2) {
                    Net.toServer(new Packets.SearchReq(q));
                } else {
                    found = java.util.List.of();
                    shownQuery = q;
                }
            }
        } else {
            typedMs = 0;
        }
    }

    /** {x, y, w, h} along the top of the open chat. */
    private static int[] headRect() {
        var mc = Minecraft.getInstance();
        var chat = mc.gui.hud.getChat();
        int x = Math.round(ChatMotion.shiftX(chat)) + 2;
        int y = Math.max(3, Math.round(ChatMotion.chatTopY()) - 2);
        return new int[]{x, y, Compat.chatWidth(chat) + 14, HEAD_H};
    }

    /** From the right: settings, search (or close while searching). */
    private static int[] gearBtn(int[] r) {
        return new int[]{r[0] + r[2] - 15, r[1] + 3, 10, 10};
    }

    private static int[] lensBtn(int[] r) {
        return new int[]{r[0] + r[2] - 30, r[1] + 3, 10, 10};
    }

    /** Gemini chat: clear button (a second click within 3 s confirms). */
    private static int[] binBtn(int[] r) {
        return new int[]{r[0] + r[2] - 45, r[1] + 3, 10, 10};
    }

    private static long binArmed;

    private static boolean on(int[] b, double mx, double my) {
        return mx >= b[0] - 2 && mx < b[0] + b[2] + 2 && my >= b[1] - 2 && my < b[1] + b[3] + 2;
    }

    private static int[] resultsRect() {
        int[] h = headRect();
        int y = h[1] + h[3] + 4;
        return new int[]{h[0], y, h[2], Math.max(RES_H + 6, fieldY(Minecraft.getInstance().getWindow().getGuiScaledHeight()) - barH() - 6 - y)};
    }

    /** Results that fit in the chat area. */
    private static int shownRows() {
        return Math.max(1, (resultsRect()[3] - 6) / RES_H);
    }

    private static void drawSearch(GuiGraphicsExtractor g, int sw, int sh, int mx, int my) {
        var mc = Minecraft.getInstance();
        var font = mc.font;
        float open = ChatMotion.openness();
        if (open < 0.05f || !searching) {
            return;
        }
        int[] r = headRect();
        Gfx.push(g);
        Gfx.raise(g, 420);
        Panel.round(g, r[0], r[1], r[2], r[3], 7, Math.round(open * 0xD8) << 24 | 0x161A23);
        int[] gear = gearBtn(r), lens = lensBtn(r);
        if (searching) {
            searchTick();
            Panel.drawIcon(g, "search", r[0] + 6, r[1] + 3, 10, 0xFF9C8CFF);
            int tx = r[0] + 21, maxW = r[2] - 21 - 22;
            if (query.isEmpty()) {
                Gfx.text(g, font, Component.translatable("ingamemessenger.search.hint"), tx, r[1] + 4, 0xFF6C7386, false);
            }
            String shown = query;
            while (font.width(shown) > maxW && shown.length() > 1) {
                shown = shown.substring(1);
            }
            Gfx.text(g, font, shown, tx, r[1] + 4, 0xFFF2F3F7, false);
            if (System.currentTimeMillis() / 500 % 2 == 0) { // caret
                int cx = tx + font.width(shown);
                g.fill(cx, r[1] + 3, cx + 1, r[1] + 13, 0xFFF2F3F7);
            }
            boolean overX = on(gear, mx, my);
            Gfx.text(g, font, "\u2715", gear[0] + 1, gear[1] + 1, overX ? 0xFFFFFFFF : 0xFF8E95A6, false);
        } else {
            java.util.UUID ch = ChatChannels.channel();
            String title = ch == null ? Component.translatable("ingamemessenger.dm.common").getString()
                    : Ai.ID.equals(ch) ? Ai.NAME : ChatChannels.SERVER.equals(ch) ? Component.translatable("ingamemessenger.dm.server").getString()
                    : ChatChannels.nameOf(ch);
            int tx = r[0] + 6;
            if (ChatChannels.SERVER.equals(ch)) {
                Panel.drawIcon(g, "server", tx, r[1] + 3, 10, 0xFFC8CCD8);
                tx += 14;
            } else if (ch != null) {
                ChatRenderer.drawFace(g, ch, title, tx, r[1] + 3, 10, 1f);
                tx += 14;
            }
            Gfx.text(g, font, font.plainSubstrByWidth(title, r[2] - (tx - r[0]) - 40), tx, r[1] + 4, 0xFFF2F3F7, false);
            Panel.drawIcon(g, "search", lens[0], lens[1], 10, on(lens, mx, my) ? 0xFFFFFFFF : 0xB3C8CCD8);
            Panel.drawIcon(g, "gear", gear[0], gear[1], 10, on(gear, mx, my) ? 0xFFFFFFFF : 0xB3C8CCD8);
            if (Ai.ID.equals(ch)) {
                int[] bin = binBtn(r);
                boolean armed = System.currentTimeMillis() - binArmed < 3000;
                Panel.drawIcon(g, "bin", bin[0], bin[1], 10, armed ? 0xFFFF6E6E : on(bin, mx, my) ? 0xFFFFFFFF : 0xB3C8CCD8);
                if (on(bin, mx, my) || armed) {
                    Component tip = Component.translatable(armed ? "ingamemessenger.ai.clear_sure" : "ingamemessenger.ai.clear_chat");
                    int tw = font.width(tip) + 12, tipX = Math.max(2, bin[0] + 5 - tw / 2);
                    glassBox(g, tipX, r[1] + r[3] + 3, tw, 14);
                    Gfx.text(g, font, tip, tipX + 6, r[1] + r[3] + 6, armed ? 0xFFFF9C9C : 0xFFF2F3F7, false);
                }
            }
        }
        if (searching) {
            int[] rr = resultsRect();
            if (query.strip().length() < 2) {
                Gfx.text(g, font, Component.translatable("ingamemessenger.search.type"), rr[0] + 10, rr[1] + 9, 0xFF8E95A6, false);
            } else if (found.isEmpty() && shownQuery.equals(query.strip())) {
                Gfx.text(g, font, Component.translatable("ingamemessenger.search.none"), rr[0] + 10, rr[1] + 9, 0xFF8E95A6, false);
            }
            for (int i = 0; i < shownRows() && foundTop + i < found.size(); i++) {
                Found f = found.get(foundTop + i);
                int ry = rr[1] + 3 + i * RES_H;
                boolean hot = foundTop + i == foundSel || mx >= rr[0] && mx < rr[0] + rr[2] && my >= ry && my < ry + RES_H;
                Panel.round(g, rr[0] + 3, ry, rr[2] - 6, RES_H - 2, 6, hot ? (foundTop + i == foundSel ? 0xB05B4BD6 : 0xB02A3040) : 0x901B1F2A);
                String when = ChatDates.when(f.t());
                Gfx.text(g, font, f.name().isEmpty() ? "\u2022" : f.name(), rr[0] + 10, ry + 2, f.name().isEmpty() ? 0xFFC8CCD8 : 0xFF000000 | ChatDecor.colorOf(f.name()), false);
                float ww = (float) Math.ceil(font.width(when) * Panel.smallScale());
                ChatDates.small(g, when, rr[0] + rr[2] - 10 - ww, ry + 3, 0xFF6C7386);
                ChatDates.small(g, font.plainSubstrByWidth(f.bit(), (int) ((rr[2] - 22) / Panel.smallScale())), rr[0] + 10, ry + 12, 0xFFB8BECC);
            }
        }
        Gfx.pop(g);
    }

    /** Clicks on the bar or a result. True if ours. */
    private static boolean clickSearch(ChatScreen screen, double mx, double my) {
        if (!searching) {
            return false;
        }
        int[] r = headRect();
        if (on(gearBtn(r), mx, my)) { // close
            stopSearch(screen);
            return true;
        }
        if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
            return true;
        }
        if (!searching || found.isEmpty()) {
            return false;
        }
        int[] rr = resultsRect();
        if (mx < rr[0] || mx >= rr[0] + rr[2] || my < rr[1] || my >= rr[1] + rr[3]) {
            return false;
        }
        int i = foundTop + (int) Math.floor((my - rr[1] - 3) / RES_H);
        if (i >= 0 && i < found.size()) {
            jump(screen, found.get(i));
        }
        return true;
    }

    private static void jump(ChatScreen screen, Found f) {
        stopSearch(screen);
        ChatReplies.jumpTo(f.id());
    }

    /** Typed characters go into the search. True if ours. */
    static boolean searchChar(char c) {
        if (!searching || c < 32 || c == 127) {
            return false;
        }
        if (query.length() < 100) {
            query += c;
        }
        return true;
    }

    /** Search keys: Backspace, up/down through results, Enter jumps, Esc closes, Ctrl+V pastes. True if ours. */
    static boolean searchKey(ChatScreen screen, int key) {
        if (!searching) {
            return false;
        }
        switch (key) {
            case com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE -> stopSearch(screen);
            case com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER -> {
                if (!found.isEmpty()) {
                    jump(screen, found.get(Math.min(foundSel, found.size() - 1)));
                }
            }
            case com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE -> query = Compat.ctrlDown() || query.isEmpty() ? "" : query.substring(0, query.length() - 1);
            case com.mojang.blaze3d.platform.InputConstants.KEY_DOWN -> moveSel(1);
            case com.mojang.blaze3d.platform.InputConstants.KEY_UP -> moveSel(-1);
            case 86 -> {
                if (Compat.ctrlDown()) {
                    String t = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]", " ");
                    query = (query + t).length() > 100 ? (query + t).substring(0, 100) : query + t;
                }
            }
            default -> {
                return key == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE || key == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT || key == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT || key == com.mojang.blaze3d.platform.InputConstants.KEY_HOME || key == com.mojang.blaze3d.platform.InputConstants.KEY_END; // (the line's own keys stay out of it)
            }
        }
        return true;
    }

    private static void moveSel(int d) {
        if (found.isEmpty()) {
            return;
        }
        foundSel = Math.max(0, Math.min(found.size() - 1, foundSel + d));
        foundTop = Math.max(Math.min(foundTop, foundSel), foundSel - shownRows() + 1);
    }

    /** Wheel over the results. True if ours. */
    static boolean scrollSearch(int sw, int sh, double mx, double my, double delta) {
        if (!searching || found.size() <= shownRows()) {
            return false;
        }
        int[] r = resultsRect();
        if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) {
            return false;
        }
        foundTop = Math.max(0, Math.min(found.size() - shownRows(), foundTop - (int) Math.signum(delta)));
        return true;
    }

    /** Bottom of the command suggestion list, slightly above the field. */
    public static int listBottom(int sh) {
        return fieldY(sh) - 1 - barH();
    }

    // Reply and edit

    /** Message being replied to (-1 = none), shown in a bar above the field. */
    private static int replyTo = -1;

    static void setReply(int mid) {
        replyTo = mid;
    }

    /** Takes the reply target (the bar disappears), for a sticker being sent. */
    public static int takeReply() {
        int r = replyTo;
        replyTo = -1;
        return r;
    }

    /** Height of the reply or edit bar. */
    static int barH() {
        return (replyTo >= 0 || editId >= 0 ? 19 : 0) + lowerH();
    }

    /** Message being edited (-1 = none). Its text is in the input. */
    private static int editId = -1;

    static void startEdit(ChatScreen screen, int mid) {
        String t = ChatReplies.fullText(mid);
        if (t == null) {
            return;
        }
        editId = mid;
        replyTo = -1;
        var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
        input.setValue(unexpand(t));
        //? if >=1.20.2 {
        input.moveCursorToEnd(false);
        //?} else {
        /*input.moveCursorToEnd();
        *///?}
        screen.setFocused(input);
    }

    static void stopEdit() {
        editId = -1;
    }

    /** Emoji codes back to input characters, for editing. */
    private static String unexpand(String text) {
        var m = java.util.regex.Pattern.compile(":([A-Za-z0-9_\\-]{1,64})/([A-Za-z0-9_\\-]{1,64}):").matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Library.Sticker st = Library.get(m.group(1), m.group(2));
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(st != null ? emojiChar(st) : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The input as sent. A command goes to the game from any channel; text for the game's chat too (so plugins and
     * bridges see it, and it comes back as our message); in any other channel it's sent as our message.
     */
    public static String send(String text) {
        editId = -1;
        replyTo = -1;
        if (text.isBlank()) {
            return "";
        }
        if (text.startsWith("/")) {
            CommandAnswer.sent(text);
            return text;
        }
        String out = expand(text).strip();
        return Messenger.sendText(out, 0) ? "" : out;
    }

    private static int[] barRect(int sw, int sh) {
        return new int[]{fieldX(sw) + 10, fieldY(sh) - 18 - lowerH(), fieldW(sw) - 20, 15};
    }

    private static void drawBar(GuiGraphicsExtractor g, int sw, int sh, int mx, int my) {
        if (replyTo < 0 && editId < 0) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int[] r = barRect(sw, sh);
        Panel.round(g, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 8, 0x30FFFFFF);
        Panel.round(g, r[0], r[1], r[2], r[3], 7, 0xEC161A23);
        g.fill(r[0] + 6, r[1] + 3, r[0] + 8, r[1] + r[3] - 3, 0xFF7B68FF);
        String[] i = ChatReplies.replyInfo(editId >= 0 ? editId : replyTo);
        String who = i == null ? Component.translatable("ingamemessenger.reply.unknown").getString()
                : i[0] == null ? Component.translatable("ingamemessenger.reply.server").getString() : i[0];
        Component head = editId >= 0 ? Component.translatable("ingamemessenger.edit.bar") : Component.translatable("ingamemessenger.reply.to", who);
        int x = r[0] + 12, y = r[1] + (r[3] - 8) / 2;
        Gfx.text(g, font, head, x, y, 0xFF9C8CFF, false);
        x += font.width(head) + 6;
        String text = i == null ? "" : i[1];
        int room = r[0] + r[2] - 18 - x;
        String cut = font.plainSubstrByWidth(text, Math.max(0, room));
        if (cut.length() < text.length()) {
            cut = font.plainSubstrByWidth(text, Math.max(0, room - font.width("…"))) + "…";
        }
        Gfx.text(g, font, cut, x, y, 0xFF9AA1B2, false);
        boolean overX = overBarClose(mx, my, sw, sh);
        Gfx.text(g, font, "\u2715", r[0] + r[2] - 11, y, overX ? 0xFFFFFFFF : 0xFF8E95A6, false);
    }

    private static boolean overBarClose(double mx, double my, int sw, int sh) {
        int[] r = barRect(sw, sh);
        return (replyTo >= 0 || editId >= 0) && mx >= r[0] + r[2] - 14 && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    static boolean overSend(double mx, double my, int sw, int sh) {
        return mx >= sendX(sw) && mx < sendX(sw) + SEND && my >= sendY(sh) && my < sendY(sh) + SEND;
    }

    /** Draws the field in place of the black bar, before the input text. */
    public static void drawField(GuiGraphicsExtractor g, ChatScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        int w = screen.width, h = screen.height;
        int x = fieldX(w), y = fieldY(h), fw = fieldW(w);
        String text = ((ChatScreenAccessor) screen).ingamemessenger$input().getValue();
        boolean commands = false; // commands are typed with "/" in any channel, like in the game
        var suggest = (com.sinuxoff.ingamemessenger.mixin.CommandSuggestionsAccessor) ((ChatScreenAccessor) screen).ingamemessenger$suggestions();
        if (suggest.ingamemessenger$commandsOnly()) {
            suggest.ingamemessenger$setCommandsOnly(false);
            ((ChatScreenAccessor) screen).ingamemessenger$suggestions().updateCommandInfo();
        }
        Panel.round(g, x - 1, y - 1, fw + 2, H + 2, H / 2 + 1, 0x24FFFFFF); // thin light border
        Panel.round(g, x, y, fw, H, H / 2, 0x9C161A23);
        drawBar(g, w, h, (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth()),
                (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight()));
        CommandAnswer.draw(g, screen, (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth()),
                (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight()));
        // mini player, centered above the input (and above the sticker panel when it's open)
        int mpY = Panel.open && !Panel.command ? y - 5 - barH() - Panel.panelHeight() - MP_H - 6 : y - barH() - MP_H - 6;
        ChatRenderer.drawTyping(g);
        miniPlayer(g, x + (fw - MP_W) / 2, mpY,
                (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth()),
                (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight()), true);
        if (text.isEmpty() && !VoiceClient.recording()) {
            boolean muted = !commands && Muted.here() > 0;
            Gfx.text(g, mc.font, muted ? Muted.hint() : Component.translatable(commands ? "ingamemessenger.input.command_hint" : "ingamemessenger.input.hint"),
                    inputX(w), textY(h), muted ? 0xFFE0707A : 0xFF6C7386, false);
        }
        // send button: grey when empty, accent when there's something to send; a microphone instead when the input is
        // empty and voice messages are allowed (hold to record)
        int sx = sendX(w), sy = sendY(h);
        boolean ready = !text.isBlank() || !Images.atts.isEmpty();
        boolean mic = !ready && VoiceClient.available();
        boolean rec = VoiceClient.recording();
        // while recording, the recording strip replaces the input text and caret
        ((ChatScreenAccessor) screen).ingamemessenger$input().setVisible(!rec);
        Panel.round(g, sx, sy, SEND, SEND, SEND / 2, rec ? 0xFFE0475A : ready ? 0xFF7B68FF : 0x28FFFFFF);
        int ax = sx + 6, ay = sy + SEND / 2;
        if (mic || rec) {
            Panel.drawIcon(g, "mic", sx + 3, sy + 3, SEND - 6, rec ? 0xFFFFFFFF : 0xFFC8CCD8);
        } else {
            // paper plane
            Panel.drawIcon(g, "send", sx + 4.5f, sy + 4, SEND - 8, ready ? 0xFFFFFFFF : 0xFF8E95A6);
        }
        if (rec) { // recording: pulsing red dot, time, how to cancel
            float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            // the dot grows with the voice level, scaled smoothly
            float dot = (6 + VoiceClient.level() * 4) / 10f;
            Gfx.push(g);
            Gfx.move(g, x + 15, y + H / 2f);
            Gfx.scale(g, dot, dot);
            Panel.round(g, -5, -5, 10, 10, 5, (int) (pulse * 255) << 24 | 0xE0475A);
            Gfx.pop(g);
            long ms = VoiceClient.recordingMs();
            String t = ms / 60000 + ":" + (ms / 1000 % 60 < 10 ? "0" : "") + ms / 1000 % 60 + " / " + VoiceClient.maxSeconds / 60 + ":"
                    + (VoiceClient.maxSeconds % 60 < 10 ? "0" : "") + VoiceClient.maxSeconds % 60;
            Gfx.text(g, mc.font, t, x + 24, textY(h), 0xFFF2F3F7, false);
            Component hint = Component.translatable("ingamemessenger.voice.hint");
            Gfx.text(g, mc.font, hint, sx - 8 - mc.font.width(hint), textY(h), 0xFF8E95A6, false);
        }
        int len = expand(text).length() + (replyTo >= 0 ? 4 + String.valueOf(replyTo).length() : 0); // length as sent
        // the input ends before the location button (and the counter near the limit)
        int countW = 0;
        if (len > LIMIT - 56) {
            String n = len + "/" + LIMIT;
            countW = mc.font.width(n) + 6;
            Gfx.text(g, mc.font, n, pinX(w) - 5 - mc.font.width(n), textY(h), len >= LIMIT ? 0xFFFF6E6E : 0xFF8E95A6, false);
        }
        var line = ((ChatScreenAccessor) screen).ingamemessenger$input();
        line.setX(inputX(w));
        line.setY(textY(h));
        line.setWidth(pinX(w) - 6 - inputX(w) - countW);
        drawClose(g, (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth()),
                (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight()));
        drawTabs(g, w, h, (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth()),
                (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight()));
        drawSearch(g, w, h, (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth()),
                (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight()));
        drawTray(g, w, h, (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth()),
                (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight()));
        // paper clip: attach location or picture
        if (pinShown()) {
            int pmx = (int) (mc.mouseHandler.xpos() * w / mc.getWindow().getScreenWidth());
            int pmy = (int) (mc.mouseHandler.ypos() * h / mc.getWindow().getScreenHeight());
            boolean over = overPin(pmx, pmy, w, h);
            Panel.drawIcon(g, "clip", pinX(w) + 0.5f, pinY(h), Panel.BTN, over || attachOpen ? 0xFFFFFFFF : 0xB3C8CCD8);
            if (attachOpen) {
                drawAttach(g, w, h, pmx, pmy);
            } else if (over) {
                Component tip = Component.translatable("ingamemessenger.attach.tip");
                int tw = mc.font.width(tip) + 12, tx = Math.min(w - tw - 4, pinX(w) + Panel.BTN / 2 - tw / 2), ty = y - barH() - 18;
                Gfx.push(g);
                Gfx.raise(g, 400);
                glassBox(g, tx, ty, tw, 14);
                Gfx.text(g, mc.font, tip, tx + 6, ty + 3, 0xFFF2F3F7, false);
                Gfx.pop(g);
            }
        }
    }

    // Attach menu

    static boolean attachOpen;
    private static java.util.List<Images.Shot> shots = java.util.List.of();
    private static final int TW = 52, TH = 30, A_GAP = 4, A_ROW = 16, MW = 4 * TW + 5 * A_GAP;

    static void closeAttach() {
        attachOpen = false;
    }

    // Picture tray and upload progress

    private static final int TRAY = 40, TRAY_GAP = 6, PROG = 13;

    private static int trayH() {
        return Images.atts.isEmpty() ? 0 : TRAY + 6;
    }

    private static int progH() {
        return Images.sending ? PROG : 0;
    }

    /** Space right above the input: tray and upload bar (the reply/edit bar goes above them). */
    private static int lowerH() {
        return trayH() + progH();
    }

    private static int[] trayCell(int sw, int sh, int i) {
        int y = fieldY(sh) - progH() - 4 - TRAY;
        return new int[]{fieldX(sw) + 10 + i * (TRAY + TRAY_GAP), y, TRAY};
    }

    private static int[] trayClose(int[] c) {
        return new int[]{c[0] + c[2] - 9, c[1] - 3, 12, 12};
    }

    private static boolean in(int[] b, double mx, double my) {
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + (b.length > 3 ? b[3] : b[2]);
    }

    private static void drawTray(GuiGraphicsExtractor g, int sw, int sh, int mx, int my) {
        var font = Minecraft.getInstance().font;
        Gfx.push(g);
        Gfx.raise(g, 350);
        java.util.List<Images.Att> atts = Images.atts;
        for (int i = 0; i < atts.size(); i++) {
            Images.Att a = atts.get(i);
            int[] c = trayCell(sw, sh, i);
            Panel.round(g, c[0] - 1, c[1] - 1, c[2] + 2, c[2] + 2, 7, 0x30FFFFFF);
            Panel.round(g, c[0], c[1], c[2], c[2], 6, 0xF0161A23);
            Images.Thumb t = a.thumb;
            if (t != null) { // cropped to the square
                float k = Math.max((c[2] - 4) / (float) t.w(), (c[2] - 4) / (float) t.h());
                int sw2 = Math.round((c[2] - 4) / k), sh2 = Math.round((c[2] - 4) / k);
                int u = (t.w() - sw2) / 2, v = (t.h() - sh2) / 2;
                Gfx.blit(g, t.rl(), c[0] + 2, c[1] + 2, c[2] - 4, c[2] - 4, u, v, Math.min(sw2, t.w()), Math.min(sh2, t.h()), t.w(), t.h(), -1);
            } else { // still shrinking: dots
                int ph = (int) (System.currentTimeMillis() / 300 % 3), cx = c[0] + c[2] / 2, cy = c[1] + c[2] / 2;
                for (int d = 0; d < 3; d++) {
                    Panel.round(g, cx - 8 + d * 6, cy - 2, 4, 4, 2, d == ph ? 0xF0C8CCD8 : 0x60C8CCD8);
                }
            }
            int[] x = trayClose(c);
            boolean over = in(x, mx, my);
            Panel.round(g, x[0], x[1], x[2], x[2], 6, over ? 0xFFE0475A : 0xF02A3040);
            Gfx.text(g, font, "\u2715", x[0] + 3, x[1] + 2, 0xFFFFFFFF, false);
        }
        if (!atts.isEmpty() && atts.size() < Images.MAX) {
            int[] c = trayCell(sw, sh, atts.size());
            boolean over = in(c, mx, my);
            Panel.round(g, c[0], c[1], c[2], c[2], 6, over ? 0xE02A3040 : 0xD01B1F2A);
            Gfx.text(g, font, "+", c[0] + (c[2] - font.width("+")) / 2, c[1] + c[2] / 2 - 4, over ? 0xFFFFFFFF : 0xFFC8CCD8, false);
        }
        if (Images.sending) {
            drawProgress(g, fieldX(sw) + 10, fieldY(sh) - PROG, fieldW(sw) - 20);
        }
        Gfx.pop(g);
    }

    /** "Sending 1/3" and a progress bar. */
    static void drawProgress(GuiGraphicsExtractor g, int x, int y, int w) {
        String label = Component.translatable("ingamemessenger.image.sending_n",
                Math.min(Images.sendTotal, Images.sendDone + 1), Images.sendTotal).getString();
        ChatDates.small(g, label, x + 2, y + 1, 0xFFC8CCD8);
        int lw = (int) Math.ceil(Minecraft.getInstance().font.width(label) * Panel.smallScale()) + 8;
        int bx = x + lw, bw = Math.max(20, w - lw - 2);
        Panel.round(g, bx, y + 2, bw, 4, 2, 0x30FFFFFF);
        Panel.round(g, bx, y + 2, Math.max(4, Math.round(bw * Images.progress())), 4, 2, 0xFF7B68FF);
    }

    /** Clicks on the tray: x removes a picture, "+" opens the attach menu. True if ours. */
    private static boolean clickTray(int sw, int sh, double mx, double my) {
        java.util.List<Images.Att> atts = Images.atts;
        for (int i = 0; i < atts.size(); i++) {
            int[] c = trayCell(sw, sh, i);
            if (in(trayClose(c), mx, my)) {
                Images.remove(atts.get(i));
                return true;
            }
            if (in(c, mx, my)) {
                return true;
            }
        }
        if (!atts.isEmpty() && atts.size() < Images.MAX && in(trayCell(sw, sh, atts.size()), mx, my)) {
            attachOpen = true;
            shots = Images.enabled ? Images.shots(8) : java.util.List.of();
            return true;
        }
        return false;
    }

    private static int rows() {
        return Images.enabled ? 3 : 2;
    }

    private static int shotsH() {
        if (!Images.enabled) {
            return 0;
        }
        return 12 + (shots.isEmpty() ? 12 : ((shots.size() + 3) / 4) * (TH + A_GAP));
    }

    /** {x, y, w, h} above the paper clip, right-aligned with it. */
    private static int[] attachRect(int sw, int sh) {
        int h = 4 + rows() * A_ROW + shotsH() + 4;
        int x = Math.max(4, Math.min(sw - MW - 4, pinX(sw) + Panel.BTN + 6 - MW));
        return new int[]{x, fieldY(sh) - barH() - 6 - h, MW, h};
    }

    private static void drawAttach(GuiGraphicsExtractor g, int sw, int sh, int mx, int my) {
        var font = Minecraft.getInstance().font;
        int[] r = attachRect(sw, sh);
        Gfx.push(g);
        Gfx.raise(g, 400);
        glassBox(g, r[0], r[1], r[2], r[3]);
        String[][] items = {{"pin", "ingamemessenger.attach.loc"}, {"poll", "ingamemessenger.attach.poll"}, {"image", "ingamemessenger.attach.file"}};
        for (int i = 0; i < rows(); i++) {
            int iy = r[1] + 4 + i * A_ROW;
            boolean over = mx >= r[0] && mx < r[0] + r[2] && my >= iy && my < iy + A_ROW;
            boolean off = i == 2 && Images.full();
            if (over && !off) {
                Panel.round(g, r[0] + 3, iy, r[2] - 6, A_ROW, 4, 0x1CFFFFFF);
            }
            Panel.drawIcon(g, items[i][0], r[0] + 8, iy + 3, 10, off ? 0x80C8CCD8 : 0xFFC8CCD8);
            Gfx.text(g, font, Component.translatable(items[i][1]), r[0] + 23, iy + 4, off ? 0xFF6C7386 : over ? 0xFFFFFFFF : 0xFFC8CCD8, false);
        }
        if (Images.enabled) {
            int sy = r[1] + 4 + rows() * A_ROW;
            ChatDates.small(g, Component.translatable("ingamemessenger.attach.shots").getString(), r[0] + 8, sy + 2, 0xFF8E95A6);
            sy += 12;
            if (shots.isEmpty()) {
                ChatDates.small(g, Component.translatable("ingamemessenger.attach.none").getString(), r[0] + 8, sy + 1, 0xFF6C7386);
            }
            for (int i = 0; i < shots.size(); i++) {
                int tx = r[0] + A_GAP + (i % 4) * (TW + A_GAP), ty = sy + (i / 4) * (TH + A_GAP);
                boolean over = mx >= tx && mx < tx + TW && my >= ty && my < ty + TH;
                Panel.round(g, tx, ty, TW, TH, 3, 0xFF0E1016);
                Images.Thumb t = Images.thumb(shots.get(i));
                if (t != null) {
                    float k = Math.min(TW / (float) t.w(), TH / (float) t.h());
                    int dw = Math.round(t.w() * k), dh = Math.round(t.h() * k);
                    Gfx.blit(g, t.rl(), tx + (TW - dw) / 2, ty + (TH - dh) / 2, dw, dh, 0, 0, t.w(), t.h(), t.w(), t.h(), -1);
                }
                if (over) {
                    Gfx.outline(g, tx, ty, TW, TH, 0xFF7B68FF);
                }
            }
        }
        Gfx.pop(g);
    }

    private static void attachClick(ChatScreen screen, double mx, double my, int[] r) {
        int row = (int) Math.floor((my - r[1] - 4) / A_ROW);
        if (my >= r[1] + 4 && row >= 0 && row < rows()) {
            if (row == 0) { // location, public (or as a reply)
                String loc = ChatLocations.myLocation();
                if (loc != null) {
                    screen.handleChatInput(loc, false);
                }
            } else if (row == 1) { // poll window
                Minecraft.getInstance().gui.setScreen(new PollScreen(screen, takeReply()));
            } else if (!Images.full()) {
                Images.pickFile();
            }
            return;
        }
        int sy = r[1] + 4 + rows() * A_ROW + 12;
        for (int i = 0; i < shots.size(); i++) {
            int tx = r[0] + A_GAP + (i % 4) * (TW + A_GAP), ty = sy + (i / 4) * (TH + A_GAP);
            if (mx >= tx && mx < tx + TW && my >= ty && my < ty + TH) {
                Images.add(shots.get(i).file(), false);
                return;
            }
        }
    }

    /** Input text area: after the left buttons, before the counter and the send button. */
    static void place(net.minecraft.client.gui.components.EditBox input, int sw, int sh) {
        input.setX(inputX(sw));
        input.setY(textY(sh));
        input.setWidth(pinX(sw) - 6 - inputX(sw));
    }

    // Command suggestions (CommandSuggestionsMixin, SuggestionsListMixin)

    /** Rounded dark glass panel with a thin light border. */
    public static void glassBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        glassBox(g, x, y, w, h, 0xC8);
    }

    /** With this much cover (0-255): the suggestion list hides the chat behind it. */
    public static void glassBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int alpha) {
        Panel.round(g, x - 1, y - 1, w + 2, h + 2, 7, 0x30FFFFFF);
        Panel.round(g, x, y, w, h, 6, alpha << 24 | 0x161A23);
    }

    /** Bar under the selected suggestion. */
    public static void highlight(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Panel.round(g, x, y, w, h, 4, 0xC05B4BD6);
    }

    /** Command usage or error: one bubble above the field, from the argument (or the field start for errors), as wide as the text. */
    public static void renderUsage(GuiGraphicsExtractor g, net.minecraft.client.gui.screens.Screen screen,
                                   java.util.List<net.minecraft.util.FormattedCharSequence> lines, int pos, int width) {
        if (lines.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (var l : lines) {
            w = Math.max(w, font.width(l));
        }
        int sw = screen.width;
        float k = Math.min(1f, (sw - 18f) / w); // a long one is made smaller to fit the screen
        int sw2 = Math.round(w * k);
        int x = width >= sw ? inputX(sw) : pos; // vanilla spans errors across the screen; here they start at the text
        x = Math.max(9, Math.min(x, sw - sw2 - 9));
        int h = Math.round(lines.size() * 11 * k) + 6, y = listBottom(screen.height) - 3 - h;
        glassBox(g, x - 5, y, sw2 + 10, h);
        Gfx.push(g);
        Gfx.move(g, x, y + 4);
        Gfx.scale(g, k, k);
        for (int i = 0; i < lines.size(); i++) {
            Gfx.text(g, font, lines.get(i), 0, i * 11, -1);
        }
        Gfx.pop(g);
    }

    // Emoji in the input. Each emoji is a single private-use character that our font renders as a blank of the emoji's
    // width, so the cursor, backspace and selection treat it as one character; the image is drawn on top. Sending turns
    // it into its ":pack/id:" code, which every client draws as the image again.

    private static final int FIRST = 0xE900, LAST = 0xECFF;
    private static final java.util.Map<String, Character> charOf = new java.util.HashMap<>();
    private static final java.util.Map<Character, String> codeOf = new java.util.HashMap<>();
    private static int next = FIRST;

    /** The input character for this emoji (stable within the session). */
    static String emojiChar(Library.Sticker s) {
        String code = s.pack() + "/" + s.id();
        Character c = charOf.get(code);
        if (c == null) {
            if (next > LAST) { // out of characters (a thousand different emoji this session): use the code itself
                return ":" + code + ":";
            }
            c = (char) next++;
            charOf.put(code, c);
            codeOf.put(c, code);
        }
        return String.valueOf(c);
    }

    /** The input with each emoji character turned into its code. */
    public static String expand(String text) {
        if (codeOf.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            String code = codeOf.get(c);
            out.append(code != null ? ":" + code + ":" : String.valueOf(c));
        }
        return out.toString();
    }

    /** Draws emoji images over their characters in the input, skipping the scrolled-out part. */
    public static void drawLineEmoji(GuiGraphicsExtractor g, ChatScreen screen) {
        if (codeOf.isEmpty()) {
            return;
        }
        var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
        String v = input.getValue();
        int from = Math.min(((com.sinuxoff.ingamemessenger.mixin.EditBoxAccessor) input).ingamemessenger$displayPos(), v.length());
        var font = Minecraft.getInstance().font;
        int x0 = input.getX(), maxX = x0 + input.getInnerWidth();
        Gfx.push(g);
        Gfx.raise(g, 50);
        for (int i = from; i < v.length(); i++) {
            String code = codeOf.get(v.charAt(i));
            if (code == null) {
                continue;
            }
            int x = x0 + font.width(v.substring(from, i));
            if (x + 10 > maxX) {
                break;
            }
            ChatUi.emoji(g, code, x + 1, input.getY() - 1, 9, 1f);
        }
        Gfx.pop(g);
    }

    // @mentions

    private static int mentionSel;
    private static String mentionFor = "";

    /** "@prefix" right before the cursor: {index of '@', prefix}, or null. */
    private static Object[] mentionAt(net.minecraft.client.gui.components.EditBox input) {
        String v = input.getValue();
        int cur = Math.min(input.getCursorPosition(), v.length());
        int i = cur;
        while (i > 0 && (Character.isLetterOrDigit(v.charAt(i - 1)) || v.charAt(i - 1) == '_')) {
            i--;
        }
        if (i == 0 || v.charAt(i - 1) != '@' || v.startsWith("/") || i - cur > 16) {
            return null;
        }
        int at = i - 1;
        if (at > 0 && !Character.isWhitespace(v.charAt(at - 1))) {
            return null; // e.g. an e-mail address
        }
        return new Object[]{at, v.substring(i, cur)};
    }

    /** Up to six online players (not us) whose names start with the prefix. */
    private static java.util.List<net.minecraft.client.multiplayer.PlayerInfo> mentionList(String prefix) {
        var conn = Minecraft.getInstance().getConnection();
        var me = Minecraft.getInstance().player;
        java.util.List<net.minecraft.client.multiplayer.PlayerInfo> out = new java.util.ArrayList<>();
        if (conn == null) {
            return out;
        }
        for (var p : conn.getOnlinePlayers()) {
            String n = p.getProfile().name();
            if ((me == null || !n.equals(me.getGameProfile().name())) && n.toLowerCase().startsWith(prefix.toLowerCase())) {
                out.add(p);
            }
        }
        // recent speakers first (latest first), then the rest by name
        out.sort(java.util.Comparator.<net.minecraft.client.multiplayer.PlayerInfo>comparingInt(p -> -ChatDecor.lately(p.getProfile().name()))
                .thenComparing(p -> p.getProfile().name().toLowerCase()));
        return out.size() > 6 ? out.subList(0, 6) : out;
    }

    private static final int ROW = 14;

    /** {x, y, w} of the list (rows go up from the bottom), or null if there's nothing to suggest. */
    private static int[] mentionBox(ChatScreen screen, java.util.List<net.minecraft.client.multiplayer.PlayerInfo> list, int at) {
        if (list.isEmpty()) {
            return null;
        }
        var font = Minecraft.getInstance().font;
        var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
        int w = 0;
        for (var p : list) {
            w = Math.max(w, font.width(p.getProfile().name()));
        }
        w += 30;
        int x = Math.max(4, Math.min(input.getScreenX(at) - 6, screen.width - w - 4));
        int y = fieldY(screen.height) - barH() - 5 - list.size() * ROW - 6;
        return new int[]{x, y, w};
    }

    /** Name list, selected one on the accent. */
    public static void renderMentions(GuiGraphicsExtractor g, ChatScreen screen, int mx, int my) {
        var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
        Object[] m = mentionAt(input);
        if (m == null) {
            mentionFor = "";
            return;
        }
        var list = mentionList((String) m[1]);
        if (!m[1].equals(mentionFor)) {
            mentionFor = (String) m[1];
            mentionSel = 0;
        }
        int[] b = mentionBox(screen, list, (int) m[0]);
        if (b == null) {
            return;
        }
        mentionSel = Math.max(0, Math.min(mentionSel, list.size() - 1));
        var font = Minecraft.getInstance().font;
        Gfx.push(g);
        Gfx.raise(g, 500);
        glassBox(g, b[0], b[1], b[2], list.size() * ROW + 6);
        for (int i = 0; i < list.size(); i++) {
            int ry = b[1] + 3 + i * ROW;
            boolean over = mx >= b[0] && mx < b[0] + b[2] && my >= ry && my < ry + ROW;
            if (over) {
                mentionSel = i;
            }
            if (i == mentionSel) {
                highlight(g, b[0] + 2, ry, b[2] - 4, ROW);
            }
            var p = list.get(i);
            ChatRenderer.drawFace(g, p.getProfile().id(), p.getProfile().name(), b[0] + 6, ry + 2, 10, 1f);
            Gfx.text(g, font, p.getProfile().name(), b[0] + 21, ry + 3, i == mentionSel ? 0xFFFFFFFF : 0xFFC8CCD8, false);
        }
        Gfx.pop(g);
    }

    /** Replaces "@prefix" with the selected name. */
    private static void accept(ChatScreen screen, String name) {
        var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
        Object[] m = mentionAt(input);
        if (m == null) {
            return;
        }
        String v = input.getValue();
        int at = (int) m[0], cur = Math.min(input.getCursorPosition(), v.length());
        String nv = v.substring(0, at) + "@" + name + " " + v.substring(cur);
        input.setValue(nv);
        input.setCursorPosition(at + name.length() + 2);
        mentionFor = "\u0000";
    }

    /** Tab/Enter insert the name, up/down select. True if ours. */
    public static boolean key(ChatScreen screen, int key) {
        var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
        Object[] m = mentionAt(input);
        if (m == null) {
            return false;
        }
        var list = mentionList((String) m[1]);
        if (list.isEmpty()) {
            return false;
        }
        switch (key) {
            case com.mojang.blaze3d.platform.InputConstants.KEY_TAB, com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER -> accept(screen, list.get(Math.max(0, Math.min(mentionSel, list.size() - 1))).getProfile().name());
            case com.mojang.blaze3d.platform.InputConstants.KEY_UP -> mentionSel = (mentionSel - 1 + list.size()) % list.size();
            case com.mojang.blaze3d.platform.InputConstants.KEY_DOWN -> mentionSel = (mentionSel + 1) % list.size();
            default -> {
                return false;
            }
        }
        return true;
    }

    static boolean clickMention(ChatScreen screen, double mx, double my) {
        var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
        Object[] m = mentionAt(input);
        if (m == null) {
            return false;
        }
        var list = mentionList((String) m[1]);
        int[] b = mentionBox(screen, list, (int) m[0]);
        if (b == null || mx < b[0] || mx >= b[0] + b[2] || my < b[1] + 3 || my >= b[1] + 3 + list.size() * ROW) {
            return false;
        }
        accept(screen, list.get((int) ((my - b[1] - 3) / ROW)).getProfile().name());
        return true;
    }

    /** Mouse released while recording: over the right half of the field it's sent, further left it's discarded. */
    static boolean release(ChatScreen screen, double mx, double my) {
        if (!VoiceClient.recording()) {
            return false;
        }
        boolean cancel = mx < fieldX(screen.width) + fieldW(screen.width) / 2 || my < fieldY(screen.height) - 30;
        VoiceClient.stop(!cancel);
        return true;
    }

    // Mini player

    static final int MP_W = 190, MP_H = 18;

    /** Just above the top of the chat, out of the messages' way. */
    public static int miniPlayerY(int sh) {
        var chat = Minecraft.getInstance().gui.hud.getChat();
        int top = (int) (sh - 40 + ChatMotion.shiftY() - ChatMotion.chatHeightPx(chat) * Compat.chatScale(chat));
        return Math.max(4, top - MP_H - 6);
    }
    private static int mpX = -1, mpY;

    /** Pill with play/pause, sender, progress, time and close (buttons work in the open chat only). */
    public static void miniPlayer(GuiGraphicsExtractor g, int x, int y, int mx, int my, boolean chatOpen) {
        String key = VoiceClient.nowKey();
        if (key == null) {
            mpX = -1;
            return;
        }
        var font = Minecraft.getInstance().font;
        mpX = x;
        mpY = y;
        Gfx.push(g);
        Gfx.raise(g, 450);
        Panel.round(g, x - 1, y - 1, MP_W + 2, MP_H + 2, MP_H / 2 + 1, 0x30FFFFFF);
        Panel.round(g, x, y, MP_W, MP_H, MP_H / 2, 0xEC161A23);
        boolean playing = VoiceClient.playing(key);
        boolean overPlay = chatOpen && mx >= x && mx < x + 18 && my >= y && my < y + MP_H;
        Panel.round(g, x + 2, y + 2, 14, 14, 7, overPlay ? 0xFF9285FF : 0xFF7B68FF);
        Panel.drawIcon(g, playing ? "pause" : "play", x + (playing ? 5 : 5.5f), y + 5, 8, 0xFFFFFFFF);
        String who = VoiceClient.nowWho() == null ? "" : VoiceClient.nowWho();
        float p = VoiceClient.progress(key, VoiceClient.nowMs());
        int nameW = Math.min(60, font.width(who));
        Gfx.text(g, font, font.plainSubstrByWidth(who, 60), x + 21, y + 5, 0xFFF2F3F7, false);
        int bx = x + 21 + nameW + 6, bw = MP_W - (bx - x) - 52 - SPEED_W;
        Panel.round(g, bx, y + 8, bw, 2, 1, 0x40FFFFFF);
        Panel.round(g, bx, y + 8, Math.max(2, Math.round(bw * p)), 2, 1, 0xFF9C8CFF);
        int secs = Math.round(VoiceClient.nowMs() / 1000f), at = Math.round(secs * p);
        String t = at / 60 + ":" + (at % 60 < 10 ? "0" : "") + at % 60;
        Gfx.text(g, font, t, bx + bw + 5, y + 5, 0xFF9AA1B2, false);
        // speed 1x / 1.5x / 2x, click for the next
        int sx = x + MP_W - 17 - SPEED_W;
        boolean overSpeed = chatOpen && mx >= sx && mx < sx + SPEED_W && my >= y && my < y + MP_H;
        String sp = speedLabel();
        Panel.round(g, sx, y + 4, SPEED_W, 10, 5, overSpeed ? 0x40FFFFFF : VoiceClient.speed() != 1f ? 0xFF5B4BD6 : 0x24FFFFFF);
        ChatDates.small(g, sp, sx + (SPEED_W - (float) Math.ceil(font.width(sp) * Panel.smallScale())) / 2f, y + 6.5f, 0xFFF2F3F7);
        boolean overX = chatOpen && mx >= x + MP_W - 15 && mx < x + MP_W && my >= y && my < y + MP_H;
        Gfx.text(g, font, "\u2715", x + MP_W - 12, y + 5, overX ? 0xFFFFFFFF : 0xFF8E95A6, false);
        Gfx.pop(g);
    }

    private static final int SPEED_W = 22;

    private static String speedLabel() {
        int s = Prefs.voiceSpeed();
        return (s == 150 ? "1.5" : s == 200 ? "2" : "1") + "\u00D7";
    }

    private static boolean clickMiniPlayer(double mx, double my) {
        if (mpX < 0 || VoiceClient.nowKey() == null || my < mpY || my >= mpY + MP_H || mx < mpX || mx >= mpX + MP_W) {
            return false;
        }
        int sx = mpX + MP_W - 17 - SPEED_W;
        if (mx >= sx && mx < sx + SPEED_W) {
            VoiceClient.cycleSpeed();
        } else if (mx >= mpX + MP_W - 15) {
            VoiceClient.close();
        } else if (mx < mpX + 18) {
            VoiceClient.toggle(VoiceClient.nowKey());
        }
        return true;
    }

    static boolean click(ChatScreen screen, double mx, double my) {
        if (clickMiniPlayer(mx, my)) {
            return true;
        }
        if (overBarClose(mx, my, screen.width, screen.height)) {
            if (editId >= 0) { // cancel editing: clear the input
                editId = -1;
                ((ChatScreenAccessor) screen).ingamemessenger$input().setValue("");
            }
            replyTo = -1;
            return true;
        }
        if (clickClose(mx, my)) {
            return true;
        }
        if (clickSearch(screen, mx, my)) {
            return true;
        }
        if (clickTray(screen.width, screen.height, mx, my)) {
            return true;
        }
        if (attachOpen) { // a click picks an item or just closes the menu
            attachOpen = false;
            if (overPin(mx, my, screen.width, screen.height)) {
                return true;
            }
            int[] r = attachRect(screen.width, screen.height);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                attachClick(screen, mx, my, r);
            }
            return true;
        }
        if (overPin(mx, my, screen.width, screen.height)) {
            attachOpen = true;
            shots = Images.enabled ? Images.shots(8) : java.util.List.of();
            return true;
        }
        if (overSend(mx, my, screen.width, screen.height)) {
            boolean blank = ((ChatScreenAccessor) screen).ingamemessenger$input().getValue().isBlank();
            if (blank && Images.atts.isEmpty() && VoiceClient.available()) {
                VoiceClient.start();
                return true;
            }
            if (!blank || !Images.atts.isEmpty()) {
                sendLine(screen);
            }
            return true;
        }
        return false;
    }
}
