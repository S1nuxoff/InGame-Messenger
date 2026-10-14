package com.sinuxoff.ingamemessenger.core.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * The messenger's database: spaces (the server and players' communities), their channels, members, roles and
 * permissions, private chats, messages with reactions and poll votes, read marks, mutes and invites. Used from the
 * storage thread only.
 */
public final class Store {
    /** The schema; an older database (the first test versions) is dropped and made anew. */
    static final int SCHEMA = 2;
    /** The server's own space. */
    public static final long MAIN = 1;
    public static final int PAGE = 50;

    public record Space(long id, String kind, String name, String icon, UUID owner, boolean isPublic, long created) {
    }

    /** spaceId 0 = a private chat. */
    public record Channel(long id, long spaceId, String kind, String name, String topic, int pos, boolean gameChat, long created) {
        public boolean dm() {
            return spaceId == 0;
        }
    }

    public record Role(long id, long spaceId, String name, int color, int pos, long perms) {
    }

    /** target "r:<role id>" or "p:<uuid>". */
    public record Override(long channelId, String target, long allow, long deny) {
    }

    /** sender null = a line of the server. replyTo 0 = none. */
    public record Message(long id, long channelId, UUID sender, String name, String kind, String body, long replyTo, long created,
                          long edited, boolean deleted) {
    }

    /** scope "space" (scopeId = space id), "channel" (channel id) or "dm" (every private chat, scopeId 0). */
    public record Mute(UUID player, String name, String scope, long scopeId, long until, String reason, String by, long created) {
    }

    public record Invite(String code, long spaceId, UUID creator, int uses, int maxUses, long expires) {
    }

    private final Database db;
    private final String p;

    public Store(Database db) {
        this.db = db;
        this.p = db.prefix;
    }

    private Connection c() throws SQLException {
        return db.connection();
    }

    public void close() {
        db.close();
    }

    // Schema

    private static final String[] OLD = {"messages", "reactions", "votes", "read_marks", "dm_reads", "files", "meta", "mutes", "shown_names"};
    private static final String[] TABLES = {"meta", "spaces", "channels", "channel_members", "players", "members", "roles", "member_roles",
            "channel_perms", "invites", "messages", "reactions", "poll_votes", "read_states", "mutes", "files"};

