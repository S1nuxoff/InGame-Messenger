package com.sinuxoff.ingamemessenger.core.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class PicturesTest {
    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Noise: hard to compress, so the size limit has to do real work. */
    private static BufferedImage noise(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, r.nextInt());
            }
        }
        return img;
    }

    @Test
    void recognisesJpegAndPng() throws IOException {
        assertTrue(Pictures.isJpegOrPng(png(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB))));
        assertFalse(Pictures.isJpegOrPng("GIF89a-------".getBytes()));
        assertFalse(Pictures.isJpegOrPng(new byte[3]));
        assertFalse(Pictures.isJpegOrPng(null));
    }

    @Test
    void shrinksUnderTheLimit() throws IOException {
        BufferedImage big = noise(2400, 1200);
        Pictures.Jpeg j = Pictures.shrinkToJpeg(big, 300 * 1024);
        assertNotNull(j);
        assertTrue(j.data().length <= 300 * 1024);
        assertTrue(Math.max(j.width(), j.height()) <= 1600);
        assertEquals(2, j.width() / j.height(), "the shape kept");
        assertTrue(Pictures.isJpegOrPng(j.data()));
    }

    @Test
    void readsBigOnesSubsampled() throws IOException {
        BufferedImage src = new BufferedImage(4000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 4000, 1000);
        g.dispose();
        BufferedImage read = Pictures.read(png(src), 1000);
        assertTrue(read.getWidth() <= 1000 && read.getWidth() >= 900, "read every n-th pixel: " + read.getWidth());
        Pictures.Argb a = Pictures.decode(png(src), 200);
        assertEquals(200, a.width());
        assertEquals(50, a.height());
        assertEquals(0xFFFF0000, a.pixels()[0]);
    }

    @Test
    void notAPicture() throws IOException {
        assertNull(Pictures.read("hello".getBytes(), 100));
        assertThrows(IOException.class, () -> Pictures.decode("hello".getBytes(), 100));
    }

    /** A tiny PNG whose header claims 100000 × 100000 pixels: refused before anything is made. */
    @Test
    void refusesABomb() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(out, "IHDR", ByteBuffer.allocate(13).putInt(100_000).putInt(100_000).put((byte) 8).put((byte) 2)
                .put((byte) 0).put((byte) 0).put((byte) 0).array());
        Deflater d = new Deflater();
        d.setInput(new byte[1000]);
        d.finish();
        byte[] z = new byte[2000];
        int n = d.deflate(z);
        chunk(out, "IDAT", java.util.Arrays.copyOf(z, n));
        chunk(out, "IEND", new byte[0]);
        byte[] bomb = out.toByteArray();
        assertTrue(bomb.length < 200);
        IOException e = assertThrows(IOException.class, () -> Pictures.read(bomb, 1000));
        assertTrue(e.getMessage().contains("too large"));
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(12 + data.length);
        b.putInt(data.length).put(type.getBytes()).put(data);
        CRC32 crc = new CRC32();
        crc.update(type.getBytes());
        crc.update(data);
        b.putInt((int) crc.getValue());
        out.writeBytes(b.array());
    }
}
