package com.sinuxoff.ingamemessenger.core.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sinuxoff.ingamemessenger.core.text.ChatText;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * All chat data in SQL: messages (public and private), reactions, poll votes, read marks and stored files. Not
 * thread-safe: call it from the storage thread only.
 */
public final class ChatStore {
    public static final int PAGE = 50;
    private static final int SCHEMA = 1;

    /**
     * kind: t text, s sticker, y system, v voice, i pictures, p poll. who and name are null for system messages;
     * pair is null for public messages; replyTo is -1 if not a reply.
     */
    public record Message(int id, long t, String kind, UUID who, String name, String json, String pack, String sticker,
                          int replyTo, String pair, boolean edited) {
    }

    /** A private chat as seen by one of its players. */
    public record DmChat(UUID other, int newest, int unread, int peerReadUpTo) {
    }

    private final Database db;
    private final String messages, reactions, votes, readMarks, dmReads, files, meta, mutes, names;

    public ChatStore(Database db) {
        this.db = db;
        String p = db.prefix;
        messages = p + "messages";
        reactions = p + "reactions";
        votes = p + "votes";
        readMarks = p + "read_marks";
        dmReads = p + "dm_reads";
        files = p + "files";
        meta = p + "meta";
        mutes = p + "mutes";
        names = p + "shown_names";
    }

    private Connection c() throws SQLException {
        return db.connection();
    }

    public void close() {
        db.close();
    }

