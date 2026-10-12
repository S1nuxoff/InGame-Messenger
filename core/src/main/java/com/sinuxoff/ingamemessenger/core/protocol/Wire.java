package com.sinuxoff.ingamemessenger.core.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Packet serialization compatible with Minecraft's codecs: VarInt/VarLong, big-endian ints, length-prefixed UTF-8
 * strings and byte arrays. Limits are checked on both sides.
 */
public final class Wire {
    private Wire() {
    }

    public static final class WireException extends RuntimeException {
        public WireException(String message) {
            super(message);
        }
    }

    public static final class Out {
        private final ByteArrayOutputStream b = new ByteArrayOutputStream(64);

        public Out varInt(int v) {
            while ((v & ~0x7F) != 0) {
                b.write(v & 0x7F | 0x80);
                v >>>= 7;
            }
            b.write(v);
            return this;
        }

        public Out varLong(long v) {
            while ((v & ~0x7FL) != 0) {
                b.write((int) (v & 0x7F) | 0x80);
                v >>>= 7;
            }
            b.write((int) v);
            return this;
        }

        public Out i32(int v) {
            b.write(v >>> 24);
            b.write(v >>> 16);
            b.write(v >>> 8);
            b.write(v);
            return this;
        }

        public Out i64(long v) {
            i32((int) (v >>> 32));
            return i32((int) v);
        }

        public Out bool(boolean v) {
            b.write(v ? 1 : 0);
            return this;
        }

        public Out uuid(UUID v) {
            return i64(v.getMostSignificantBits()).i64(v.getLeastSignificantBits());
        }

    /** max is in characters; the UTF-8 size is limited to 3 * max bytes. */
        public Out str(String v, int max) {
            if (v.length() > max) {
                throw new WireException("string too long: " + v.length() + " > " + max);
            }
            byte[] u = v.getBytes(StandardCharsets.UTF_8);
            if (u.length > max * 3) {
                throw new WireException("string too long: " + u.length + " bytes > " + max * 3);
            }
            varInt(u.length);
            b.writeBytes(u);
            return this;
        }

        public Out bytes(byte[] v, int max) {
            if (v.length > max) {
                throw new WireException("byte array too long: " + v.length + " > " + max);
            }
            varInt(v.length);
            b.writeBytes(v);
            return this;
        }

        public byte[] toByteArray() {
            return b.toByteArray();
        }
    }

    public static final class In {
        private final byte[] d;
        private int at;

        public In(byte[] data) {
            d = data;
        }

        private int u8() {
            if (at >= d.length) {
                throw new WireException("packet cut short");
            }
            return d[at++] & 0xFF;
        }

        public int varInt() {
            int v = 0, shift = 0, x;
            do {
                if (shift >= 35) {
                    throw new WireException("VarInt too big");
                }
                x = u8();
                v |= (x & 0x7F) << shift;
                shift += 7;
            } while ((x & 0x80) != 0);
            return v;
        }

        public long varLong() {
            long v = 0;
            int shift = 0, x;
            do {
                if (shift >= 70) {
                    throw new WireException("VarLong too big");
                }
                x = u8();
                v |= (long) (x & 0x7F) << shift;
                shift += 7;
            } while ((x & 0x80) != 0);
            return v;
        }

        public int i32() {
            return u8() << 24 | u8() << 16 | u8() << 8 | u8();
        }

        public long i64() {
            return (long) i32() << 32 | i32() & 0xFFFFFFFFL;
        }

        public boolean bool() {
            return u8() != 0;
        }

        public UUID uuid() {
            return new UUID(i64(), i64());
        }

        public String str(int max) {
            int n = varInt();
            if (n < 0 || n > max * 3) {
                throw new WireException("string too long: " + n + " bytes > " + max * 3);
            }
            need(n);
            String s = new String(d, at, n, StandardCharsets.UTF_8);
            at += n;
            if (s.length() > max) {
                throw new WireException("string too long: " + s.length() + " > " + max);
            }
            return s;
        }

        public byte[] bytes(int max) {
            int n = varInt();
            if (n < 0 || n > max) {
                throw new WireException("byte array too long: " + n + " > " + max);
            }
            need(n);
            byte[] out = java.util.Arrays.copyOfRange(d, at, at + n);
            at += n;
            return out;
        }

        private void need(int n) {
            if (d.length - at < n) {
                throw new WireException("packet cut short");
            }
        }

        public int remaining() {
            return d.length - at;
        }
    }
}
