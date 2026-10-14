package com.sinuxoff.ingamemessenger.client;

//? if >=1.19.3 {
import com.mojang.math.Axis;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sticker panel: rounded glass with tabs (recent, favorites, emoji, then the packs, scrollable) and a sliding pill
 * under the selected one. Opens above the chat input from the smiley button (right-click pins it), or centered while
 * the chat key is held. Always opens on Recent. Click sends, hold shows a big preview, right-click toggles favorite.
 */
final class Panel {
    private static final int CELL = 36, EMOJI_CELL = 26, MAX_W = 312;
    static final int BTN = 12;
    private static final int PAD = 8, TAB = 24, TITLE = 13, GRID_H = 4 * CELL, RADIUS = 10;
    /** Fixed tabs: recent, favorites, emoji; pack tabs follow (tab 3 is the first pack). */
    private static final int RECENT = 0, FAVORITES = 1, EMOJI = 2, PINNED = 3;
    // colors
    private static final int GLASS = 0xD4141821, GLASS_EDGE = 0x2CFFFFFF;
    private static final int ACCENT = 0xFF7B68FF, ACCENT_TOP = 0x40FFFFFF, HOVER = 0x16FFFFFF;
    private static final int TEXT = 0xFFF2F3F7, MUTED = 0xFF8E95A6;
    private static final Identifier ICON_BUTTON = tex("button"), ICON_STAR = tex("star"), ICON_GEAR = tex("gear");
    private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);

    static boolean open;
    /** Hold-to-pick mode: centered, without the chat. */
    static boolean centered;
    private static int tab = RECENT;
    /** First visible grid row; pack tab scroll in pixels. */
    private static int rowScroll, tabScroll;

    /** Typing a command: the panel makes room for the command suggestions. */
    static boolean command;

    /** Right-click menu of the smiley is open. */
    private static boolean smileMenu;
    /** Pressed sticker (sent on release unless held for the preview); previewed sticker and since when. */
    private static Library.Sticker pressed, preview;
    private static long pressMs, previewMs;
    private static final long HOLD_MS = 280;

    // animation
    private static long openedMs, lastFrameMs;
    private static float pillX = -1;
    private static final Map<String, Float> grow = new HashMap<>();
    private static float gearAngle;

    private Panel() {
    }

    private static Identifier tex(String name) {
        return InGameMessenger.id("textures/gui/" + name + ".png");
    }

    // Layout

    private static int width(int sw) {
        return Math.min(sw - 8, MAX_W);
    }

    private static int height() {
        return PAD + TAB + 4 + TITLE + GRID_H + PAD;
    }

    static int panelHeight() {
        return height();
    }

    private static int left(int sw) {
        return centered ? (sw - width(sw)) / 2 : Math.max(4, Math.min(sw - width(sw) - 4, ChatInput.fieldX(sw) + (ChatInput.fieldW(sw) - width(sw)) / 2));
    }

    private static int top(int sh) {
        return centered ? (sh - height()) / 2 : ChatInput.fieldY(sh) - 5 - ChatInput.barH() - height();
    }

    private static int tabsY(int sh) {
        return top(sh) + PAD;
    }

    private static int titleY(int sh) {
        return tabsY(sh) + TAB + 4;
    }

    private static int gridY(int sh) {
        return titleY(sh) + TITLE;
    }

    /** Right end of the pack tabs. */
    private static int gearX(int sw) {
        return left(sw) + width(sw) - PAD + 6;
    }

    private static int tabsX(int sw) {
        return left(sw) + PAD;
    }

    /** Pack tabs: after the fixed ones and a separator. */
    private static int packTabsX(int sw) {
        return tabsX(sw) + PINNED * TAB + 7;
    }

    private static int packTabsW(int sw) {
        return gearX(sw) - 6 - packTabsX(sw);
    }

    /** X of tab t, pack tabs scrolled. */
    private static int tabX(int t, int sw) {
        return t < PINNED ? tabsX(sw) + t * TAB : packTabsX(sw) + (t - PINNED) * TAB - tabScroll;
    }

    // Smiley button in the chat input

    private static int btnX(int sw) {
        return ChatInput.smileX(sw);
    }

    private static int btnY(int sh) {
        return ChatInput.fieldY(sh) + (ChatInput.H - BTN) / 2;
    }

    private static boolean overButton(double mx, double my, int sw, int sh) {
        return !centered && !ChatChannels.SERVER.equals(ChatChannels.channel()) && mx >= btnX(sw) - 2 && mx < btnX(sw) + BTN + 2 && my >= btnY(sh) - 2 && my < btnY(sh) + BTN + 2;
    }

    /** True if the mouse is over the panel or the smiley (the chat below shouldn't react). */
    static boolean covers(double mx, double my, int sw, int sh) {
        return overPanel(mx, my, sw, sh) || overButton(mx, my, sw, sh);
    }

    private static boolean overPanel(double mx, double my, int sw, int sh) {
        return open && !(command && !centered) && mx >= left(sw) && mx < left(sw) + width(sw) && my >= top(sh) && my < top(sh) + height();
    }

    // Grid content

    /** A grid row: a pack heading (emoji tab) or a row of cells. */
    private record Row(String heading, List<Library.Sticker> items, int cell) {
        int h() {
            return heading != null ? 12 : cell;
        }
    }

    private static int tabCount() {
        return PINNED + Library.packs().size();
    }

    private static List<Row> rows(int sw) {
        int inner = width(sw) - 2 * PAD;
        List<Row> out = new ArrayList<>();
        if (tab == EMOJI) { // all emoji packs, each under its title, smaller cells
            int cols = Math.max(1, inner / EMOJI_CELL);
            for (Library.Pack p : Library.emojiPacks()) {
                out.add(new Row(p.title(), List.of(), EMOJI_CELL));
                chunk(out, p.stickers(), cols, EMOJI_CELL);
            }
            return out;
        }
        chunk(out, current(), Math.max(1, inner / CELL), CELL);
        return out;
    }

    private static void chunk(List<Row> out, List<Library.Sticker> list, int cols, int cell) {
        for (int i = 0; i < list.size(); i += cols) {
            out.add(new Row(null, list.subList(i, Math.min(list.size(), i + cols)), cell));
        }
    }

    private static List<Library.Sticker> current() {
        return switch (tab) {
            case RECENT -> Library.recent();
            case FAVORITES -> Library.favorites();
            case EMOJI -> List.of();
            default -> tab - PINNED < Library.packs().size() ? Library.packs().get(tab - PINNED).stickers() : List.of();
        };
    }

    private static int count() {
        if (tab != EMOJI) {
            return current().size();
        }
        int n = 0;
        for (Library.Pack p : Library.emojiPacks()) {
            n += p.stickers().size();
        }
        return n;
    }

    private static Component title() {
        return switch (tab) {
            case RECENT -> Component.translatable("ingamemessenger.tab.recent");
            case FAVORITES -> Component.translatable("ingamemessenger.tab.favorites");
            case EMOJI -> Component.translatable("ingamemessenger.tab.emoji");
            default -> Component.literal(Library.packs().get(tab - PINNED).title());
        };
    }

    /** Grid rows that fit, starting at rowScroll. */
    private static int fitting(List<Row> rows, int from) {
        int h = 0, n = 0;
        for (int i = from; i < rows.size(); i++) {
            h += rows.get(i).h();
            if (h > GRID_H) {
                break;
            }
            n++;
        }
        return n;
    }

    private static int maxScroll(List<Row> rows) {
        int h = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            h += rows.get(i).h();
            if (h > GRID_H) {
                return i + 1;
            }
        }
        return 0;
    }

    // Drawing helpers

    /** Screen pixels per GUI pixel. */
    static int pixels() {
        return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
    }

    /** Scale for small text that keeps font pixels whole on screen (about 3/4). */
    static float smallScale() {
        int s = pixels();
        return Math.max(1, Math.round(s * 0.75f)) / (float) s;
    }

    /** Filled rounded rectangle drawn in real screen pixels with anti-aliased edges, crisp at any GUI scale. */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        int s = pixels();
        if (s <= 1) {
            roundPx(g, x, y, w, h, r, color);
            return;
        }
        Gfx.push(g);
        Gfx.scale(g, 1f / s, 1f / s);
        roundPx(g, x * s, y * s, w * s, h * s, r * s, color);
        Gfx.pop(g);
    }

    private static void roundPx(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double in = r - Math.sqrt(r * r - dy * dy); // exact circle inset on this row
            int solid = (int) Math.ceil(in);
            float edge = (float) (solid - in); // coverage of the edge pixel
            roundRow(g, x, y + i, w, solid, edge, color);
            roundRow(g, x, y + h - 1 - i, w, solid, edge, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** One row of a rounded shape: solid inside, edge pixels by coverage. */
    private static void roundRow(GuiGraphicsExtractor g, int x, int y, int w, int solid, float edge, int color) {
        g.fill(x + solid, y, x + w - solid, y + 1, color);
        int a = Math.round((color >>> 24) * edge);
        if (solid > 0 && a > 0) {
            int c = a << 24 | color & 0xFFFFFF;
            g.fill(x + solid - 1, y, x + solid, y + 1, c);
            g.fill(x + w - solid, y, x + w - solid + 1, y + 1, c);
        }
    }

    /** Glass: soft shadow, thin light border, dark body, faint highlight along the top. */
    static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        round(g, x - 3, y - 1, w + 6, h + 6, RADIUS + 3, 0x18000000);
        round(g, x - 1, y + 1, w + 2, h + 2, RADIUS + 1, 0x30000000);
        round(g, x - 1, y - 1, w + 2, h + 2, RADIUS + 1, GLASS_EDGE);
        round(g, x, y, w, h, RADIUS, GLASS);
        g.fill(x + RADIUS, y, x + w - RADIUS, y + 1, 0x14FFFFFF);
    }

    private static final java.util.Set<Identifier> smoothed = new java.util.HashSet<>();

    /** GUI icon (textures/gui/<name>.png), smoothly scaled and tinted. */
    static void drawIcon(GuiGraphicsExtractor g, String name, float x, float y, float size, int argb) {
        Identifier tex = tex(name);
        if (smoothed.add(tex)) {
            Gfx.smooth(tex);
        }
        Gfx.push(g);
        Gfx.move(g, x, y);
        Gfx.scale(g, size / 64f, size / 64f);
        Gfx.blit(g, tex, 0, 0, 64, 64, 0, 0, 64, 64, 64, 64, argb);
        Gfx.pop(g);
    }

    /** Panel icon (large image drawn small, smoothly). */
    private static void icon(GuiGraphicsExtractor g, Identifier tex, int x, int y, int size, float alpha) {
        if (smoothed.add(tex)) {
            Gfx.smooth(tex);
        }
        Gfx.blit(g, tex, x, y, size, alpha);
    }

    private static void small(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        float k = smallScale();
        Gfx.push(g);
        Gfx.move(g, x, y);
        Gfx.scale(g, k, k);
        Gfx.text(g, font, text, 0, 0, color, false);
        Gfx.pop(g);
    }

    private static float approach(float cur, float target, float dt, float speed) {
        return cur + (target - cur) * Math.min(1f, dt * speed);
    }

    /** Opening animation 0..1, eased. */
    static float appear() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedMs) / 170f);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // Drawing

    static void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0 ? 0 : Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        Gfx.push(g);
        Gfx.raise(g, 300);
        if (!centered && !VoiceClient.recording() && !ChatChannels.SERVER.equals(ChatChannels.channel())) { // smiley in the input (not while recording, not for commands)
            icon(g, ICON_BUTTON, btnX(sw), btnY(sh), BTN, open || overButton(mx, my, sw, sh) ? 1f : 0.7f);
        }
        if (!open || command && !centered) {
            drawSmileMenu(g, font, mx, my, sw, sh);
            Gfx.pop(g);
            return;
        }
        if (tab >= tabCount()) {
            tab = RECENT;
        }
        int x = left(sw), y = top(sh), w = width(sw), h = height();

        // opening: fade in and grow slightly from its side
        float a = appear();
        if (a < 1f) {
            float scale = 0.92f + 0.08f * a;
            float ax = x + w / 2f, ay = centered ? y + h / 2f : y + h;
            Gfx.move(g, ax, ay + (1 - a) * 6);
            Gfx.scale(g, scale, scale);
            Gfx.move(g, -ax, -ay);
        }
        Gfx.fade(g, a);
        glass(g, x, y, w, h);

        // tabs: fixed ones, a separator, the packs (scrollable), and the pill under the selected one
        int tabsY = tabsY(sh), ptx = packTabsX(sw), ptw = packTabsW(sw);
        tabScroll = Math.max(0, Math.min(tabScroll, Math.max(0, Library.packs().size() * TAB - ptw)));
        float target = tabX(tab, sw);
        pillX = pillX < 0 || a < 1f ? target : approach(pillX, target, dt, 22f);
        g.fill(ptx - 4, tabsY + 5, ptx - 3, tabsY + TAB - 5, 0x24FFFFFF);
        // pill over a fixed tab or inside the pack strip
        boolean pillInPacks = tab >= PINNED;
        if (pillInPacks) {
            Gfx.scissor(g, ptx, tabsY, ptx + ptw, tabsY + TAB);
        }
        int px = Math.round(pillX);
        round(g, px, tabsY, TAB, TAB, 7, ACCENT);
        g.fill(px + 5, tabsY, px + TAB - 5, tabsY + 1, ACCENT_TOP);
        if (pillInPacks) {
            Gfx.unscissor(g);
        }
        for (int t = 0; t < PINNED; t++) {
            drawTab(g, t, tabX(t, sw), tabsY, mx, my, a, true);
        }
        Gfx.scissor(g, ptx, tabsY, ptx + ptw, tabsY + TAB);
        for (int t = PINNED; t < tabCount(); t++) {
            int tx = tabX(t, sw);
            if (tx + TAB >= ptx && tx <= ptx + ptw) {
                drawTab(g, t, tx, tabsY, mx, my, a, mx >= ptx && mx < ptx + ptw);
            }
        }
        Gfx.unscissor(g);
        Gfx.fade(g, a);

        // small title: name and count
        int ty = titleY(sh) + 3;
        Component name = title();
        String n = String.valueOf(count());
        int nw = Math.round(font.width(n) * smallScale());
        String shown = font.plainSubstrByWidth(name.getString(), Math.round((w - 2 * PAD - nw - 10) / smallScale()));
        small(g, font, Component.literal(shown), x + PAD + 2, ty, MUTED);
        small(g, font, Component.literal(n), x + w - PAD - 2 - nw, ty, 0xFF6C7386);

        // grid
        List<Row> rows = rows(sw);
        rowScroll = Math.max(0, Math.min(rowScroll, maxScroll(rows)));
        int gy = gridY(sh);
        Library.Sticker hovered = null;
        int hx = 0, hy = 0;
        if (rows.isEmpty()) {
            String key = tab == RECENT ? "ingamemessenger.panel.empty_recent" : tab == FAVORITES ? "ingamemessenger.panel.empty_favorites"
                    : tab == EMOJI ? "ingamemessenger.panel.no_emoji" : "ingamemessenger.panel.empty";
            int my0 = gy + GRID_H / 2 - 8;
            for (var line : font.split(Component.translatable(key), w - 4 * PAD)) {
                Gfx.centeredText(g, font, line, x + w / 2, my0, MUTED);
                my0 += 10;
            }
        }
        Gfx.scissor(g, x, gy - 2, x + w, gy + GRID_H + 2);
        int ry = gy;
        for (int r = rowScroll; r < rows.size() && ry < gy + GRID_H; r++) {
            Row row = rows.get(r);
            if (row.heading() != null) {
                small(g, font, Component.literal(font.plainSubstrByWidth(row.heading(), Math.round((w - 2 * PAD) / smallScale()))),
                        x + PAD + 2, ry + 4, 0xFF6C7386);
                ry += row.h();
                continue;
            }
            int cell = row.cell(), cols = Math.max(1, (w - 2 * PAD) / cell), gx = x + (w - cols * cell) / 2;
            for (int c = 0; c < row.items().size(); c++) {
                Library.Sticker s = row.items().get(c);
                int cx = gx + c * cell, cy = ry;
                boolean over = mx >= cx && mx < cx + cell && my >= cy && my < cy + cell && my < gy + GRID_H;
                float k = grow.getOrDefault(s.key(), 0f);
                k = approach(k, over ? 1f : 0f, dt, 18f);
                if (k > 0.01f) {
                    grow.put(s.key(), k);
                    round(g, cx + 1, cy + 1, cell - 2, cell - 2, cell / 4, ((int) (k * 0x1C) << 24) | 0xFFFFFF);
                } else {
                    grow.remove(s.key());
                }
                // scale smoothly around the center (not whole pixels, no jitter)
                int pic = cell - (cell >= CELL ? 6 : 4);
                Gfx.push(g);
                Gfx.move(g, cx + cell / 2f, cy + cell / 2f);
                float sc = 1f + 0.16f * k;
                Gfx.scale(g, sc, sc);
                Library.draw(g, s, -pic / 2, -pic / 2, pic, pic, a);
                Gfx.pop(g);
                if (over) {
                    hovered = s;
                    hx = cx;
                    hy = cy;
                }
                if (tab != FAVORITES && Library.isFavorite(s)) {
                    Gfx.push(g);
                    Gfx.move(g, cx + cell - 10, cy + 2, 10);
                    Gfx.scale(g, 0.5f, 0.5f);
                    icon(g, ICON_STAR, 0, 0, 16, a);
                    Gfx.pop(g);
                }
            }
            ry += row.h();
        }
        Gfx.unscissor(g);
        Gfx.fade(g, a);
        // slim scroll bar
        int maxS = maxScroll(rows);
        if (maxS > 0) {
            int barH = Math.max(12, GRID_H * fitting(rows, rowScroll) / Math.max(1, rows.size()));
            int barY = gy + (GRID_H - barH) * rowScroll / maxS;
            round(g, x + w - 5, barY, 3, barH, 1, 0x50FFFFFF);
        }

        Gfx.fade(g, 1f);
        drawSmileMenu(g, font, mx, my, sw, sh);

        // held long enough: big preview in the middle over the blurred screen
        if (pressed != null && preview == null && now - pressMs >= HOLD_MS) {
            preview = pressed;
            previewMs = now;
        }
        if (preview != null) {
            if (hovered != null) {
                preview = hovered;
            }
            drawPreview(g, sw, sh, now);
        }
        Gfx.pop(g);
    }

    /** Big preview: just the image with a soft shadow over the blurred screen. */
    private static void drawPreview(GuiGraphicsExtractor g, int sw, int sh, long now) {
        Minecraft mc = Minecraft.getInstance();
        float t = Math.min(1f, (now - previewMs) / 160f), e = 1 - (1 - t) * (1 - t) * (1 - t);
        //? if <1.21.5 {
        /*g.flush();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        *///?}
        Gfx.blur(g, Gfx.partialTick());
        Gfx.push(g);
        Gfx.raise(g, 400);
        g.fill(0, 0, sw, sh, (int) (e * 0x60) << 24);
        int size = (int) Math.min(Math.min(sh * 0.55f, sw * 0.45f), 220);
        float k = 0.8f + 0.2f * e;
        Gfx.move(g, sw / 2f, sh / 2f);
        Gfx.scale(g, k, k);
        int x0 = -size / 2, y0 = -size / 2;
        // shadow: the image itself, darkened and spread, underneath
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int ox = (int) Math.round(Math.cos(ang) * 4), oy = (int) Math.round(Math.sin(ang) * 4) + 5;
            Library.drawTinted(g, preview, x0 + ox, y0 + oy, size, size, 0f, 0f, 0f, 0.07f * e);
        }
        Library.draw(g, preview, x0, y0, size, size, e);
        Gfx.pop(g);
        //? if <1.21.5 {
        /*com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        *///?}
    }

    /** Smiley's right-click menu: pin or unpin the panel. */
    private static void drawSmileMenu(GuiGraphicsExtractor g, Font font, int mx, int my, int sw, int sh) {
        if (!smileMenu || centered) {
            return;
        }
        int[] r = smileMenuRect(font, sw, sh);
        Gfx.push(g);
        Gfx.raise(g, 50);
        round(g, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 7, GLASS_EDGE);
        round(g, r[0], r[1], r[2], r[3], 6, 0xF81B1F2A);
        boolean over = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
        if (over) {
            round(g, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, 5, 0x1CFFFFFF);
        }
        small(g, font, smileMenuLabel(), r[0] + 6, r[1] + (r[3] - Math.round(8 * smallScale())) / 2, over ? TEXT : 0xFFC8CCD8);
        Gfx.pop(g);
    }

    private static Component smileMenuLabel() {
        return Component.translatable(Prefs.pinned() ? "ingamemessenger.menu.unpin" : "ingamemessenger.menu.pin");
    }

    /** {x, y, w, h} of a small popup right above the smiley. */
    private static int[] smileMenuRect(Font font, int sw, int sh) {
        int w = Math.round(font.width(smileMenuLabel()) * smallScale()) + 12, h = 13;
        int x = Math.min(btnX(sw) + BTN + 2, sw - 4) - w;
        int y = btnY(sh) - h - 6;
        return new int[]{x, y, w, h};
    }

    private static void drawTab(GuiGraphicsExtractor g, int t, int tx, int ty, int mx, int my, float a, boolean mouseHere) {
        boolean over = mouseHere && mx >= tx && mx < tx + TAB && my >= ty && my < ty + TAB;
        if (over && t != tab) {
            round(g, tx, ty, TAB, TAB, 7, HOVER);
        }
        int ix = tx + (TAB - 16) / 2, iy = ty + (TAB - 16) / 2;
        switch (t) {
            case RECENT -> Gfx.item(g, CLOCK, ix, iy); // Minecraft's clock item, it even ticks
            case FAVORITES -> icon(g, ICON_STAR, ix, iy, 16, a);
            case EMOJI -> ChatUi.emoji(g, 2, ix, iy, 16, a);
            default -> {
                Library.Pack p = Library.packs().get(t - PINNED);
                if (!p.stickers().isEmpty()) {
                    Library.draw(g, p.stickers().get(0), ix, iy, 16, 16, a);
                }
            }
        }
    }

    private static String packTitle(String id) {
        for (Library.Pack p : Library.allPacks()) {
            if (p.id().equals(id)) {
                return p.title();
            }
        }
        return id;
    }

    // Input

    /** The chat closed: clear press and preview state. */
    static void reset() {
        pressed = null;
        preview = null;
        smileMenu = false;
    }

    /** Opens on the recent stickers. */
    static void openFresh() {
        open = true;
        openedMs = System.currentTimeMillis();
        pillX = -1;
        tab = RECENT;
        rowScroll = 0;
    }

    /** True if the click was ours. */
    static boolean click(double mx, double my, int button, int sw, int sh) {
        if (smileMenu) {
            smileMenu = false;
            int[] r = smileMenuRect(Minecraft.getInstance().font, sw, sh);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                Prefs.setPinned(!Prefs.pinned());
                if (Prefs.pinned() && !open) {
                    openFresh();
                }
            }
            return true;
        }
        if (overButton(mx, my, sw, sh)) {
            if (button == 1) {
                smileMenu = true;
            } else if (button == 0) {
                if (open) {
                    open = false;
                } else {
                    openFresh();
                }
            }
            return true;
        }
        if (!overPanel(mx, my, sw, sh)) {
            return false;
        }
        int tabsY = tabsY(sh);
        if (my >= tabsY && my < tabsY + TAB) {
            int t = -1;
            if (mx >= tabsX(sw) && mx < tabsX(sw) + PINNED * TAB) {
                t = (int) ((mx - tabsX(sw)) / TAB);
            } else if (mx >= packTabsX(sw) && mx < packTabsX(sw) + packTabsW(sw)) {
                t = PINNED + (int) ((mx - packTabsX(sw) + tabScroll) / TAB);
            }
            if (t >= 0 && t < tabCount() && t != tab) {
                tab = t;
                rowScroll = 0;
            }
            return true;
        }
        Library.Sticker s = stickerAt(mx, my, sw, sh);
        if (s != null) {
            if (button == 1) {
                Library.toggleFavorite(s);
            } else if (button == 0) { // sent on release unless held for the preview
                pressed = s;
                pressMs = System.currentTimeMillis();
                preview = null;
            }
        }
        return true;
    }

    /** Mouse released: a quick click sends, after a hold the preview just closes. True if ours. */
    static boolean release(double mx, double my, int button, int sw, int sh) {
        if (button != 0 || pressed == null) {
            return false;
        }
        Library.Sticker was = pressed;
        boolean held = preview != null;
        pressed = null;
        preview = null;
        if (!held && was.equals(stickerAt(mx, my, sw, sh))) {
            if (Library.isEmoji(was.pack())) { // emoji go into the text
                Library.used(was);
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen chat) {
                    var input = ((com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor) chat).ingamemessenger$input();
                    input.insertText(ChatInput.emojiChar(was));
                    chat.setFocused(input);
                } else { // hold-to-pick mode has no input: open the chat with it
                    open = false;
                    ClientEvents.swallowHeldKeys(); // the held key types nothing until released
                    mc.gui.setScreen(Compat.chatScreen(ChatInput.emojiChar(was)));
                }
            } else {
                send(was); // stickers are sent right away
            }
        }
        return true;
    }

    /** Sticker under the mouse, or null. */
    static Library.Sticker stickerAt(double mx, double my, int sw, int sh) {
        if (!overPanel(mx, my, sw, sh)) {
            return null;
        }
        int gy = gridY(sh), x = left(sw), w = width(sw);
        if (my < gy || my >= gy + GRID_H) {
            return null;
        }
        List<Row> rows = rows(sw);
        int ry = gy;
        for (int r = Math.max(0, rowScroll); r < rows.size() && ry < gy + GRID_H; r++) {
            Row row = rows.get(r);
            if (my >= ry && my < ry + row.h() && row.heading() == null) {
                int cols = Math.max(1, (w - 2 * PAD) / row.cell()), gx = x + (w - cols * row.cell()) / 2;
                int c = (int) Math.floor((mx - gx) / row.cell());
                return c >= 0 && c < row.items().size() ? row.items().get(c) : null;
            }
            ry += row.h();
        }
        return null;
    }

    /** Sends a sticker and adds it to Recent. */
    static void send(Library.Sticker s) {
        if (Ai.ID.equals(ChatChannels.channel())) { // Gemini only reads text
            Minecraft.getInstance().gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.translatable("ingamemessenger.ai.text_only"), false);
            return;
        }
        Net.toServer(new Packets.Send(s.pack(), s.id(), ChatInput.takeReply()));
        Library.used(s);
        Minecraft mc = Minecraft.getInstance();
        if (Prefs.keepOpen() && mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
            return; // "keep chat open": the chat and the panel stay
        }
        open = false;
        mc.gui.setScreen(null);
    }

    /** True if the wheel was over the panel. */
    static boolean scroll(double mx, double my, double delta, int sw, int sh) {
        if (!overPanel(mx, my, sw, sh)) {
            return false;
        }
        int tabsY = tabsY(sh);
        if (my >= tabsY && my < tabsY + TAB) {
            tabScroll -= (int) Math.signum(delta) * TAB;
        } else {
            rowScroll = Math.max(0, Math.min(rowScroll - (int) Math.signum(delta), maxScroll(rows(sw))));
        }
        return true;
    }

    // Shared with the settings window

    /** Window background. */
    static void window(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        glass(g, x, y, w, h);
    }

    /** Recessed area inside the glass. */
    static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        round(g, x, y, w, h, 7, 0x40000000);
    }
}
