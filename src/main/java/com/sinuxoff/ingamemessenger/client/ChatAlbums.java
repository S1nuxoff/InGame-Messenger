package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Picture messages (1..4 pictures): grid layout, chat lines, drawing, incoming. */
final class ChatAlbums {
    private ChatAlbums() {
    }

    record Pic(String ref, int w, int h) {
    }

    /** Layout: block size, each cell {x, y, w, h}, and the lines it takes. */
    record AlbumInfo(List<Pic> pics, String caption, int width, int height, int lines, List<int[]> cells) {
    }

    /** Bubble padding around the pictures. */
    static final int ALBUM_PAD = 3;
    /**
     * Grid: one picture alone; two side by side; three as one on top and two below; four as 2x2. Pictures in a row
     * share a height, the block is as wide as the chat allows and not too tall.
     */
    static AlbumInfo album(List<Pic> pics, String caption) {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.hud.getChat();
        int lineH = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
        int chatW = (int) (Compat.chatWidth(chat) / Math.max(0.1, Compat.chatScale(chat)));
        // independent of the sticker size setting: at most 150 wide and 160 tall, smaller in a narrow chat
        float w = Math.min(chatW - Faces.AV - 2 * ALBUM_PAD - 12, 150);
        float maxH = 160;
        int[][] rows = pics.size() <= 2 ? new int[][]{pics.size() == 1 ? new int[]{0} : new int[]{0, 1}}
                : pics.size() == 3 ? new int[][]{{0}, {1, 2}} : new int[][]{{0, 1}, {2, 3}};
        final int gap = 2;
        List<int[]> cells = new ArrayList<>();
        int height = 0;
        for (int pass = 0; pass < 2; pass++) {
            cells.clear();
            float y = 0;
            for (int r = 0; r < rows.length; r++) {
                float sum = 0;
                for (int i : rows[r]) {
                    sum += shape(pics.get(i));
                }
                float rh = (w - gap * (rows[r].length - 1)) / sum;
                float x = 0;
                for (int k = 0; k < rows[r].length; k++) {
                    Pic pc = pics.get(rows[r][k]);
                    int cw = k == rows[r].length - 1 ? Math.round(w - x) : Math.round(rh * shape(pc));
                    cells.add(new int[]{Math.round(x), Math.round(y), cw, Math.round(rh)});
                    x += cw + gap;
                }
                y += rh + (r < rows.length - 1 ? gap : 0);
            }
            height = Math.round(y);
            if (height <= maxH || pass == 1) {
                break;
            }
            w = w * maxH / height;
        }
        // round to whole lines; rows are stretched slightly so the block fills them exactly
        int lines = Math.max(2, Math.round(height / (float) lineH));
        int fit = (lines - 1) * lineH + 9;
        float k = fit / (float) Math.max(1, height);
        List<int[]> fitted = new ArrayList<>();
        for (int[] c : cells) {
            int y0 = Math.round(c[1] * k), y1 = Math.round((c[1] + c[3]) * k);
            fitted.add(new int[]{c[0], y0, c[2], Math.max(1, y1 - y0)});
        }
        return new AlbumInfo(List.copyOf(pics), caption == null ? "" : caption.strip(), Math.round(w), fit, lines, List.copyOf(fitted));
    }

    /** Aspect ratio for the grid, clamped between 1:2 and 5:2 (beyond that the picture is cropped). */
    private static float shape(Pic p) {
        return Math.max(0.5f, Math.min(2.5f, p.w() / (float) p.h()));
    }

    /** Pictures in their cells (placeholder while loading, a note if deleted); click to open. */
    static void drawAlbum(GuiGraphicsExtractor g, AlbumInfo ai, String who, int x, int y, float a) {
        for (int i = 0; i < ai.pics().size() && i < ai.cells().size(); i++) {
            Pic pc = ai.pics().get(i);
            int[] c = ai.cells().get(i);
            int cx = x + c[0], cy = y + c[1];
            Library.Sticker st = Library.image(pc.ref(), pc.w(), pc.h());
            if (Library.failed(st)) {
                Panel.round(g, cx, cy, c[2], c[3], 6, (int) (a * 0xC0) << 24 | 0x12151C);
                ChatDates.smallText(g, Component.translatable("ingamemessenger.image.gone").getString(), cx + 6, cy + c[3] / 2f - 3,
                        (int) (a * 0xC0) << 24 | 0x8E95A6);
            } else if (!Library.ready(st)) {
                Panel.round(g, cx, cy, c[2], c[3], 6, (int) (a * 0xC0) << 24 | 0x12151C);
                int ph = (int) (System.currentTimeMillis() / 300 % 3), mx = cx + c[2] / 2, my = cy + c[3] / 2;
                for (int d = 0; d < 3; d++) {
                    Panel.round(g, mx - 9 + d * 7, my - 2, 4, 4, 2, (int) (a * (d == ph ? 0xF0 : 0x60)) << 24 | 0xC8CCD8);
                }
            } else {
                Library.drawRounded(g, st, cx, cy, c[2], c[3], 6, a, true);
            }
            ChatUi.picture(st, who, cx, cy, c[2], c[3]);
        }
    }

}
