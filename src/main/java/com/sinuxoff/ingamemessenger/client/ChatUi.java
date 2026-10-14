package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.text.ChatText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mouse interaction with the open chat: the reaction button next to a hovered message and its picker, reaction pills
 * (click to toggle, hover for who), player cards and message menus. {@link Feed} records hit areas while
 * drawing; this draws the overlays on top.
 */
final class ChatUi {
    private static final int ACCENT = 0xFF7B68FF, TEXT = 0xFFF2F3F7, MUTED = 0xFF8E95A6;
    private static final int BTN = 13, PICK_CELL = 20;
    private static final Identifier[] EMOJI = new Identifier[Protocol.BUILT_IN_REACTIONS], EMOJI_SMALL = new Identifier[Protocol.BUILT_IN_REACTIONS];
    private static final Set<Identifier> smooth = new HashSet<>();

    static {
        for (int i = 0; i < Protocol.BUILT_IN_REACTIONS; i++) {
            EMOJI[i] = InGameMessenger.id("textures/gui/emoji/" + i + ".png");
            EMOJI_SMALL[i] = InGameMessenger.id("textures/gui/emoji/" + i + "_s.png");
        }
    }

    // hit areas this frame, in GUI pixels
    private record Box(float x0, float y0, float x1, float y1) {
        boolean has(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private record BubbleHit(int mid, Box box, Box button, Box reply) {
    }

    private record QuoteHit(int target, Box box) {
    }

    private record VoiceHit(String key, String who, int ms, Box box) {
    }

    private static final List<VoiceHit> voices = new ArrayList<>();

    private record LocHit(ChatText.Location loc, Box box) {
    }

    private record PicHit(Library.Sticker pic, String who, Box box) {
    }

    private record PollHit(int mid, int option, Box box) {
    }

    private static final List<PollHit> pollHits = new ArrayList<>();

    static void pollOption(int mid, int option, int x, int y, int w, int h) {
        pollHits.add(new PollHit(mid, option, box(x, y, w, h)));
    }

    private static final List<PicHit> pics = new ArrayList<>();

    static void picture(Library.Sticker pic, String who, int x, int y, int w, int h) {
        pics.add(new PicHit(pic, who, box(x, y, w, h)));
    }

    private static final List<LocHit> locs = new ArrayList<>();
    /** Scroll-down button this frame, or null. */
    private static Box down;
    /** Mouse position last frame. */
    private static double mouseX = -1, mouseY = -1;

    static void loc(ChatText.Location l, int x, int y, int w, int h) {
        locs.add(new LocHit(l, box(x, y, w, h)));
    }

    static void down(float x, float y, float w, float h) {
        // not clipped to the message area: the button sits level with the input
        float dy = ChatMotion.shiftY();
        down = new Box(shift + (x + 4) * scale, (y + slide) * scale + dy, shift + (x + w + 4) * scale, (y + h + slide) * scale + dy);
    }

    /** The scroll-down button is checked first, it's drawn above everything. */
    static boolean clickDown(double mx, double my, int button) {
        if (down != null && button == 0 && down.has(mx, my)) {
            Feed.toBottom();
            return true;
        }
        return false;
    }

    /** True if the mouse was over this area last frame. */
    static boolean over(float x, float y, float w, float h) {
        float dy = ChatMotion.shiftY();
        return new Box(shift + (x + 4) * scale, (y + slide) * scale + dy, shift + (x + w + 4) * scale, (y + h + slide) * scale + dy).has(mouseX, mouseY);
    }

    static void voice(String key, String who, int ms, int x, int y, int w, int h) {
        voices.add(new VoiceHit(key, who, ms, box(x, y, w, h)));
    }

    private static final List<QuoteHit> quotes = new ArrayList<>();

    static void quote(int target, int x, int y, int w, int h) {
        quotes.add(new QuoteHit(target, box(x, y, w, h)));
    }

    private record PillHit(int mid, Messenger.Reaction r, Box box) {
    }

    private record FaceHit(UUID id, String name, Box box) {
    }

    private static final List<BubbleHit> bubbles = new ArrayList<>();
    private static final List<PillHit> pills = new ArrayList<>();
    private static final List<FaceHit> faces = new ArrayList<>();
    private static float scale = 1f, slide;
    private static int areaTop, areaBottom;

    /** Reaction picker for this message (-1 = closed) and where. */
    private static int pickMid = -1;
    private static float pickX;
    private static long pickOpenedMs;
    /** Player card (null = closed) and where. */
    private static FaceHit menu;
    private static float menuX, menuY;
    /** Message menu (-1 = closed) and where; delete needs a second click. */
    private static int msgMenu = -1;
    private static float msgX, msgY;
    private static boolean confirmDelete;
    private static final int CARD_W = 158, CARD_HEAD = 46, ITEM = 14;
    /** Message whose reaction button is shown. */
    private static int hoverMid = -1;
    /** Last click on a message, for double-click (adds a heart). */
    private static int tapMid = -1;
    private static long tapMs;
    private static final String HEART = "1";
    /** The picker shows all emoji packs, not just the quick row. */
    private static boolean pickMore;
    private static int pickTab, pickRow, pickTabScroll;
    /** Picker anchor: the button's top and bottom (opens above, or below if there's no room). */
    private static float pickAnchorTop, pickAnchorBottom;
    private static final int MORE_ROWS = 5;
    /** Quick row: heart first, then recently used, then the other built-in ones. */
    private static List<String> quick = List.of();

    private static List<String> quickRow() {
        List<String> out = new ArrayList<>();
        out.add(HEART);
        for (String k : Prefs.recentReactions()) {
            if (out.size() >= Protocol.BUILT_IN_REACTIONS) {
                break;
            }
            if (!out.contains(k) && (k.indexOf('/') < 0 || Library.get(k.substring(0, k.indexOf('/')), k.substring(k.indexOf('/') + 1)) != null)) {
                out.add(k);
            }
        }
        for (int i = 0; i < Protocol.BUILT_IN_REACTIONS && out.size() < Protocol.BUILT_IN_REACTIONS; i++) {
            if (!out.contains(String.valueOf(i))) {
                out.add(String.valueOf(i));
            }
        }
        return out;
    }

    private ChatUi() {
    }

    // Recorded by Feed while drawing (chat pixels)

    static void clearHits() {
        bubbles.clear();
        pills.clear();
        quotes.clear();
        voices.clear();
        locs.clear();
        pics.clear();
        pollHits.clear();
        down = null;
        faces.clear();
    }

    /** Horizontal chat offset this frame. */
    private static float shift;

    static void frame(float s, float sl, int top, int bottom, float shiftX) {
        shift = shiftX;
        scale = s;
        slide = sl;
        areaTop = top;
        areaBottom = bottom;
    }

    private static Box box(float x, float y, float w, float h) {
        float y0 = Math.max(y, areaTop), y1 = Math.min(y + h, areaBottom);
        float dy = ChatMotion.shiftY();
        return new Box(shift + (x + 4) * scale, (y0 + slide) * scale + dy, shift + (x + w + 4) * scale, (y1 + slide) * scale + dy);
    }

    static void bubble(int mid, int x0, int y0, int x1, int y1) {
        if (y1 <= areaTop || y0 >= areaBottom) {
            return;
        }
        int by = Math.max(y0, Math.min(y1 - BTN, (y0 + y1 - BTN) / 2));
        bubbles.add(new BubbleHit(mid, box(x0, y0, x1 - x0, y1 - y0), box(x1 + 3, by, BTN, BTN), box(x1 + 3 + BTN + 3, by, BTN, BTN)));
    }

    static void pill(int mid, Messenger.Reaction r, int x, int y, int w, int h) {
        pills.add(new PillHit(mid, r, box(x, y, w, h)));
    }

    static void face(UUID id, String name, int x, int y, int w, int h) {
        if (name != null) {
            faces.add(new FaceHit(id, name, box(x, y, w, h)));
        }
    }

    // Emoji

    /** Reaction emoji by key: "0".."7" (built in) or "pack/id" (emoji pack, possibly animated). */
    static void emoji(GuiGraphicsExtractor g, String key, float x, float y, float size, float alpha) {
        if (key.length() == 1 && Character.isDigit(key.charAt(0))) {
            emoji(g, key.charAt(0) - '0', x, y, size, alpha);
            return;
        }
        int slash = key.indexOf('/');
        Library.Sticker st = slash > 0 ? Library.get(key.substring(0, slash), key.substring(slash + 1)) : null;
        if (st != null) {
            Gfx.push(g);
            Gfx.move(g, x, y);
            float k = size / 32f; // drawn in a 32px box and scaled, smooth at any size
            Gfx.scale(g, k, k);
            Library.draw(g, st, 0, 0, 32, 32, alpha);
            Gfx.pop(g);
        }
    }

    /** A built-in emoji, smoothly scaled. */
    static void emoji(GuiGraphicsExtractor g, int i, float x, float y, float size, float alpha) {
        if (i < 0 || i >= Protocol.BUILT_IN_REACTIONS) {
            return;
        }
        Identifier rl = size <= 10 ? EMOJI_SMALL[i] : EMOJI[i];
        int src = size <= 10 ? 18 : 32;
        if (smooth.add(rl)) {
            Gfx.smooth(rl);
        }
        Gfx.push(g);
        Gfx.move(g, x, y);
        Gfx.scale(g, size / src, size / src);
        Gfx.blit(g, rl, 0, 0, src, alpha);
        Gfx.pop(g);
    }

    // Overlays on the open chat

    private static boolean hasMore() {
        return !Library.emojiPacks().isEmpty();
    }

    /** Built-in emoji, plus "+" when there are emoji packs. */
    private static int pickCols() {
        return Protocol.BUILT_IN_REACTIONS + (hasMore() ? 1 : 0);
    }

    private static int pickW() {
        return pickCols() * PICK_CELL + 8;
    }

    private static final int PICK_H = PICK_CELL + 8;

    private static int pickH() {
        return pickMore ? 8 + PICK_CELL + 4 + MORE_ROWS * PICK_CELL : PICK_H;
    }

    private static float pickTop() {
        float top = pickAnchorTop - pickH() - 3;
        return top >= 2 ? top : pickAnchorBottom + 3;
    }

    private static List<Library.Sticker> pickList() {
        List<Library.Pack> ps = Library.emojiPacks();
        return pickTab < ps.size() ? ps.get(pickTab).stickers() : List.of();
    }

    /** Under the mouse in the picker: emoji key, "+", "tab:N", "" (empty area) or null (outside). */
    private static String pickAt(double mx, double my) {
        float px = pickX, py = pickTop();
        if (mx < px || mx >= px + pickW() || my < py || my >= py + pickH()) {
            return null;
        }
        int c = (int) Math.floor((mx - px - 4) / PICK_CELL);
        if (!pickMore) {
            if (my < py + 4 || my >= py + 4 + PICK_CELL || c < 0 || c >= pickCols()) {
                return "";
            }
            return c < quick.size() ? quick.get(c) : "+";
        }
        if (c < 0 || c >= pickCols()) {
            return "";
        }
        if (my >= py + 4 && my < py + 4 + PICK_CELL) {
            int t = c + pickTabScroll;
            return t < Library.emojiPacks().size() ? "tab:" + t : "";
        }
        int r = (int) Math.floor((my - py - 8 - PICK_CELL) / PICK_CELL);
        List<Library.Sticker> list = pickList();
        int i = (r + Math.max(0, pickRow)) * pickCols() + c;
        return r >= 0 && r < MORE_ROWS && i >= 0 && i < list.size() ? list.get(i).key() : "";
    }

    static void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, boolean blocked) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        mouseX = mx;
        mouseY = my;
        Gfx.push(g);
        Gfx.raise(g, 200);

