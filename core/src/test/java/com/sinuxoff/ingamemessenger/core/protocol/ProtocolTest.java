package com.sinuxoff.ingamemessenger.core.protocol;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private static String hex(Wire.Out o) {
        return HexFormat.of().formatHex(o.toByteArray());
    }

    /** The bytes Minecraft's own codecs make (FriendlyByteBuf / ByteBufCodecs). */
    @Test
    void sameBytesAsMinecraft() {
        assertEquals("00", hex(new Wire.Out().varInt(0)));
        assertEquals("7f", hex(new Wire.Out().varInt(127)));
        assertEquals("8001", hex(new Wire.Out().varInt(128)));
        assertEquals("ac02", hex(new Wire.Out().varInt(300)));
        assertEquals("ffffffff0f", hex(new Wire.Out().varInt(-1)));
        assertEquals("ffffffffffffffffff01", hex(new Wire.Out().varLong(-1L)));
        assertEquals("0000012c", hex(new Wire.Out().i32(300)));
        assertEquals("ffffffff", hex(new Wire.Out().i32(-1)));
        assertEquals("01", hex(new Wire.Out().bool(true)));
        assertEquals("0123456789abcdeffedcba9876543210",
                hex(new Wire.Out().uuid(new UUID(0x0123456789ABCDEFL, 0xFEDCBA9876543210L))));
        assertEquals("02d0af", hex(new Wire.Out().str("Я", 10)), "UTF-8, its byte length first");
        assertEquals("03010203", hex(new Wire.Out().bytes(new byte[]{1, 2, 3}, 10)));
    }

    @Test
    void readsBackWhatItWrites() {
        Wire.In in = new Wire.In(new Wire.Out().varInt(-5).varInt(Integer.MAX_VALUE).varLong(Long.MIN_VALUE).i32(-7).i64(1L << 40)
                .str("привет ✓", 20).bytes(new byte[0], 0).toByteArray());
        assertEquals(-5, in.varInt());
        assertEquals(Integer.MAX_VALUE, in.varInt());
        assertEquals(Long.MIN_VALUE, in.varLong());
        assertEquals(-7, in.i32());
        assertEquals(1L << 40, in.i64());
        assertEquals("привет ✓", in.str(20));
        assertEquals(0, in.bytes(0).length);
        assertEquals(0, in.remaining());
    }

    @Test
    void limitsAreKept() {
        assertThrows(Wire.WireException.class, () -> new Wire.Out().str("x".repeat(11), 10));
        assertThrows(Wire.WireException.class, () -> new Wire.Out().bytes(new byte[11], 10));
        byte[] longString = new Wire.Out().str("x".repeat(20), 20).toByteArray();
        assertThrows(Wire.WireException.class, () -> new Wire.In(longString).str(10));
        assertThrows(Wire.WireException.class, () -> new Wire.In(new byte[]{5, 1}).str(10), "cut short");
        assertThrows(Wire.WireException.class, () -> new Wire.In(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 1}).varInt());
        byte[] two = new Wire.Out().varInt(1).varInt(2).toByteArray();
        assertThrows(Wire.WireException.class, () -> Protocol.decode(Packets.Deleted.TYPE, two), "left over");
    }

    @Test
    void everyPacketRoundTrips() throws Exception {
        Set<String> ids = new HashSet<>();
        for (PacketType<?> t : Packets.ALL) {
            assertTrue(ids.add(t.id()), "one id each: " + t.id());
            assertSame(t, Protocol.type(t.id()));
            RecordComponent[] comps = t.type().getRecordComponents();
            Object[] args = new Object[comps.length];
            Class<?>[] types = new Class<?>[comps.length];
            for (int i = 0; i < comps.length; i++) {
                types[i] = comps[i].getType();
                args[i] = sample(types[i], i);
            }
            Packet p = t.type().getDeclaredConstructor(types).newInstance(args);
            assertSame(t, p.type());
            Packet back = Protocol.decode(t, Protocol.encode(p));
            for (RecordComponent c : comps) {
                Object a = c.getAccessor().invoke(p), b = c.getAccessor().invoke(back);
                if (a instanceof byte[] x) {
                    assertArrayEquals(x, (byte[]) b, t.id() + "." + c.getName());
                } else {
                    assertEquals(a, b, t.id() + "." + c.getName());
                }
            }
        }
        assertEquals(Packets.ALL.size(), ids.size());
    }

    private static Object sample(Class<?> c, int i) {
        if (c == String.class) {
            return "ы✓" + i; // (short enough for every packet's limit)
        }
        if (c == int.class) {
            return i % 2 == 0 ? 300 + i : -1;
        }
        if (c == long.class) {
            return 1_760_000_000_000L + i;
        }
        if (c == boolean.class) {
            return i % 2 == 0;
        }
        if (c == UUID.class) {
            return new UUID(i, -i);
        }
        if (c == byte[].class) {
            byte[] b = new byte[5 + i];
            Arrays.fill(b, (byte) i);
            return b;
        }
        throw new IllegalArgumentException(c.toString());
    }
}
