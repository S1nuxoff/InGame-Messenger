package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.EXTThreadLocalContext;
import com.sinuxoff.ingamemessenger.core.audio.OpusCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Sound devices for voice messages: the microphone and its gain, the output device (the game's own or a separate one),
 * and the microphone test.
 */
final class Audio {
    private Audio() {
    }

    /** OpenAL capture device names. */
    static List<String> microphones() {
        try {
            List<String> l = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
            return l == null ? List.of() : new ArrayList<>(l);
        } catch (Throwable e) {
            return List.of();
        }
    }

    /** OpenAL output device names. */
    static List<String> speakers() {
        try {
            List<String> l = ALUtil.getStringList(0L, org.lwjgl.openal.EnumerateAllExt.ALC_ALL_DEVICES_SPECIFIER);
            if (l == null || l.isEmpty()) {
                l = ALUtil.getStringList(0L, ALC10.ALC_DEVICE_SPECIFIER);
            }
            return l == null ? List.of() : new ArrayList<>(l);
        } catch (Throwable e) {
            return List.of();
        }
    }

    /** The chosen microphone, or null for the system default. */
    static String micName() {
        String m = Prefs.micDevice();
        return m.isEmpty() ? null : m;
    }

    /** Microphone gain as a factor, from the dB setting. */
    static float micGain() {
        return (float) Math.pow(10, Prefs.micGainDb() / 20.0);
    }

    // Playback on a chosen device

    /** Our own device and context for this thread; null = Minecraft's. */
    record Out(long device, long context) {
    }

    /** Makes the chosen output device current for the calling thread only, or returns null to use the game's. Undo with {@link #close}. */
    static Out openOut() {
        String name = Prefs.speakerDevice();
        if (name.isEmpty()) {
            return null;
        }
        try {
            long dev = ALC10.alcOpenDevice(name);
            if (dev == 0) {
                return null;
            }
            var dc = ALC.createCapabilities(dev);
            if (!dc.ALC_EXT_thread_local_context) {
                ALC10.alcCloseDevice(dev);
                return null;
            }
            long ctx = ALC10.alcCreateContext(dev, (java.nio.IntBuffer) null);
            EXTThreadLocalContext.alcSetThreadContext(ctx);
            ALCapabilities caps = AL.createCapabilities(dc);
            AL.setCurrentThread(caps);
            // Minecraft's master volume is set on its own listener, so apply it to ours
            AL10.alListenerf(AL10.AL_GAIN, Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER));
            return new Out(dev, ctx);
        } catch (Throwable e) {
            return null;
        }
    }

    static void close(Out o) {
        if (o == null) {
            return;
        }
        try {
            AL.setCurrentThread(null);
            EXTThreadLocalContext.alcSetThreadContext(0);
            ALC10.alcDestroyContext(o.context());
            ALC10.alcCloseDevice(o.device());
        } catch (Throwable ignored) {
        }
    }

    // Microphone test

    private static volatile boolean testing, hearSelf;
    private static volatile float testLevel;
    private static Thread testThread;

    static boolean testing() {
        return testing;
    }

    static float testLevel() {
        return testLevel;
    }

    static boolean hearSelf() {
        return hearSelf;
    }

    static void setHearSelf(boolean on) {
        hearSelf = on;
    }

    /** Toggles the test: shows the level and optionally plays the microphone back. */
    static void toggleTest() {
        if (testing) {
            testing = false;
            return;
        }
        if (VoiceClient.recording()) {
            return;
        }
        Mic mic = Mic.open(micName());
        if (mic == null) {
            return;
        }
        testing = true;
        testThread = new Thread(() -> {
            short[] pcm = new short[OpusCodec.FRAME];
            Out out = openOut();
            int source = 0;
            int[] bufs = new int[4];
            java.util.ArrayDeque<Integer> free = new java.util.ArrayDeque<>();
            java.nio.ShortBuffer buf = org.lwjgl.system.MemoryUtil.memAllocShort(OpusCodec.FRAME);
            try {
                source = AL10.alGenSources();
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alGenBuffers(bufs);
                for (int b : bufs) {
                    free.add(b);
                }
                mic.start();
                while (testing && Minecraft.getInstance().screen instanceof SettingsScreen) {
                    if (!mic.read(pcm)) {
                        Thread.sleep(4);
                        continue;
                    }
                    float gain = micGain();
                    double sum = 0;
                    for (int i = 0; i < OpusCodec.FRAME; i++) {
                        pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(pcm[i] * gain)));
                        sum += pcm[i] * (double) pcm[i];
                    }
                    testLevel = Math.min(1f, (float) Math.sqrt(sum / OpusCodec.FRAME) / 32768f * 4f);
                    int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                    for (int d = 0; d < done; d++) {
                        free.add(AL10.alSourceUnqueueBuffers(source));
                    }
                    if (hearSelf && !free.isEmpty()) {
                        buf.clear();
                        buf.put(pcm).flip();
                        int b = free.poll();
                        AL10.alBufferData(b, AL10.AL_FORMAT_MONO16, buf, 48000);
                        AL10.alSourceQueueBuffers(source, b);
                        AL10.alSourcef(source, AL10.AL_GAIN, Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.VOICE));
                        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                            AL10.alSourcePlay(source);
                        }
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                testing = false;
                testLevel = 0;
                try {
                    mic.close();
                    if (source != 0) {
                        AL10.alSourceStop(source);
                        AL10.alSourcei(source, AL10.AL_BUFFER, 0);
                        AL10.alDeleteSources(source);
                        AL10.alDeleteBuffers(bufs);
                    }
                } catch (Throwable ignored) {
                }
                org.lwjgl.system.MemoryUtil.memFree(buf);
                close(out);
            }
        }, "InGame Messenger microphone test");
        testThread.setDaemon(true);
        testThread.start();
    }
}
