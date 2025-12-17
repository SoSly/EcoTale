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
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.navigation.Cell;
import org.sosly.ecotale.navigation.Graph;
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

        if (!(level.getBlockEntity(roostPos) instanceof RoostBlockEntity roost)) {
            context.getSource().sendFailure(Component.literal("No roost block entity at that position"));
            return 0;
        }

        Graph graph = roost.getGraph();
        if (graph == null) {
            context.getSource().sendFailure(Component.literal("No graph available (not yet generated?)"));
            return 0;
        }

        Map<BlockPos, GraphDebugPacket.CellData> cellData = new HashMap<>();
        for (Cell cell : graph.getCells().values()) {
            GraphDebugPacket.CellData data = GraphDebugPacket.CellData.fromCell(cell, graph);
            BlockPos boundsMin = new BlockPos(
                (int) cell.getBounds().minX,
                (int) cell.getBounds().minY,
                (int) cell.getBounds().minZ);
            cellData.put(boundsMin, data);
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

        return 1;
    }

    private static int regenerate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos roostPos = BlockPosArgument.getLoadedBlockPos(context, "roostPos");
        ServerLevel level = context.getSource().getLevel();

        if (!isRoost(level, roostPos)) {
            context.getSource().sendFailure(Component.literal("No roost at that position"));
            return 0;
        }

        RoostBlockEntity roost = (RoostBlockEntity) level.getBlockEntity(roostPos);
        if (roost == null) {
            context.getSource().sendFailure(Component.literal("No roost block entity at that position"));
            return 0;
        }

        Manager.getInstance().requestPriorityGeneration(roostPos, level, EntityRegistry.BAT.get(), graph -> {
            if (graph == null) {
                context.getSource().sendFailure(Component.literal("Graph regeneration failed"));
                return;
            }

            roost.onGraphGenerationComplete(graph);
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

        if (!(level.getBlockEntity(roostPos) instanceof RoostBlockEntity roost)) {
            context.getSource().sendFailure(Component.literal("No roost block entity at that position"));
            return 0;
        }

        Graph graph = roost.getGraph();
        if (graph == null) {
            context.getSource().sendFailure(Component.literal("No graph available (not yet generated?)"));
            return 0;
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

        if (!(level.getBlockEntity(roostPos) instanceof RoostBlockEntity roost)) {
            context.getSource().sendFailure(Component.literal("No roost block entity at that position"));
            return 0;
        }

        Graph graph = roost.getGraph();
        if (graph == null) {
            context.getSource().sendFailure(Component.literal("No graph available (not yet generated?)"));
            return 0;
        }

        Cell fromCell = graph.findContainingCell(fromPos);
        if (fromCell == null) {
            context.getSource().sendFailure(Component.literal("From position not in graph"));
            return 0;
        }

        Cell toCell = graph.findContainingCell(toPos);
        if (toCell == null) {
            context.getSource().sendFailure(Component.literal("To position not in graph"));
            return 0;
        }

        Set<BlockPos> highlightedPath = new HashSet<>();
        BlockPos currentPos = fromPos;
        BlockPos destinationPos = toPos;

        int maxSteps = graph.getCells().size();
        int steps = 0;

        while (currentPos != null && steps < maxSteps) {
            Cell currentCell = graph.findContainingCell(currentPos);
            if (currentCell == null) {
                break;
            }

            GraphDebugPacket.CellData currentData = GraphDebugPacket.CellData.fromCell(currentCell, graph);
            BlockPos currentHub = currentData.hubs.isEmpty()
                ? new BlockPos((int) currentCell.getBounds().minX,
                    (int) currentCell.getBounds().minY,
                    (int) currentCell.getBounds().minZ)
                : currentData.hubs.iterator().next();
            highlightedPath.add(currentHub);

            if (currentCell.contains(destinationPos)) {
                break;
            }

            BlockPos nextHop = graph.getNextHop(currentPos, destinationPos);
            if (nextHop == null) {
                break;
            }
            currentPos = nextHop;
            steps++;
        }

        Map<BlockPos, GraphDebugPacket.CellData> cellData = new HashMap<>();
        for (Cell cell : graph.getCells().values()) {
            GraphDebugPacket.CellData data = GraphDebugPacket.CellData.fromCell(cell, graph);
            BlockPos boundsMin = new BlockPos(
                (int) cell.getBounds().minX,
                (int) cell.getBounds().minY,
                (int) cell.getBounds().minZ);
            cellData.put(boundsMin, data);
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

        return 1;
    }

    private static boolean isRoost(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AbstractRoostBlock;
    }
}
