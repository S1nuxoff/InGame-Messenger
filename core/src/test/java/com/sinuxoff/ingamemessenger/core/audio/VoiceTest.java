package com.sinuxoff.ingamemessenger.core.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceTest {
    /** A second of a 440 Hz tone, in frames. */
    private static List<short[]> tone(int frames, double amplitude) {
        List<short[]> out = new ArrayList<>();
        int t = 0;
        for (int f = 0; f < frames; f++) {
            short[] pcm = new short[OpusCodec.FRAME];
            for (int i = 0; i < pcm.length; i++, t++) {
                pcm[i] = (short) Math.round(Math.sin(2 * Math.PI * 440 * t / OpusCodec.RATE) * amplitude);
            }
            out.add(pcm);
        }
        return out;
    }

    @Test
    void fileRoundTrip() {
        List<byte[]> packets = List.of(new byte[]{1, 2, 3}, new byte[0], new byte[300]);
        List<byte[]> back = VoiceFile.read(VoiceFile.write(packets));
        assertEquals(3, back.size());
        assertArrayEquals(packets.get(0), back.get(0));
        assertEquals(0, back.get(1).length);
        assertEquals(300, back.get(2).length);
    }

    @Test
    void fileRefusesOtherData() {
        assertTrue(VoiceFile.read(new byte[]{'X', 'C', 'V', '1', 0, 1, 5}).isEmpty());
        assertTrue(VoiceFile.read(null).isEmpty());
        // a cut-off last packet is left out
        assertEquals(0, VoiceFile.read(new byte[]{'M', 'C', 'V', '1', 0, 9, 1, 2}).size());
    }

    @Test
    void opusEncodesAndDecodes() throws Exception {
        List<short[]> in = tone(25, 8000);
        OpusCodec.Encoder enc = new OpusCodec.Encoder();
        List<byte[]> packets = new ArrayList<>();
        for (short[] f : in) {
            byte[] p = enc.encode(f);
            assertTrue(p.length > 0 && p.length < 1275);
            packets.add(p);
        }
        List<short[]> out = OpusCodec.decodeAll(VoiceFile.read(VoiceFile.write(packets)));
        assertEquals(in.size(), out.size());
        assertEquals(OpusCodec.FRAME, out.get(10).length);
        float level = VoiceDsp.rms(out.get(20));
        assertTrue(level > 0.1f && level < 0.25f, "a tone comes back about as loud: " + level);
    }

    @Test
    void brokenPacketIsSilence() throws Exception {
        short[] s = new OpusCodec.Decoder().decode(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        assertNotNull(s);
    }

    @Test
    void stretchKeepsTheTimeRight() {
        List<short[]> in = tone(100, 8000); // 2 s
        assertEquals(50, VoiceDsp.stretch(in, 2f).size());
        assertEquals(67, VoiceDsp.stretch(in, 1.5f).size());
        for (short[] f : VoiceDsp.stretch(in, 2f).subList(5, 45)) {
            assertTrue(VoiceDsp.rms(f) > 0.1f, "no holes in the sound");
        }
    }

    @Test
    void levelsAndWaveform() {
        float[] levels = new float[100];
        for (int i = 30; i < 100; i++) {
            levels[i] = 0.5f;
        }
        assertEquals(28, VoiceDsp.leadingSilence(levels, 0.01f, 75, 2));
        assertEquals(0, VoiceDsp.leadingSilence(new float[50], 0.01f, 75, 2), "all silence: nothing cut");
        byte[] wave = VoiceDsp.waveform(levels);
        assertEquals(VoiceDsp.BARS, wave.length);
        assertEquals(0, wave[0]);
        assertEquals((byte) 255, wave[VoiceDsp.BARS - 1]);
    }

    @Test
    void gain() {
        assertEquals(8f, VoiceDsp.normalizingGain(tone(5, 100)), 1e-3);
        assertEquals(1f, VoiceDsp.normalizingGain(tone(5, 32000)), 1e-3);
        short[] f = {1000, -1000, 30000};
        VoiceDsp.amplify(f, 2f);
        assertArrayEquals(new short[]{2000, -2000, 32767}, f);
    }
}