        // reaction button next to the hovered message (stays while moving to it)
        BubbleHit hb = null;
        if (!blocked && menu == null) {
            for (BubbleHit b : bubbles) {
                if (b.mid() == hoverMid && (b.button().has(mx, my) || b.reply().has(mx, my))) {
                    hb = b;
                }
            }
            if (hb == null) {
                for (BubbleHit b : bubbles) {
                    if (b.box().has(mx, my) || new Box(b.box().x1(), b.box().y0(), b.reply().x1() + 2, b.box().y1()).has(mx, my)) {
                        hb = b;
                    }
                }
            }
        }
        hoverMid = hb != null ? hb.mid() : pickMid;
        if (hb != null || pickMid >= 0) {
            BubbleHit b = hb;
            if (b == null) {
                for (BubbleHit x : bubbles) {
                    if (x.mid() == pickMid) {
                        b = x;
                    }
                }
            }
            if (b != null) {
                Box bt = b.button();
                boolean over = bt.has(mx, my) || pickMid == b.mid();
                int x = Math.round(bt.x0()), y = Math.round(bt.y0()), s = Math.round(bt.x1() - bt.x0());
                Panel.round(g, x, y, s, s, s / 2, over ? 0xF02E3446 : 0xC81B1F2A);
                emoji(g, 1, x + (s - 9) / 2f, y + (s - 9) / 2f, 9, over ? 1f : 0.6f);
                // reply button: a curved arrow
                Box rb = b.reply();
                boolean overR = rb.has(mx, my);
                int rx = Math.round(rb.x0()), ry = Math.round(rb.y0());
                Panel.round(g, rx, ry, s, s, s / 2, overR ? 0xF02E3446 : 0xC81B1F2A);
                int col = overR ? 0xFFFFFFFF : 0xA0C8CCD8, cx = rx + s / 2, cy = ry + s / 2;
                g.fill(cx - 3, cy - 1, cx + 3, cy, col);
                g.fill(cx + 2, cy, cx + 3, cy + 3, col);
                for (int i = 0; i < 3; i++) {                     // arrow head, pointing left
                    g.fill(cx - 3 + i, cy - 1 - i, cx - 2 + i, cy + i, col);
                }
            }
        }

