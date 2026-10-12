package com.sinuxoff.ingamemessenger.core.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Voice message file format: "MCV1", then Opus packets, each prefixed with a 2-byte big-endian length. */
public final class VoiceFile {
    private static final byte[] MAGIC = {'M', 'C', 'V', '1'};

    private VoiceFile() {
    }

    public static byte[] write(List<byte[]> packets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(MAGIC);
        for (byte[] p : packets) {
            if (p.length > 0xFFFF) {
                throw new IllegalArgumentException("packet too long: " + p.length);
            }
            out.write(p.length >> 8);
            out.write(p.length & 0xFF);
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    /** Empty list if the data isn't a voice file. A truncated last packet is dropped. */
    public static List<byte[]> read(byte[] data) {
        List<byte[]> out = new ArrayList<>();
        if (data == null || data.length < MAGIC.length) {
            return out;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return out;
            }
        }
        ByteBuffer b = ByteBuffer.wrap(data, MAGIC.length, data.length - MAGIC.length);
        while (b.remaining() >= 2) {
            int len = b.getShort() & 0xFFFF;
            if (len > b.remaining()) {
                break;
            }
            byte[] f = new byte[len];
            b.get(f);
            out.add(f);
        }
        return out;
    }
}
