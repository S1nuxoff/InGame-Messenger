package com.sinuxoff.ingamemessenger.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Popular chat mods that change the chat we draw, switched off while the messenger is on and back on a server without
 * it (each switch is a public static flag of theirs):
 * <ul>
 * <li>Chat Heads puts a head into each line and moves the text; our bubbles have faces. Its own switch for servers.</li>
 * <li>Chat Animation hides each line's tag (by which we know a line's message: its picture, voice note, reactions,
 * face) and slides and fades the lines; we have our own motion.</li>
 * </ul>
 */
final class ModCompat {
    private ModCompat() {
    }

    /** A flag of another mod, the value it needs while we draw the chat, and the one its player had. */
    private static final class Flag {
        final Field field;
        final boolean ours;
        boolean theirs;
        boolean set;

        Flag(Field field, boolean ours) {
            this.field = field;
            this.ours = ours;
        }
    }

    private static final List<Flag> flags = new ArrayList<>();
    private static boolean looked;

    private static void find(String cls, String field, boolean ours) {
        try {
            Field f = Class.forName(cls).getField(field);
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == boolean.class) {
                flags.add(new Flag(f, ours));
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // not installed, or a version without it
        }
    }

    static void tick() {
        if (!looked) {
            looked = true;
            find("dzwdz.chat_heads.ChatHeads", "serverDisabledChatHeads", true);
            String ca = "com.ezzenix.chatanimation.config.ModConfig";
            find(ca, "removeMessageIndicator", false);
            find(ca, "enableMessageAnimation", false);
            find(ca, "enableOpacity", false);
            find(ca, "enableTextFieldAnimation", false);
        }
        boolean on = Session.on();
        for (Flag f : flags) {
            try {
                if (on && !f.set) {
                    f.theirs = f.field.getBoolean(null);
                    f.set = true;
                }
                if (on && f.field.getBoolean(null) != f.ours) {
                    f.field.setBoolean(null, f.ours);
                } else if (!on && f.set) {
                    f.set = false;
                    f.field.setBoolean(null, f.theirs);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // left as it is
            }
        }
    }
}
