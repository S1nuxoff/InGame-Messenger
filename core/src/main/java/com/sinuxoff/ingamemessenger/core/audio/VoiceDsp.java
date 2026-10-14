package com.sinuxoff.ingamemessenger.core.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Audio helpers for voice messages. Frames are {@link OpusCodec#FRAME} samples. */
public final class VoiceDsp {
    public static final int BARS = 48;

    private VoiceDsp() {
    }

    /** RMS, 0..1. */
    public static float rms(short[] pcm) {
        double sum = 0;
        for (short v : pcm) {
            sum += v * (double) v;
        }
        return pcm.length == 0 ? 0 : (float) Math.sqrt(sum / pcm.length) / 32768f;
    }

    public static void amplify(short[] pcm, float gain) {
        if (gain == 1f) {
            return;
        }
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = clip(Math.round(pcm[i] * gain));
        }
    }

    /** Number of silent frames at the start (at most max), minus keep. */
    public static int leadingSilence(float[] levels, float threshold, int max, int keep) {
        int first = 0;
        while (first < levels.length && first < max && levels[first] < threshold) {
            first++;
        }
        return first >= levels.length ? 0 : Math.max(0, first - keep);
    }

    /** {@link #BARS} values 0..255: peak per segment, square-rooted and normalized. */
    public static byte[] waveform(float[] levels) {
        float[] bars = new float[BARS];
        float top = 0.02f;
        for (int b = 0; b < BARS; b++) {
            int from = b * levels.length / BARS, to = Math.max(from + 1, (b + 1) * levels.length / BARS);
            for (int i = from; i < to && i < levels.length; i++) {
                bars[b] = Math.max(bars[b], levels[i]);
            }
            top = Math.max(top, bars[b]);
        }
        byte[] out = new byte[BARS];
        for (int b = 0; b < BARS; b++) {
            out[b] = (byte) Math.round(Math.sqrt(bars[b] / top) * 255);
        }
        return out;
    }

    /** Gain that brings the peak close to full scale, between 1x and 8x. */
    public static float normalizingGain(List<short[]> frames) {
        int peak = 1;
        for (short[] f : frames) {
            for (short v : f) {
                peak = Math.max(peak, Math.abs((int) v));
            }
        }
        return Math.max(1f, Math.min(8f, 0.9f * 32767f / peak));
    }

    /** Speeds up without changing pitch (WSOLA): 40 ms windows every 20 ms, aligned by cross-correlation (±7 ms). */
    public static List<short[]> stretch(List<short[]> frames, float speed) {
        final int frame = OpusCodec.FRAME;
        int total = 0;
        for (short[] f : frames) {
            total += Math.min(f.length, frame);
        }
        float[] x = new float[total];
        int at = 0;
        for (short[] f : frames) {
            for (int k = 0; k < Math.min(f.length, frame); k++) {
                x[at++] = f[k];
            }
        }
        final int w = 1920, hs = w / 2, tol = 336;
        float[] win = new float[w];
        for (int n = 0; n < w; n++) {
            win[n] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * n / (w - 1)));
        }
        int outLen = (int) (total / speed) + w;
        float[] y = new float[outLen], norm = new float[outLen];
        int prev = -1;
        for (int k = 0; ; k++) {
            int outPos = k * hs, ideal = Math.round(k * hs * speed);
            if (ideal + w + tol >= total || outPos + w >= outLen) {
                break;
            }
            int best = ideal;
            if (prev >= 0) {
                int nat = prev + hs;
                double bestC = Double.NEGATIVE_INFINITY;
                for (int d = -tol; d <= tol; d += 8) {
                    int c = ideal + d;
                    if (c < 0 || c + hs >= total || nat + hs >= total) {
                        continue;
                    }
                    double corr = 0;
                    for (int n = 0; n < hs; n += 4) {
                        corr += x[c + n] * x[nat + n];
                    }
                    if (corr > bestC) {
                        bestC = corr;
                        best = c;
                    }
                }
            }
            for (int n = 0; n < w; n++) {
                y[outPos + n] += x[best + n] * win[n];
                norm[outPos + n] += win[n];
            }
            prev = best;
        }
        List<short[]> out = new ArrayList<>();
        int len = (int) (total / speed);
        for (int s = 0; s < len; s += frame) {
            short[] f = new short[frame];
            for (int n = 0; n < frame && s + n < len; n++) {
                float v = norm[s + n] > 1e-3f ? y[s + n] / norm[s + n] : 0;
                f[n] = clip(Math.round(v));
            }
            out.add(f);
        }
        return out;
    }

    private static short clip(long v) {
        return (short) Math.max(-32768, Math.min(32767, v));
    }
}
