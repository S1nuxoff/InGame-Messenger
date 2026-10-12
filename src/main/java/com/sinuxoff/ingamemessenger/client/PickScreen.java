package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Quick sticker picker, open while the chat key (or our key) is held: click a sticker to send, release to close. */
final class PickScreen extends BaseScreen {
    private final net.minecraft.client.KeyMapping holdKey;

    PickScreen(net.minecraft.client.KeyMapping holdKey) {
        super(Component.translatable("ingamemessenger.screen.title"));
        this.holdKey = holdKey;
    }

    @Override
    protected void init() {
        Panel.centered = true;
        Panel.openFresh();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float pt) {
        drawScreen(g, mx, my, pt);
        int panelTop = (height - Panel.panelHeight()) / 2;
        Gfx.centeredText(g, font, Component.translatable("ingamemessenger.pick.hint"), width / 2, panelTop + Panel.panelHeight() + 8, 0xA0A0A0);
        Panel.render(g, mx, my, width, height);
    }

    @Override
    protected boolean click(double mx, double my, int button) {
        if (Compat.isMouse(holdKey, button)) {
            return true;
        }
        if (!Panel.click(mx, my, button, width, height) && button == 0) {
            onClose(); // clicked outside the panel
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        Panel.scroll(mx, my, dy, width, height);
        return true;
    }

    @Override
    protected boolean release(double mx, double my, int button) {
        if (Compat.isMouse(holdKey, button)) {
            onClose(); // key released
            return true;
        }
        if (Panel.release(mx, my, button, width, height)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean keyUp(int key, int scan, int mods) {
        if (Compat.isKey(holdKey, key, scan)) {
            onClose(); // key released
            return true;
        }
        return false;
    }

    @Override
    protected boolean key(int key, int scan, int mods) {
        if (Compat.isKey(holdKey, key, scan)) {
            return true; // ignore key repeats
        }
        return false;
    }

    @Override
    public void removed() {
        Panel.centered = false;
        Panel.open = false;
        Panel.reset();
        ClientEvents.waitRelease = true; // the chat key may still be held, don't let it open the chat
    }
}
