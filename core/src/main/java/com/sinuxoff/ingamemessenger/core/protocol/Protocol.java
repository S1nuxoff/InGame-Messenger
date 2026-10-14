package com.sinuxoff.ingamemessenger.core.protocol;

import java.util.HashMap;
import java.util.Map;

/** Protocol version, channel namespace and packet encoding. Client and server talk only when their versions match. */
public final class Protocol {
    /** Bump whenever the wire format of any packet changes. */
    public static final String VERSION = "22";
    public static final String NAMESPACE = "ingamemessenger";
    /** Number of built-in reactions ("0".."7"). */
    public static final int BUILT_IN_REACTIONS = 8;

    private static final Map<String, PacketType<?>> BY_ID = new HashMap<>();

    static {
        for (PacketType<?> t : Packets.ALL) {
            if (BY_ID.put(t.id(), t) != null) {
                throw new IllegalStateException("two packets with id " + t.id());
            }
        }
    }

    private Protocol() {
    }

    /**
     * Fingerprint of a message text. The server's id for a message arrives separately from the message itself,
     * so the client matches them by sender and this hash. Never 0 (0 means "any").
     */
    public static int print(String text) {
        int h = text.strip().hashCode();
        return h == 0 ? 1 : h;
    }

    public static PacketType<?> type(String id) {
        return BY_ID.get(id);
    }

    public static byte[] encode(Packet p) {
        Wire.Out out = new Wire.Out();
        p.write(out);
        return out.toByteArray();
    }

    /** Decodes a whole packet; leftover bytes are an error. */
    public static <T extends Packet> T decode(PacketType<T> type, byte[] data) {
        Wire.In in = new Wire.In(data);
        T p = type.reader().apply(in);
        if (in.remaining() != 0) {
            throw new Wire.WireException(in.remaining() + " bytes left over in " + type.id());
        }
        return p;
    }
}
