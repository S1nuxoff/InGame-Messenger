package com.sinuxoff.ingamemessenger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.core.server.ChatServer;

/**
 * /messenger for operators: reload, typing, voice. With -Dingamemessenger.dev=true also test and bots, for trying
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
                    ChatServer chat = ServerSide.chat;
                    if (chat != null) {
                        chat.featuresChanged();
                        chat.reloadStickers();
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
                        }))));
        if (DEV) {
            root.then(Commands.literal("test").executes(ctx -> {
                        TestChat.start(ctx.getSource().getServer(), ctx.getSource().getTextName());
                        return done(ctx.getSource(), "test chat started");
                    }))
                    .then(Commands.literal("bots")
                            .executes(ctx -> {
                                int n = FakeBots.add(ctx.getSource().getServer());
                                return done(ctx.getSource(), n + " test bots added, @mention one to get an answer");
                            })
                            .then(Commands.literal("off").executes(ctx -> {
                                FakeBots.remove(ctx.getSource().getServer());
                                return done(ctx.getSource(), "test bots removed");
                            })));
        }
        dispatcher.register(root);
    }

    private static int changed(CommandSourceStack src, String what) {
        ChatServer chat = ServerSide.chat;
        if (chat != null) {
            chat.featuresChanged();
        }
        return done(src, what);
    }

    private static int done(CommandSourceStack src, String what) {
        src.sendSuccess(() -> Component.literal("InGame Messenger: " + what), true);
        return 1;
    }
}