    public void init() throws SQLException {
        Integer schema = schema();
        if (schema == null || schema < SCHEMA) {
            try (Statement s = c().createStatement()) {
                for (String t : OLD) {
                    s.execute("DROP TABLE IF EXISTS " + p + t);
                }
            }
        }
        String text = db.dialect.text;
        try (Statement s = c().createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "meta (meta_key VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(255) NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "spaces (id BIGINT PRIMARY KEY, kind VARCHAR(16) NOT NULL, name VARCHAR(64) NOT NULL, "
                    + "icon VARCHAR(128), owner VARCHAR(36), is_public BOOLEAN NOT NULL, created BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "channels (id BIGINT PRIMARY KEY, space_id BIGINT NOT NULL, kind VARCHAR(8) NOT NULL, "
                    + "name VARCHAR(64) NOT NULL, topic VARCHAR(255) NOT NULL, pos INT NOT NULL, game_chat BOOLEAN NOT NULL, "
                    + "discord VARCHAR(32), created BIGINT NOT NULL, deleted BOOLEAN NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "channel_members (channel_id BIGINT NOT NULL, player VARCHAR(36) NOT NULL, "
                    + "PRIMARY KEY (channel_id, player))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "players (player VARCHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL, shown " + text
                    + ", last_seen BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "members (space_id BIGINT NOT NULL, player VARCHAR(36) NOT NULL, joined BIGINT NOT NULL, "
                    + "PRIMARY KEY (space_id, player))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "roles (id BIGINT PRIMARY KEY, space_id BIGINT NOT NULL, name VARCHAR(64) NOT NULL, "
                    + "color INT NOT NULL, pos INT NOT NULL, perms BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "member_roles (space_id BIGINT NOT NULL, player VARCHAR(36) NOT NULL, role_id BIGINT NOT NULL, "
                    + "PRIMARY KEY (player, role_id))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "channel_perms (channel_id BIGINT NOT NULL, target VARCHAR(40) NOT NULL, "
                    + "allow_bits BIGINT NOT NULL, deny_bits BIGINT NOT NULL, PRIMARY KEY (channel_id, target))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "invites (code VARCHAR(16) PRIMARY KEY, space_id BIGINT NOT NULL, creator VARCHAR(36) NOT NULL, "
                    + "uses INT NOT NULL, max_uses INT NOT NULL, expires BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "messages (id BIGINT PRIMARY KEY, channel_id BIGINT NOT NULL, sender VARCHAR(36), "
                    + "sender_name VARCHAR(32), kind VARCHAR(8) NOT NULL, body " + text + " NOT NULL, reply_to BIGINT NOT NULL, "
                    + "created BIGINT NOT NULL, edited BIGINT NOT NULL, deleted BOOLEAN NOT NULL, plain " + text + ", discord VARCHAR(32))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "reactions (message_id BIGINT NOT NULL, player VARCHAR(36) NOT NULL, "
                    + "player_name VARCHAR(32) NOT NULL, emoji VARCHAR(130) NOT NULL, created BIGINT NOT NULL, PRIMARY KEY (message_id, player, emoji))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "poll_votes (message_id BIGINT NOT NULL, player VARCHAR(36) NOT NULL, bits INT NOT NULL, "
                    + "PRIMARY KEY (message_id, player))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "read_states (player VARCHAR(36) NOT NULL, channel_id BIGINT NOT NULL, "
                    + "last_read BIGINT NOT NULL, PRIMARY KEY (player, channel_id))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "mutes (player VARCHAR(36) NOT NULL, player_name VARCHAR(32) NOT NULL, "
                    + "scope VARCHAR(8) NOT NULL, scope_id BIGINT NOT NULL, until_ms BIGINT NOT NULL, reason VARCHAR(255) NOT NULL, "
                    + "by_name VARCHAR(64) NOT NULL, created BIGINT NOT NULL, PRIMARY KEY (player, scope, scope_id))");
            s.execute("CREATE TABLE IF NOT EXISTS " + p + "files (file_key VARCHAR(64) PRIMARY KEY, category VARCHAR(16) NOT NULL, "
                    + "created BIGINT NOT NULL)");
        }
        index("msg_chan", "messages", "channel_id, id");
        index("chan_space", "channels", "space_id");
        index("cm_player", "channel_members", "player");
        index("files_cat", "files", "category, created");
        update("DELETE FROM " + p + "meta WHERE meta_key = ?", "schema");
        update("INSERT INTO " + p + "meta (meta_key, meta_value) VALUES (?, ?)", "schema", String.valueOf(SCHEMA));
    }

    private Integer schema() {
        try (PreparedStatement ps = c().prepareStatement("SELECT meta_value FROM " + p + "meta WHERE meta_key = 'schema'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Integer.valueOf(rs.getString(1)) : null;
        } catch (SQLException | NumberFormatException e) {
            return null; // no meta table yet
        }
    }

    /** MySQL has no CREATE INDEX IF NOT EXISTS, so there an existing index just fails. */
    private void index(String name, String table, String columns) {
        String ifNew = db.dialect == Dialect.MYSQL ? "" : "IF NOT EXISTS ";
        try (Statement s = c().createStatement()) {
            s.execute("CREATE INDEX " + ifNew + p + name + " ON " + p + table + " (" + columns + ")");
        } catch (SQLException ignored) {
        }
    }

    /** The highest id in a table, 0 if empty. */
    public long maxId(String table) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT MAX(id) FROM " + p + table); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // Spaces

    public void addSpace(Space s) throws SQLException {
        update("INSERT INTO " + p + "spaces (id, kind, name, icon, owner, is_public, created) VALUES (?, ?, ?, ?, ?, ?, ?)",
                s.id(), s.kind(), s.name(), s.icon(), s.owner() == null ? null : s.owner().toString(), s.isPublic(), s.created());
    }

    public Space space(long id) throws SQLException {
        List<Space> l = spaces("WHERE id = ?", id);
        return l.isEmpty() ? null : l.get(0);
    }

    public List<Space> allSpaces() throws SQLException {
        return spaces("ORDER BY id");
    }

    private List<Space> spaces(String where, Object... params) throws SQLException {
        List<Space> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT id, kind, name, icon, owner, is_public, created FROM " + p + "spaces " + where)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Space(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), uuid(rs.getString(5)), rs.getBoolean(6),
                            rs.getLong(7)));
                }
            }
        }
        return out;
    }

    // Channels

    public void addChannel(Channel ch) throws SQLException {
        update("INSERT INTO " + p + "channels (id, space_id, kind, name, topic, pos, game_chat, discord, created, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ch.id(), ch.spaceId(), ch.kind(), ch.name(), ch.topic(), ch.pos(), ch.gameChat(),
                null, ch.created(), false);
    }

    /** Every channel that isn't deleted (private chats included). */
    public List<Channel> allChannels() throws SQLException {
        List<Channel> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT id, space_id, kind, name, topic, pos, game_chat, created FROM " + p
                + "channels WHERE deleted = FALSE ORDER BY space_id, pos, id"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Channel(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6),
                        rs.getBoolean(7), rs.getLong(8)));
            }
        }
        return out;
    }

    public void addChannelMember(long channel, UUID player) throws SQLException {
        update("INSERT INTO " + p + "channel_members (channel_id, player) VALUES (?, ?)", channel, player.toString());
    }

    /** The players of every private chat: channel id -> players. */
    public Map<Long, List<UUID>> channelMembers() throws SQLException {
        Map<Long, List<UUID>> out = new HashMap<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT channel_id, player FROM " + p + "channel_members"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(UUID.fromString(rs.getString(2)));
            }
        }
        return out;
    }

    // Players and members

    public void seePlayer(UUID who, String name, String shown, long now) throws SQLException {
        update("DELETE FROM " + p + "players WHERE player = ?", who.toString());
        update("INSERT INTO " + p + "players (player, name, shown, last_seen) VALUES (?, ?, ?, ?)", who.toString(), name, shown, now);
    }

    /** "uuid \t name \t shown JSON" lines for those of these players whose shown name is known. */
    public String shownNames(Collection<UUID> who) throws SQLException {
        StringJoiner out = new StringJoiner("\n");
        List<UUID> ids = new ArrayList<>(who);
        for (int from = 0; from < ids.size(); from += 200) {
            List<UUID> part = ids.subList(from, Math.min(ids.size(), from + 200));
            try (PreparedStatement ps = c().prepareStatement("SELECT player, name, shown FROM " + p + "players WHERE shown IS NOT NULL AND player IN ("
                    + marks(part.size()) + ")")) {
                bind(ps, part.stream().map(UUID::toString).toArray());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1) + "\t" + rs.getString(2) + "\t" + rs.getString(3));
                    }
                }
            }
        }
        return out.toString();
    }

    public UUID playerNamed(String name) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT player FROM " + p + "players WHERE LOWER(name) = ?")) {
            bind(ps, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        }
    }

    public void addMember(long space, UUID player, long now) throws SQLException {
        update("DELETE FROM " + p + "members WHERE space_id = ? AND player = ?", space, player.toString());
        update("INSERT INTO " + p + "members (space_id, player, joined) VALUES (?, ?, ?)", space, player.toString(), now);
    }

    public void removeMember(long space, UUID player) throws SQLException {
        update("DELETE FROM " + p + "members WHERE space_id = ? AND player = ?", space, player.toString());
        update("DELETE FROM " + p + "member_roles WHERE space_id = ? AND player = ?", space, player.toString());
    }

    /** space id -> its members. */
    public Map<Long, List<UUID>> members() throws SQLException {
        Map<Long, List<UUID>> out = new HashMap<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT space_id, player FROM " + p + "members"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(UUID.fromString(rs.getString(2)));
            }
        }
        return out;
    }

    // Roles and permissions

    public void addRole(Role r) throws SQLException {
        update("INSERT INTO " + p + "roles (id, space_id, name, color, pos, perms) VALUES (?, ?, ?, ?, ?, ?)", r.id(), r.spaceId(), r.name(), r.color(),
                r.pos(), r.perms());
    }

    public void setRolePerms(long role, long perms) throws SQLException {
        update("UPDATE " + p + "roles SET perms = ? WHERE id = ?", perms, role);
    }

    public List<Role> roles() throws SQLException {
        List<Role> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT id, space_id, name, color, pos, perms FROM " + p + "roles ORDER BY space_id, pos");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Role(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getLong(6)));
            }
        }
        return out;
    }

    public void giveRole(long space, UUID player, long role) throws SQLException {
        update("DELETE FROM " + p + "member_roles WHERE player = ? AND role_id = ?", player.toString(), role);
        update("INSERT INTO " + p + "member_roles (space_id, player, role_id) VALUES (?, ?, ?)", space, player.toString(), role);
    }

    public void takeRole(UUID player, long role) throws SQLException {
        update("DELETE FROM " + p + "member_roles WHERE player = ? AND role_id = ?", player.toString(), role);
    }

    /** player -> their role ids (every space). */
    public Map<UUID, List<Long>> memberRoles() throws SQLException {
        Map<UUID, List<Long>> out = new HashMap<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT player, role_id FROM " + p + "member_roles"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(UUID.fromString(rs.getString(1)), k -> new ArrayList<>()).add(rs.getLong(2));
            }
        }
        return out;
    }

    public void setOverride(Override o) throws SQLException {
        update("DELETE FROM " + p + "channel_perms WHERE channel_id = ? AND target = ?", o.channelId(), o.target());
        if (o.allow() != 0 || o.deny() != 0) {
            update("INSERT INTO " + p + "channel_perms (channel_id, target, allow_bits, deny_bits) VALUES (?, ?, ?, ?)", o.channelId(), o.target(),
                    o.allow(), o.deny());
        }
    }

    public List<Override> overrides() throws SQLException {
        List<Override> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT channel_id, target, allow_bits, deny_bits FROM " + p + "channel_perms");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Override(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4)));
            }
        }
        return out;
    }

    // Messages

    public void addMessage(Message m, String plain) throws SQLException {
        update("INSERT INTO " + p + "messages (id, channel_id, sender, sender_name, kind, body, reply_to, created, edited, deleted, plain) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", m.id(), m.channelId(), m.sender() == null ? null : m.sender().toString(), m.name(),
                m.kind(), m.body(), m.replyTo(), m.created(), m.edited(), m.deleted(), plain);
    }

    public Message message(long id) throws SQLException {
        List<Message> l = messages("WHERE id = ?", id);
        return l.isEmpty() ? null : l.get(0);
    }

    public void editMessage(long id, String body, String plain, long now) throws SQLException {
        update("UPDATE " + p + "messages SET body = ?, plain = ?, edited = ? WHERE id = ?", body, plain, now, id);
    }

    public void deleteMessage(long id) throws SQLException {
        update("UPDATE " + p + "messages SET deleted = TRUE, body = '{}', plain = NULL WHERE id = ?", id);
        update("DELETE FROM " + p + "reactions WHERE message_id = ?", id);
    }

    private static final String MSG_COLS = "id, channel_id, sender, sender_name, kind, body, reply_to, created, edited, deleted";

    private List<Message> messages(String where, Object... params) throws SQLException {
        List<Message> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT " + MSG_COLS + " FROM " + p + "messages " + where)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Message(rs.getLong(1), rs.getLong(2), uuid(rs.getString(3)), rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getBoolean(10)));
                }
            }
        }
        return out;
    }

    /** A page of a channel: up to PAGE messages before the given id (0 = newest), oldest first, and whether there are more. */
    public record Page(List<Message> messages, boolean more) {
    }

    public Page page(long channel, long before) throws SQLException {
        List<Message> rows = messages("WHERE channel_id = ? AND deleted = FALSE" + (before > 0 ? " AND id < ?" : "") + " ORDER BY id DESC LIMIT "
                + (PAGE + 1), before > 0 ? new Object[]{channel, before} : new Object[]{channel});
        boolean more = rows.size() > PAGE;
        if (more) {
            rows.remove(rows.size() - 1);
        }
        java.util.Collections.reverse(rows);
        return new Page(rows, more);
    }

    /** Of these ids, the deleted ones (replies show "deleted" for them). */
    public List<Long> deletedOf(Collection<Long> ids) throws SQLException {
        List<Long> out = new ArrayList<>();
        if (ids.isEmpty()) {
            return out;
        }
        try (PreparedStatement ps = c().prepareStatement("SELECT id FROM " + p + "messages WHERE deleted = TRUE AND id IN (" + marks(ids.size()) + ")")) {
            bind(ps, ids.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getLong(1));
                }
            }
        }
        return out;
    }

    /** Messages of these channels containing the query, newest first. */
    public List<Message> search(Collection<Long> channels, String query, int limit) throws SQLException {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.length() < 2 || channels.isEmpty()) {
            return List.of();
        }
        // '!' escapes: MySQL treats a backslash in a string literal specially
        String like = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        List<Object> params = new ArrayList<>(channels);
        params.add(like);
        return messages("WHERE deleted = FALSE AND channel_id IN (" + marks(channels.size()) + ") AND LOWER(plain) LIKE ? ESCAPE '!' "
                + "ORDER BY id DESC LIMIT " + limit, params.toArray());
    }

    // Reactions and polls

    /** Adds the reaction, or takes it back if the player had it. True if added. */
    public boolean toggleReaction(long message, UUID who, String name, String emoji, long now) throws SQLException {
        if (update("DELETE FROM " + p + "reactions WHERE message_id = ? AND player = ? AND emoji = ?", message, who.toString(), emoji) > 0) {
            return false;
        }
        update("INSERT INTO " + p + "reactions (message_id, player, player_name, emoji, created) VALUES (?, ?, ?, ?, ?)", message, who.toString(), name,
                emoji, now);
        return true;
    }

    /** message id -> "emoji:name,name;emoji:name" in the order each emoji first came. */
    public Map<Long, String> reactions(Collection<Long> ids) throws SQLException {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Long, Map<String, StringJoiner>> by = new LinkedHashMap<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT message_id, emoji, player_name FROM " + p + "reactions WHERE message_id IN ("
                + marks(ids.size()) + ") ORDER BY created, player")) {
            bind(ps, ids.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    by.computeIfAbsent(rs.getLong(1), k -> new LinkedHashMap<>()).computeIfAbsent(rs.getString(2), k -> new StringJoiner(","))
                            .add(rs.getString(3));
                }
            }
        }
        by.forEach((id, m) -> {
            StringJoiner j = new StringJoiner(";");
            m.forEach((emoji, names) -> j.add(emoji + ":" + names));
            out.put(id, j.toString());
        });
        return out;
    }

    public int vote(long message, UUID who) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT bits FROM " + p + "poll_votes WHERE message_id = ? AND player = ?")) {
            bind(ps, message, who.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void setVote(long message, UUID who, int bits) throws SQLException {
        update("DELETE FROM " + p + "poll_votes WHERE message_id = ? AND player = ?", message, who.toString());
        if (bits != 0) {
            update("INSERT INTO " + p + "poll_votes (message_id, player, bits) VALUES (?, ?, ?)", message, who.toString(), bits);
        }
    }

    /** Votes per option, "3,0,5". */
    public String pollCounts(long message, int options) throws SQLException {
        int[] n = new int[options];
        try (PreparedStatement ps = c().prepareStatement("SELECT bits FROM " + p + "poll_votes WHERE message_id = ?")) {
            bind(ps, message);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int bits = rs.getInt(1);
                    for (int i = 0; i < options; i++) {
                        if ((bits & (1 << i)) != 0) {
                            n[i]++;
                        }
                    }
                }
            }
        }
        StringJoiner j = new StringJoiner(",");
        for (int v : n) {
            j.add(String.valueOf(v));
        }
        return j.toString();
    }

    // Read marks

    /** Moves the player's read mark forward (never back). True if it moved. */
    public boolean markRead(UUID who, long channel, long upTo) throws SQLException {
        if (update("UPDATE " + p + "read_states SET last_read = ? WHERE player = ? AND channel_id = ? AND last_read < ?", upTo, who.toString(), channel,
                upTo) > 0) {
            return true;
        }
        try (PreparedStatement ps = c().prepareStatement("SELECT 1 FROM " + p + "read_states WHERE player = ? AND channel_id = ?")) {
            bind(ps, who.toString(), channel);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return false;
                }
            }
        }
        update("INSERT INTO " + p + "read_states (player, channel_id, last_read) VALUES (?, ?, ?)", who.toString(), channel, upTo);
        return true;
    }

    /** channel id -> the player's read mark. */
    public Map<Long, Long> readMarks(UUID who) throws SQLException {
        Map<Long, Long> out = new HashMap<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT channel_id, last_read FROM " + p + "read_states WHERE player = ?")) {
            bind(ps, who.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getLong(1), rs.getLong(2));
                }
            }
        }
        return out;
    }

    /** Everyone's read marks in a channel: player -> mark. */
    public Map<UUID, Long> readMarksOf(long channel) throws SQLException {
        Map<UUID, Long> out = new HashMap<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT player, last_read FROM " + p + "read_states WHERE channel_id = ?")) {
            bind(ps, channel);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
                }
            }
        }
        return out;
    }

    /** Unread messages of others in each of these channels after the player's mark (at most 100 counted each). */
    public Map<Long, Integer> unread(UUID who, Collection<Long> channels) throws SQLException {
        Map<Long, Long> marks = readMarks(who);
        Map<Long, Integer> out = new HashMap<>();
        for (long ch : channels) {
            try (PreparedStatement ps = c().prepareStatement("SELECT COUNT(*) FROM (SELECT id FROM " + p + "messages WHERE channel_id = ? AND id > ? "
                    + "AND deleted = FALSE AND (sender IS NULL OR sender <> ?) LIMIT 100) u")) {
                bind(ps, ch, marks.getOrDefault(ch, 0L), who.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    int n = rs.next() ? rs.getInt(1) : 0;
                    if (n > 0) {
                        out.put(ch, n);
                    }
                }
            }
        }
        return out;
    }

    // Mutes

    public List<Mute> mutes() throws SQLException {
        List<Mute> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT player, player_name, scope, scope_id, until_ms, reason, by_name, created FROM " + p
                + "mutes"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Mute(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getString(6),
                        rs.getString(7), rs.getLong(8)));
            }
        }
        return out;
    }

    public void setMute(Mute m) throws SQLException {
        unmute(m.player(), m.scope(), m.scopeId());
        update("INSERT INTO " + p + "mutes (player, player_name, scope, scope_id, until_ms, reason, by_name, created) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                m.player().toString(), m.name(), m.scope(), m.scopeId(), m.until(), m.reason(), m.by(), m.created());
    }

    public void unmute(UUID player, String scope, long scopeId) throws SQLException {
        update("DELETE FROM " + p + "mutes WHERE player = ? AND scope = ? AND scope_id = ?", player.toString(), scope, scopeId);
    }

    // Invites

    public void addInvite(Invite i) throws SQLException {
        update("INSERT INTO " + p + "invites (code, space_id, creator, uses, max_uses, expires) VALUES (?, ?, ?, ?, ?, ?)", i.code(), i.spaceId(),
                i.creator().toString(), i.uses(), i.maxUses(), i.expires());
    }

    public Invite invite(String code) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT code, space_id, creator, uses, max_uses, expires FROM " + p + "invites WHERE code = ?")) {
            bind(ps, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Invite(rs.getString(1), rs.getLong(2), UUID.fromString(rs.getString(3)), rs.getInt(4), rs.getInt(5), rs.getLong(6))
                        : null;
            }
        }
    }

    public void useInvite(String code) throws SQLException {
        update("UPDATE " + p + "invites SET uses = uses + 1 WHERE code = ?", code);
    }

    // Files (voice notes and pictures kept on disk or S3)

    public void addFile(String key, String category, long now) throws SQLException {
        update("INSERT INTO " + p + "files (file_key, category, created) VALUES (?, ?, ?)", key, category, now);
    }

    public void removeFile(String key) throws SQLException {
        update("DELETE FROM " + p + "files WHERE file_key = ?", key);
    }

    /** Files of a category older than the given time. */
    public List<String> filesBefore(String category, long before) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT file_key FROM " + p + "files WHERE category = ? AND created < ?")) {
            bind(ps, category, before);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    // JSON for the wire

    /** A message as the client reads it. */
    public static JsonObject json(Message m) {
        JsonObject o = new JsonObject();
        o.addProperty("i", m.id());
        o.addProperty("ch", m.channelId());
        if (m.sender() != null) {
            o.addProperty("u", m.sender().toString());
            o.addProperty("n", m.name());
        }
        o.addProperty("k", m.kind());
        o.addProperty("b", m.body());
        if (m.replyTo() > 0) {
            o.addProperty("r", m.replyTo());
        }
        o.addProperty("t", m.created());
        if (m.edited() > 0) {
            o.addProperty("e", m.edited());
        }
        return o;
    }

    public static JsonArray json(List<Message> l) {
        JsonArray a = new JsonArray();
        l.forEach(m -> a.add(json(m)));
        return a;
    }

    // Helpers

    private int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            if (v == null) {
                ps.setNull(i + 1, Types.VARCHAR);
            } else if (v instanceof Long l) {
                ps.setLong(i + 1, l);
            } else if (v instanceof Integer n) {
                ps.setInt(i + 1, n);
            } else if (v instanceof Boolean b) {
                ps.setBoolean(i + 1, b);
            } else {
                ps.setString(i + 1, v.toString());
            }
        }
    }

    private static String marks(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    private static UUID uuid(String s) {
        return s == null ? null : UUID.fromString(s);
    }
}
