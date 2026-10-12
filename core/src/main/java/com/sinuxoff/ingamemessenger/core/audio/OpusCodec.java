package com.sinuxoff.ingamemessenger.core.audio;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import io.github.jaredmdobson.concentus.OpusSignal;

import java.util.Arrays;

/** Opus via Concentus (pure Java). Frames are 20 ms of 48 kHz mono. Encoders and decoders are not thread-safe. */
public final class OpusCodec {
    public static final int RATE = 48_000;
    public static final int FRAME = 960;
    private static final int MAX_PACKET = 1275;

    private OpusCodec() {
    }

    public static final class Encoder {
        private final OpusEncoder opus;
        private final byte[] packet = new byte[MAX_PACKET];

        public Encoder() throws OpusException {
            opus = new OpusEncoder(RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
            opus.setBitrate(32_000);
            opus.setComplexity(8);
            opus.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
        }

        public byte[] encode(short[] pcm) throws OpusException {
            int n = opus.encode(pcm, 0, FRAME, packet, 0, packet.length);
            return Arrays.copyOf(packet, n);
        }
    }

    public static final class Decoder {
        private final OpusDecoder opus;

        public Decoder() throws OpusException {
            opus = new OpusDecoder(RATE, 1);
        }

    /** A broken packet decodes to silence. */
        public short[] decode(byte[] packet) {
            short[] out = new short[FRAME];
            try {
                int n = opus.decode(packet, 0, packet.length, out, 0, FRAME, false);
                return n == FRAME ? out : Arrays.copyOf(out, Math.max(0, n));
            } catch (OpusException | RuntimeException e) {
                return new short[FRAME];
            }
        }
    }

    public static java.util.List<short[]> decodeAll(java.util.List<byte[]> packets) throws OpusException {
        Decoder d = new Decoder();
        java.util.List<short[]> out = new java.util.ArrayList<>(packets.size());
        for (byte[] p : packets) {
            out.add(d.decode(p));
        }
        return out;
    }
}
