package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.audio.OpusCodec;
import com.sinuxoff.ingamemessenger.core.audio.VoiceDsp;
import com.sinuxoff.ingamemessenger.core.audio.VoiceFile;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Voice messages, client side. Recording: the chosen microphone with its gain, 20 ms Opus frames ({@link OpusCodec})
 * stored as a {@link VoiceFile}, plus a level per frame for the waveform; uploaded in chunks when the button is
 * released (if at least 0.5 s; at the length limit it's sent automatically). Playback: downloaded once, decoded and
 * played at the voice volume; pressing again pauses and later resumes.
 */
final class VoiceClient {
    /** Server settings. */
    static boolean serverOn;
    static int maxSeconds = 60;

    private static final int FRAME = OpusCodec.FRAME;

    private VoiceClient() {
    }

    static void onFeatures(Packets.Features f) {
        serverOn = f.voice();
        maxSeconds = f.voiceMaxSeconds();
    }

    /** Whether to show the microphone button. */
    static boolean available() {
        return serverOn;
    }

    // Recording

    private static volatile boolean recording, stopFlag, reachedLimit;
    private static Thread recThread;
    private static long recStartMs;
    /** Encoded frames, kept separately so leading silence can be cut. */
    private static final List<byte[]> recEncoded = new ArrayList<>();
    private static final List<Float> recLevels = new ArrayList<>();
    private static volatile float level;
    private static volatile int recFrames;
    private static int uploadId;

    static boolean recording() {
        return recording;
    }

    static long recordingMs() {
        return recording ? System.currentTimeMillis() - recStartMs : 0;
    }

    /** Current input level, 0..1. */
    static float level() {
        return level;
    }

    static void start() {
        if (recording || !available()) {
            return;
        }
        if (Audio.testing()) {
            Audio.toggleTest(); // free the microphone from the settings test
        }
        Mic mic = Mic.open(Audio.micName());
        if (mic == null) {
            say(Component.translatable("ingamemessenger.voice.no_mic"));
            return;
        }
        float gain = Audio.micGain();
        mic.start();
        OpusCodec.Encoder encoder;
        try {
            encoder = new OpusCodec.Encoder();
        } catch (Throwable e) {
            mic.close();
            say(Component.translatable("ingamemessenger.voice.no_mic"));
            return;
        }
        synchronized (recLevels) {
            recLevels.clear();
            recEncoded.clear();
        }
        recFrames = 0;
        stopFlag = false;
        reachedLimit = false;
        recording = true;
        recStartMs = System.currentTimeMillis();
        int maxFrames = maxSeconds * 50;
        recThread = new Thread(() -> {
            short[] pcm = new short[FRAME];
            try {
                while (!stopFlag) {
                    if (!mic.read(pcm)) {
                        Thread.sleep(4);
                        continue;
                    }
                    VoiceDsp.amplify(pcm, gain);
                    float rms = VoiceDsp.rms(pcm);
                    level = Math.min(1f, rms * 4f);
                    byte[] enc = encoder.encode(pcm);
                    synchronized (recLevels) {
                        recEncoded.add(enc);
                        recLevels.add(rms);
                    }
                    if (++recFrames >= maxFrames) {
                        reachedLimit = true;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                mic.close();
            }
        }, "InGame Messenger recorder");
        recThread.setDaemon(true);
        recThread.start();
    }

    /** Every frame: sends the recording when it hits the length limit. */
    static void tick() {
        if (recording && reachedLimit) {
            stop(true);
        }
    }

    /** Stops recording and sends it if long enough, otherwise discards it. */
    static void stop(boolean send) {
        if (!recording) {
            return;
        }
        stopFlag = true;
        try {
            recThread.join(600);
        } catch (InterruptedException ignored) {
        }
        recording = false;
        level = 0;
        if (!send) {
            return;
        }
        List<byte[]> enc;
        float[] all;
        synchronized (recLevels) {
            enc = new ArrayList<>(recEncoded);
            all = new float[recLevels.size()];
            for (int i = 0; i < all.length; i++) {
                all[i] = recLevels.get(i);
            }
        }
        upload(enc, all);
    }

    /** The autotest's: this sound, encoded and sent as a voice message without recording it. */
    static void sendPcm(short[] pcm) {
        OpusCodec.Encoder encoder;
        try {
            encoder = new OpusCodec.Encoder();
        } catch (Throwable e) {
            return;
        }
        List<byte[]> enc = new ArrayList<>();
        float[] all = new float[pcm.length / FRAME];
        try {
            for (int i = 0; i < all.length; i++) {
                short[] f = java.util.Arrays.copyOfRange(pcm, i * FRAME, (i + 1) * FRAME);
                all[i] = VoiceDsp.rms(f);
                enc.add(encoder.encode(f));
            }
        } catch (Exception e) {
            return;
        }
        upload(enc, all);
    }

    private static void upload(List<byte[]> enc, float[] all) {
        // cut leading silence (microphone warming up, the pause before speaking), keeping 40 ms, at most 1.5 s
        float gate = Prefs.noiseGate() / 2000f; // setting 24 -> 0.012
        int from = VoiceDsp.leadingSilence(all, gate, 75, 2);
        int frames = enc.size() - from;
        if (frames < 25) { // under 0.5 s
            say(Component.translatable("ingamemessenger.voice.too_short"));
            return;
        }
        byte[] data = VoiceFile.write(enc.subList(from, enc.size()));
        byte[] wave = VoiceDsp.waveform(java.util.Arrays.copyOfRange(all, from, all.length));
        if (Messenger.muted()) {
            return;
        }
        int id = ++uploadId;
        ChatInput.takeReply();
        Net.toServer(new com.sinuxoff.ingamemessenger.core.protocol.Proto.VoiceUp(id, Messenger.current, "", data.length, frames * 20, wave, 0));
        for (int off = 0; off < data.length; off += 30_000) {
            byte[] part = java.util.Arrays.copyOfRange(data, off, Math.min(data.length, off + 30_000));
            Net.toServer(new Packets.VoiceChunk(id, off, part));
        }
    }

    private static void say(Component c) {
        var p = Minecraft.getInstance().player;
        if (p != null) {
            Compat.actionBar(p, c);
        }
    }

    // Playback

    private static final Map<String, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 24;
        }
    };
    private static final Set<String> asked = new HashSet<>(), gone = new HashSet<>();
    private static final Map<String, Integer> resumeAt = new LinkedHashMap<>();
    private static volatile String playing;
    private static String wanted;
    private static volatile float progress;
    private static volatile boolean stopPlay;
    private static Thread playThread;

    static boolean playing(String key) {
        return key.equals(playing);
    }

    /** Playback position 0..1 (also while paused). */
    static float progress(String key, int durationMs) {
        if (key.equals(playing)) {
            return progress;
        }
        Integer at = resumeAt.get(key);
        return at == null ? 0 : Math.min(1f, at * 20f / Math.max(1, durationMs));
    }

    static boolean gone(String key) {
        return gone.contains(key);
    }

    static boolean loading(String key) {
        return key.equals(wanted) && !cache.containsKey(key);
    }

    /** The message being played or paused in the mini player: key, sender, duration. */
    private static String nowKey, nowWho;
    private static int nowMs;

    static String nowKey() {
        return nowKey;
    }

    static String nowWho() {
        return nowWho;
    }

    static int nowMs() {
        return nowMs;
    }

    static void toggle(String key, String who, int ms) {
        nowKey = key;
        nowWho = who;
        nowMs = ms;
        toggle(key);
    }

    /** Mini player's close button: stop and forget the position. */
    static void close() {
        String k = nowKey;
        stopPlaying();
        if (k != null) {
            resumeAt.remove(k);
        }
        nowKey = null;
    }

    static void toggle(String key) {
        if (key.equals(playing)) {
            stopPlaying();
            return;
        }
        stopPlaying();
        byte[] data = cache.get(key);
        if (data != null) {
            play(key, data);
        } else if (!gone.contains(key)) {
            wanted = key;
            if (asked.add(key)) {
                Net.toServer(new Packets.VoiceGet(key));
            }
        }
    }

    static void onData(Packets.VoiceData d) {
        asked.remove(d.key());
        if (d.data().length == 0) {
            gone.add(d.key());
            return;
        }
        cache.put(d.key(), d.data());
        if (d.key().equals(wanted)) {
            wanted = null;
            play(d.key(), d.data());
        }
    }

    static void stopPlaying() {
        stopPlay = true;
        if (playThread != null) {
            try {
                playThread.join(300);
            } catch (InterruptedException ignored) {
            }
        }
        playing = null;
        wanted = null;
    }

    private static void play(String key, byte[] data) {
        List<byte[]> frames = VoiceFile.read(data);
        if (frames.isEmpty()) {
            gone.add(key);
            return;
        }
        int from = resumeAt.getOrDefault(key, 0);
        if (from >= frames.size() - 1) {
            from = 0;
        }
        int start = from;
        Minecraft mc = Minecraft.getInstance();
        // the listener already has the master volume; the voice volume goes on top
        float volume = mc.options.getSoundSourceVolume(SoundSource.VOICE);
        stopPlay = false;
        playing = key;
        progress = start / (float) frames.size();
        playThread = new Thread(() -> {
            int source = 0;
            int[] bufs = new int[6];
            Audio.Out out = null;
            frameSpeed = 1f;
            java.nio.ShortBuffer pcmBuf = null;
            int i = start, played = start;
            final int per = 5; // frames per buffer: 100 ms
            try {
                List<short[]> pcm = OpusCodec.decodeAll(frames);
                // normalize quiet recordings, then apply the player's voice volume
                float gain = VoiceDsp.normalizingGain(pcm) * Prefs.voiceVolume() / 100f;
                float speed = speed();
                if (speed != 1f) { // time-stretch, keeping the pitch
                    pcm = VoiceDsp.stretch(pcm, speed);
                }
                final float spd = speed;
                int startAt = Math.round(start / speed);
                i = played = startAt;
                frameCount = pcm.size();
                frameSpeed = spd;
                out = Audio.openOut();
                source = org.lwjgl.openal.AL10.alGenSources();
                org.lwjgl.openal.AL10.alSourcei(source, org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE, org.lwjgl.openal.AL10.AL_TRUE);
                org.lwjgl.openal.AL10.alSource3f(source, org.lwjgl.openal.AL10.AL_POSITION, 0f, 0f, 0f);
                org.lwjgl.openal.AL10.alSourcef(source, org.lwjgl.openal.AL10.AL_GAIN, volume);
                org.lwjgl.openal.AL10.alGenBuffers(bufs);
                pcmBuf = org.lwjgl.system.MemoryUtil.memAllocShort(FRAME * per);
                java.util.ArrayDeque<int[]> queued = new java.util.ArrayDeque<>(); // (buffer, frames in it)
                for (int b : bufs) {
                    int n = fill(pcm, i, per, pcmBuf, gain);
                    if (n == 0) {
                        break;
                    }
                    org.lwjgl.openal.AL10.alBufferData(b, org.lwjgl.openal.AL10.AL_FORMAT_MONO16, pcmBuf, 48000);
                    org.lwjgl.openal.AL10.alSourceQueueBuffers(source, b);
                    queued.add(new int[]{b, n});
                    i += n;
                }
                org.lwjgl.openal.AL10.alSourcePlay(source);
                while (!stopPlay && !queued.isEmpty()) {
                    int done = org.lwjgl.openal.AL10.alGetSourcei(source, org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED);
                    for (int d = 0; d < done && !queued.isEmpty(); d++) {
                        int b = org.lwjgl.openal.AL10.alSourceUnqueueBuffers(source);
                        int[] q = queued.poll();
                        played += q[1];
                        int n = fill(pcm, i, per, pcmBuf, gain);
                        if (n > 0) {
                            org.lwjgl.openal.AL10.alBufferData(b, org.lwjgl.openal.AL10.AL_FORMAT_MONO16, pcmBuf, 48000);
                            org.lwjgl.openal.AL10.alSourceQueueBuffers(source, b);
                            queued.add(new int[]{b, n});
                            i += n;
                        }
                    }
                    if (!queued.isEmpty() && org.lwjgl.openal.AL10.alGetSourcei(source, org.lwjgl.openal.AL10.AL_SOURCE_STATE)
                            != org.lwjgl.openal.AL10.AL_PLAYING) {
                        org.lwjgl.openal.AL10.alSourcePlay(source); // buffer underrun: restart
                    }
                    float within = org.lwjgl.openal.AL11.alGetSourcef(source, org.lwjgl.openal.AL11.AL_SEC_OFFSET) * 50f;
                    progress = Math.min(1f, (played + within) / pcm.size());
                    Thread.sleep(10);
                }
                i = Math.min(i, played + Math.round(org.lwjgl.openal.AL11.alGetSourcef(source, org.lwjgl.openal.AL11.AL_SEC_OFFSET) * 50f));
            } catch (Throwable ignored) {
            } finally {
                if (source != 0) {
                    org.lwjgl.openal.AL10.alSourceStop(source);
                    org.lwjgl.openal.AL10.alSourcei(source, org.lwjgl.openal.AL10.AL_BUFFER, 0);
                    org.lwjgl.openal.AL10.alDeleteSources(source);
                    org.lwjgl.openal.AL10.alDeleteBuffers(bufs);
                }
                if (pcmBuf != null) {
                    org.lwjgl.system.MemoryUtil.memFree(pcmBuf);
                }
                Audio.close(out);
                int orig = Math.round(i * frameSpeed); // position in the original frames
                if (stopPlay && orig < frames.size()) {
                    resumeAt.put(key, orig);
                } else {
                    resumeAt.remove(key);
                }
                if (key.equals(playing)) {
                    playing = null;
                    if (!stopPlay && key.equals(nowKey)) {
                        nowKey = null; // finished
                    }
                }
            }
        }, "InGame Messenger player");
        playThread.setDaemon(true);
        playThread.start();
    }

    // Speed

    private static volatile float frameSpeed = 1f;
    private static volatile int frameCount;

    /** 1, 1.5 or 2. */
    static float speed() {
        return Prefs.voiceSpeed() / 100f;
    }

    /** Cycles 1 -> 1.5 -> 2 -> 1; playback continues from the same position. */
    static void cycleSpeed() {
        int now = Prefs.voiceSpeed();
        Prefs.setVoiceSpeed(now >= 200 ? 100 : now >= 150 ? 200 : 150);
        String k = playing;
        if (k != null) {
            stopPlaying();
            toggle(k);
        }
    }

    /** Decodes up to n frames from index i into buf, flipped for OpenAL. Returns how many. */
    private static int fill(List<short[]> pcm, int i, int n, java.nio.ShortBuffer buf, float gain) {
        buf.clear();
        int took = 0;
        for (; took < n && i + took < pcm.size(); took++) {
            short[] f = pcm.get(i + took);
            for (int k = 0; k < Math.min(f.length, FRAME); k++) {
                buf.put((short) Math.max(-32768, Math.min(32767, Math.round(f[k] * gain))));
            }
        }
        buf.flip();
        return took;
    }

    static void reset() {
        if (recording) {
            stop(false);
        }
        stopPlaying();
        asked.clear();
    }
}
