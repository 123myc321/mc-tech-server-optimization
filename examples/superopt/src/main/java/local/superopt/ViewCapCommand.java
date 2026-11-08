package local.superopt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public final class ViewCapCommand {
    private ViewCapCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("superopt")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("viewcap")
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("distance", IntegerArgumentType.integer(2, 32))
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    int distance = IntegerArgumentType.getInteger(context, "distance");
                                                    ViewCapConfig.setCap(player.getUUID(), distance);
                                                    context.getSource().sendSystemMessage(Component.literal(
                                                            "View cap for " + player.getGameProfile().name()
                                                                    + " set to " + distance + " chunks"));
                                                    return 1;
                                                }))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            ViewCapConfig.setCap(player.getUUID(), 0);
                                            context.getSource().sendSystemMessage(Component.literal(
                                                    "View cap removed for " + player.getGameProfile().name()));
                                            return 1;
                                        })))
                        .then(Commands.literal("default")
                                .then(Commands.argument("distance", IntegerArgumentType.integer(0, 32))
                                        .executes(context -> {
                                            int distance = IntegerArgumentType.getInteger(context, "distance");
                                            ViewCapConfig.setDefaultCap(distance);
                                            context.getSource().sendSystemMessage(Component.literal(
                                                    "Default view cap set to "
                                                            + (distance == 0 ? "off" : distance + " chunks")));
                                            return 1;
                                        })))
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    MinecraftServer server = context.getSource().getServer();
                                    Map<UUID, Integer> caps = ViewCapConfig.snapshot();
                                    context.getSource().sendSystemMessage(Component.literal(
                                            "Default cap: "
                                                    + (ViewCapConfig.getDefaultCap() == 0
                                                    ? "off" : ViewCapConfig.getDefaultCap())));
                                    if (caps.isEmpty()) {
                                        context.getSource().sendSystemMessage(Component.literal("No per-player caps."));
                                    } else {
                                        caps.forEach((uuid, cap) -> {
                                            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                                            String name = online != null
                                                    ? online.getGameProfile().name() : uuid.toString();
                                            context.getSource().sendSystemMessage(
                                                    Component.literal(" - " + name + ": " + cap));
                                        });
                                    }
                                    return 1;
                                }))
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    ViewCapConfig.snapshot().keySet().forEach(uuid -> ViewCapConfig.setCap(uuid, 0));
                                    context.getSource().sendSystemMessage(
                                            Component.literal("All per-player view caps cleared."));
                                    return 1;
                                })))
                .then(Commands.literal("asyncsave")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    ViewCapConfig.setAsyncPlayerSaveEnabled(enabled);
                                    context.getSource().sendSystemMessage(Component.literal(
                                            "Async player save " + (enabled ? "enabled" : "disabled")));
                                    return 1;
                                })))
                .then(Commands.literal("status")
                        .executes(context -> {
                            MinecraftServer server = context.getSource().getServer();
                            double avgMs = server.getAverageTickTimeNanos() / 1_000_000.0;
                            double smoothedMs = server.getCurrentSmoothedTickTime();
                            context.getSource().sendSystemMessage(Component.literal(String.format(
                                    "Players: %d | avg tick: %.2f ms | smoothed: %.2f ms",
                                    server.getPlayerCount(), avgMs, smoothedMs)));
                            context.getSource().sendSystemMessage(Component.literal(
                                    "Default view cap: "
                                            + (ViewCapConfig.getDefaultCap() == 0
                                            ? "off" : ViewCapConfig.getDefaultCap())
                                            + " | async save: "
                                            + (ViewCapConfig.isAsyncPlayerSaveEnabled() ? "on" : "off")
                                            + " | save queue: " + AsyncPlayerSave.queued()
                                            + " | failed: " + AsyncPlayerSave.failed()));
                            return 1;
                        })));
    }
}
