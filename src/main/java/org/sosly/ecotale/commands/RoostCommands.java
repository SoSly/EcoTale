package org.sosly.ecotale.commands;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.sosly.ecotale.blocks.AbstractRoostBlock;
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.navigation.Manager;
import org.sosly.ecotale.network.NetworkHandler;
import org.sosly.ecotale.network.RoostDebugPacket;

public final class RoostCommands {
    private static final int MAX_SPAWN_COUNT = 64;
    private static final int UPDATE_INTERVAL_TICKS = 20;
    private static final Set<UUID> DEBUG_PLAYERS = new HashSet<>();

    private RoostCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ecotale")
            .then(Commands.literal("roost")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                    .executes(RoostCommands::debug))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .then(Commands.literal("spawn")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_SPAWN_COUNT))
                            .executes(RoostCommands::spawnBats))))));
    }

    private static int debug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID playerId = player.getUUID();
        Map<BlockPos, Boolean> roostStatuses = Manager.getInstance().getRoostStatuses();

        boolean enabling = !DEBUG_PLAYERS.contains(playerId);
        if (enabling) {
            DEBUG_PLAYERS.add(playerId);
        } else {
            DEBUG_PLAYERS.remove(playerId);
        }

        NetworkHandler.sendToPlayer(player, new RoostDebugPacket(roostStatuses, true));

        int count = roostStatuses.size();
        long failed = roostStatuses.values().stream().filter(hasGraph -> !hasGraph).count();
        String state = enabling ? "enabled" : "disabled";
        context.getSource().sendSuccess(
            () -> Component.literal("Roost debug " + state + " (" + count + " roosts, " + failed + " failed)"),
            false);
        return 1;
    }

    public static void tickDebugUpdates(MinecraftServer server) {
        if (DEBUG_PLAYERS.isEmpty()) {
            return;
        }
        if (server.getTickCount() % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        Map<BlockPos, Boolean> roostStatuses = Manager.getInstance().getRoostStatuses();
        RoostDebugPacket packet = new RoostDebugPacket(roostStatuses, false);

        for (UUID playerId : DEBUG_PLAYERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                NetworkHandler.sendToPlayer(player, packet);
            }
        }
    }

    public static void onPlayerDisconnect(UUID playerId) {
        DEBUG_PLAYERS.remove(playerId);
    }

    public static void clearDebugPlayers() {
        DEBUG_PLAYERS.clear();
    }

    private static int spawnBats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        int count = IntegerArgumentType.getInteger(context, "count");
        ServerLevel level = context.getSource().getLevel();

        RoostBlockEntity roost = getRoostBlockEntity(context, level, pos);
        if (roost == null) {
            return 0;
        }

        int spawned = roost.spawnColony(level, count);

        context.getSource().sendSuccess(
            () -> Component.literal("Spawned " + spawned + " bat(s) at roost " + pos.toShortString()),
            true);
        return spawned;
    }

    private static RoostBlockEntity getRoostBlockEntity(
            CommandContext<CommandSourceStack> context,
            ServerLevel level,
            BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractRoostBlock)) {
            context.getSource().sendFailure(Component.literal("No roost at " + pos.toShortString()));
            return null;
        }

        if (!(level.getBlockEntity(pos) instanceof RoostBlockEntity roost)) {
            context.getSource().sendFailure(Component.literal("No roost at " + pos.toShortString()));
            return null;
        }

        return roost;
    }
}