        // reaction picker: quick row (and "+"), or all emoji packs
        if (pickMid >= 0) {
            float t = Math.min(1f, (System.currentTimeMillis() - pickOpenedMs) / 140f);
            float e = 1 - (1 - t) * (1 - t) * (1 - t);
            int W = pickW(), H = pickH(), px = Math.round(pickX), py = Math.round(pickTop());
            String at = pickAt(mx, my);
            boolean up = pickTop() < pickAnchorTop;
            Gfx.push(g);
            Gfx.move(g, px + W / 2f, up ? py + H : py);
            Gfx.scale(g, 0.85f + 0.15f * e, 0.85f + 0.15f * e);
            Gfx.move(g, -(px + W / 2f), -(up ? py + H : py));
            Gfx.fade(g, e);
            int radius = pickMore ? 10 : PICK_H / 2;
            Panel.round(g, px - 1, py - 1, W + 2, H + 2, radius + 1, 0x28FFFFFF);
            Panel.round(g, px, py, W, H, radius, 0xF0161A23);
            if (!pickMore) {
                for (int i = 0; i < pickCols(); i++) {
                    int cx = px + 4 + i * PICK_CELL, cy = py + 4;
                    boolean over = i < quick.size() ? quick.get(i).equals(at) : "+".equals(at);
                    if (over) {
                        Panel.round(g, cx, cy, PICK_CELL, PICK_CELL, PICK_CELL / 2, 0x20FFFFFF);
                    }
                    if (i < quick.size()) {
                        float size = over ? 18 : 14;
                        emoji(g, quick.get(i), cx + (PICK_CELL - size) / 2f, cy + (PICK_CELL - size) / 2f, size, e);
                    } else {
                        Gfx.centeredText(g, font, "+", cx + PICK_CELL / 2, cy + 6, over ? TEXT : MUTED);
                    }
                }
            } else {
                List<Library.Pack> ps = Library.emojiPacks();
                pickTab = Math.max(0, Math.min(pickTab, ps.size() - 1));
                // pack tabs: each pack's first emoji, the selected one on the accent
                for (int c = 0; c < pickCols() && c + pickTabScroll < ps.size(); c++) {
                    int tIdx = c + pickTabScroll, cx = px + 4 + c * PICK_CELL, cy = py + 4;
                    if (tIdx == pickTab) {
                        Panel.round(g, cx, cy, PICK_CELL, PICK_CELL, 6, ACCENT);
                    } else if (("tab:" + tIdx).equals(at)) {
                        Panel.round(g, cx, cy, PICK_CELL, PICK_CELL, 6, 0x20FFFFFF);
                    }
                    List<Library.Sticker> first = ps.get(tIdx).stickers();
                    if (!first.isEmpty()) {
                        emoji(g, first.get(0).key(), cx + 2, cy + 2, 16, e);
                    }
                }
                g.fill(px + 6, py + 6 + PICK_CELL, px + W - 6, py + 7 + PICK_CELL, 0x14FFFFFF);
                List<Library.Sticker> list = pickList();
                int rows = (list.size() + pickCols() - 1) / pickCols();
                pickRow = Math.max(0, Math.min(pickRow, Math.max(0, rows - MORE_ROWS)));
                int gy = py + 8 + PICK_CELL;
                for (int r = 0; r < MORE_ROWS; r++) {
                    for (int c = 0; c < pickCols(); c++) {
                        int i = (r + pickRow) * pickCols() + c;
                        if (i >= list.size()) {
                            break;
                        }
                        String key = list.get(i).key();
                        int cx = px + 4 + c * PICK_CELL, cy = gy + r * PICK_CELL;
                        boolean over = key.equals(at);
                        if (over) {
                            Panel.round(g, cx, cy, PICK_CELL, PICK_CELL, PICK_CELL / 2, 0x20FFFFFF);
                        }
                        float size = over ? 19 : 16;
                        emoji(g, key, cx + (PICK_CELL - size) / 2f, cy + (PICK_CELL - size) / 2f, size, e);
                    }
                }
                if (rows > MORE_ROWS) {
                    int trackH = MORE_ROWS * PICK_CELL, barH = Math.max(8, trackH * MORE_ROWS / rows);
                    int barY = gy + (trackH - barH) * pickRow / (rows - MORE_ROWS);
                    Panel.round(g, px + W - 4, barY, 2, barH, 1, 0x50FFFFFF);
                }
            }
            Gfx.fade(g, 1f);
            Gfx.pop(g);
        }

