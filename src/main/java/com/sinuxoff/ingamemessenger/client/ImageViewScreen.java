package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full-size picture view. It grows out of its place in the chat and shrinks back on close (click or Esc). */
final class ImageViewScreen extends BaseScreen {
    private static final long OPEN_MS = 220, CLOSE_MS = 170;

    private final Screen back;
    private final Library.Sticker pic;
    private final String who;
    private final int chatScroll;
    /** Its rectangle in the chat, in screen pixels. */
    private final float fromX, fromY, fromW, fromH;
    private final long opened = System.currentTimeMillis();
    private long closing = -1;

    ImageViewScreen(Screen back, Library.Sticker pic, String who, int chatScroll, float fromX, float fromY, float fromW, float fromH) {
        super(Component.translatable("ingamemessenger.image.word"));
        this.back = back;
        this.pic = pic;
        this.who = who;
        this.chatScroll = chatScroll;
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromW = Math.max(1, fromW);
        this.fromH = Math.max(1, fromH);
    }

    private boolean web() {
        return pic.id().startsWith("https://");
    }

    /** {x, y, w, h} of the "open in browser" link. */
    private int[] linkBox() {
        Component c = Component.translatable("ingamemessenger.image.open");
        int w = font.width(c) + 16;
        return new int[]{(width - w) / 2, height - 22, w, 14};
    }

    /** 0 = in its chat position, 1 = fully open (eased). */
    private float shown() {
        long now = System.currentTimeMillis();
        if (closing >= 0) {
            float t = 1f - Math.min(1f, (now - closing) / (float) CLOSE_MS);
            return t * t * (3 - 2 * t);
        }
        float t = Math.min(1f, (now - opened) / (float) OPEN_MS);
        return 1 - (1 - t) * (1 - t) * (1 - t); // ease-out cubic
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, Math.round(shown() * 0xE0) << 24 | 0x0B0D12);
    }

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float pt) {
        if (closing >= 0 && System.currentTimeMillis() - closing >= CLOSE_MS) {
            back();
            return;
        }
        drawScreen(g, mx, my, pt);
        float t = shown();
        int boxW = width - 40, boxH = height - 60;
        float k = Math.min(boxW / (float) pic.w(), boxH / (float) pic.h());
        int w = Math.max(1, Math.round(pic.w() * k)), h = Math.max(1, Math.round(pic.h() * k));
        int x = (width - w) / 2, y = 26 + (boxH - h) / 2;
        // drawn at full size, moved and scaled towards the chat position by (1 - t)
        float s0 = Math.max(fromW / w, fromH / h);
        float s = s0 + (1 - s0) * t;
        float cx = fromX + fromW / 2 + (x + w / 2f - fromX - fromW / 2) * t;
        float cy = fromY + fromH / 2 + (y + h / 2f - fromY - fromH / 2) * t;
        Gfx.push(g);
        Gfx.move(g, cx, cy);
        Gfx.scale(g, s, s);
        Gfx.move(g, -w / 2f, -h / 2f);
        if (Library.failed(pic)) {
            Gfx.centeredText(g, font, Component.translatable("ingamemessenger.image.gone"), w / 2, h / 2 - 4, 0xFF8E95A6);
        } else {
            Library.drawRounded(g, pic, 0, 0, w, h, 8 / Math.max(0.15f, s), 1f);
        }
        Gfx.pop(g);
        int textA = Math.round(Math.max(0, t * 2 - 1) * 255); // labels fade in last
        if (textA > 8) {
            if (who != null) {
                Gfx.text(g, font, who, 12, 9, textA << 24 | ChatDecor.colorOf(who), false);
            }
            Component close = Component.translatable("ingamemessenger.image.close");
            Gfx.text(g, font, close, width - 12 - font.width(close), 9, textA << 24 | 0x8E95A6, false);
            if (web()) {
                int[] b = linkBox();
                boolean over = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
                Panel.round(g, b[0], b[1], b[2], b[3], 6, Math.round(textA * (over ? 1f : 0.75f)) << 24 | (over ? 0x5B4BD6 : 0x262B38));
                Gfx.centeredText(g, font, Component.translatable("ingamemessenger.image.open"), width / 2, b[1] + 3, textA << 24 | 0xF2F3F7);
            }
        }
    }

    @Override
    protected boolean click(double mx, double my, int button) {
        if (closing >= 0) {
            return true;
        }
        if (web()) {
            int[] b = linkBox();
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                Compat.openUri(pic.id());
                return true;
            }
        }
        onClose();
        return true;
    }

    /** Shrinks back into the chat before closing. */
    @Override
    public void onClose() {
        if (closing < 0) {
            closing = System.currentTimeMillis();
        }
    }

    private void back() {
        minecraft.setScreen(back);
        ((com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor) minecraft.gui.getChat()).ingamemessenger$setScroll(chatScroll);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
