package com.sinuxoff.ingamemessenger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * /messenger for operators: reload, typing, voice, mute. With -Dingamemessenger.dev=true also test and bots, for trying
 * the chat alone.
 */
final class MessengerCommands {
    static final boolean DEV = Boolean.getBoolean("ingamemessenger.dev");

    private MessengerCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("messenger").requires(ServerCompat::isOperator)
                .then(Commands.literal("reload").executes(ctx -> {
                    Platform.get().settings().reload();
                    var hub = ServerSide.hub;
                    if (hub != null) {
                        hub.featuresChanged();
                        hub.reloadStickers();
                    }
                    return done(ctx.getSource(), "config and stickers reloaded");
                }))
                .then(Commands.literal("typing").then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                    boolean on = BoolArgumentType.getBool(ctx, "on");
                    Platform.get().settings().setTyping(on);
                    return changed(ctx.getSource(), "typing indicator " + (on ? "on" : "off"));
                })))
                .then(Commands.literal("voice")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            boolean on = BoolArgumentType.getBool(ctx, "on");
                            Platform.get().settings().setVoiceOn(on);
                            return changed(ctx.getSource(), "voice messages " + (on ? "on" : "off"));
                        }))
                        .then(Commands.literal("length").then(Commands.argument("seconds", IntegerArgumentType.integer(5, 300)).executes(ctx -> {
                            int s = IntegerArgumentType.getInteger(ctx, "seconds");
                            Platform.get().settings().setVoiceMaxSeconds(s);
                            return changed(ctx.getSource(), "voice messages up to " + s + " s");
                        })))
                        .then(Commands.literal("keep").then(Commands.argument("days", IntegerArgumentType.integer(0, 3650)).executes(ctx -> {
                            int d = IntegerArgumentType.getInteger(ctx, "days");
                            Platform.get().settings().setVoiceKeepDays(d);
                            return changed(ctx.getSource(), d == 0 ? "voice messages kept forever" : "voice messages kept " + d + " days");
                        }))))
                .then(mute("mute"))
                .then(mute("unmute"))
                .then(Commands.literal("mutes").executes(ctx -> mutes(ctx.getSource(), "mutes", "")));
        if (DEV) {
            root.then(Commands.literal("clients").executes(ctx -> {
                        StringBuilder b = new StringBuilder();
                        for (var p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                            b.append(b.length() > 0 ? ", " : "").append(p.getGameProfile().name())
                                    .append(InGameMessenger.hasClient(p) ? ": messenger" : ": plain chat");
                        }
                        return done(ctx.getSource(), b.length() > 0 ? b.toString() : "nobody on");
                    }));
        }
        dispatcher.register(root);
    }

    /** mute|unmute [player time scope reason...]: the words are read by the hub's moderation. */
    private static LiteralArgumentBuilder<CommandSourceStack> mute(String what) {
        return Commands.literal(what).executes(ctx -> mutes(ctx.getSource(), what, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b))
                        .executes(ctx -> mutes(ctx.getSource(), what, StringArgumentType.getString(ctx, "args"))));
    }

    private static int mutes(CommandSourceStack src, String what, String args) {
        var hub = ServerSide.hub;
        if (hub == null) {
            return done(src, "off (no database)");
        }
        java.util.List<String> words = args.isBlank() ? java.util.List.of() : java.util.List.of(args.strip().split("\\s+"));
        return done(src, hub.moderation().command(src.getTextName(), what, words));
    }

    private static int changed(CommandSourceStack src, String what) {
        var hub = ServerSide.hub;
        if (hub != null) {
            hub.featuresChanged();
        }
        return done(src, what);
    }

    private static int done(CommandSourceStack src, String what) {
        //? if >=1.20 {
        src.sendSuccess(() -> Component.literal("InGame Messenger: " + what), true);
        //?} else {
        /*src.sendSuccess(Component.literal("InGame Messenger: " + what), true);
        *///?}
        return 1;
    }
}