        // player card: big face, name, online status, last message time, actions
        if (menu != null) {
            List<Component> items = menuItems(menu);
            int x = Math.round(menuX), y = Math.round(menuY), h = CARD_HEAD + items.size() * ITEM + (items.isEmpty() ? 0 : 6);
            Panel.glass(g, x, y, CARD_W, h);
            Faces.drawFace(g, menu.id(), menu.name(), x + 9, y + 9, 26, 1f);
            Gfx.text(g, font, Component.literal(font.plainSubstrByWidth(menu.name(), CARD_W - 50)).withStyle(st -> st
                    .withColor(ChatDecor.colorOf(menu.name())).withBold(true)), x + 42, y + 10, TEXT, false);
            boolean online = ChatDecor.online(menu.id(), menu.name());
            Panel.round(g, x + 42, y + 23, 5, 5, 2, online ? 0xFF4CD964 : 0xFF6C7386);
            Gfx.text(g, font, Component.translatable(online ? "ingamemessenger.card.online" : "ingamemessenger.card.offline"), x + 50, y + 22, online ? 0xFF9BE7A6 : MUTED, false);
            Long last = ChatDecor.lastSeen(menu.name());
            if (last != null) {
                ChatDates.small(g, Component.translatable("ingamemessenger.card.wrote", ChatDates.when(last)).getString(), x + 42, y + 33, 0xFF6C7386);
            }
            for (int i = 0; i < items.size(); i++) {
                int iy = y + CARD_HEAD + i * ITEM;
                boolean over = mx >= x && mx < x + CARD_W && my >= iy && my < iy + ITEM;
                if (over) {
                    Panel.round(g, x + 3, iy, CARD_W - 6, ITEM, 4, 0x1CFFFFFF);
                }
                Gfx.text(g, font, items.get(i), x + 9, iy + 3, over ? TEXT : 0xFFC8CCD8, false);
            }
        }
        // message menu
        if (msgMenu >= 0) {
            List<Component> items = msgItems(msgMenu);
            int w = msgW(items), x = Math.round(msgX), y = Math.round(msgY), h = items.size() * ITEM + 6;
            Panel.glass(g, x, y, w, h);
            for (int i = 0; i < items.size(); i++) {
                int iy = y + 3 + i * ITEM;
                boolean over = mx >= x && mx < x + w && my >= iy && my < iy + ITEM;
                if (over) {
                    Panel.round(g, x + 3, iy, w - 6, ITEM, 4, 0x1CFFFFFF);
                }
                boolean danger = items.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                        && tc.getKey().startsWith("ingamemessenger.msg.delete");
                Gfx.text(g, font, items.get(i), x + 9, iy + 3, danger ? 0xFFFF7A86 : over ? TEXT : 0xFFC8CCD8, false);
            }
        }

