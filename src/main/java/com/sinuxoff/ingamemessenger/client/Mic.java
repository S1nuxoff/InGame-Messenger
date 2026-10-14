package com.sinuxoff.ingamemessenger.client;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.core.audio.OpusCodec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Microphone capture, 48 kHz mono 16-bit, 20 ms frames. Uses OpenAL, except on 26.x where Java Sound is used: the
 * OpenAL build shipped with those versions returns distorted microphone audio.
 */
abstract class Mic implements AutoCloseable {
    /** name is an OpenAL device name, null = default. Falls back to the default; null if there's no microphone. */
    static Mic open(String name) {
        //? if >=26.1 {
        Mic m = JavaSound.open(name);
        //?} else {
        /*Mic m = OpenAl.open(name);
        *///?}
        return m;
    }

    abstract void start();

    /** False if a full frame isn't available yet. */
    abstract boolean read(short[] pcm);

    @Override
    public abstract void close();

    static final class OpenAl extends Mic {
        private final long dev;

        private OpenAl(long dev) {
            this.dev = dev;
        }

        static Mic open(String name) {
            long d = 0;
            try {
                if (name != null) {
                    d = ALC11.alcCaptureOpenDevice(name, 48000, AL10.AL_FORMAT_MONO16, OpusCodec.FRAME * 16);
                }
                if (d == 0) {
                    d = ALC11.alcCaptureOpenDevice((java.nio.ByteBuffer) null, 48000, AL10.AL_FORMAT_MONO16, OpusCodec.FRAME * 16);
                }
            } catch (Throwable e) {
                d = 0;
            }
            if (d == 0) {
                return null;
            }
            InGameMessenger.LOG.info("Microphone (OpenAL): {} (asked for: {})",
                    ALC10.alcGetString(d, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER), name == null ? "the default" : name);
            return new OpenAl(d);
        }

        @Override
        void start() {
            ALC11.alcCaptureStart(dev);
        }

        @Override
        boolean read(short[] pcm) {
            if (ALC10.alcGetInteger(dev, ALC11.ALC_CAPTURE_SAMPLES) < pcm.length) {
                return false;
            }
            ALC11.alcCaptureSamples(dev, pcm, pcm.length);
            return true;
        }

        @Override
        public void close() {
            ALC11.alcCaptureStop(dev);
            ALC11.alcCaptureCloseDevice(dev);
        }
    }

    static final class JavaSound extends Mic {
        private static final AudioFormat FORMAT = new AudioFormat(48000f, 16, 1, true, false);
        private final TargetDataLine line;
        private byte[] bytes = new byte[0];

        private JavaSound(TargetDataLine line) {
            this.line = line;
        }

        static Mic open(String name) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            TargetDataLine line = null;
            String used = "the default";
            if (name != null) { // OpenAL "OpenAL Soft on Mic (USB...)" is Java's "Mic (USB..." (truncated on Windows)
                String want = name.startsWith("OpenAL Soft on ") ? name.substring("OpenAL Soft on ".length()) : name;
                for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                    String n = mi.getName();
                    if (n.length() >= 8 && (want.startsWith(n) || n.startsWith(want)) && AudioSystem.getMixer(mi).isLineSupported(info)) {
                        try {
                            line = (TargetDataLine) AudioSystem.getMixer(mi).getLine(info);
                            used = n;
                            break;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            try {
                if (line == null) {
                    line = (TargetDataLine) AudioSystem.getLine(info);
                }
                line.open(FORMAT, OpusCodec.FRAME * 2 * 16);
            } catch (Exception e) {
                InGameMessenger.LOG.warn("Microphone (Java sound) couldn't be opened", e);
                return null;
            }
            InGameMessenger.LOG.info("Microphone (Java sound): {} (asked for: {})", used, name == null ? "the default" : name);
            return new JavaSound(line);
        }

        @Override
        void start() {
            line.start();
        }

        @Override
        boolean read(short[] pcm) {
            int need = pcm.length * 2;
            if (line.available() < need) {
                return false;
            }
            if (bytes.length != need) {
                bytes = new byte[need];
            }
            int got = 0;
            while (got < need) {
                int n = line.read(bytes, got, need - got);
                if (n <= 0) {
                    return false;
                }
                got += n;
            }
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short) (bytes[i * 2] & 0xFF | bytes[i * 2 + 1] << 8);
            }
            return true;
        }

        @Override
        public void close() {
            line.stop();
            line.close();
        }
    }
}