    /** Creates the tables if they don't exist yet. */
    public void init() throws SQLException {
        String text = db.dialect.text;
        try (Statement s = c().createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS " + messages + " (id BIGINT PRIMARY KEY, ts BIGINT NOT NULL, kind VARCHAR(1) NOT NULL, "
                    + "sender VARCHAR(36), sender_name VARCHAR(64), content " + text + ", pack VARCHAR(64), sticker VARCHAR(64), "
                    + "reply_to BIGINT NOT NULL, pair_id VARCHAR(80), edited BOOLEAN NOT NULL, deleted BOOLEAN NOT NULL, plain " + text + ")");
            s.execute("CREATE TABLE IF NOT EXISTS " + reactions + " (message_id BIGINT NOT NULL, player VARCHAR(36) NOT NULL, "
                    + "player_name VARCHAR(64) NOT NULL, emoji VARCHAR(130) NOT NULL, ts BIGINT NOT NULL, PRIMARY KEY (message_id, player))");
            s.execute("CREATE TABLE IF NOT EXISTS " + votes + " (message_id BIGINT NOT NULL, player VARCHAR(36) NOT NULL, "
                    + "bits INT NOT NULL, PRIMARY KEY (message_id, player))");
            s.execute("CREATE TABLE IF NOT EXISTS " + readMarks + " (player VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL, "
                    + "up_to BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + dmReads + " (reader VARCHAR(36) NOT NULL, other_player VARCHAR(36) NOT NULL, "
                    + "up_to BIGINT NOT NULL, PRIMARY KEY (reader, other_player))");
            s.execute("CREATE TABLE IF NOT EXISTS " + files + " (file_id VARCHAR(64) PRIMARY KEY, category VARCHAR(16) NOT NULL, ts BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + meta + " (meta_key VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(255) NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + mutes + " (player VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL, "
                    + "chat_until BIGINT NOT NULL, dm_until BIGINT NOT NULL, reason VARCHAR(255) NOT NULL, by_name VARCHAR(64) NOT NULL, "
                    + "ts BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + names + " (player VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL, "
                    + "shown " + text + ")");
        }
        index(messages + "_pair", messages, "pair_id");
        index(files + "_cat", files, "category, ts");
        if (getMeta("schema") == null) {
            setMeta("schema", String.valueOf(SCHEMA));
        }
    }

    /** MySQL has no CREATE INDEX IF NOT EXISTS, so there an existing index just fails. */
    private void index(String name, String table, String columns) {
        String ifNew = db.dialect == Dialect.MYSQL ? "" : "IF NOT EXISTS ";
        try (Statement s = c().createStatement()) {
            s.execute("CREATE INDEX " + ifNew + name + " ON " + table + " (" + columns + ")");
        } catch (SQLException ignored) {
        }
    }

    private String getMeta(String key) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT meta_value FROM " + meta + " WHERE meta_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void setMeta(String key, String value) throws SQLException {
        upsert("UPDATE " + meta + " SET meta_value = ? WHERE meta_key = ?", "INSERT INTO " + meta + " (meta_value, meta_key) VALUES (?, ?)",
                value, key);
    }

    /** Portable upsert: the update runs first, the insert only if nothing was updated. Both take the same parameters. */
    private void upsert(String update, String insert, Object... params) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement(update)) {
            bind(ps, params);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement ps = c().prepareStatement(insert)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            if (v == null) {
                ps.setNull(i + 1, Types.VARCHAR);
            } else if (v instanceof Integer n) {
                ps.setLong(i + 1, n);
            } else if (v instanceof Long n) {
                ps.setLong(i + 1, n);
            } else if (v instanceof Boolean b) {
                ps.setBoolean(i + 1, b);
            } else {
                ps.setString(i + 1, v.toString());
            }
        }
    }

    private int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    // How the server shows names

    /** Remembers a player's shown name (component JSON, null = just the name), for history pages after they left. */
    public void setShownName(UUID who, String name, String shown) throws SQLException {
        update("DELETE FROM " + names + " WHERE player = ?", who.toString());
        if (shown != null) {
            update("INSERT INTO " + names + " (player, player_name, shown) VALUES (?, ?, ?)", who.toString(), name, shown);
        }
    }

    /** "uuid \t name \t json" lines for those of these players whose shown name is known. */
    public String shownNames(Collection<UUID> who) throws SQLException {
        StringBuilder b = new StringBuilder();
        List<UUID> ids = new ArrayList<>(who);
        for (int from = 0; from < ids.size(); from += 200) {
            List<UUID> part = ids.subList(from, Math.min(ids.size(), from + 200));
            try (PreparedStatement ps = c().prepareStatement("SELECT player, player_name, shown FROM " + names + " WHERE player IN ("
                    + marks(part.size()) + ")")) {
                bind(ps, part.stream().map(UUID::toString).toArray());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        b.append(b.length() > 0 ? "\n" : "").append(rs.getString(1)).append('\t').append(rs.getString(2)).append('\t')
                                .append(rs.getString(3));
                    }
                }
            }
        }
        return b.toString();
    }

    // Mutes

    /** A player's mute: until when in the public chat and in private chats (0 = not muted there). */
    public record Mute(UUID who, String name, long chatUntil, long dmUntil, String reason, String by, long t) {
    }

    public List<Mute> mutes() throws SQLException {
        List<Mute> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT player, player_name, chat_until, dm_until, reason, by_name, ts FROM " + mutes);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Mute(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getString(5),
                        rs.getString(6), rs.getLong(7)));
            }
        }
        return out;
    }

    public void setMute(Mute m) throws SQLException {
        update("DELETE FROM " + mutes + " WHERE player = ?", m.who().toString());
        if (m.chatUntil() > 0 || m.dmUntil() > 0) {
            update("INSERT INTO " + mutes + " (player, player_name, chat_until, dm_until, reason, by_name, ts) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    m.who().toString(), m.name(), m.chatUntil(), m.dmUntil(), m.reason(), m.by(), m.t());
        }
    }

    // Messages

    /** Highest id ever used, deleted messages included, so ids are never reused. */
    public int maxId() throws SQLException {
        try (Statement s = c().createStatement(); ResultSet rs = s.executeQuery("SELECT MAX(id) FROM " + messages)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public void insert(Message m) throws SQLException {
        update("INSERT INTO " + messages + " (id, ts, kind, sender, sender_name, content, pack, sticker, reply_to, pair_id, edited, deleted, plain) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                m.id(), m.t(), m.kind(), m.who() == null ? null : m.who().toString(), m.name(), m.json(), m.pack(), m.sticker(),
                replyTarget(m), m.pair(), false, false, plain(m.kind(), m.json()));
    }

    /** Text messages keep their reply target inside the text ("↩n "). */
    private static int replyTarget(Message m) {
        if (m.replyTo() < 0 && "t".equals(m.kind()) && m.json() != null) {
            return ChatText.replyTargetInJson(m.json());
        }
        return m.replyTo();
    }

    private static final String COLUMNS = "id, ts, kind, sender, sender_name, content, pack, sticker, reply_to, pair_id, edited";

    private static Message read(ResultSet rs) throws SQLException {
        String who = rs.getString(4);
        return new Message(rs.getInt(1), rs.getLong(2), rs.getString(3), who == null ? null : UUID.fromString(who), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getInt(9), rs.getString(10), rs.getBoolean(11));
    }

    /** Null if there's no such message or it was deleted. */
    public Message get(int id) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT " + COLUMNS + " FROM " + messages + " WHERE id = ? AND deleted = FALSE")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    public void edit(int id, String json) throws SQLException {
        update("UPDATE " + messages + " SET content = ?, plain = ?, edited = TRUE WHERE id = ?", json, plain("t", json), id);
    }

    /** Keeps the id (replies show it as deleted) but drops the content, reactions and votes. */
    public void delete(int id) throws SQLException {
        update("UPDATE " + messages + " SET deleted = TRUE, content = NULL, plain = NULL WHERE id = ?", id);
        update("DELETE FROM " + reactions + " WHERE message_id = ?", id);
        update("DELETE FROM " + votes + " WHERE message_id = ?", id);
    }

    // Reactions

    /** The player's reaction on a message, or null. */
    public String reaction(int id, UUID who) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT emoji FROM " + reactions + " WHERE message_id = ? AND player = ?")) {
            ps.setLong(1, id);
            ps.setString(2, who.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** emoji "" removes the reaction. */
    public void setReaction(int id, UUID who, String name, String emoji, long now) throws SQLException {
        update("DELETE FROM " + reactions + " WHERE message_id = ? AND player = ?", id, who.toString());
        if (!emoji.isEmpty()) {
            update("INSERT INTO " + reactions + " (message_id, player, player_name, emoji, ts) VALUES (?, ?, ?, ?, ?)",
                    id, who.toString(), name, emoji, now);
        }
    }

    /** "emoji:name,name;emoji:name" in the order each emoji first appeared, "" if none. */
    public String reactions(int id) throws SQLException {
        return reactionsOf(List.of(id)).getOrDefault(id, "");
    }

    private Map<Integer, String> reactionsOf(List<Integer> ids) throws SQLException {
        Map<Integer, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Integer, Map<String, StringJoiner>> by = new HashMap<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT message_id, emoji, player_name FROM " + reactions
                + " WHERE message_id IN (" + marks(ids.size()) + ") ORDER BY ts, player")) {
            bind(ps, ids.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    by.computeIfAbsent(rs.getInt(1), k -> new LinkedHashMap<>())
                            .computeIfAbsent(rs.getString(2), k -> new StringJoiner(",")).add(rs.getString(3));
                }
            }
        }
        by.forEach((id, emojis) -> {
            StringJoiner all = new StringJoiner(";");
            emojis.forEach((e, names) -> all.add(e + ":" + names));
            out.put(id, all.toString());
        });
        return out;
    }

    private static String marks(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    // Polls

    /** The player's chosen options as bits, 0 if none. */
    public int vote(int id, UUID who) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT bits FROM " + votes + " WHERE message_id = ? AND player = ?")) {
            ps.setLong(1, id);
            ps.setString(2, who.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** bits 0 removes the vote. */
    public void setVote(int id, UUID who, int bits) throws SQLException {
        update("DELETE FROM " + votes + " WHERE message_id = ? AND player = ?", id, who.toString());
        if (bits != 0) {
            update("INSERT INTO " + votes + " (message_id, player, bits) VALUES (?, ?, ?)", id, who.toString(), bits);
        }
    }

    /** "count,count,...;voters" for a poll with this many options. */
    public String pollCounts(int id, int options) throws SQLException {
        int[] c = new int[options];
        int voters = 0;
        try (PreparedStatement ps = c().prepareStatement("SELECT bits FROM " + votes + " WHERE message_id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int bits = rs.getInt(1);
                    voters++;
                    for (int i = 0; i < options; i++) {
                        if ((bits >> i & 1) != 0) {
                            c[i]++;
                        }
                    }
                }
            }
        }
        StringJoiner j = new StringJoiner(",");
        for (int v : c) {
            j.add(String.valueOf(v));
        }
        return j + ";" + voters;
    }

    public static int pollOptions(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("o").size();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static boolean pollMulti(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return o.has("m") && o.get("m").getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // Read marks

    /** Public chat read marks, one "uuid name upTo" per line. */
    public String readMarks() throws SQLException {
        StringJoiner out = new StringJoiner("\n");
        try (Statement s = c().createStatement(); ResultSet rs = s.executeQuery("SELECT player, player_name, up_to FROM " + readMarks)) {
            while (rs.next()) {
                out.add(rs.getString(1) + " " + rs.getString(2) + " " + rs.getInt(3));
            }
        }
        return out.toString();
    }

    /** Only moves forward; true if it did. */
    public boolean markRead(UUID who, String name, int upTo) throws SQLException {
        int changed = update("UPDATE " + readMarks + " SET up_to = ?, player_name = ? WHERE player = ? AND up_to < ?",
                upTo, name, who.toString(), upTo);
        if (changed > 0) {
            return true;
        }
        try (PreparedStatement ps = c().prepareStatement("SELECT 1 FROM " + readMarks + " WHERE player = ?")) {
            ps.setString(1, who.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return false;
                }
            }
        }
        update("INSERT INTO " + readMarks + " (player, player_name, up_to) VALUES (?, ?, ?)", who.toString(), name, upTo);
        return true;
    }

    // Private chats

    public static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public static UUID[] both(String pair) {
        String[] two = pair.split("\\|");
        return new UUID[]{UUID.fromString(two[0]), UUID.fromString(two[1])};
    }

    public int dmReadUpTo(UUID reader, UUID other) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT up_to FROM " + dmReads + " WHERE reader = ? AND other_player = ?")) {
            ps.setString(1, reader.toString());
            ps.setString(2, other.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Only moves forward; true if it did. */
    public boolean markDmRead(UUID reader, UUID other, int upTo) throws SQLException {
        if (upTo <= dmReadUpTo(reader, other)) {
            return false;
        }
        upsert("UPDATE " + dmReads + " SET up_to = ? WHERE reader = ? AND other_player = ?",
                "INSERT INTO " + dmReads + " (up_to, reader, other_player) VALUES (?, ?, ?)", upTo, reader.toString(), other.toString());
        return true;
    }

    /** Newest message id in the pair's chat, 0 if none. */
    public int newestInPair(String pair) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT MAX(id) FROM " + messages + " WHERE pair_id = ? AND deleted = FALSE")) {
            ps.setString(1, pair);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** The player's private chats, newest first. */
    public List<DmChat> dmChats(UUID me) throws SQLException {
        List<DmChat> out = new ArrayList<>();
        Map<String, Integer> newest = new LinkedHashMap<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT pair_id, MAX(id) AS newest FROM " + messages
                + " WHERE pair_id LIKE ? AND deleted = FALSE GROUP BY pair_id ORDER BY newest DESC")) {
            ps.setString(1, "%" + me + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    newest.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        for (var e : newest.entrySet()) {
            UUID[] two = both(e.getKey());
            UUID other = two[0].equals(me) ? two[1] : two[0];
            int seen = dmReadUpTo(me, other);
            int unread;
            try (PreparedStatement ps = c().prepareStatement("SELECT COUNT(*) FROM " + messages
                    + " WHERE pair_id = ? AND deleted = FALSE AND sender = ? AND id > ?")) {
                bind(ps, e.getKey(), other.toString(), seen);
                try (ResultSet rs = ps.executeQuery()) {
                    unread = rs.next() ? rs.getInt(1) : 0;
                }
            }
            out.add(new DmChat(other, e.getValue(), unread, dmReadUpTo(other, me)));
        }
        return out;
    }

    // Pages and search

    /** JSON array of messages, oldest first, and whether older ones exist. */
    public record Page(boolean more, String json) {
    }

    /**
     * A page of the public chat (pair null) or a private chat, before the given id (0 = newest), as the viewer sees it:
     * reactions, edit marks, deleted reply targets, poll counts and the viewer's vote.
     */
    public Page page(int before, UUID viewer, String pair) throws SQLException {
        List<Message> rows = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM " + messages + " WHERE deleted = FALSE AND "
                + (pair == null ? "pair_id IS NULL" : "pair_id = ?") + (before > 0 ? " AND id < ?" : "") + " ORDER BY id DESC LIMIT " + (PAGE + 1);
        try (PreparedStatement ps = c().prepareStatement(sql)) {
            List<Object> params = new ArrayList<>();
            if (pair != null) {
                params.add(pair);
            }
            if (before > 0) {
                params.add(before);
            }
            bind(ps, params.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(read(rs));
                }
            }
        }
        boolean more = rows.size() > PAGE;
        if (more) {
            rows.remove(rows.size() - 1);
        }
        java.util.Collections.reverse(rows);
        List<Integer> ids = new ArrayList<>(), targets = new ArrayList<>();
        for (Message m : rows) {
            ids.add(m.id());
            if (m.replyTo() >= 0) {
                targets.add(m.replyTo());
            }
        }
        Map<Integer, String> reacts = reactionsOf(ids);
        Set<Integer> deletedTargets = deleted(targets);
        JsonArray arr = new JsonArray();
        for (Message m : rows) {
            JsonObject o = json(m);
            if (m.edited()) {
                o.addProperty("ed", 1);
            }
            if (m.replyTo() >= 0 && deletedTargets.contains(m.replyTo())) {
                o.addProperty("rd", 1);
            }
            if ("p".equals(m.kind()) && m.json() != null) {
                o.addProperty("pc", pollCounts(m.id(), pollOptions(m.json())));
                o.addProperty("pm", vote(m.id(), viewer));
            }
            String r = reacts.get(m.id());
            if (r != null) {
                o.addProperty("r", r);
            }
            arr.add(o);
        }
        return new Page(more, arr.toString());
    }

    private Set<Integer> deleted(List<Integer> ids) throws SQLException {
        Set<Integer> out = new HashSet<>();
        if (ids.isEmpty()) {
            return out;
        }
        try (PreparedStatement ps = c().prepareStatement("SELECT id FROM " + messages + " WHERE deleted = TRUE AND id IN (" + marks(ids.size()) + ")")) {
            bind(ps, ids.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
        }
        return out;
    }

    /** The JSON fields the client reads for a message. */
    private static JsonObject json(Message m) {
        JsonObject o = new JsonObject();
        o.addProperty("i", m.id());
        o.addProperty("t", m.t());
        o.addProperty("k", m.kind());
        if (m.who() != null) {
            o.addProperty("u", m.who().toString());
        }
        if (m.name() != null) {
            o.addProperty("n", m.name());
        }
        if (m.json() != null) {
            o.addProperty("c", m.json());
        }
        if (m.pack() != null) {
            o.addProperty("p", m.pack());
            o.addProperty("s", m.sticker());
        }
        if (m.replyTo() >= 0 && !"t".equals(m.kind())) {
            o.addProperty("rt", m.replyTo());
        }
        return o;
    }

    /** Public messages containing the query, newest first. One "id \t time \t name \t excerpt" per line. */
    public String search(String query, int limit) throws SQLException {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return "";
        }
        // '!' as the escape character: MySQL treats a backslash in a string literal specially
        String like = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        StringJoiner out = new StringJoiner("\n");
        try (PreparedStatement ps = c().prepareStatement("SELECT id, ts, sender_name, plain FROM " + messages
                + " WHERE deleted = FALSE AND pair_id IS NULL AND LOWER(plain) LIKE ? ESCAPE '!' ORDER BY id DESC LIMIT " + limit)) {
            ps.setString(1, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString(4);
                    int at = Math.max(0, text.toLowerCase(Locale.ROOT).indexOf(q));
                    int from = Math.max(0, at - 20), to = Math.min(text.length(), at + q.length() + 50);
                    String bit = (from > 0 ? "…" : "") + text.substring(from, to) + (to < text.length() ? "…" : "");
                    String name = rs.getString(3);
                    out.add(rs.getInt(1) + "\t" + rs.getLong(2) + "\t" + (name == null ? "" : name) + "\t"
                            + bit.replace('\t', ' ').replace('\n', ' '));
                }
            }
        }
        return out.toString();
    }

    /** Searchable text: the words of a text message without the reply prefix, or a picture caption. */
    static String plain(String kind, String json) {
        if (json == null) {
            return null;
        }
        try {
            if ("t".equals(kind)) {
                String t = plain(JsonParser.parseString(json));
                return t.substring(ChatText.replyMarkLength(t));
            }
            if ("i".equals(kind)) {
                JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                return o.has("text") ? o.get("text").getAsString() : null;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** Plain text of a text component JSON. */
    static String plain(JsonElement el) {
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        StringBuilder b = new StringBuilder();
        if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                b.append(plain(x));
            }
        } else if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("text")) {
                b.append(o.get("text").getAsString());
            }
            if (o.has("extra")) {
                b.append(plain(o.get("extra")));
            }
        }
        return b.toString();
    }

    /** Picture refs of a picture message: one "ref w h" line per picture in its data. */
    public static List<String> imageRefs(Message m) {
        List<String> out = new ArrayList<>();
        if (m == null || !"i".equals(m.kind()) || m.json() == null) {
            return out;
        }
        try {
            for (String line : JsonParser.parseString(m.json()).getAsJsonObject().get("data").getAsString().split("\n")) {
                out.add(line.split(" ")[0]);
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    // Stored files

    public void addFile(String fileId, String category, long now) throws SQLException {
        update("INSERT INTO " + files + " (file_id, category, ts) VALUES (?, ?, ?)", fileId, category, now);
    }

    /** Files of a category stored before the given time. */
    public List<String> filesBefore(String category, long before) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT file_id FROM " + files + " WHERE category = ? AND ts < ?")) {
            bind(ps, category, before);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    public void removeFile(String fileId) throws SQLException {
        update("DELETE FROM " + files + " WHERE file_id = ?", fileId);
    }
}
