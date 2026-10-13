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

    /** C2S: send a sticker (replyTo -1 = not a reply). */
    public record Send(String pack, String id, int replyTo) implements Packet {
        public static final PacketType<Send> TYPE = new PacketType<>("send", Direction.TO_SERVER, Send.class, Send::read);

        @Override
        public PacketType<Send> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(pack, 64);
            out.str(id, 64);
            out.i32(replyTo);
        }

        static Send read(Wire.In in) {
            return new Send(in.str(64), in.str(64), in.i32());
        }
    }

    /** S2C: someone sent a sticker. */
    public record Show(UUID sender, String name, String pack, String id, int replyTo) implements Packet {
        public static final PacketType<Show> TYPE = new PacketType<>("show", Direction.TO_CLIENT, Show.class, Show::read);

        @Override
        public PacketType<Show> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(sender);
            out.str(name, 64);
            out.str(pack, 64);
            out.str(id, 64);
            out.i32(replyTo);
        }

        static Show read(Wire.In in) {
            return new Show(in.uuid(), in.str(64), in.str(64), in.str(64), in.i32());
        }
    }

    /**
     * S2C: the id of a message, sent along with it (slightly before or after). The client matches it to the message
     * by sender and hash ({@link Protocol#print}; 0 = any).
     */
    public record Tag(int id, UUID sender, int hash) implements Packet {
        public static final PacketType<Tag> TYPE = new PacketType<>("tag", Direction.TO_CLIENT, Tag.class, Tag::read);

        @Override
        public PacketType<Tag> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
            out.uuid(sender);
            out.i32(hash);
        }

        static Tag read(Wire.In in) {
            return new Tag(in.varInt(), in.uuid(), in.i32());
        }
    }

    /** C2S: toggle a reaction. emoji is "0".."7" (built in) or "pack/id" (from an emoji pack). */
    public record React(int id, String emoji) implements Packet {
        public static final PacketType<React> TYPE = new PacketType<>("react", Direction.TO_SERVER, React.class, React::read);

        @Override
        public PacketType<React> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
            out.str(emoji, 130);
        }

        static React read(Wire.In in) {
            return new React(in.varInt(), in.str(130));
        }
    }

    /** S2C: a message's reactions, "emoji:name,name;emoji:name" ("" = none). */
    public record Reactions(int id, String data) implements Packet {
        public static final PacketType<Reactions> TYPE = new PacketType<>("reactions", Direction.TO_CLIENT, Reactions.class, Reactions::read);

        @Override
        public PacketType<Reactions> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
            out.str(data, 8000);
        }

        static Reactions read(Wire.In in) {
            return new Reactions(in.varInt(), in.str(8000));
        }
    }

    /** C2S: a history page before the given id (0 = newest). */
    public record HistoryRequest(int before) implements Packet {
        public static final PacketType<HistoryRequest> TYPE = new PacketType<>("history_request", Direction.TO_SERVER, HistoryRequest.class, HistoryRequest::read);

        @Override
        public PacketType<HistoryRequest> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(before);
        }

        static HistoryRequest read(Wire.In in) {
            return new HistoryRequest(in.varInt());
        }
    }

    /** S2C: JSON array, oldest first; more = older messages exist. */
    public record HistoryPage(boolean more, String json) implements Packet {
        public static final PacketType<HistoryPage> TYPE = new PacketType<>("history_page", Direction.TO_CLIENT, HistoryPage.class, HistoryPage::read);

        @Override
        public PacketType<HistoryPage> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.bool(more);
            out.str(json, 1 << 20);
        }

        static HistoryPage read(Wire.In in) {
            return new HistoryPage(in.bool(), in.str(1 << 20));
        }
    }

    /** C2S: typing (repeated while it lasts) or stopped. */
    public record Typing(boolean on) implements Packet {
        public static final PacketType<Typing> TYPE = new PacketType<>("typing", Direction.TO_SERVER, Typing.class, Typing::read);

        @Override
        public PacketType<Typing> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.bool(on);
        }

        static Typing read(Wire.In in) {
            return new Typing(in.bool());
        }
    }

    public record TypingState(UUID who, String name, boolean on) implements Packet {
        public static final PacketType<TypingState> TYPE = new PacketType<>("typing_state", Direction.TO_CLIENT, TypingState.class, TypingState::read);

        @Override
        public PacketType<TypingState> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(who);
            out.str(name, 64);
            out.bool(on);
        }

        static TypingState read(Wire.In in) {
            return new TypingState(in.uuid(), in.str(64), in.bool());
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

    /** C2S: edit own message (a reply keeps its "↩n " prefix). */
    public record EditMsg(int id, String text) implements Packet {
        public static final PacketType<EditMsg> TYPE = new PacketType<>("edit", Direction.TO_SERVER, EditMsg.class, EditMsg::read);

        @Override
        public PacketType<EditMsg> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
            out.str(text, 300);
        }

        static EditMsg read(Wire.In in) {
            return new EditMsg(in.varInt(), in.str(300));
        }
    }

    /** C2S: delete own message (operators: any message). */
    public record DeleteMsg(int id) implements Packet {
        public static final PacketType<DeleteMsg> TYPE = new PacketType<>("delete", Direction.TO_SERVER, DeleteMsg.class, DeleteMsg::read);

        @Override
        public PacketType<DeleteMsg> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
        }

        static DeleteMsg read(Wire.In in) {
            return new DeleteMsg(in.varInt());
        }
    }

    public record Edited(int id, String text) implements Packet {
        public static final PacketType<Edited> TYPE = new PacketType<>("edited", Direction.TO_CLIENT, Edited.class, Edited::read);

        @Override
        public PacketType<Edited> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
            out.str(text, 300);
        }

        static Edited read(Wire.In in) {
            return new Edited(in.varInt(), in.str(300));
        }
    }

    public record Deleted(int id) implements Packet {
        public static final PacketType<Deleted> TYPE = new PacketType<>("deleted", Direction.TO_CLIENT, Deleted.class, Deleted::read);

        @Override
        public PacketType<Deleted> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
        }

        static Deleted read(Wire.In in) {
            return new Deleted(in.varInt());
        }
    }

    /** C2S: a picture message follows: total pictures (1..4) and a caption. */
    public record AlbumStart(int album, int total, String caption, int replyTo) implements Packet {
        public static final PacketType<AlbumStart> TYPE = new PacketType<>("album_start", Direction.TO_SERVER, AlbumStart.class, AlbumStart::read);

        @Override
        public PacketType<AlbumStart> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(album);
            out.varInt(total);
            out.str(caption, 1024);
            out.i32(replyTo);
        }

        static AlbumStart read(Wire.In in) {
            return new AlbumStart(in.varInt(), in.varInt(), in.str(1024), in.i32());
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

    /** S2C: a picture message. data has one "ref w h" line per picture; ref is a URL or a key on this server. */
    public record ImageShow(UUID sender, String name, String data, String caption, int replyTo) implements Packet {
        public static final PacketType<ImageShow> TYPE = new PacketType<>("image_show", Direction.TO_CLIENT, ImageShow.class, ImageShow::read);

        @Override
        public PacketType<ImageShow> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(sender);
            out.str(name, 64);
            out.str(data, 2048);
            out.str(caption, 1024);
            out.i32(replyTo);
        }

        static ImageShow read(Wire.In in) {
            return new ImageShow(in.uuid(), in.str(64), in.str(2048), in.str(1024), in.i32());
        }
    }

    /** S2C: the next message belongs to the private chat with other. */
    public record Route(UUID other) implements Packet {
        public static final PacketType<Route> TYPE = new PacketType<>("route", Direction.TO_CLIENT, Route.class, Route::read);

        @Override
        public PacketType<Route> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(other);
        }

        static Route read(Wire.In in) {
            return new Route(in.uuid());
        }
    }

    /** C2S: the chat the player has open (another player, or the nil UUID for the public chat). */
    public record ChatTarget(UUID other) implements Packet {
        public static final PacketType<ChatTarget> TYPE = new PacketType<>("chat_target", Direction.TO_SERVER, ChatTarget.class, ChatTarget::read);

        @Override
        public PacketType<ChatTarget> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(other);
        }

        static ChatTarget read(Wire.In in) {
            return new ChatTarget(in.uuid());
        }
    }

    public record DmSend(UUID to, String text) implements Packet {
        public static final PacketType<DmSend> TYPE = new PacketType<>("dm_send", Direction.TO_SERVER, DmSend.class, DmSend::read);

        @Override
        public PacketType<DmSend> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(to);
            out.str(text, 1024);
        }

        static DmSend read(Wire.In in) {
            return new DmSend(in.uuid(), in.str(1024));
        }
    }

    public record DmShow(int id, long t, UUID from, String fromName, UUID to, String toName, String text) implements Packet {
        public static final PacketType<DmShow> TYPE = new PacketType<>("dm_show", Direction.TO_CLIENT, DmShow.class, DmShow::read);

        @Override
        public PacketType<DmShow> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
            out.varLong(t);
            out.uuid(from);
            out.str(fromName, 64);
            out.uuid(to);
            out.str(toName, 64);
            out.str(text, 1024);
        }

        static DmShow read(Wire.In in) {
            return new DmShow(in.varInt(), in.varLong(), in.uuid(), in.str(64), in.uuid(), in.str(64), in.str(1024));
        }
    }

    /** C2S: a page of the private chat with other (before 0 = newest). */
    public record DmHistoryReq(UUID other, int before) implements Packet {
        public static final PacketType<DmHistoryReq> TYPE = new PacketType<>("dm_history_req", Direction.TO_SERVER, DmHistoryReq.class, DmHistoryReq::read);

        @Override
        public PacketType<DmHistoryReq> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(other);
            out.varInt(before);
        }

        static DmHistoryReq read(Wire.In in) {
            return new DmHistoryReq(in.uuid(), in.varInt());
        }
    }

    public record DmHistory(UUID other, boolean more, String json) implements Packet {
        public static final PacketType<DmHistory> TYPE = new PacketType<>("dm_history", Direction.TO_CLIENT, DmHistory.class, DmHistory::read);

        @Override
        public PacketType<DmHistory> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(other);
            out.bool(more);
            out.str(json, 1 << 18);
        }

        static DmHistory read(Wire.In in) {
            return new DmHistory(in.uuid(), in.bool(), in.str(1 << 18));
        }
    }

    public record DmRead(UUID other, int upTo) implements Packet {
        public static final PacketType<DmRead> TYPE = new PacketType<>("dm_read", Direction.TO_SERVER, DmRead.class, DmRead::read);

        @Override
        public PacketType<DmRead> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(other);
            out.varInt(upTo);
        }

        static DmRead read(Wire.In in) {
            return new DmRead(in.uuid(), in.varInt());
        }
    }

    public record DmReadState(UUID reader, int upTo) implements Packet {
        public static final PacketType<DmReadState> TYPE = new PacketType<>("dm_read_state", Direction.TO_CLIENT, DmReadState.class, DmReadState::read);

        @Override
        public PacketType<DmReadState> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(reader);
            out.varInt(upTo);
        }

        static DmReadState read(Wire.In in) {
            return new DmReadState(in.uuid(), in.varInt());
        }
    }

    /** S2C on join: the player's private chats, one "uuid name newestId unread peerReadUpTo" per line. */
    public record DmChats(String data) implements Packet {
        public static final PacketType<DmChats> TYPE = new PacketType<>("dm_chats", Direction.TO_CLIENT, DmChats.class, DmChats::read);

        @Override
        public PacketType<DmChats> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(data, 1 << 18);
        }

        static DmChats read(Wire.In in) {
            return new DmChats(in.str(1 << 18));
        }
    }

    /** C2S: read the public chat up to mid. */
    public record ReadUpTo(int mid) implements Packet {
        public static final PacketType<ReadUpTo> TYPE = new PacketType<>("read_up_to", Direction.TO_SERVER, ReadUpTo.class, ReadUpTo::read);

        @Override
        public PacketType<ReadUpTo> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(mid);
        }

        static ReadUpTo read(Wire.In in) {
            return new ReadUpTo(in.varInt());
        }
    }

    public record ReadState(UUID who, String name, int mid) implements Packet {
        public static final PacketType<ReadState> TYPE = new PacketType<>("read_state", Direction.TO_CLIENT, ReadState.class, ReadState::read);

        @Override
        public PacketType<ReadState> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(who);
            out.str(name, 64);
            out.varInt(mid);
        }

        static ReadState read(Wire.In in) {
            return new ReadState(in.uuid(), in.str(64), in.varInt());
        }
    }

    /** S2C on join and on every change: until when the player is muted in the chat and in private chats (0 = not). */
    public record MuteState(long chatUntil, long dmUntil, String reason) implements Packet {
        public static final PacketType<MuteState> TYPE = new PacketType<>("mute_state", Direction.TO_CLIENT, MuteState.class, MuteState::read);

        @Override
        public PacketType<MuteState> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.i64(chatUntil);
            out.i64(dmUntil);
            out.str(reason, 255);
        }

        static MuteState read(Wire.In in) {
            return new MuteState(in.i64(), in.i64(), in.str(255));
        }
    }

    /** S2C on join: everyone's read marks, one "uuid name mid" per line. */
    public record ReadAll(String data) implements Packet {
        public static final PacketType<ReadAll> TYPE = new PacketType<>("read_all", Direction.TO_CLIENT, ReadAll.class, ReadAll::read);

        @Override
        public PacketType<ReadAll> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(data, 1 << 18);
        }

        static ReadAll read(Wire.In in) {
            return new ReadAll(in.str(1 << 18));
        }
    }

    /** C2S: create a poll; options are one per line. */
    public record PollCreate(String question, String options, boolean multi, int replyTo) implements Packet {
        public static final PacketType<PollCreate> TYPE = new PacketType<>("poll_create", Direction.TO_SERVER, PollCreate.class, PollCreate::read);

        @Override
        public PacketType<PollCreate> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(question, 1024);
            out.str(options, 2048);
            out.bool(multi);
            out.i32(replyTo);
        }

        static PollCreate read(Wire.In in) {
            return new PollCreate(in.str(1024), in.str(2048), in.bool(), in.i32());
        }
    }

    public record PollVote(int id, int option) implements Packet {
        public static final PacketType<PollVote> TYPE = new PacketType<>("poll_vote", Direction.TO_SERVER, PollVote.class, PollVote::read);

        @Override
        public PacketType<PollVote> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
            out.varInt(option);
        }

        static PollVote read(Wire.In in) {
            return new PollVote(in.varInt(), in.varInt());
        }
    }

    /** S2C: json is {q, o[], m}. */
    public record PollShow(UUID sender, String name, String json, int replyTo) implements Packet {
        public static final PacketType<PollShow> TYPE = new PacketType<>("poll_show", Direction.TO_CLIENT, PollShow.class, PollShow::read);

        @Override
        public PacketType<PollShow> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(sender);
            out.str(name, 64);
            out.str(json, 4096);
            out.i32(replyTo);
        }

        static PollShow read(Wire.In in) {
            return new PollShow(in.uuid(), in.str(64), in.str(4096), in.i32());
        }
    }

    /** S2C: counts "c,c,...;voters" after voter changed their vote to bits. */
    public record PollState(int id, String counts, UUID voter, int bits) implements Packet {
        public static final PacketType<PollState> TYPE = new PacketType<>("poll_state", Direction.TO_CLIENT, PollState.class, PollState::read);

        @Override
        public PacketType<PollState> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(id);
            out.str(counts, 128);
            out.uuid(voter);
            out.varInt(bits);
        }

        static PollState read(Wire.In in) {
            return new PollState(in.varInt(), in.str(128), in.uuid(), in.varInt());
        }
    }

    public record SearchReq(String query) implements Packet {
        public static final PacketType<SearchReq> TYPE = new PacketType<>("search", Direction.TO_SERVER, SearchReq.class, SearchReq::read);

        @Override
        public PacketType<SearchReq> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(query, 100);
        }

        static SearchReq read(Wire.In in) {
            return new SearchReq(in.str(100));
        }
    }

    /** S2C: search results, see ChatStore#search. */
    public record SearchRes(String query, String data) implements Packet {
        public static final PacketType<SearchRes> TYPE = new PacketType<>("search_res", Direction.TO_CLIENT, SearchRes.class, SearchRes::read);

        @Override
        public PacketType<SearchRes> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(query, 100);
            out.str(data, 16384);
        }

        static SearchRes read(Wire.In in) {
            return new SearchRes(in.str(100), in.str(16384));
        }
    }

    /** C2S, Catbox mode: pictures already uploaded by the client, one "url w h" per line. */
    public record ImageLinks(String data, String caption, int replyTo) implements Packet {
        public static final PacketType<ImageLinks> TYPE = new PacketType<>("image_links", Direction.TO_SERVER, ImageLinks.class, ImageLinks::read);

        @Override
        public PacketType<ImageLinks> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.str(data, 1024);
            out.str(caption, 1024);
            out.i32(replyTo);
        }

        static ImageLinks read(Wire.In in) {
            return new ImageLinks(in.str(1024), in.str(1024), in.i32());
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

    /** C2S: a voice message follows: size, duration, waveform bars, reply target. */
    public record VoiceStart(int upload, int bytes, int durationMs, byte[] wave, int replyTo) implements Packet {
        public static final PacketType<VoiceStart> TYPE = new PacketType<>("voice_start", Direction.TO_SERVER, VoiceStart.class, VoiceStart::read);

        @Override
        public PacketType<VoiceStart> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.varInt(upload);
            out.varInt(bytes);
            out.varInt(durationMs);
            out.bytes(wave, 256);
            out.i32(replyTo);
        }

        static VoiceStart read(Wire.In in) {
            return new VoiceStart(in.varInt(), in.varInt(), in.varInt(), in.bytes(256), in.i32());
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

    public record VoiceShow(UUID sender, String name, String key, int durationMs, byte[] wave, int replyTo) implements Packet {
        public static final PacketType<VoiceShow> TYPE = new PacketType<>("voice_show", Direction.TO_CLIENT, VoiceShow.class, VoiceShow::read);

        @Override
        public PacketType<VoiceShow> type() {
            return TYPE;
        }

        @Override
        public void write(Wire.Out out) {
            out.uuid(sender);
            out.str(name, 64);
            out.str(key, 32);
            out.varInt(durationMs);
            out.bytes(wave, 256);
            out.i32(replyTo);
        }

        static VoiceShow read(Wire.In in) {
            return new VoiceShow(in.uuid(), in.str(64), in.str(32), in.varInt(), in.bytes(256), in.i32());
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

    public static final List<PacketType<?>> ALL = List.of(Welcome.TYPE, Hello.TYPE, StickerIndex.TYPE, StickerGet.TYPE, StickerData.TYPE, Send.TYPE, Show.TYPE, Tag.TYPE, React.TYPE, Reactions.TYPE, HistoryRequest.TYPE, HistoryPage.TYPE, Typing.TYPE, TypingState.TYPE, Features.TYPE, EditMsg.TYPE, DeleteMsg.TYPE, Edited.TYPE, Deleted.TYPE, AlbumStart.TYPE, ImageStart.TYPE, ImageChunk.TYPE, ImageShow.TYPE, Route.TYPE, ChatTarget.TYPE, DmSend.TYPE, DmShow.TYPE, DmHistoryReq.TYPE, DmHistory.TYPE, DmRead.TYPE, DmReadState.TYPE, DmChats.TYPE, ReadUpTo.TYPE, ReadState.TYPE, ReadAll.TYPE, PollCreate.TYPE, PollVote.TYPE, PollShow.TYPE, PollState.TYPE, SearchReq.TYPE, SearchRes.TYPE, ImageLinks.TYPE, ImageGet.TYPE, ImageData.TYPE, VoiceStart.TYPE, VoiceChunk.TYPE, VoiceShow.TYPE, VoiceGet.TYPE, VoiceData.TYPE, MuteState.TYPE);
}
