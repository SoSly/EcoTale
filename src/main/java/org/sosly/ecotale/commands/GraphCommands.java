package org.sosly.ecotale.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.sosly.ecotale.blocks.AbstractRoostBlock;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.navigation.Cell;
import org.sosly.ecotale.navigation.Manager;
import org.sosly.ecotale.network.GraphDebugPacket;
import org.sosly.ecotale.network.NetworkHandler;

public final class GraphCommands {
    private GraphCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ecotale")
            .then(Commands.literal("graph")
                .then(Commands.argument("roostPos", BlockPosArgument.blockPos())
                    .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .executes(GraphCommands::debug))
                    .then(Commands.literal("regenerate")
                        .requires(source -> source.hasPermission(2))
                        .executes(GraphCommands::regenerate))
                    .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(2))
                        .executes(GraphCommands::info))
                    .then(Commands.literal("from")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("fromPos", BlockPosArgument.blockPos())
                            .then(Commands.literal("to")
                                .then(Commands.argument("toPos", BlockPosArgument.blockPos())
                                    .executes(GraphCommands::highlightPath))))))));
    }

    private static int debug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos roostPos = BlockPosArgument.getLoadedBlockPos(context, "roostPos");
        ServerLevel level = context.getSource().getLevel();
        ServerPlayer player = context.getSource().getPlayerOrException();

        if (!isRoost(level, roostPos)) {
            context.getSource().sendFailure(Component.literal("No roost at that position"));
            return 0;
        }

        Manager.getInstance().requestPriorityGeneration(roostPos, level, EntityRegistry.BAT.get(), graph -> {
            if (graph == null) {
                context.getSource().sendFailure(Component.literal("Graph generation failed"));
                return;
            }

            Map<BlockPos, GraphDebugPacket.CellData> cellData = new HashMap<>();
            for (Cell cell : graph.getCells().values()) {
                cellData.put(cell.getHub(), GraphDebugPacket.CellData.fromCell(cell));
            }

            NetworkHandler.sendToPlayer(player, new GraphDebugPacket(
                roostPos,
                graph.getGraphStart(),
                graph.getGraphExits(),
                cellData,
                new HashSet<>()
            ));

            context.getSource().sendSuccess(
                () -> Component.literal("Toggled graph visualization (" + graph.getCells().size() + " cells)"),
                false);
        });

        return 1;
    }

    private static int regenerate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos roostPos = BlockPosArgument.getLoadedBlockPos(context, "roostPos");
        ServerLevel level = context.getSource().getLevel();

        if (!isRoost(level, roostPos)) {
            context.getSource().sendFailure(Component.literal("No roost at that position"));
            return 0;
        }

        Manager.getInstance().requestPriorityGeneration(roostPos, level, EntityRegistry.BAT.get(), graph -> {
            if (graph == null) {
                context.getSource().sendFailure(Component.literal("Graph regeneration failed"));
                return;
            }

            context.getSource().sendSuccess(
                () -> Component.literal("Graph regenerated (" + graph.getCells().size() + " cells, "
                    + graph.getGraphExits().size() + " exits)"),
                false);
        });

        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos roostPos = BlockPosArgument.getLoadedBlockPos(context, "roostPos");
        ServerLevel level = context.getSource().getLevel();

        if (!isRoost(level, roostPos)) {
            context.getSource().sendFailure(Component.literal("No roost at that position"));
            return 0;
        }

        Manager.getInstance().requestPriorityGeneration(roostPos, level, EntityRegistry.BAT.get(), graph -> {
            if (graph == null) {
                context.getSource().sendFailure(Component.literal("No graph available"));
                return;
            }

            int cellCount = graph.getCells().size();
            int exitCount = graph.getGraphExits().size();
            BlockPos graphStart = graph.getGraphStart();

            context.getSource().sendSuccess(
                () -> Component.literal("Graph stats:\n"
                    + "  Cells: " + cellCount + "\n"
                    + "  Exits: " + exitCount + "\n"
                    + "  Start: " + graphStart.toShortString()),
                false);
        });

        return 1;
    }

    private static int highlightPath(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos roostPos = BlockPosArgument.getLoadedBlockPos(context, "roostPos");
        BlockPos fromPos = BlockPosArgument.getLoadedBlockPos(context, "fromPos");
        BlockPos toPos = BlockPosArgument.getLoadedBlockPos(context, "toPos");
        ServerLevel level = context.getSource().getLevel();
        ServerPlayer player = context.getSource().getPlayerOrException();

        if (!isRoost(level, roostPos)) {
            context.getSource().sendFailure(Component.literal("No roost at that position"));
            return 0;
        }

        Manager.getInstance().requestPriorityGeneration(roostPos, level, EntityRegistry.BAT.get(), graph -> {
            if (graph == null) {
                context.getSource().sendFailure(Component.literal("No graph available"));
                return;
            }

            Cell fromCell = graph.findContainingCell(fromPos);
            if (fromCell == null) {
                context.getSource().sendFailure(Component.literal("From position not in graph"));
                return;
            }

            Cell toCell = graph.findContainingCell(toPos);
            if (toCell == null) {
                context.getSource().sendFailure(Component.literal("To position not in graph"));
                return;
            }

            Set<BlockPos> highlightedPath = new HashSet<>();
            BlockPos currentHub = fromCell.getHub();
            BlockPos destinationHub = toCell.getHub();

            int maxSteps = graph.getCells().size();
            int steps = 0;

            while (currentHub != null && !currentHub.equals(destinationHub) && steps < maxSteps) {
                highlightedPath.add(currentHub);
                BlockPos nextHop = graph.getNextHop(currentHub, destinationHub);
                if (nextHop == null) {
                    break;
                }
                currentHub = nextHop;
                steps++;
            }

            if (currentHub != null && currentHub.equals(destinationHub)) {
                highlightedPath.add(destinationHub);
            }

            Map<BlockPos, GraphDebugPacket.CellData> cellData = new HashMap<>();
            for (Cell cell : graph.getCells().values()) {
                cellData.put(cell.getHub(), GraphDebugPacket.CellData.fromCell(cell));
            }

            NetworkHandler.sendToPlayer(player, new GraphDebugPacket(
                roostPos,
                graph.getGraphStart(),
                graph.getGraphExits(),
                cellData,
                highlightedPath
            ));

            int pathLength = highlightedPath.size();
            context.getSource().sendSuccess(
                () -> Component.literal("Highlighted path with " + pathLength + " cells"),
                false);
        });

        return 1;
    }

    private static boolean isRoost(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AbstractRoostBlock;
    }
}
