package org.sosly.ecotale.navigation;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Represents a request for flow field generation or validation.
 * Processed asynchronously on the flow field worker thread.
 */
public record FlowFieldRequest(
        BlockPos roostPos,
        Level level,
        Consumer<FlowFieldSolution> callback,
        RequestType type,
        FlowFieldSolution existingSolution
) {
    public enum RequestType {
        GENERATE,
        VALIDATE
    }

    public static FlowFieldRequest generation(BlockPos roostPos, Level level, Consumer<FlowFieldSolution> callback) {
        return new FlowFieldRequest(roostPos, level, callback, RequestType.GENERATE, null);
    }

    public static FlowFieldRequest validation(
            BlockPos roostPos,
            Level level,
            FlowFieldSolution solution,
            Consumer<Boolean> callback
    ) {
        return new FlowFieldRequest(
                roostPos,
                level,
                result -> callback.accept(result != null && !result.isFailed()),
                RequestType.VALIDATE,
                solution
        );
    }
}
