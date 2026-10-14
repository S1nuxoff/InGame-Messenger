package com.sinuxoff.ingamemessenger.client;

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

import java.util.ArrayList;
import java.util.List;

/** New poll: question, 2..8 options, single or multiple choice. */
final class PollScreen extends BaseScreen {
    private static final int W = 260, ROW = 22, MAX = 8;

    private final Screen back;
    private final int replyTo;
    private EditBox question;
    private final List<EditBox> options = new ArrayList<>();
    private final List<String> kept = new ArrayList<>(List.of("", ""));
    private String keptQuestion = "";
    private boolean multi;

    PollScreen(Screen back, int replyTo) {
        super(Component.translatable("ingamemessenger.poll.new"));
        this.back = back;
        this.replyTo = replyTo;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int winH() {
        return 30 + ROW + 14 + kept.size() * ROW + (kept.size() < MAX ? ROW : 0) + 6 + ROW + 8 + 26;
    }

    private int top() {
        return Math.max(4, (height - winH()) / 2);
    }

    @Override
    protected void init() {
        // typed text survives the rebuild when "+" adds a row
        if (question != null) {
            keptQuestion = question.getValue();
        }
        for (int i = 0; i < options.size() && i < kept.size(); i++) {
            kept.set(i, options.get(i).getValue());
        }
        options.clear();
        int x = left() + 10, y = top() + 30, w = W - 20;
        question = field(x, y, w, "ingamemessenger.poll.question", 200);
        question.setValue(keptQuestion);
        y += ROW + 14;
        for (int i = 0; i < kept.size(); i++) {
            EditBox o = field(x, y, w, "ingamemessenger.poll.option", 80);
            o.setValue(kept.get(i));
            options.add(o);
            y += ROW;
        }
        if (kept.size() < MAX) {
            addRenderableWidget(new Glass.Button(x, y + 2, w, ROW - 4, () -> Component.translatable("ingamemessenger.poll.add"), false, () -> {
                kept.add("");
                rebuildWidgets();
                setFocused(options.get(options.size() - 1));
            }));
            y += ROW;
        }
        y += 6;
        addRenderableWidget(new Glass.Switch(x, y, w, ROW, () -> Component.translatable("ingamemessenger.poll.multi"), () -> multi, () -> true,
                () -> multi = !multi));
        int by = top() + winH() - 26;
        addRenderableWidget(new Glass.Button(x, by, 80, 20, () -> Component.translatable("gui.cancel"), false, this::onClose));
        addRenderableWidget(new Glass.Button(x + w - 100, by, 100, 20, () -> Component.translatable("ingamemessenger.poll.create"), true, this::create) {
            @Override
            void draw(GuiGraphicsExtractor g, int mx, int my, float pt) {
                active = ready();
                super.draw(g, mx, my, pt);
            }
        });
        setInitialFocus(question);
    }

    private EditBox field(int x, int y, int w, String hint, int max) {
        EditBox b = new EditBox(font, x + 8, y + 6, w - 16, 10, Component.translatable(hint));
        b.setBordered(false);
        b.setMaxLength(max);
        //? if >=1.19.4 {
        b.setHint(Component.translatable(hint).withStyle(s -> s.withColor(0x6C7386)));
        //?} else {
        /*b.setSuggestion(Component.translatable(hint).getString());
        *///?}
        addRenderableWidget(b);
        return b;
    }

    private boolean ready() {
        return question != null && !question.getValue().isBlank() && options.stream().filter(o -> !o.getValue().isBlank()).count() >= 2;
    }

    private void create() {
        if (!ready()) {
            return;
        }
        List<String> opts = new ArrayList<>();
        for (EditBox o : options) {
            if (!o.getValue().isBlank()) {
                opts.add(o.getValue().strip());
            }
        }
        Messenger.sendPoll(question.getValue().strip(), opts, multi, Math.max(0, replyTo));
        onClose();
    }

    @Override
    protected void drawBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x50000000);
        int x = left(), y = top();
        Panel.window(g, x, y, W, winH());
        Gfx.text(g, font, getTitle(), x + 10, y + 10, 0xFFF2F3F7, false);
        int fx = x + 10, fw = W - 20;
        Panel.round(g, fx, y + 30, fw, ROW - 2, 7, 0x40000000);
        ChatDates.small(g, Component.translatable("ingamemessenger.poll.answers").getString(), fx + 2, y + 30 + ROW + 3, 0xFF8E95A6);
        for (int i = 0; i < options.size(); i++) {
            Panel.round(g, fx, y + 30 + ROW + 14 + i * ROW, fw, ROW - 2, 7, 0x40000000);
        }
    }

    @Override
    protected boolean key(int key, int scan, int mods) {
        if ((key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) && ready()) {
            create();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
