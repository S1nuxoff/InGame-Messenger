package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
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

/** Polls in the chat: parsed from their JSON, kept up to date, drawn. */
final class ChatPolls {
    private ChatPolls() {
    }

    /** myBits = the options this player chose. */
    static final class PollInfo {
        final int mid;
        final String question;
        final List<String> options;
        final boolean multi;
        int[] counts;
        int voters, mine;

        PollInfo(int mid, String question, List<String> options, boolean multi) {
            this.mid = mid;
            this.question = question;
            this.options = options;
            this.multi = multi;
            counts = new int[options.size()];
        }

        void counts(String data) { // "c,c,...;voters"
            try {
                String[] p = data.split(";");
                String[] c = p[0].split(",");
                for (int i = 0; i < counts.length && i < c.length; i++) {
                    counts[i] = Integer.parseInt(c[i].trim());
                }
                voters = Integer.parseInt(p[1].trim());
            } catch (RuntimeException ignored) {
            }
        }
    }

    static final Map<GuiMessageTag, PollInfo> pollOf = new IdentityHashMap<>();
    static PollInfo pendingPoll;
    static final int POLL_W = 170;

    /** A poll message of the channels' model, with its counts and our choice. */
    static PollInfo of(Messenger.Entry e) {
        List<String> opts = new ArrayList<>();
        if (e.body.has("o")) {
            e.body.getAsJsonArray("o").forEach(x -> opts.add(x.getAsString()));
        }
        PollInfo pi = new PollInfo((int) e.id, e.body.has("q") ? e.body.get("q").getAsString() : "", opts,
                e.body.has("m") && e.body.get("m").getAsBoolean());
        pi.counts(e.pollCounts);
        pi.mine = e.myVote;
        return pi;
    }

    static PollInfo parsePoll(int mid, String json) {
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        List<String> opts = new ArrayList<>();
        o.getAsJsonArray("o").forEach(e -> opts.add(e.getAsString()));
        return new PollInfo(mid, o.get("q").getAsString(), opts, o.has("m") && o.get("m").getAsBoolean());
    }

    /** Question (bold, wrapped), two lines per option, a line for the vote count. */
    static void appendPoll(MutableComponent out, PollInfo pi) {
        out.append(Component.literal(pi.question).withStyle(s -> s.withColor(0xFFFFFF))).append("\n");
        for (int i = 0; i < pi.options.size() * 2 + 1; i++) {
            out.append(i == pi.options.size() * 2 ? " " : " \n");
        }
        out.append("\n ");
    }

    static String pollWord(PollInfo pi) {
        return Component.translatable("ingamemessenger.poll.word").getString() + ": " + ChatDecor.snippet(pi.question);
    }

    static void addPoll(Packets.PollShow v) {
        UUID ch = ChatChannels.takeRoute();
        int hash = Protocol.print("poll:" + v.json());
        int mid = ChatModel.takeTag(v.sender(), hash);
        PollInfo pi;
        try {
            pi = parsePoll(mid, v.json());
        } catch (RuntimeException e) {
            return;
        }
        boolean named = ch == null && ChatModel.named(v.sender(), false);
        long now = System.currentTimeMillis();
        MutableComponent text = Component.empty();
        if (ch == null) {
            ChatDates.liveDate(text, now);
        }
        if (named) {
            text.append(ChatDecor.nameLine(v.name())).append("\n");
        }
        if (v.replyTo() >= 0) {
            text.append(ChatReplies.quoteLine(v.replyTo())).append("\n");
        }
        appendPoll(text, pi);
        GuiMessageTag tag = ChatModel.rememberNumbered(new ChatModel.Msg(v.sender(), v.name(), named, null, null, 0, true, now, mid, null), v.sender(), hash);
        pollOf.put(tag, pi);
        ChatModel.note(tag, v.name(), pollWord(pi), v.replyTo());
        ChatRenderer.arrived();
        ChatDecor.spoke(v.name());
        ChatChannels.postTo(Minecraft.getInstance().gui.hud.getChat(), text, null, tag, ch);
    }

    /** Updates the counts (and our own choice if we voted). */
    static void onPollState(Packets.PollState s) {
        GuiMessageTag tag = ChatModel.byMid.get(s.id());
        PollInfo pi = tag == null ? null : pollOf.get(tag);
        if (pi == null) {
            return;
        }
        pi.counts(s.counts());
        var me = Minecraft.getInstance().player;
        if (me != null && me.getUUID().equals(s.voter())) {
            pi.mine = s.bits();
        }
    }

    /** Options (circle or check, text, share and bar), then the vote count. */
    static void drawPoll(GuiGraphicsExtractor g, PollInfo pi, int x, int footY, int lineH, float a) {
        var font = Minecraft.getInstance().font;
        int n = pi.options.size(), total = 0;
        for (int c : pi.counts) {
            total += c;
        }
        for (int i = 0; i < n; i++) {
            int y = footY - (n - i) * 2 * lineH;
            boolean chosen = (pi.mine >> i & 1) != 0;
            boolean over = ChatUi.over(x - 3, y - 1, POLL_W + 6, 2 * lineH - 1);
            if (over) {
                Panel.round(g, x - 3, y - 1, POLL_W + 6, 2 * lineH - 1, 5, (int) (a * 0x1C) << 24 | 0xFFFFFF);
            }
            int c = (int) (a * 255) << 24;
            if (chosen) {
                Panel.round(g, x, y + 1, 8, 8, pi.multi ? 2 : 4, c | 0x7B68FF);
                Panel.drawIcon(g, "check1", x + 1, y + 2, 6, c | 0xFFFFFF);
            } else {
                Panel.round(g, x, y + 1, 8, 8, pi.multi ? 2 : 4, (int) (a * 0x60) << 24 | 0xC8CCD8);
                Panel.round(g, x + 1, y + 2, 6, 6, pi.multi ? 1 : 3, (int) (a * 0xFF) << 24 | 0x1E2333);
            }
            int pct = total == 0 ? 0 : Math.round(pi.counts[i] * 100f / total);
            String ps = pct + "%";
            int pw = font.width(ps);
            Gfx.text(g, font, font.plainSubstrByWidth(pi.options.get(i), POLL_W - 18 - pw), x + 12, y + 1, c | 0xF2F3F7, false);
            Gfx.text(g, font, ps, x + POLL_W - pw, y + 1, c | (chosen ? 0xC9C0FF : 0xC8CCD8), false);
            int by = y + 11, bw = POLL_W - 12;
            Panel.round(g, x + 12, by, bw, 3, 1, (int) (a * 0x30) << 24 | 0xFFFFFF);
            if (pi.counts[i] > 0) {
                Panel.round(g, x + 12, by, Math.max(3, Math.round(bw * pi.counts[i] / (float) Math.max(1, total))), 3, 1,
                        c | (chosen ? 0x9C8CFF : 0x7B68FF));
            }
            ChatUi.pollOption(pi.mid, i, x - 3, y - 1, POLL_W + 6, 2 * lineH - 1);
        }
        String foot = Component.translatable("ingamemessenger.poll.votes", pi.voters).getString()
                + (pi.multi ? " \u00B7 " + Component.translatable("ingamemessenger.poll.several").getString() : "");
        Gfx.text(g, font, foot, x, footY, (int) (a * 0xE0) << 24 | 0xA8AFBF, false);
    }
}
