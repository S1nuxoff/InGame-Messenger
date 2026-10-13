package com.sinuxoff.ingamemessenger;

import net.minecraft.world.entity.player.Player;

/** Server-side code that differs between Minecraft versions (client-side differences are in client.Compat and Gfx). */
public final class ServerCompat {
    private ServerCompat() {
    }

    public static boolean isOperator(Player p) {
        //? if >=1.21.11 {
        /*return p.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        *///?} else {
        return p.hasPermissions(2);
        //?}
    }

    public static java.util.UUID id(com.mojang.authlib.GameProfile p) {
        //? if >=1.21.9 {
        /*return p.id();
        *///?} else {
        return p.getId();
        //?}
    }

    public static String name(com.mojang.authlib.GameProfile p) {
        //? if >=1.21.9 {
        /*return p.name();
        *///?} else {
        return p.getName();
        //?}
    }

    /** Hover event that shows an entity, like a player name in the tab list. */
    public static net.minecraft.network.chat.HoverEvent showEntity(net.minecraft.world.entity.EntityType<?> type, java.util.UUID id,
                                                                    net.minecraft.network.chat.Component name) {
        var who = new net.minecraft.network.chat.HoverEvent.EntityTooltipInfo(type, id, name);
        //? if >=1.21.5 {
        /*return new net.minecraft.network.chat.HoverEvent.ShowEntity(who);
        *///?} else {
        return new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_ENTITY, who);
        //?}
    }

    public static String toJson(net.minecraft.network.chat.Component c, net.minecraft.core.HolderLookup.Provider registries) {
        //? if >=1.21.6 {
        /*return net.minecraft.network.chat.ComponentSerialization.CODEC
                .encodeStart(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), c)
                .getOrThrow().toString();
        *///?} else if >=1.20.5 {
        return net.minecraft.network.chat.Component.Serializer.toJson(c, registries);
        //?} else {
        /*return net.minecraft.network.chat.Component.Serializer.toJson(c);
        *///?}
    }

    public static net.minecraft.network.chat.Component fromJson(String json, net.minecraft.core.HolderLookup.Provider registries) {
        //? if >=1.21.6 {
        /*return net.minecraft.network.chat.ComponentSerialization.CODEC
                .parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), com.google.gson.JsonParser.parseString(json))
                .result().orElse(null);
        *///?} else if >=1.20.5 {
        return net.minecraft.network.chat.Component.Serializer.fromJson(json, registries);
        //?} else {
        /*return net.minecraft.network.chat.Component.Serializer.fromJson(json);
        *///?}
    }

    /** Last known name of a possibly offline player, or null. */
    public static String knownName(net.minecraft.server.MinecraftServer server, java.util.UUID id) {
        //? if >=1.21.9 {
        /*return server.services().nameToIdCache().get(id).map(net.minecraft.server.players.NameAndId::name).orElse(null);
        *///?} else {
        var cache = server.getProfileCache();
        return cache == null ? null : cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(null);
        //?}
    }

    public static boolean isOperator(net.minecraft.commands.CommandSourceStack source) {
        //? if >=1.21.11 {
        /*return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        *///?} else {
        return source.hasPermission(2);
        //?}
    }

    /** The player shown by a text's hover event (a name in chat), or null. */
    public static java.util.UUID hoverPlayer(net.minecraft.network.chat.HoverEvent hover) {
        //? if >=1.21.5 {
        /*if (hover instanceof net.minecraft.network.chat.HoverEvent.ShowEntity(var info)) {
            return info.type == net.minecraft.world.entity.EntityType.PLAYER ? info.uuid : null;
        }
        return null;
        *///?} else {
        var info = hover.getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_ENTITY);
        return info != null && info.type == net.minecraft.world.entity.EntityType.PLAYER ? info.id : null;
        //?}
    }

    /** e.g. "1.21.1", "26.2". */
    public static String gameVersion() {
        //? if >=1.21.6 {
        /*return net.minecraft.SharedConstants.getCurrentVersion().name();
        *///?} else {
        return net.minecraft.SharedConstants.getCurrentVersion().getName();
        //?}
    }
}
