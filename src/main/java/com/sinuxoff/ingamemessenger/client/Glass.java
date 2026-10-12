package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Rounded translucent buttons, sliders and switches. */
final class Glass {
    static final int ACCENT = 0xFF7B68FF, ACCENT_HOVER = 0xFF9285FF, TEXT = 0xFFF2F3F7, MUTED = 0xFF8E95A6;
    static final int FILL = 0x1CFFFFFF, FILL_HOVER = 0x30FFFFFF;

    private Glass() {
    }

    /** Base for our widgets: press() and draw(). Hides how each Minecraft version names onPress and the render method. */
    abstract static class Pressable extends AbstractButton {
        Pressable(int x, int y, int w, int h, Component message) {
            super(x, y, w, h, message);
        }

        abstract void press();

        abstract void draw(GuiGraphics g, int mx, int my, float pt);

        //? if >=1.21.9 {
        /*@Override
        public final void onPress(net.minecraft.client.input.InputWithModifiers input) {
            press();
        }
        *///?} else {
        @Override
        public final void onPress() {
            press();
        }
        //?}

        @Override
        //? if >=26.1 {
        /*protected final void extractContents(GuiGraphics g, int mx, int my, float pt) {
        *///?} else if >=1.21.11 {
        /*protected final void renderContents(GuiGraphics g, int mx, int my, float pt) {
        *///?} else {
        protected final void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        //?}
            draw(g, mx, my, pt);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** Rounded button; primary ones use the accent color. The label can be dynamic. */
    static class Button extends Pressable {
        private final Runnable onPress;
        private final boolean primary;
        private final Supplier<Component> label;

        Button(int x, int y, int w, int h, Supplier<Component> label, boolean primary, Runnable onPress) {
            super(x, y, w, h, label.get());
            this.label = label;
            this.primary = primary;
            this.onPress = onPress;
        }

        @Override
        void press() {
            onPress.run();
            setMessage(label.get());
        }

        @Override
        void draw(GuiGraphics g, int mx, int my, float pt) {
            setMessage(label.get());
            boolean hover = isHovered() && active;
            int bg = primary ? (hover ? ACCENT_HOVER : ACCENT) : hover ? FILL_HOVER : FILL;
            Panel.round(g, getX(), getY(), width, height, 6, bg);
            if (primary) {
                g.fill(getX() + 5, getY(), getX() + width - 5, getY() + 1, 0x40FFFFFF);
            }
            var font = Minecraft.getInstance().font;
            Gfx.centeredText(g, font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, active ? TEXT : MUTED);
        }
    }

    /** Integer slider: rounded track filled with the accent, round knob, label above. */
    static final class Slider extends AbstractSliderButton {
        private final int min, max;
        private final IntFunction<Component> label;
        private final IntConsumer set;

        Slider(int x, int y, int w, int h, int min, int max, int current, IntFunction<Component> label, IntConsumer set) {
            super(x, y, w, h, label.apply(current), (current - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.label = label;
            this.set = set;
        }

        private int v() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(v()));
        }

        @Override
        protected void applyValue() {
            set.accept(v());
        }

        @Override
        //? if >=26.1 {
        /*public void extractWidgetRenderState(GuiGraphics g, int mx, int my, float pt) {
        *///?} else {
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        //?}
            boolean hover = isHovered();
            int x = getX(), y = getY();
            Panel.round(g, x, y, width, height, 7, hover ? FILL_HOVER : FILL);
            var font = Minecraft.getInstance().font;
            Gfx.text(g, font, getMessage(), x + 8, y + 4, TEXT, false);
            int tx = x + 8, tw = width - 16, ty = y + height - 8;
            Panel.round(g, tx, ty, tw, 3, 1, 0x30FFFFFF);
            int kx = tx + (int) Math.round(value * tw);
            Panel.round(g, tx, ty, Math.max(3, kx - tx), 3, 1, ACCENT);
            Panel.round(g, kx - 4, ty - 3, 9, 9, 4, hover ? 0xFFFFFFFF : 0xFFE4E6EE);
        }
    }
    /** Settings row with a switch on the right; the whole row is clickable. Greyed out when disabled. */
    static class Switch extends Pressable {
        private final Supplier<Component> label;
        private final java.util.function.BooleanSupplier on, enabled;
        private final Runnable toggle;
        private float knob = -1;

        Switch(int x, int y, int w, int h, Supplier<Component> label, java.util.function.BooleanSupplier on,
               java.util.function.BooleanSupplier enabled, Runnable toggle) {
            super(x, y, w, h, label.get());
            this.label = label;
            this.on = on;
            this.enabled = enabled;
            this.toggle = toggle;
        }

        @Override
        void press() {
            if (enabled.getAsBoolean()) {
                toggle.run();
            }
        }

        @Override
        void draw(GuiGraphics g, int mx, int my, float pt) {
            active = enabled.getAsBoolean();
            boolean isOn = on.getAsBoolean() && active, hover = isHovered() && active;
            int x = getX(), y = getY();
            Panel.round(g, x, y, width, height, 7, hover ? FILL_HOVER : FILL);
            var font = Minecraft.getInstance().font;
            Gfx.text(g, font, label.get(), x + 9, y + (height - 8) / 2, active ? TEXT : MUTED, false);
            int tw = 22, th = 12, tx = x + width - 9 - tw, ty = y + (height - th) / 2;
            float target = isOn ? 1f : 0f;
            knob = knob < 0 ? target : knob + (target - knob) * 0.35f;
            Panel.round(g, tx, ty, tw, th, th / 2, isOn ? (active ? ACCENT : 0xFF5A5470) : 0x40FFFFFF);
            int kx = tx + 2 + Math.round(knob * (tw - th));
            Panel.round(g, kx, ty + 2, th - 4, th - 4, (th - 4) / 2, active ? 0xFFFFFFFF : 0xFFB0B4C0);
        }
    }
}
