package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read marks in the public chat: everyone's position, sending our own, and the ✓ / ✓✓ next to our messages. */
final class ChatReads {
    private ChatReads() {
    }

    private record Seen(String name, int upTo) {
    }

    private static final Map<UUID, Seen> seen = new HashMap<>();
    private static int readSent = -1;
    private static long readSentMs;

    static void reset() {
        seen.clear();
        readSent = -1;
        readSentMs = 0;
    }

    static void onReadState(Packets.ReadState r) {
        seen.put(r.who(), new Seen(r.name(), r.mid()));
    }

    static void onReadAll(Packets.ReadAll r) {
        seen.clear();
        for (String line : r.data().split("\n")) {
            String[] f = line.split(" ");
            if (f.length == 3) {
                try {
                    seen.put(UUID.fromString(f[0]), new Seen(f[1], Integer.parseInt(f[2])));
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    /** Players other than us who have read message mid. */
    static List<String> readers(int mid) {
        var me = Minecraft.getInstance().player;
        List<String> out = new ArrayList<>();
        seen.forEach((id, s) -> {
            if (s.upTo() >= mid && (me == null || !me.getUUID().equals(id))) {
                out.add(s.name());
            }
        });
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Chat open and scrolled to the bottom: reports the newest message as read (at most once a second). */
    static void iHaveRead() {
        if (ChatChannels.channel != null) {
            ChatChannels.dmHaveRead();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - readSentMs < 1000 || ChatModel.byMid.isEmpty() || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        int newest = -1;
        for (var e : ChatModel.byMid.entrySet()) {
            if (!ChatChannels.chanOf.containsKey(e.getValue())) {
                newest = Math.max(newest, e.getKey());
            }
        }
        if (newest > readSent) {
            readSent = newest;
            readSentMs = now;
            Net.toServer(new Packets.ReadUpTo(newest));
        }
    }

    /** ✓ sent, blue ✓✓ read by someone. Remembers where it was drawn, for the "read by" tooltip. */
    static void drawChecks(GuiGraphicsExtractor g, int mid, float x, float y, float a) {
        boolean dm = ChatChannels.isDm(mid);
        boolean read = dm ? ChatChannels.channel != null && ChatChannels.dmPeerRead.getOrDefault(ChatChannels.channel, 0) >= mid : !readers(mid).isEmpty();
        Panel.drawIcon(g, read ? "check2" : "check1", x - 1, y - 2.5f, 9, (int) (a * 0xF0) << 24 | (read ? 0x6CC6FF : 0x8E95A6));
        if (!dm) {
            ChatUi.checks(mid, Math.round(x) - 2, Math.round(y) - 3, 11, 10);
        }
    }
}
