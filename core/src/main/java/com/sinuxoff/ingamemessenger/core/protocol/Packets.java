package com.sinuxoff.ingamemessenger.core.protocol;

import com.sinuxoff.ingamemessenger.core.protocol.PacketType.Direction;

import java.util.List;
import java.util.UUID;

/**
 * All packets and their wire format. Each one goes on its own channel "ingamemessenger:&lt;id&gt;". The encoding matches
 * Minecraft's codecs, so clients and servers on different loaders understand each other.
 */
public final class Packets {
    /** Max size of a picture sent to a client, in bytes. */
    public static final int IMAGE_DATA_MAX = 1000 * 1024 + 1024;

    private Packets() {
    }

    /**
     * S2C, on join: the server's protocol and mod version. Welcome and Hello never change their format, so any two
     * versions can tell each other apart. Nothing else is sent until both sides have the same protocol.
     */
    public record Welcome(String protocol, String version) implements Packet {
        public static final PacketType<Welcome> TYPE = new PacketType<>("welcome", Direction.TO_CLIENT, Welcome.class, Welcome::read);

        @Override
        public PacketType<Welcome> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(protocol, 32);
            out.str(version, 64);
        }

        static Welcome read(Wire.In in) {
            return new Welcome(in.str(32), in.str(64));
        }
    }

    /** C2S: the answer to Welcome, with the client's protocol and mod version. */
    public record Hello(String protocol, String version) implements Packet {
        public static final PacketType<Hello> TYPE = new PacketType<>("hello", Direction.TO_SERVER, Hello.class, Hello::read);

        @Override
        public PacketType<Hello> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(protocol, 32);
            out.str(version, 64);
        }

        static Hello read(Wire.In in) {
            return new Hello(in.str(32), in.str(64));
        }
    }

    /** S2C: the server's sticker and emoji packs (on join and after a reload). */
    public record StickerIndex(String json) implements Packet {
        public static final PacketType<StickerIndex> TYPE = new PacketType<>("sticker_index", Direction.TO_CLIENT, StickerIndex.class, StickerIndex::read);

        @Override
        public PacketType<StickerIndex> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(json, 1 << 18);
        }

        static StickerIndex read(Wire.In in) {
            return new StickerIndex(in.str(1 << 18));
        }
    }

    /** C2S: request a sticker image (thumb = the small preview). */
    public record StickerGet(String pack, String id, boolean thumb) implements Packet {
        public static final PacketType<StickerGet> TYPE = new PacketType<>("sticker_get", Direction.TO_SERVER, StickerGet.class, StickerGet::read);

        @Override
        public PacketType<StickerGet> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(pack, 64);
            out.str(id, 64);
            out.bool(thumb);
        }

        static StickerGet read(Wire.In in) {
            return new StickerGet(in.str(64), in.str(64), in.bool());
        }
    }

    /** S2C: a sticker image as PNG; empty if there's no such sticker. */
    public record StickerData(String pack, String id, boolean thumb, byte[] data) implements Packet {
        public static final PacketType<StickerData> TYPE = new PacketType<>("sticker_data", Direction.TO_CLIENT, StickerData.class, StickerData::read);

        @Override
        public PacketType<StickerData> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(pack, 64);
            out.str(id, 64);
            out.bool(thumb);
            out.bytes(data, IMAGE_DATA_MAX);
        }

        static StickerData read(Wire.In in) {
            return new StickerData(in.str(64), in.str(64), in.bool(), in.bytes(IMAGE_DATA_MAX));
        }
    }

    /** S2C: what this server has enabled (on join and when an operator changes it). */
    public record Features(boolean typing, boolean voice, int voiceMaxSeconds, boolean images, int imageMaxKb, String imageHost) implements Packet {
        public static final PacketType<Features> TYPE = new PacketType<>("features", Direction.TO_CLIENT, Features.class, Features::read);

        @Override
        public PacketType<Features> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.bool(typing);
            out.bool(voice);
            out.varInt(voiceMaxSeconds);
            out.bool(images);
            out.varInt(imageMaxKb);
            out.str(imageHost, 16);
        }

        static Features read(Wire.In in) {
            return new Features(in.bool(), in.bool(), in.varInt(), in.bool(), in.varInt(), in.str(16));
        }
    }

    /** C2S: one picture of the album follows (JPEG or PNG). */
    public record ImageStart(int upload, int bytes, int w, int h, int album, int index) implements Packet {
        public static final PacketType<ImageStart> TYPE = new PacketType<>("image_start", Direction.TO_SERVER, ImageStart.class, ImageStart::read);

        @Override
        public PacketType<ImageStart> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(upload);
            out.varInt(bytes);
            out.varInt(w);
            out.varInt(h);
            out.varInt(album);
            out.varInt(index);
        }

        static ImageStart read(Wire.In in) {
            return new ImageStart(in.varInt(), in.varInt(), in.varInt(), in.varInt(), in.varInt(), in.varInt());
        }
    }

    public record ImageChunk(int upload, int offset, byte[] data) implements Packet {
        public static final PacketType<ImageChunk> TYPE = new PacketType<>("image_chunk", Direction.TO_SERVER, ImageChunk.class, ImageChunk::read);

        @Override
        public PacketType<ImageChunk> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(upload);
            out.varInt(offset);
            out.bytes(data, 30_000);
        }

        static ImageChunk read(Wire.In in) {
            return new ImageChunk(in.varInt(), in.varInt(), in.bytes(30_000));
        }
    }

    /**
     * S2C: how the server shows players' names (a rank, colors), one "uuid \t name \t component JSON" per line: on
     * join for everyone online, on every change, and before a history page for its senders.
     */
    public record Names(String data) implements Packet {
        public static final PacketType<Names> TYPE = new PacketType<>("names", Direction.TO_CLIENT, Names.class, Names::read);

        @Override
        public PacketType<Names> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(data, 262144);
        }

        static Names read(Wire.In in) {
            return new Names(in.str(262144));
        }
    }

    public record ImageGet(String key) implements Packet {
        public static final PacketType<ImageGet> TYPE = new PacketType<>("image_get", Direction.TO_SERVER, ImageGet.class, ImageGet::read);

        @Override
        public PacketType<ImageGet> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(key, 400);
        }

        static ImageGet read(Wire.In in) {
            return new ImageGet(in.str(400));
        }
    }

    public record ImageData(String key, byte[] data) implements Packet {
        public static final PacketType<ImageData> TYPE = new PacketType<>("image_data", Direction.TO_CLIENT, ImageData.class, ImageData::read);

        @Override
        public PacketType<ImageData> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(key, 400);
            out.bytes(data, IMAGE_DATA_MAX);
        }

        static ImageData read(Wire.In in) {
            return new ImageData(in.str(400), in.bytes(IMAGE_DATA_MAX));
        }
    }

    public record VoiceChunk(int upload, int offset, byte[] data) implements Packet {
        public static final PacketType<VoiceChunk> TYPE = new PacketType<>("voice_chunk", Direction.TO_SERVER, VoiceChunk.class, VoiceChunk::read);

        @Override
        public PacketType<VoiceChunk> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(upload);
            out.varInt(offset);
            out.bytes(data, 30_000);
        }

        static VoiceChunk read(Wire.In in) {
            return new VoiceChunk(in.varInt(), in.varInt(), in.bytes(30_000));
        }
    }

    public record VoiceGet(String key) implements Packet {
        public static final PacketType<VoiceGet> TYPE = new PacketType<>("voice_get", Direction.TO_SERVER, VoiceGet.class, VoiceGet::read);

        @Override
        public PacketType<VoiceGet> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(key, 32);
        }

        static VoiceGet read(Wire.In in) {
            return new VoiceGet(in.str(32));
        }
    }

    /** S2C: empty data means the file was deleted. */
    public record VoiceData(String key, byte[] data) implements Packet {
        public static final PacketType<VoiceData> TYPE = new PacketType<>("voice_data", Direction.TO_CLIENT, VoiceData.class, VoiceData::read);

        @Override
        public PacketType<VoiceData> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(key, 32);
            out.bytes(data, 1 << 20);
        }

        static VoiceData read(Wire.In in) {
            return new VoiceData(in.str(32), in.bytes(1 << 20));
        }
    }

    public static final List<PacketType<?>> ALL = List.of(Welcome.TYPE, Hello.TYPE, StickerIndex.TYPE, StickerGet.TYPE, StickerData.TYPE, Features.TYPE, ImageStart.TYPE, ImageChunk.TYPE, ImageGet.TYPE, ImageData.TYPE, VoiceChunk.TYPE, VoiceGet.TYPE, VoiceData.TYPE, Names.TYPE);
}
