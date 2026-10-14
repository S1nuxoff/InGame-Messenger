package com.sinuxoff.ingamemessenger.core.hub;

import com.sinuxoff.ingamemessenger.core.storage.Store;

import java.util.Collection;
import java.util.UUID;

/**
 * What a player may do in a channel, the way Discord works it out: the space's "everyone" role and the player's roles
 * give the base, then the channel's overrides for everyone, for the player's roles and for the player.
 */
public final class Perms {
    private Perms() {
    }

    public static final long VIEW = 1, SEND = 1 << 1, ATTACH = 1 << 2, VOICE = 1 << 3, REACT = 1 << 4, MANAGE_MESSAGES = 1 << 5,
            MUTE = 1 << 6, MANAGE_CHANNELS = 1 << 7, MANAGE_ROLES = 1 << 8, INVITE = 1 << 9, ADMIN = 1L << 40;
    public static final long ALL = (1L << 10) - 1 | ADMIN;
    /** What everyone may do in a new space. */
    public static final long EVERYONE = VIEW | SEND | ATTACH | VOICE | REACT | INVITE;
    /** The two players of a private chat. */
    public static final long DM = VIEW | SEND | ATTACH | VOICE | REACT;
    /** What a mute takes away. */
    public static final long WRITING = SEND | ATTACH | VOICE | REACT;

    public static boolean has(long perms, long what) {
        return (perms & what) == what;
    }

    /**
     * The base: everyone's role and the player's roles together; ADMIN means everything.
     */
    public static long base(long everyone, Collection<Store.Role> roles) {
        long p = everyone;
        for (Store.Role r : roles) {
            p |= r.perms();
        }
        return has(p, ADMIN) ? ALL : p;
    }

    /**
     * The channel's overrides on top of the base: everyone's first, then the player's roles' (all denies, then all
     * allows), then the player's own. An admin keeps everything.
     */
    public static long inChannel(long base, long everyoneRole, Collection<Long> roles, UUID player, Collection<Store.Override> overrides) {
        if (has(base, ADMIN)) {
            return ALL;
        }
        long p = base;
        String everyone = "r:" + everyoneRole;
        for (Store.Override o : overrides) {
            if (o.target().equals(everyone)) {
                p = p & ~o.deny() | o.allow();
            }
        }
        long allow = 0, deny = 0;
        for (Store.Override o : overrides) {
            if (o.target().startsWith("r:") && !o.target().equals(everyone) && roles.contains(Long.parseLong(o.target().substring(2)))) {
                allow |= o.allow();
                deny |= o.deny();
            }
        }
        p = p & ~deny | allow;
        String own = "p:" + player;
        for (Store.Override o : overrides) {
            if (o.target().equals(own)) {
                p = p & ~o.deny() | o.allow();
            }
        }
        return has(p, VIEW) ? p : 0; // what you can't see you can't do anything in
    }
}
