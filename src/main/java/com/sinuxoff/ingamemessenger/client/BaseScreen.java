package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for the mod's screens, hiding how each Minecraft version names its render and input methods. Subclasses
 * override draw / drawBackground / click / release / key / keyUp (returning true if handled) and call drawScreen for
 * the vanilla background and widgets.
 */
public abstract class BaseScreen extends Screen {
    protected BaseScreen(Component title) {
        super(title);
    }

    /** Defaults to the vanilla background and widgets. */
    protected void draw(GuiGraphics g, int mx, int my, float pt) {
        drawScreen(g, mx, my, pt);
    }

    /** Defaults to the vanilla blurred background. */
    protected void drawBackground(GuiGraphics g, int mx, int my, float pt) {
        drawDefaultBackground(g, mx, my, pt);
    }

    /** True if handled. */
    protected boolean click(double mx, double my, int button) {
        return false;
    }

    protected boolean release(double mx, double my, int button) {
        return false;
    }

    /** GLFW key, scancode, modifiers. */
    protected boolean key(int key, int scan, int mods) {
        return false;
    }

    protected boolean keyUp(int key, int scan, int mods) {
        return false;
    }

    // Version-specific overrides

    //? if >=26.1 {
    /*@Override
    public final void extractRenderState(GuiGraphics g, int mx, int my, float pt) {
        draw(g, mx, my, pt);
    }

    /^* Minecraft's background and the widgets. *^/
    protected final void drawScreen(GuiGraphics g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
    }

    @Override
    public final void extractBackground(GuiGraphics g, int mx, int my, float pt) {
        drawBackground(g, mx, my, pt);
    }

    protected final void drawDefaultBackground(GuiGraphics g, int mx, int my, float pt) {
        super.extractBackground(g, mx, my, pt);
    }
    *///?} else {
    @Override
    public final void render(GuiGraphics g, int mx, int my, float pt) {
        draw(g, mx, my, pt);
    }

    /** Vanilla background and widgets. */
    protected final void drawScreen(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
    }

    @Override
    public final void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        drawBackground(g, mx, my, pt);
    }

    protected final void drawDefaultBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
    }
    //?}

    //? if >=1.21.9 {
    /*@Override
    public final boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
        return click(e.x(), e.y(), Compat.button(e.button())) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public final boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        return release(e.x(), e.y(), Compat.button(e.button())) || super.mouseReleased(e);
    }

    @Override
    public final boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        return key(e.key(), scan(e), e.modifiers()) || super.keyPressed(e);
    }

    @Override
    public final boolean keyReleased(net.minecraft.client.input.KeyEvent e) {
        return keyUp(e.key(), scan(e), e.modifiers()) || super.keyReleased(e);
    }

    private static int scan(net.minecraft.client.input.KeyEvent e) {
        //? if >=26.3 {
        /^return e.keycode();
        ^///?} else {
        /^return e.scancode();
        ^///?}
    }
    *///?} else {
    @Override
    public final boolean mouseClicked(double mx, double my, int button) {
        return click(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public final boolean mouseReleased(double mx, double my, int button) {
        return release(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public final boolean keyPressed(int key, int scan, int mods) {
        return key(key, scan, mods) || super.keyPressed(key, scan, mods);
    }

    @Override
    public final boolean keyReleased(int key, int scan, int mods) {
        return keyUp(key, scan, mods) || super.keyReleased(key, scan, mods);
    }
    //?}
}
