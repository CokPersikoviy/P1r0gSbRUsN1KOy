package ru.wilyfox.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.SharedSuggestionProvider;
import ru.wilyfox.client.chat.BossShareService;
import ru.wilyfox.client.profiler.ProfilerDebugCommand;
import ru.wilyfox.client.protocol.ProtocolDebugCommand;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Registers FrogHelper's client-side pseudo-commands with Brigadier so they get proper tab
 * autocomplete (subcommands + argument suggestions). Execution still flows through the chat-input
 * intercept in {@code ChatScreenMixin} (which fires first and cancels), so the executors here are a
 * correct fallback that normally never runs.
 */
public final class FhCommands {
    private FhCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /fhprof <status|start|stop|reset|report [prefix]|dump [prefix]>
            dispatcher.register(literal("fhprof")
                    .executes(ctx -> run("/fhprof"))
                    .then(literal("status").executes(ctx -> run("/fhprof status")))
                    .then(literal("start").executes(ctx -> run("/fhprof start")))
                    .then(literal("stop").executes(ctx -> run("/fhprof stop")))
                    .then(literal("reset").executes(ctx -> run("/fhprof reset")))
                    .then(literal("report").executes(ctx -> run("/fhprof report"))
                            .then(argument("prefix", StringArgumentType.greedyString())
                                    .executes(ctx -> run("/fhprof report " + StringArgumentType.getString(ctx, "prefix")))))
                    .then(literal("dump").executes(ctx -> run("/fhprof dump"))
                            .then(argument("prefix", StringArgumentType.greedyString())
                                    .executes(ctx -> run("/fhprof dump " + StringArgumentType.getString(ctx, "prefix"))))));

            // /fhproto <stats|anomalies|reset>
            dispatcher.register(literal("fhproto")
                    .executes(ctx -> run("/fhproto"))
                    .then(literal("stats").executes(ctx -> run("/fhproto stats")))
                    .then(literal("anomalies").executes(ctx -> run("/fhproto anomalies")))
                    .then(literal("reset").executes(ctx -> run("/fhproto reset"))));

            // /fhshare <nick>
            dispatcher.register(literal("fhshare")
                    .executes(ctx -> run("/fhshare"))
                    .then(argument("nick", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                            .executes(ctx -> run("/fhshare " + StringArgumentType.getString(ctx, "nick")))));
        });
    }

    /** Fallback executor — the ChatScreenMixin intercept normally handles these first. Each handler
     *  only acts on its own prefix, so calling all four is safe. */
    private static int run(String command) {
        if (BossShareService.handleOutgoingCommand(command, false)
                || ProtocolDebugCommand.handleOutgoingCommand(command, false)
                || ProfilerDebugCommand.handleOutgoingCommand(command, false)) {
            return 1;
        }
        return 1;
    }
}
