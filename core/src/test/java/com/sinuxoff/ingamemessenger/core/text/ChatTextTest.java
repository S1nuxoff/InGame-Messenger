package com.sinuxoff.ingamemessenger.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatTextTest {
    @Test
    void replies() {
        String t = ChatText.withReply(42, "hi there");
        assertEquals("↩42 hi there", t);
        assertEquals(42, ChatText.replyTarget(t));
        assertEquals("hi there", t.substring(ChatText.replyMarkLength(t)));
        assertEquals(-1, ChatText.replyTarget("just text ↩5 "));
        assertEquals(0, ChatText.replyMarkLength("just text"));
        assertEquals("x", ChatText.withReply(-1, "x"));
        assertEquals(7, ChatText.replyTargetInJson("{\"text\":\"↩7 hello\"}"));
        assertEquals(-1, ChatText.replyTargetInJson("{\"text\":\"hello\"}"));
    }

    @Test
    void whatPlayersMayWrite() {
        assertTrue(ChatText.isPlainText("hello", 10));
        assertFalse(ChatText.isPlainText("", 10));
        assertFalse(ChatText.isPlainText("hello world!", 10));
        assertFalse(ChatText.isPlainText("§chello", 10));
        assertFalse(ChatText.isPlainText("a\nb", 10));
        assertEquals("", ChatText.caption("   ", 10));
        assertEquals("hi", ChatText.caption("  hi ", 10));
        assertNull(ChatText.caption("§4red", 10));
        assertEquals("abcd…", ChatText.snippet("abcdefgh", 5));
        assertEquals("a b", ChatText.snippet("a\nb", 5));
    }

    @Test
    void locations() {
        ChatText.Location l = new ChatText.Location("S1nuxoff", 12, -60, -3400, "minecraft:the_nether");
        assertEquals("[name:\"S1nuxoff\", x:12, y:-60, z:-3400, dim:minecraft:the_nether]", l.wire());
        assertEquals(l, ChatText.parseLocation(" " + l.wire() + " "));
        assertEquals("12 -60 -3400", l.coords());
        assertNull(ChatText.parseLocation("look " + l.wire()));
        assertNull(ChatText.parseLocation("[name:\"x\", x:1, y:2, z:3, dim:overworld]"));
    }

    @Test
    void markdown() {
        List<ChatText.Span> s = ChatText.markdown("a **b** *c* `d` _e_");
        assertEquals(List.of(
                new ChatText.Span("a ", false, false, false),
                new ChatText.Span("b", true, false, false),
                new ChatText.Span(" ", false, false, false),
                new ChatText.Span("c", false, true, false),
                new ChatText.Span(" ", false, false, false),
                new ChatText.Span("d", false, false, true),
                new ChatText.Span(" ", false, false, false),
                new ChatText.Span("e", false, true, false)), s);
        assertEquals("• one\n• two", join(ChatText.markdown("- one\n* two")));
        assertTrue(ChatText.markdown("## Title").get(0).bold());
        assertEquals("snake_case_name", join(ChatText.markdown("snake_case_name")), "underscores inside words stay");
    }

    private static String join(List<ChatText.Span> spans) {
        StringBuilder b = new StringBuilder();
        spans.forEach(x -> b.append(x.text()));
        return b.toString();
    }
}
