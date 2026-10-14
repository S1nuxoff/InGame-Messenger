package com.sinuxoff.ingamemessenger.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text conventions shared by client and server: replies, shared locations, input validation, simple markdown. */
public final class ChatText {
    private ChatText() {
    }

    // Replies: "↩<id> text"

    private static final Pattern REPLY = Pattern.compile("^↩(\\d{1,9}) ");

    /** The id the text replies to, or -1. */
    public static int replyTarget(String text) {
        Matcher m = REPLY.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Length of the reply prefix, 0 if none. */
    public static int replyMarkLength(String text) {
        Matcher m = REPLY.matcher(text);
        return m.find() ? m.end() : 0;
    }

    public static String withReply(int target, String text) {
        return target >= 0 ? "↩" + target + " " + text : text;
    }

    /** Same as {@link #replyTarget} but for a stored text component JSON. */
    public static int replyTargetInJson(String json) {
        Matcher m = Pattern.compile("↩(\\d{1,9}) ").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // Input validation

    /** Non-empty, within the limit, no § codes or control characters. */
    public static boolean isPlainText(String text, int maxLength) {
        return !text.isEmpty() && text.length() <= maxLength && text.chars().noneMatch(c -> c == '§' || c < 32);
    }

    /** Trimmed caption (may be empty), or null if invalid. */
    public static String caption(String c, int maxLength) {
        String t = c.strip();
        return t.isEmpty() || isPlainText(t, maxLength) ? t : null;
    }

    /** Single-line excerpt, cut with "…". */
    public static String snippet(String text, int max) {
        String t = text.replace('\n', ' ').strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    // Shared locations, in JourneyMap's waypoint format

    public record Location(String name, int x, int y, int z, String dim) {
        public String coords() {
            return x + " " + y + " " + z;
        }

    /** JourneyMap turns this format into a waypoint link. */
        public String wire() {
            return "[name:\"" + name + "\", x:" + x + ", y:" + y + ", z:" + z + ", dim:" + dim + "]";
        }
    }

    private static final Pattern LOC = Pattern.compile(
            "\\[name:\"([^\"]{0,40})\", x:(-?\\d{1,8}), y:(-?\\d{1,4}), z:(-?\\d{1,8}), dim:([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)]");

    /** The location shared by this text, or null. */
    public static Location parseLocation(String text) {
        Matcher m = LOC.matcher(text.strip());
        if (!m.matches()) {
            return null;
        }
        try {
            return new Location(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                    m.group(5));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Markdown subset

    public record Span(String text, boolean bold, boolean italic, boolean code) {
    }

    private static final Pattern MD = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|(?<![\\w*])[*_]([^*_\\n]+)[*_](?![\\w*])");

    /** **bold**, *italic*, _italic_, `code`; "- " and "* " list items become "• "; headings become bold. */
    public static List<Span> markdown(String text) {
        String t = text.replaceAll("(?m)^#{1,6}\\s*(.*)$", "**$1**").replaceAll("(?m)^\\s*[-*]\\s+", "• ");
        List<Span> out = new ArrayList<>();
        Matcher m = MD.matcher(t);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) {
                out.add(new Span(t.substring(at, m.start()), false, false, false));
            }
            if (m.group(1) != null) {
                out.add(new Span(m.group(1), true, false, false));
            } else if (m.group(2) != null) {
                out.add(new Span(m.group(2), false, false, true));
            } else {
                out.add(new Span(m.group(3), false, true, false));
            }
            at = m.end();
        }
        if (at < t.length()) {
            out.add(new Span(t.substring(at), false, false, false));
        }
        return out;
    }
}
