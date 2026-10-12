package com.sinuxoff.ingamemessenger.core.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Safe image decoding (guards against decompression bombs), JPEG compression to a size limit, ARGB decoding. */
public final class Pictures {
    public static final int MAX_SIDE_EVER = 30_000;

    private Pictures() {
    }

    public record Jpeg(byte[] data, int width, int height) {
    }

    public record Argb(int width, int height, int[] pixels) {
    }

    /** Checks the magic bytes. */
    public static boolean isJpegOrPng(byte[] d) {
        return d != null && d.length > 8 && (d[0] == (byte) 0xFF && d[1] == (byte) 0xD8
                || d[0] == (byte) 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G');
    }

    /**
     * Reads the size from the header first and rejects anything over {@link #MAX_SIDE_EVER}. Large images are
     * subsampled to about maxSide. Returns null for formats Java can't read (HEIC, WebP...).
     */
    public static BufferedImage read(byte[] bytes, int maxSide) throws IOException {
        try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader r = readers.next();
            try {
                r.setInput(in, true, true);
                int w = r.getWidth(0), h = r.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_SIDE_EVER || h > MAX_SIDE_EVER) {
                    throw new IOException("picture too large: " + w + "x" + h);
                }
                int step = Math.max(1, (int) Math.ceil(Math.max(w, h) / (double) maxSide));
                ImageReadParam p = r.getDefaultReadParam();
                p.setSourceSubsampling(step, step, 0, 0);
                return r.read(0, p);
            } finally {
                r.dispose();
            }
        }
    }

    /** Shrinks and lowers quality until it fits; null if it doesn't fit even at 640 px. */
    public static Jpeg shrinkToJpeg(BufferedImage src, int maxBytes) throws IOException {
        for (int side : new int[]{1600, 1280, 1024, 800, 640}) {
            BufferedImage img = scaled(src, side, true);
            for (float q : new float[]{0.85f, 0.72f, 0.6f}) {
                byte[] out = jpeg(img, q);
                if (out.length <= maxBytes) {
                    return new Jpeg(out, img.getWidth(), img.getHeight());
                }
            }
        }
        return null;
    }

    public static Argb decode(byte[] bytes, int maxSide) throws IOException {
        BufferedImage src = read(bytes, maxSide * 2);
        if (src == null) {
            throw new IOException("not a picture");
        }
        BufferedImage img = scaled(src, maxSide, false);
        int w = img.getWidth(), h = img.getHeight();
        return new Argb(w, h, img.getRGB(0, 0, w, h, null, 0, w));
    }

    /** Opaque images get the chat background color (JPEG has no alpha). */
    static BufferedImage scaled(BufferedImage src, int side, boolean opaque) {
        int w = src.getWidth(), h = src.getHeight();
        float k = Math.min(1f, side / (float) Math.max(w, h));
        if (k >= 1f && !opaque) {
            return src;
        }
        int nw = Math.max(1, Math.round(w * k)), nh = Math.max(1, Math.round(h * k));
        BufferedImage out = new BufferedImage(nw, nh, opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (opaque) {
            g.setColor(new Color(0x1B1F2A));
            g.fillRect(0, 0, nw, nh);
        }
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    static byte[] jpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            w.setOutput(out);
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        return bytes.toByteArray();
    }
}
