package org.sosly.ecotale.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import org.sosly.ecotale.navigation.FlowFieldManager;
import org.sosly.ecotale.navigation.FlowFieldSolution;
import org.sosly.ecotale.network.FlowFieldDebugPacket;
import org.sosly.ecotale.network.NetworkHandler;
import org.sosly.ecotale.network.RoostStatusPacket;

import java.util.ArrayList;
import java.util.List;

public final class FlowFieldCommands {
    private static final int DEFAULT_ROOST_SCAN_RADIUS = 64;
    private static final int MAX_ROOST_SCAN_RADIUS = 512;

    private FlowFieldCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ecotale")
            .then(buildFlowFieldCommand("flowfield"))
            .then(buildFlowFieldCommand("ff")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildFlowFieldCommand(
            String name) {
        return Commands.literal(name)
            .then(Commands.literal("visualize")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(FlowFieldCommands::visualize)))
            .then(Commands.literal("clear")
                .requires(source -> source.hasPermission(2))
                .executes(FlowFieldCommands::clearVisualization))
            .then(Commands.literal("revalidate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(FlowFieldCommands::revalidate)))
            .then(Commands.literal("regenerate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(FlowFieldCommands::regenerate)))
            .then(Commands.literal("roosts")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> showRoosts(ctx, DEFAULT_ROOST_SCAN_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_ROOST_SCAN_RADIUS))
                    .executes(ctx -> showRoosts(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
            .then(Commands.literal("pause")
                .requires(source -> source.hasPermission(2))
                .executes(FlowFieldCommands::togglePause));
    }

    private static int visualize(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();
        ServerPlayer player = context.getSource().getPlayerOrException();

        RoostBlockEntity roost = getRoostBlockEntity(context, level, pos);
        if (roost == null) {
            return 0;
        }

        FlowFieldSolution solution = roost.getFlowFieldSolution();
        if (solution == null || solution.isFailed()) {
            context.getSource().sendFailure(Component.literal("No flow field generated for this roost"));
            return 0;
        }

        NetworkHandler.sendToPlayer(player, new FlowFieldDebugPacket(pos, solution));

        int cellCount = solution.getOutwardCellCount() + solution.getInwardCellCount();
        context.getSource().sendSuccess(
            () -> Component.literal("Toggled flow field visualization (" + cellCount + " cells)"),
            false);
        return 1;
    }

    private static int clearVisualization(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NetworkHandler.sendToPlayer(player, FlowFieldDebugPacket.clearAll());
        NetworkHandler.sendToPlayer(player, RoostStatusPacket.clearAll());
        context.getSource().sendSuccess(() -> Component.literal("Cleared all flow field visualizations"), false);
        return 1;
    }

    private static int togglePause(CommandContext<CommandSourceStack> context) {
        FlowFieldManager manager = FlowFieldManager.getInstance();
        boolean newState = !manager.isPaused();
        manager.setPaused(newState);

        String message = newState
            ? "Flow field generation paused (priority/manual requests still work)"
            : "Flow field generation resumed";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int revalidate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();

        RoostBlockEntity roost = getRoostBlockEntity(context, level, pos);
        if (roost == null) {
            return 0;
        }

        FlowFieldSolution oldSolution = roost.getFlowFieldSolution();
        boolean wasValid = oldSolution != null && !oldSolution.isFailed() && oldSolution.isValid(level);

        roost.forceRevalidate();

        FlowFieldSolution newSolution = roost.getFlowFieldSolution();
        if (wasValid && newSolution != null && !newSolution.isFailed()) {
            context.getSource().sendSuccess(() -> Component.literal("Flow field valid"), false);
        } else if (newSolution != null && !newSolution.isFailed()) {
            context.getSource().sendSuccess(
                () -> Component.literal("Flow field regenerated successfully"),
                false);
        } else {
            context.getSource().sendSuccess(
                () -> Component.literal("Flow field invalid, regeneration failed (will retry with backoff)"),
                false);
        }
        return 1;
    }

    private static int regenerate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();

        RoostBlockEntity roost = getRoostBlockEntity(context, level, pos);
        if (roost == null) {
            return 0;
        }

        roost.forceRegenerate();
        context.getSource().sendSuccess(
            () -> Component.literal("Flow field regeneration queued for " + pos.toShortString()),
            false);
        return 1;
    }

    private static int showRoosts(CommandContext<CommandSourceStack> context, int radius)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        BlockPos playerPos = player.blockPosition();

        List<BlockPos> withFlowField = new ArrayList<>();
        List<BlockPos> withoutFlowField = new ArrayList<>();

        BlockPos.betweenClosedStream(
            playerPos.offset(-radius, -radius, -radius),
            playerPos.offset(radius, radius, radius)
        ).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractRoostBlock)) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof RoostBlockEntity roost)) {
                return;
            }

            FlowFieldSolution solution = roost.getFlowFieldSolution();
            BlockPos immutablePos = pos.immutable();
            if (solution != null && !solution.isFailed()) {
                withFlowField.add(immutablePos);
            } else {
                withoutFlowField.add(immutablePos);
            }
        });

        int total = withFlowField.size() + withoutFlowField.size();
        if (total == 0) {
            context.getSource().sendSuccess(
                () -> Component.literal("No roosts found within " + radius + " blocks"),
                false);
            return 0;
        }

        NetworkHandler.sendToPlayer(player, new RoostStatusPacket(withFlowField, withoutFlowField));
        int okCount = withFlowField.size();
        int missingCount = withoutFlowField.size();
        context.getSource().sendSuccess(
            () -> Component.literal("Found " + total + " roosts: " + okCount + " with flow fields (green), "
                + missingCount + " without (red)"),
            false);
        return 1;
    }

    private static RoostBlockEntity getRoostBlockEntity(
            CommandContext<CommandSourceStack> context,
            ServerLevel level,
            BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractRoostBlock)) {
            context.getSource().sendFailure(Component.literal("No roost at that position"));
            return null;
        }

        if (!(level.getBlockEntity(pos) instanceof RoostBlockEntity roost)) {
            context.getSource().sendFailure(Component.literal("No roost at that position"));
            return null;
        }

        return roost;
    }
}