        // tooltips: who reacted, whose face this is
        if (!blocked && menu == null && msgMenu < 0 && pickMid < 0) {
            for (PillHit p : pills) {
                if (p.box().has(mx, my)) {
                    Gfx.tooltip(g, font, java.util.List.of(Component.literal(String.join(", ", p.r().names()))), mx, my);
                }
            }
            for (FaceHit f : faces) {
                if (f.box().has(mx, my)) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(Component.literal(f.name()).withStyle(s -> s.withColor(TEXT & 0xFFFFFF)));
                    if (!self(f)) {
                        tip.add(Component.translatable("ingamemessenger.menu.hint").withStyle(s -> s.withColor(MUTED & 0xFFFFFF)));
                    }
                    Gfx.tooltip(g, font, tip, mx, my);
                }
            }
        }
        Gfx.pop(g);
    }

    private static boolean self(FaceHit f) {
        var p = Minecraft.getInstance().player;
        return p != null && (p.getUUID().equals(f.id()) || p.getGameProfile().name().equals(f.name()));
    }

    /** Card actions: message, mention, hide or show stickers (none for ourselves). */
    private static List<Component> menuItems(FaceHit f) {
        List<Component> out = new ArrayList<>();
        if (self(f)) {
            return out;
        }
        out.add(Component.translatable("ingamemessenger.menu.msg"));
        out.add(Component.translatable("ingamemessenger.card.mention"));
        if (f.id() != null) {
            out.add(Component.translatable(Prefs.isMuted(f.id()) ? "ingamemessenger.menu.unmute" : "ingamemessenger.menu.mute"));
        }
        return out;
    }

    /** Menu actions: reply, copy, edit (own text), delete (own; any for operators). */
    private static List<Component> msgItems(int mid) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("ingamemessenger.msg.reply"));
        if (ChatReplies.fullText(mid) != null) {
            out.add(Component.translatable("ingamemessenger.msg.copy"));
        }
        if (ChatReplies.isMineText(mid)) {
            out.add(Component.translatable("ingamemessenger.msg.edit"));
        }
        var p = Minecraft.getInstance().player;
        if (ChatReplies.isMineAny(mid) || p != null && com.sinuxoff.ingamemessenger.ServerCompat.isOperator(p)) {
            out.add(Component.translatable(confirmDelete ? "ingamemessenger.msg.delete_sure" : "ingamemessenger.msg.delete"));
        }
        return out;
    }

    private static int msgW(List<Component> items) {
        Font font = Minecraft.getInstance().font;
        int w = 60;
        for (Component c : items) {
            w = Math.max(w, font.width(c));
        }
        return w + 18;
    }

    /** True if the click was ours. */
    static boolean click(ChatScreen screen, double mx, double my, int button, boolean blocked) {
        Minecraft mc = Minecraft.getInstance();
        if (menu != null) {
            List<Component> items = menuItems(menu);
            FaceHit f = menu;
            menu = null;
            int i = (int) Math.floor((my - menuY - CARD_HEAD) / ITEM);
            if (mx >= menuX && mx < menuX + CARD_W && my >= menuY + CARD_HEAD && i >= 0 && i < items.size()) {
                var input = ((com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor) screen).ingamemessenger$input();
                if (i == 0) { // private chat
                    if (f.id() != null) {
                        Messenger.openDm(f.id());
                    } else {
                        input.setValue("/msg " + f.name() + " ");
                    }
                    screen.setFocused(input);
                } else if (i == 1) {
                    input.insertText("@" + f.name() + " ");
                    screen.setFocused(input);
                } else {
                    Prefs.toggleMuted(f.id());
                }
            }
            return true;
        }
        if (msgMenu >= 0) {
            List<Component> items = msgItems(msgMenu);
            int w = msgW(items), mid = msgMenu;
            int i = (int) Math.floor((my - msgY - 3) / ITEM);
            boolean inside = mx >= msgX && mx < msgX + w && my >= msgY + 3 && i >= 0 && i < items.size();
            String key = inside && items.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : "";
                if (key.equals("ingamemessenger.msg.delete")) { // ask again, the menu stays open
                confirmDelete = true;
                return true;
            }
            msgMenu = -1;
            confirmDelete = false;
            var input = ((com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor) screen).ingamemessenger$input();
            switch (key) {
                case "ingamemessenger.msg.reply" -> {
                    ChatInput.setReply(mid);
                    screen.setFocused(input);
                }
                case "ingamemessenger.msg.copy" -> Minecraft.getInstance().keyboardHandler.setClipboard(ChatReplies.fullText(mid));
                case "ingamemessenger.msg.edit" -> ChatInput.startEdit(screen, mid);
                case "ingamemessenger.msg.delete_sure" -> ChatReplies.deleteMine(mid);
                default -> {
                }
            }
            return true;
        }
        if (pickMid >= 0) {
            String at = pickAt(mx, my);
            if (at == null) {
                pickMid = -1; // clicked outside
            } else if (at.equals("+")) {
                pickMore = true;
                pickOpenedMs = System.currentTimeMillis();
            } else if (at.startsWith("tab:")) {
                pickTab = Integer.parseInt(at.substring(4));
                pickRow = 0;
            } else if (!at.isEmpty()) {
                Messenger.react(pickMid, at);
                pickMid = -1;
            }
            return true;
        }
        if (blocked || button != 0 && button != 1) {
            return false;
        }

        for (BubbleHit b : bubbles) {
            if (b.mid() == hoverMid && b.reply().has(mx, my)) {
                ChatInput.setReply(b.mid());
                var input = ((com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor) screen).ingamemessenger$input();
                screen.setFocused(input);
                return true;
            }
            if (b.mid() == hoverMid && b.button().has(mx, my)) {
                pickMid = b.mid();
                pickMore = false;
                quick = quickRow();
                pickOpenedMs = System.currentTimeMillis();
                pickX = Math.max(2, Math.min(screen.width - pickW() - 2, b.button().x0() - 6));
                pickAnchorTop = b.button().y0();
                pickAnchorBottom = b.button().y1();
                return true;
            }
        }
        for (PillHit p : pills) {
            if (p.box().has(mx, my)) {
                Messenger.react(p.mid(), p.r().emoji());
                return true;
            }
        }
        for (PollHit h : pollHits) { // vote (again = take back)
            if (h.box().has(mx, my) && button == 0) {
                Net.toServer(new com.sinuxoff.ingamemessenger.core.protocol.Proto.Vote(h.mid(), h.option()));
                return true;
            }
        }
        for (PicHit p : pics) { // open the picture
            if (p.box().has(mx, my) && button == 0) {
                // opening the picture resets Minecraft's chat scroll, so it's kept
                int scroll = ((com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor) mc.gui.hud.getChat()).ingamemessenger$scroll();
                Box b = p.box();
                ChatMotion.keepScrollOnce();
                mc.gui.setScreen(new ImageViewScreen(screen, p.pic(), p.who(), scroll, b.x0(), b.y0(), b.x1() - b.x0(), b.y1() - b.y0()));
                return true;
            }
        }
        for (LocHit l : locs) { // waypoint or copy
            if (l.box().has(mx, my) && button == 0) {
                ChatLocations.openLoc(screen, l.loc());
                return true;
            }
        }
        for (VoiceHit v : voices) { // play/pause
            if (v.box().has(mx, my) && button == 0) {
                VoiceClient.toggle(v.key(), v.who(), v.ms());
                return true;
            }
        }
        for (QuoteHit q : quotes) { // jump to the quoted message
            if (q.box().has(mx, my) && button == 0) {
                Feed.jumpTo(q.target());
                return true;
            }
        }
        for (FaceHit f : faces) {
            if (f.box().has(mx, my) && button == 0) {
                menu = f;
                menuX = (float) Math.min(mx, screen.width - CARD_W - 4);
                menuY = (float) Math.max(2, my - CARD_HEAD - 20);
                return true;
            }
        }
        // right click: message menu
        if (button == 1) {
            for (BubbleHit b : bubbles) {
                if (b.box().has(mx, my)) {
                    msgMenu = b.mid();
                    confirmDelete = false;
                    msgX = (float) Math.min(mx, screen.width - 150);
                    msgY = (float) Math.max(2, my - 10);
                    return true;
                }
            }
        }
        // double click: heart (again = remove); a single click goes on to Minecraft (links)
        if (button == 0) {
            for (BubbleHit b : bubbles) {
                if (b.box().has(mx, my)) {
                    long now = System.currentTimeMillis();
                    if (b.mid() == tapMid && now - tapMs < 400) {
                        tapMid = -1;
                        Messenger.react(b.mid(), HEART);
                        return true;
                    }
                    tapMid = b.mid();
                    tapMs = now;
                    break;
                }
            }
        }
        return false;
    }

    /** Wheel over the emoji packs: scrolls rows, or the tabs. True if ours. */
    static boolean scroll(double mx, double my, double delta) {
        if (pickMid < 0 || !pickMore || pickAt(mx, my) == null) {
            return false;
        }
        if (my < pickTop() + 4 + PICK_CELL) {
            pickTabScroll = Math.max(0, Math.min(pickTabScroll - (int) Math.signum(delta),
                    Math.max(0, Library.emojiPacks().size() - pickCols())));
        } else {
            int rows = (pickList().size() + pickCols() - 1) / pickCols();
            pickRow = Math.max(0, Math.min(pickRow - (int) Math.signum(delta), Math.max(0, rows - MORE_ROWS)));
        }
        return true;
    }

    static void closed() {
        pickMid = -1;
        menu = null;
        msgMenu = -1;
        hoverMid = -1;
    }
}
