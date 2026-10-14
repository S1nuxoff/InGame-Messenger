package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings window with three tabs: Chat, Packs (the server's packs) and Sound. */
final class SettingsScreen extends BaseScreen {
    private static final int WIN_W = 326, COL = 310, MAX_H = 392, ROW = 22, GAP = 5;
    private static final int TITLE_COLOR = 0xF2F3F7, DARK = 0x8E95A6;
    private static final int TEXT = 0xFFFFFF, MUTED = 0xA0A0A0;
    private static final int CHAT = 0, PACKS = 1, SOUND = 2, TABS = 3;

    /** Remembered while the window is closed. */
    private static int tab = CHAT;

    /** The tab the window opens on next (the test run goes through them). */
    static void openOn(int t) {
        tab = Math.max(0, Math.min(TABS - 1, t));
    }

    private final Screen parent;
    private PackList list;
    private int shownVersion = -1;

    SettingsScreen(Screen parent) {
        super(Component.translatable("ingamemessenger.screen.title"));
        this.parent = parent;
    }

    private int left() {
        return (width - COL) / 2;
    }

    private int winH() {
        return Math.min(height - 16, MAX_H);
    }

    private int top() {
        return (height - winH()) / 2;
    }

    /** Top of a tab's content, under the title and tabs. */
    private int contentY() {
        return top() + 30;
    }

    private int listY() {
        return contentY() + 30;
    }

    /** The muted players row on the Chat tab. */
    private int mutedY() {
        return contentY() + 6 * (ROW + GAP) + 4 + 3 * (28 + GAP) + 4;
    }

    // tabs, top right
    private static final int TAB_W = 52, TAB_H = 18;

    private int tabX(int i) {
        int order = i == SOUND ? 1 : i == CHAT ? 0 : i == PACKS ? 2 : 3;
        return left() + COL - TABS * TAB_W - (TABS - 1) * 3 + order * (TAB_W + 3);
    }

    @Override
    protected void init() {
        int x = left(), t = top();
        list = null;
        if (tab == CHAT) {
            int y = contentY();
            addRenderableWidget(new Glass.Switch(x, y, COL, ROW, () -> Component.translatable("ingamemessenger.screen.show_typing2"),
                    Prefs::showMyTyping, () -> Typing.serverOn, Prefs::toggleShowTyping));
            y += ROW + GAP;
            addRenderableWidget(new Glass.Switch(x, y, COL, ROW, () -> Component.translatable("ingamemessenger.screen.see_typing2"),
                    Prefs::seeTyping, () -> Typing.serverOn, Prefs::toggleSeeTyping));
            y += ROW + GAP;
            addRenderableWidget(new Glass.Switch(x, y, COL, ROW, () -> Component.translatable("ingamemessenger.screen.own_right"),
                    Prefs::ownRight, () -> true, Prefs::toggleOwnRight));
            y += ROW + GAP;
            addRenderableWidget(new Glass.Switch(x, y, COL, ROW, () -> Component.translatable("ingamemessenger.screen.hide_hotbar"),
                    Prefs::hideHotbar, () -> true, Prefs::toggleHideHotbar));
            y += ROW + GAP;
            addRenderableWidget(new Glass.Switch(x, y, COL, ROW, () -> Component.translatable("ingamemessenger.screen.keep_open"),
                    Prefs::keepOpen, () -> true, Prefs::toggleKeepOpen));
            y += ROW + GAP + 4;
            addRenderableWidget(new Glass.Slider(x, y, COL, 28, Prefs.MIN_SCALE, Prefs.MAX_SCALE, Prefs.chatScale(),
                    v -> Component.translatable("ingamemessenger.screen.scale", v), Prefs::setChatScale));
            y += 28 + GAP;
            addRenderableWidget(new Glass.Slider(x, y, COL, 28, Prefs.MIN_LINES, Prefs.MAX_LINES, Prefs.stickerLines(),
                    v -> Component.translatable("ingamemessenger.screen.size", v), Prefs::setStickerLines));
            y += 28 + GAP;
            addRenderableWidget(new Glass.Slider(x, y, COL, 28, Prefs.MIN_HEIGHT, Prefs.MAX_HEIGHT, Prefs.chatHeight(),
                    v -> Component.translatable("ingamemessenger.screen.height", v), Prefs::setChatHeight));
            // muted players: count (drawn) and a button to unmute all
            addRenderableWidget(new Glass.Button(x + COL - 108, mutedY() + 3, 104, ROW - 6, () -> Component.translatable("ingamemessenger.screen.unmute"),
                    false, Prefs::unmuteAll) {
                @Override
                void draw(GuiGraphicsExtractor g, int mx, int my, float pt) {
                    active = Prefs.mutedCount() > 0;
                    super.draw(g, mx, my, pt);
                }
            });
        } else if (tab == SOUND) {
            int y = contentY();
            addRenderableWidget(new Glass.Button(x, y, COL, ROW, () -> Component.translatable("ingamemessenger.sound.mic",
                    shortName(Prefs.micDevice(), "ingamemessenger.sound.default")), false, () -> picking = 1));
            y += ROW + GAP;
            addRenderableWidget(new Glass.Slider(x, y, COL, 28, -10, 20, Prefs.micGainDb(),
                    v -> Component.translatable("ingamemessenger.sound.gain", (v > 0 ? "+" : "") + v), Prefs::setMicGainDb));
            y += 28 + GAP;
            addRenderableWidget(new Glass.Slider(x, y, COL, 28, 0, 100, Prefs.noiseGate(),
                    v -> Component.translatable("ingamemessenger.sound.gate", v), Prefs::setNoiseGate));
            y += 28 + GAP;
            addRenderableWidget(new Glass.Button(x, y, 150, ROW, () -> Component.translatable(Audio.testing()
                    ? "ingamemessenger.sound.test_stop" : "ingamemessenger.sound.test"), true, Audio::toggleTest));
            addRenderableWidget(new Glass.Switch(x + 156, y, COL - 156, ROW, () -> Component.translatable("ingamemessenger.sound.hear"),
                    Audio::hearSelf, () -> true, () -> Audio.setHearSelf(!Audio.hearSelf())));
            y += ROW + GAP + 14; // room for the level bar
            addRenderableWidget(new Glass.Button(x, y, COL, ROW, () -> Component.translatable("ingamemessenger.sound.speakers",
                    shortName(Prefs.speakerDevice(), "ingamemessenger.sound.as_mc")), false, () -> picking = 2));
            y += ROW + GAP;
            addRenderableWidget(new Glass.Slider(x, y, COL, 28, 0, Prefs.MAX_VOICE_VOLUME, Prefs.voiceVolume(),
                    v -> Component.translatable("ingamemessenger.screen.voice_volume", v), Prefs::setVoiceVolume));
        } else {
            list = new PackList(minecraft, COL, Math.max(30, t + winH() - 34 - listY()), listY());
            //? if >=1.20.2 {
            list.setX(x);
            //?} else {
            /*list.setLeftPos(x);
            *///?}
            addRenderableWidget(list);
            list.fill();
            shownVersion = Library.allPacks().hashCode();
        }
        addRenderableWidget(new Glass.Button(x + COL - 100, t + winH() - 26, 100, 20,
                () -> Component.translatable("gui.done"), true, this::onClose));
    }

    @Override
    protected boolean click(double mx, double my, int button) {
        int ty = top() + 6;
        if (picking != 0) { // device list open: pick, or click outside to close
            pickDevice(mx, my);
            return true;
        }
        for (int i = 0; i < TABS; i++) {
            if (button == 0 && mx >= tabX(i) && mx < tabX(i) + TAB_W && my >= ty && my < ty + TAB_H) {
                if (tab != i) {
                    tab = i;
                    rebuildWidgets();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        int v = Library.allPacks().hashCode();
        if (list != null && v != shownVersion) { // the server reloaded its packs
            shownVersion = v;
            list.fill();
        }
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, int mx, int my, float pt) {
        drawScreen(g, mx, my, pt);
        int x = left(), t = top();
        Gfx.text(g, font, title, x, t + 11, TITLE_COLOR, false);
        // pill under the selected tab
        int ty = t + 6;
        for (int i = 0; i < TABS; i++) {
            int tx = tabX(i);
            boolean over = mx >= tx && mx < tx + TAB_W && my >= ty && my < ty + TAB_H;
            if (i == tab) {
                Panel.round(g, tx, ty, TAB_W, TAB_H, TAB_H / 2, Glass.ACCENT);
            } else if (over) {
                Panel.round(g, tx, ty, TAB_W, TAB_H, TAB_H / 2, Glass.FILL_HOVER);
            }
            Component name = Component.translatable(i == CHAT ? "ingamemessenger.tab.chat"
                    : i == SOUND ? "ingamemessenger.tab.sound" : "ingamemessenger.tab.packs");
            Gfx.fittedText(g, font, name, tx + TAB_W / 2, ty + 5, TAB_W - 6, i == tab ? 0xFFFFFF : 0xC8CCD8, true, true);
        }
        if (tab == SOUND) { // microphone level while testing, device list when picking
            int ly = testY() + ROW + GAP + 3;
            Panel.round(g, x, ly, COL, 5, 2, 0x40000000);
            float lv = Audio.testLevel();
            if (lv > 0) {
                Panel.round(g, x, ly, Math.max(5, Math.round(COL * lv)), 5, 2, lv > 0.85f ? 0xFFE0475A : 0xFF4CD98A);
            }
            int gx = x + Math.round(COL * Math.min(1f, Prefs.noiseGate() / 2000f * 4f)); // noise gate threshold
            g.fill(gx, ly - 2, gx + 1, ly + 7, 0xC0FFFFFF);
            if (picking != 0) {
                drawPicker(g, mx, my);
            }
            return;
        }
        if (tab == CHAT) {
            Gfx.text(g, font, Component.translatable("ingamemessenger.screen.muted_row", Prefs.mutedCount()), x + 9, mutedY() + 8, Prefs.mutedCount() > 0 ? 0xF2F3F7 : DARK, false);
            if (!Typing.serverOn) {
                Gfx.text(g, font, Component.translatable("ingamemessenger.screen.typing_off"), x, t + winH() - 20, DARK, false);
            }
            return;
        }
        Gfx.text(g, font, Component.translatable("ingamemessenger.screen.packs", Library.allPacks().size()), x, contentY(), TITLE_COLOR, false);
        Gfx.text(g, font, Component.translatable("ingamemessenger.screen.packs_hint"), x, contentY() + 13, DARK, false);
        // slim scroll bar of our own; Minecraft's is moved out of sight
        //? if >=1.21.4 {
        int max = list.maxScrollAmount();
        double scrolled = list.scrollAmount();
        //?} else {
        /*int max = list.getMaxScroll();
        double scrolled = list.getScrollAmount();
        *///?}
        if (max > 0) {
            int lh = list.h(), barH = Math.max(14, lh * lh / (lh + max));
            int barY = listY() + (int) ((lh - barH) * scrolled / max);
            Panel.round(g, x + COL - 5, barY + 2, 3, barH - 4, 1, 0x60FFFFFF);
        }
        if (Library.allPacks().isEmpty()) {
            Gfx.centeredText(g, font, Component.translatable("ingamemessenger.screen.no_packs"), width / 2, listY() + 20, TEXT);
        }
    }

    @Override
    protected void drawBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x50000000);
        Panel.window(g, (width - WIN_W) / 2, top() - 2, WIN_W, winH() + 2);
        if (tab == PACKS && list != null) {
            Panel.inset(g, left() - 1, listY() - 1, COL + 2, list.h() + 2);
        } else if (tab == CHAT) {
            Panel.round(g, left(), mutedY(), COL, ROW, 7, Glass.FILL);
        }
    }

    @Override
    public void onClose() {
        if (Audio.testing()) {
            Audio.toggleTest();
        }
        minecraft.gui.setScreen(parent);
    }

    // Device list (1 = microphone, 2 = speakers)

    private int picking;

    /** Y of the test row on the Sound tab. */
    private int testY() {
        return contentY() + (ROW + GAP) + 2 * (28 + GAP);
    }

    /** Device name without OpenAL's "OpenAL Soft on " prefix; "" is the default. */
    private static Component shortName(String d, String defaultKey) {
        if (d == null || d.isEmpty()) {
            return Component.translatable(defaultKey);
        }
        return Component.literal(d.replaceFirst("^OpenAL Soft on ", ""));
    }

    private java.util.List<String> devices() {
        java.util.List<String> l = new java.util.ArrayList<>();
        l.add("");
        l.addAll(picking == 1 ? Audio.microphones() : Audio.speakers());
        return l;
    }

    private int[] pickerRect() {
        int n = Math.min(10, devices().size());
        int y = picking == 1 ? contentY() + ROW + 2 : testY() + ROW + GAP + 14 + ROW + 2;
        int h = n * 16 + 6;
        y = Math.min(y, height - h - 6);
        return new int[]{left(), y, COL, h};
    }

    private void drawPicker(GuiGraphicsExtractor g, int mx, int my) {
        int[] r = pickerRect();
        Gfx.push(g);
        Gfx.raise(g, 500);
        ChatInput.glassBox(g, r[0], r[1], r[2], r[3]);
        java.util.List<String> l = devices();
        String now = picking == 1 ? Prefs.micDevice() : Prefs.speakerDevice();
        for (int i = 0; i < l.size() && i < 10; i++) {
            int iy = r[1] + 3 + i * 16;
            boolean over = mx >= r[0] && mx < r[0] + r[2] && my >= iy && my < iy + 16;
            boolean on = l.get(i).equals(now);
            if (over || on) {
                Panel.round(g, r[0] + 3, iy, r[2] - 6, 16, 5, on ? 0x605B4BD6 : 0x1CFFFFFF);
            }
            Component name = shortName(l.get(i), picking == 1 ? "ingamemessenger.sound.default" : "ingamemessenger.sound.as_mc");
            Gfx.text(g, font, font.plainSubstrByWidth(name.getString(), r[2] - 16), r[0] + 8, iy + 4, over ? 0xFFFFFFFF : 0xFFC8CCD8, false);
        }
        Gfx.pop(g);
    }

    private void pickDevice(double mx, double my) {
        int[] r = pickerRect();
        java.util.List<String> l = devices();
        int i = (int) Math.floor((my - r[1] - 3) / 16);
        if (mx >= r[0] && mx < r[0] + r[2] && i >= 0 && i < Math.min(10, l.size())) {
            if (picking == 1) {
                Prefs.setMicDevice(l.get(i));
                if (Audio.testing()) { // restart the test with the new device
                    Audio.toggleTest();
                }
            } else {
                Prefs.setSpeakerDevice(l.get(i));
            }
        }
        picking = 0;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Pack list

    private final class PackList extends ObjectSelectionList<PackEntry> {
        PackList(Minecraft mc, int width, int height, int y) {
            //? if >=1.20.2 {
            super(mc, width, height, y, 26);
            //?} else {
            /*super(mc, width, height, y, y + height, 26);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
            *///?}
        }

        int h() {
            //? if >=1.20.2 {
            return getHeight();
            //?} else {
            /*return height;
            *///?}
        }

        void fill() {
            clearEntries();
            for (Library.Pack p : Library.allPacks()) {
                addEntry(new PackEntry(p));
            }
        }

        @Override
        public int getRowWidth() {
            return COL;
        }

        // no background or separators, the window draws its own
        //? if >=26.1 {
        @Override
        protected void extractListBackground(GuiGraphicsExtractor g) {
        }

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor g) {
        }
        //?} else if >=1.20.5 {
        /*@Override
        protected void renderListBackground(GuiGraphicsExtractor g) {
        }

        @Override
        protected void renderListSeparators(GuiGraphicsExtractor g) {
        }
        *///?}

        //? if <1.21.9 {
        /*@Override
        protected boolean isSelectedItem(int index) {
            return false; // no selection frame, rows highlight on hover
        }
        *///?}

        @Override
        //? if >=1.21.4 {
        protected int scrollBarX() {
        //?} else {
        /*protected int getScrollbarPosition() {
        *///?}
            return -1000; // hidden, the window draws a slim one
        }
    }

    private final class PackEntry extends ObjectSelectionList.Entry<PackEntry> {
        private final Library.Pack pack;

        PackEntry(Library.Pack pack) {
            this.pack = pack;
        }

        @Override
        public Component getNarration() {
            return Component.literal(pack.title());
        }

        //? if >=26.1 {
        @Override
        public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hovering, float pt) {
            draw(g, getContentY(), getContentX(), getContentWidth(), getContentHeight(), mx, my, hovering);
        }
        //?} else if >=1.21.9 {
        /*@Override
        public void renderContent(GuiGraphicsExtractor g, int mx, int my, boolean hovering, float pt) {
            draw(g, getContentY(), getContentX(), getContentWidth(), getContentHeight(), mx, my, hovering);
        }
        *///?} else if >=1.20 {
        /*@Override
        public void render(GuiGraphicsExtractor g, int index, int top, int left, int width, int height, int mx, int my,
                           boolean hovering, float pt) {
            draw(g, top, left, width, height, mx, my, hovering);
        }
        *///?} else {
        /*@Override
        public void render(com.mojang.blaze3d.vertex.PoseStack pose, int index, int top, int left, int width, int height, int mx, int my,
                           boolean hovering, float pt) {
            draw(new GuiGraphicsExtractor(pose), top, left, width, height, mx, my, hovering);
        }
        *///?}

        private void draw(GuiGraphicsExtractor g, int top, int left, int width, int height, int mx, int my, boolean hovering) {
            if (hovering) {
                Panel.round(g, left, top - 1, width - 2, height + 2, 6, 0x12FFFFFF);
            }
            if (!pack.stickers().isEmpty()) {
                Library.draw(g, pack.stickers().get(0), left + 2, top + 1, 22, 22, 1f);
            }
            Gfx.text(g, font, font.plainSubstrByWidth(pack.title(), width - 80), left + 30, top + 3, TEXT);
            Gfx.text(g, font, Component.translatable(pack.emoji() ? "ingamemessenger.screen.count_emoji" : "ingamemessenger.screen.count",
                    pack.stickers().size()), left + 30, top + 13, MUTED);
        }

        //? if >=1.21.9 {
        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
            return true;
        }
        //?} else {
        /*@Override
        public boolean mouseClicked(double mx, double my, int button) {
            return true;
        }
        *///?}
    }
}
