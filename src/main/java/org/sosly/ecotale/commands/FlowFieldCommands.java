package org.sosly.ecotale.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.sosly.ecotale.blocks.AbstractRoostBlock;
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.navigation.FlowFieldDebugRenderer;
import org.sosly.ecotale.navigation.FlowFieldSolution;

public final class FlowFieldCommands {
    private FlowFieldCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ecotale")
            .then(Commands.literal("flowfield")
                .then(Commands.literal("visualize")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(FlowFieldCommands::visualize)))
                .then(Commands.literal("revalidate")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(FlowFieldCommands::revalidate)))));
    }

    private static int visualize(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();

        RoostBlockEntity roost = getRoostBlockEntity(context, level, pos);
        if (roost == null) {
            return 0;
        }

        FlowFieldSolution solution = roost.getFlowFieldSolution();
        if (solution == null || solution.isFailed()) {
            context.getSource().sendFailure(Component.literal("No flow field generated for this roost"));
            return 0;
        }

        FlowFieldDebugRenderer.render(level, solution);

        int cellCount = solution.getOutwardCellCount() + solution.getInwardCellCount();
        context.getSource().sendSuccess(
            () -> Component.literal("Rendered flow field with " + cellCount + " cells"),
            false);
        return 1;
    }

    private static int revalidate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();

        RoostBlockEntity roost = getRoostBlockEntity(context, level, pos);
        if (roost == null) {
            return 0;
        }

        FlowFieldSolution solution = roost.getFlowFieldSolution();
        if (solution == null) {
            context.getSource().sendFailure(Component.literal("No flow field to validate"));
            return 0;
        }

        if (solution.isFailed()) {
            context.getSource().sendSuccess(
                () -> Component.literal("Flow field failed, regeneration would be queued"),
                false);
            return 1;
        }

        boolean valid = solution.isValid(level);
        if (valid) {
            context.getSource().sendSuccess(() -> Component.literal("Flow field valid"), false);
        } else {
            context.getSource().sendSuccess(
                () -> Component.literal("Flow field invalid, regeneration queued"),
                false);
        }
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
