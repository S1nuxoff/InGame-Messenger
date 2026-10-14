package com.sinuxoff.ingamemessenger.core.protocol;

import com.sinuxoff.ingamemessenger.core.protocol.PacketType.Direction;

import java.util.List;
import java.util.UUID;

/**
 * The channel protocol: everything is addressed to a channel by its id (the server's chat, its channels, private
 * chats, communities' channels alike). Messages travel as JSON objects (see {@code Store.json}).
 */
public final class Proto {
    private Proto() {
    }

    private static final int TEXT = 4000, JSON = 1 << 20, NONCE = 32, BODY = 16000;

    // Client to server

    /** A page of a channel before a message id (0 = newest). */
    public record History(long channel, long before) implements Packet {
        public static final PacketType<History> TYPE = new PacketType<>("c_history", Direction.TO_SERVER, History.class, History::read);

        public PacketType<History> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(channel).varLong(before);
        }

        static History read(Wire.In in) {
            return new History(in.varLong(), in.varLong());
        }
    }

    /** A new message: kind "text" (body: the text), "sticker" ({"p":pack,"s":id}) or "poll" ({"q":..,"o":[..],"m":multi}). */
    public record Post(long channel, String nonce, String kind, String body, long replyTo) implements Packet {
        public static final PacketType<Post> TYPE = new PacketType<>("c_post", Direction.TO_SERVER, Post.class, Post::read);

        public PacketType<Post> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(channel).str(nonce, NONCE).str(kind, 16).str(body, BODY).varLong(replyTo);
        }

        static Post read(Wire.In in) {
            return new Post(in.varLong(), in.str(NONCE), in.str(16), in.str(BODY), in.varLong());
        }
    }

    public record Edit(long id, String text) implements Packet {
        public static final PacketType<Edit> TYPE = new PacketType<>("c_edit", Direction.TO_SERVER, Edit.class, Edit::read);

        public PacketType<Edit> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id).str(text, TEXT);
        }

        static Edit read(Wire.In in) {
            return new Edit(in.varLong(), in.str(TEXT));
        }
    }

    public record Delete(long id) implements Packet {
        public static final PacketType<Delete> TYPE = new PacketType<>("c_delete", Direction.TO_SERVER, Delete.class, Delete::read);

        public PacketType<Delete> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id);
        }

        static Delete read(Wire.In in) {
            return new Delete(in.varLong());
        }
    }

    /** Adds the reaction, or takes it back. */
    public record React(long id, String emoji) implements Packet {
        public static final PacketType<React> TYPE = new PacketType<>("c_react", Direction.TO_SERVER, React.class, React::read);

        public PacketType<React> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id).str(emoji, 130);
        }

        static React read(Wire.In in) {
            return new React(in.varLong(), in.str(130));
        }
    }

    public record Read(long channel, long upTo) implements Packet {
        public static final PacketType<Read> TYPE = new PacketType<>("c_read", Direction.TO_SERVER, Read.class, Read::read);

        public PacketType<Read> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(channel).varLong(upTo);
        }

        static Read read(Wire.In in) {
            return new Read(in.varLong(), in.varLong());
        }
    }

    /** "I'm typing here" (sent again every few seconds while typing). */
    public record Typing(long channel) implements Packet {
        public static final PacketType<Typing> TYPE = new PacketType<>("c_typing", Direction.TO_SERVER, Typing.class, Typing::read);

        public PacketType<Typing> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(channel);
        }

        static Typing read(Wire.In in) {
            return new Typing(in.varLong());
        }
    }

    public record Vote(long id, int option) implements Packet {
        public static final PacketType<Vote> TYPE = new PacketType<>("c_vote", Direction.TO_SERVER, Vote.class, Vote::read);

        public PacketType<Vote> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id).varInt(option);
        }

        static Vote read(Wire.In in) {
            return new Vote(in.varLong(), in.varInt());
        }
    }

    /** Search in a channel, or (channel 0) in every channel the player sees. */
    public record Find(String query, long channel) implements Packet {
        public static final PacketType<Find> TYPE = new PacketType<>("c_find", Direction.TO_SERVER, Find.class, Find::read);

        public PacketType<Find> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.str(query, 100).varLong(channel);
        }

        static Find read(Wire.In in) {
            return new Find(in.str(100), in.varLong());
        }
    }

    /** The private chat with a player: found or made, then sent as ChannelUp. */
    public record OpenDm(UUID player) implements Packet {
        public static final PacketType<OpenDm> TYPE = new PacketType<>("c_open_dm", Direction.TO_SERVER, OpenDm.class, OpenDm::read);

        public PacketType<OpenDm> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.uuid(player);
        }

        static OpenDm read(Wire.In in) {
            return new OpenDm(in.uuid());
        }
    }

    /** Test servers only: asks the test peer to do something ("dm hello", "react 12 1"...). */
    public record TestPeer(String action) implements Packet {
        public static final PacketType<TestPeer> TYPE = new PacketType<>("c_test_peer", Direction.TO_SERVER, TestPeer.class, TestPeer::read);

        public PacketType<TestPeer> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.str(action, 500);
        }

        static TestPeer read(Wire.In in) {
            return new TestPeer(in.str(500));
        }
    }

    /** A voice message's upload starts (its bytes follow as VoiceChunk). wave: one byte per bar. */
    public record VoiceUp(int upload, long channel, String nonce, int bytes, int durationMs, byte[] wave, long replyTo) implements Packet {
        public static final PacketType<VoiceUp> TYPE = new PacketType<>("c_voice_up", Direction.TO_SERVER, VoiceUp.class, VoiceUp::read);

        public PacketType<VoiceUp> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varInt(upload).varLong(channel).str(nonce, NONCE).varInt(bytes).varInt(durationMs).bytes(wave, 256).varLong(replyTo);
        }

        static VoiceUp read(Wire.In in) {
            return new VoiceUp(in.varInt(), in.varLong(), in.str(NONCE), in.varInt(), in.varInt(), in.bytes(256), in.varLong());
        }
    }

    /** An album of pictures starts (each picture then ImageStart and ImageChunk). */
    public record AlbumUp(int album, long channel, String nonce, int total, String caption, long replyTo) implements Packet {
        public static final PacketType<AlbumUp> TYPE = new PacketType<>("c_album_up", Direction.TO_SERVER, AlbumUp.class, AlbumUp::read);

        public PacketType<AlbumUp> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varInt(album).varLong(channel).str(nonce, NONCE).varInt(total).str(caption, TEXT).varLong(replyTo);
        }

        static AlbumUp read(Wire.In in) {
            return new AlbumUp(in.varInt(), in.varLong(), in.str(NONCE), in.varInt(), in.str(TEXT), in.varLong());
        }
    }

    /** Pictures already on a picture host: "url w h" per line. */
    public record LinksUp(long channel, String nonce, String data, String caption, long replyTo) implements Packet {
        public static final PacketType<LinksUp> TYPE = new PacketType<>("c_links_up", Direction.TO_SERVER, LinksUp.class, LinksUp::read);

        public PacketType<LinksUp> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(channel).str(nonce, NONCE).str(data, 8000).str(caption, TEXT).varLong(replyTo);
        }

        static LinksUp read(Wire.In in) {
            return new LinksUp(in.varLong(), in.str(NONCE), in.str(8000), in.str(TEXT), in.varLong());
        }
    }

    // Server to client

    /**
     * On join: {"me":uuid, "spaces":[..], "channels":[.. with "perms"], "unread":{channel:n}, "reads":{channel:id},
     * "features":{..}, "mute":{..}}.
     */
    public record Snapshot(String json) implements Packet {
        public static final PacketType<Snapshot> TYPE = new PacketType<>("c_snapshot", Direction.TO_CLIENT, Snapshot.class, Snapshot::read);

        public PacketType<Snapshot> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.str(json, JSON);
        }

        static Snapshot read(Wire.In in) {
            return new Snapshot(in.str(JSON));
        }
    }

    /** A new message; nonce is the sender's own ("" for everyone else). */
    public record Msg(String json, String nonce) implements Packet {
        public static final PacketType<Msg> TYPE = new PacketType<>("c_msg", Direction.TO_CLIENT, Msg.class, Msg::read);

        public PacketType<Msg> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.str(json, BODY * 2).str(nonce, NONCE);
        }

        static Msg read(Wire.In in) {
            return new Msg(in.str(BODY * 2), in.str(NONCE));
        }
    }

    /** A page of a channel, oldest first; each message also has its reactions ("rx"), poll counts and the viewer's vote. */
    public record Page(long channel, long before, boolean more, String json) implements Packet {
        public static final PacketType<Page> TYPE = new PacketType<>("c_page", Direction.TO_CLIENT, Page.class, Page::read);

        public PacketType<Page> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(channel).varLong(before).bool(more).str(json, JSON);
        }

        static Page read(Wire.In in) {
            return new Page(in.varLong(), in.varLong(), in.bool(), in.str(JSON));
        }
    }

    public record Edited(long id, long channel, String body, long at) implements Packet {
        public static final PacketType<Edited> TYPE = new PacketType<>("c_edited", Direction.TO_CLIENT, Edited.class, Edited::read);

        public PacketType<Edited> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id).varLong(channel).str(body, BODY).varLong(at);
        }

        static Edited read(Wire.In in) {
            return new Edited(in.varLong(), in.varLong(), in.str(BODY), in.varLong());
        }
    }

    public record Deleted(long id, long channel) implements Packet {
        public static final PacketType<Deleted> TYPE = new PacketType<>("c_deleted", Direction.TO_CLIENT, Deleted.class, Deleted::read);

        public PacketType<Deleted> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id).varLong(channel);
        }

        static Deleted read(Wire.In in) {
            return new Deleted(in.varLong(), in.varLong());
        }
    }

    /** A message's reactions now: "emoji:name,name;emoji:name". */
    public record Reactions(long id, long channel, String data) implements Packet {
        public static final PacketType<Reactions> TYPE = new PacketType<>("c_reactions", Direction.TO_CLIENT, Reactions.class, Reactions::read);

        public PacketType<Reactions> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id).varLong(channel).str(data, 32000);
        }

        static Reactions read(Wire.In in) {
            return new Reactions(in.varLong(), in.varLong(), in.str(32000));
        }
    }

    /** Someone read a channel up to a message (private chats: for the "read" checks). */
    public record ReadMark(long channel, UUID player, long upTo) implements Packet {
        public static final PacketType<ReadMark> TYPE = new PacketType<>("c_read_mark", Direction.TO_CLIENT, ReadMark.class, ReadMark::read);

        public PacketType<ReadMark> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(channel).uuid(player).varLong(upTo);
        }

        static ReadMark read(Wire.In in) {
            return new ReadMark(in.varLong(), in.uuid(), in.varLong());
        }
    }

    public record TypingAt(long channel, UUID who, String name) implements Packet {
        public static final PacketType<TypingAt> TYPE = new PacketType<>("c_typing_at", Direction.TO_CLIENT, TypingAt.class, TypingAt::read);

        public PacketType<TypingAt> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(channel).uuid(who).str(name, 32);
        }

        static TypingAt read(Wire.In in) {
            return new TypingAt(in.varLong(), in.uuid(), in.str(32));
        }
    }

    /** A poll's counts after a vote; bits = the voter's choice. */
    public record PollState(long id, long channel, String counts, UUID voter, int bits) implements Packet {
        public static final PacketType<PollState> TYPE = new PacketType<>("c_poll", Direction.TO_CLIENT, PollState.class, PollState::read);

        public PacketType<PollState> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id).varLong(channel).str(counts, 200).uuid(voter).varInt(bits);
        }

        static PollState read(Wire.In in) {
            return new PollState(in.varLong(), in.varLong(), in.str(200), in.uuid(), in.varInt());
        }
    }

    public record Found(String query, String json) implements Packet {
        public static final PacketType<Found> TYPE = new PacketType<>("c_found", Direction.TO_CLIENT, Found.class, Found::read);

        public PacketType<Found> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.str(query, 100).str(json, JSON);
        }

        static Found read(Wire.In in) {
            return new Found(in.str(100), in.str(JSON));
        }
    }

    /** A channel the player sees, added or changed (with their permissions in it). */
    public record ChannelUp(String json) implements Packet {
        public static final PacketType<ChannelUp> TYPE = new PacketType<>("c_channel", Direction.TO_CLIENT, ChannelUp.class, ChannelUp::read);

        public PacketType<ChannelUp> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.str(json, 8000);
        }

        static ChannelUp read(Wire.In in) {
            return new ChannelUp(in.str(8000));
        }
    }

    public record ChannelGone(long id) implements Packet {
        public static final PacketType<ChannelGone> TYPE = new PacketType<>("c_channel_gone", Direction.TO_CLIENT, ChannelGone.class, ChannelGone::read);

        public PacketType<ChannelGone> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.varLong(id);
        }

        static ChannelGone read(Wire.In in) {
            return new ChannelGone(in.varLong());
        }
    }

    /** The player's mutes now: [{"scope":..,"id":..,"until":..,"reason":..}]. */
    public record Mutes(String json) implements Packet {
        public static final PacketType<Mutes> TYPE = new PacketType<>("c_mutes", Direction.TO_CLIENT, Mutes.class, Mutes::read);

        public PacketType<Mutes> type() {
            return TYPE;
        }

        public void write(Wire.Out out) {
            out.str(json, 8000);
        }

        static Mutes read(Wire.In in) {
            return new Mutes(in.str(8000));
        }
    }

    public static final List<PacketType<?>> ALL = List.of(History.TYPE, Post.TYPE, Edit.TYPE, Delete.TYPE, React.TYPE, Read.TYPE, Typing.TYPE, Vote.TYPE,
            Find.TYPE, OpenDm.TYPE, TestPeer.TYPE, VoiceUp.TYPE, AlbumUp.TYPE, LinksUp.TYPE, Snapshot.TYPE, Msg.TYPE, Page.TYPE, Edited.TYPE, Deleted.TYPE,
            Reactions.TYPE, ReadMark.TYPE, TypingAt.TYPE, PollState.TYPE, Found.TYPE, ChannelUp.TYPE, ChannelGone.TYPE, Mutes.TYPE);
}
