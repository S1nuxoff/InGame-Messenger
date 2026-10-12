package com.sinuxoff.ingamemessenger.core.image;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns sticker files (PNG, JPG, GIF) into what clients load: a PNG with all frames in a grid, plus a small preview of
 * the first frame.
 */
public final class StickerImages {
    /** Max side of a still sticker. */
    public static final int STILL_SIDE = 512;
    /** Max side of one frame of an animated sticker, before the size limit shrinks it further. */
    public static final int FRAME_SIDE = 192;
    public static final int THUMB_SIDE = 64;
    public static final int MAX_FRAMES = 64;
    /** Limit for one sticker's PNG, so it fits in a packet. */
    public static final int MAX_BYTES = 900 * 1024;

    private StickerImages() {
    }

    /** A converted sticker: the frame grid and its layout, plus the preview. */
    public record Converted(byte[] png, int frameW, int frameH, int frames, int fps, int cols, byte[] thumb) {
    }

    private record Frame(BufferedImage image, int delayMs) {
    }

    /** Null if the file isn't a readable image. */
    public static Converted convert(byte[] file) throws IOException {
        List<Frame> frames = isGif(file) ? readGif(file) : List.of();
        if (frames.size() > 1) {
            return animated(frames);
        }
        BufferedImage img = frames.size() == 1 ? frames.get(0).image() : Pictures.read(file, STILL_SIDE * 2);
        if (img == null) {
            return null;
        }
        BufferedImage still = Pictures.scaled(img, STILL_SIDE, false);
        byte[] png = png(still);
        return new Converted(png, still.getWidth(), still.getHeight(), 1, 0, 1, png(Pictures.scaled(still, THUMB_SIDE, false)));
    }

    private static boolean isGif(byte[] d) {
        return d.length > 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F';
    }

    private static Converted animated(List<Frame> all) throws IOException {
        List<Frame> frames = all;
        long total = 0;
        for (Frame f : all) {
            total += f.delayMs();
        }
        if (all.size() > MAX_FRAMES) {
            frames = new ArrayList<>();
            for (int i = 0; i < MAX_FRAMES; i++) {
                frames.add(all.get(i * all.size() / MAX_FRAMES));
            }
        }
        int n = frames.size();
        int fps = (int) Math.max(1, Math.min(50, Math.round(n * 1000.0 / Math.max(1, total))));
        int cols = (int) Math.ceil(Math.sqrt(n)), rows = (n + cols - 1) / cols;
        BufferedImage first = frames.get(0).image();
        for (int side = FRAME_SIDE; ; side = side * 4 / 5) {
            float k = Math.min(1f, side / (float) Math.max(first.getWidth(), first.getHeight()));
            int fw = Math.max(1, Math.round(first.getWidth() * k)), fh = Math.max(1, Math.round(first.getHeight() * k));
            BufferedImage sheet = new BufferedImage(fw * cols, fh * rows, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for (int i = 0; i < n; i++) {
                g.drawImage(frames.get(i).image(), (i % cols) * fw, (i / cols) * fh, fw, fh, null);
            }
            g.dispose();
            byte[] png = png(sheet);
            if (png.length <= MAX_BYTES || side <= 48) {
                return new Converted(png, fw, fh, n, fps, cols, png(Pictures.scaled(first, THUMB_SIDE, false)));
            }
        }
    }

    /** All frames composed onto the full canvas, honoring offsets and disposal methods. */
    private static List<Frame> readGif(byte[] file) throws IOException {
        List<Frame> out = new ArrayList<>();
        try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
            var readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return out;
            }
            ImageReader r = readers.next();
            try {
                r.setInput(in, false, false);
                int w = 0, h = 0;
                Node screen = child(r.getStreamMetadata(), "javax_imageio_gif_stream_1.0", "LogicalScreenDescriptor");
                if (screen != null) {
                    w = intAttr(screen, "logicalScreenWidth");
                    h = intAttr(screen, "logicalScreenHeight");
                }
                BufferedImage canvas = null;
                for (int i = 0; ; i++) {
                    BufferedImage part;
                    try {
                        part = r.read(i);
                    } catch (IndexOutOfBoundsException end) {
                        break;
                    }
                    IIOMetadata meta = r.getImageMetadata(i);
                    Node desc = child(meta, "javax_imageio_gif_image_1.0", "ImageDescriptor");
                    Node gce = child(meta, "javax_imageio_gif_image_1.0", "GraphicControlExtension");
                    int x = desc != null ? intAttr(desc, "imageLeftPosition") : 0;
                    int y = desc != null ? intAttr(desc, "imageTopPosition") : 0;
                    int delay = gce != null ? intAttr(gce, "delayTime") * 10 : 100;
                    String disposal = gce != null ? attr(gce, "disposalMethod") : "none";
                    if (canvas == null) {
                        w = Math.max(w, x + part.getWidth());
                        h = Math.max(h, y + part.getHeight());
                        if (w > Pictures.MAX_SIDE_EVER || h > Pictures.MAX_SIDE_EVER) {
                            throw new IOException("GIF too large: " + w + "x" + h);
                        }
                        canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    }
                    BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                    Graphics2D g = canvas.createGraphics();
                    g.drawImage(part, x, y, null);
                    g.dispose();
                    out.add(new Frame(copy(canvas), delay < 20 ? 100 : delay));
                    if ("restoreToBackgroundColor".equals(disposal)) {
                        g = canvas.createGraphics();
                        g.setComposite(AlphaComposite.Clear);
                        g.fillRect(x, y, part.getWidth(), part.getHeight());
                        g.dispose();
                    } else if (before != null) {
                        canvas = before;
                    }
                    if (out.size() >= MAX_FRAMES * 4) {
                        break;
                    }
                }
            } finally {
                r.dispose();
            }
        }
        return out;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    private static Node child(IIOMetadata meta, String format, String name) {
        if (meta == null) {
            return null;
        }
        for (Node n = meta.getAsTree(format).getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeName().equals(name)) {
                return n;
            }
        }
        return null;
    }

    private static String attr(Node n, String name) {
        NamedNodeMap a = n.getAttributes();
        Node v = a == null ? null : a.getNamedItem(name);
        return v == null ? "" : v.getNodeValue();
    }

    private static int intAttr(Node n, String name) {
        try {
            return Integer.parseInt(attr(n, name));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
