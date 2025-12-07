package org.sosly.ecotale.navigation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Holds the computed flow field data for a roost.
 * Immutable after construction.
 */
public class FlowFieldSolution {
    private final Map<FlowFieldCell, Vec3> outwardField;
    private final Map<FlowFieldCell, Vec3> inwardField;
    private final Map<FlowFieldCell, BlockPos> hubCache;
    private final BlockPos exitPoint;
    private final BlockPos roostPos;
    private final boolean failed;

    private FlowFieldSolution(
            Map<FlowFieldCell, Vec3> outwardField,
            Map<FlowFieldCell, Vec3> inwardField,
            Map<FlowFieldCell, BlockPos> hubCache,
            BlockPos exitPoint,
            BlockPos roostPos,
            boolean failed) {
        this.outwardField = outwardField;
        this.inwardField = inwardField;
        this.hubCache = hubCache;
        this.exitPoint = exitPoint;
        this.roostPos = roostPos;
        this.failed = failed;
    }

    public static FlowFieldSolution create(
            Map<FlowFieldCell, Vec3> outwardField,
            Map<FlowFieldCell, Vec3> inwardField,
            Map<FlowFieldCell, BlockPos> hubCache,
            BlockPos exitPoint,
            BlockPos roostPos) {
        return new FlowFieldSolution(
            new HashMap<>(outwardField),
            new HashMap<>(inwardField),
            new HashMap<>(hubCache),
            exitPoint,
            roostPos,
            false
        );
    }

    public static FlowFieldSolution failed(BlockPos roostPos) {
        return new FlowFieldSolution(
            Map.of(),
            Map.of(),
            Map.of(),
            null,
            roostPos,
            true
        );
    }

    public boolean isFailed() {
        return failed;
    }

    public Optional<Vec3> getOutwardDirection(FlowFieldCell cell) {
        return Optional.ofNullable(outwardField.get(cell));
    }

    public Optional<Vec3> getInwardDirection(FlowFieldCell cell) {
        return Optional.ofNullable(inwardField.get(cell));
    }

    public Optional<BlockPos> getHubPosition(FlowFieldCell cell) {
        return Optional.ofNullable(hubCache.get(cell));
    }

    public BlockPos getExitPoint() {
        return exitPoint;
    }

    public BlockPos getRoostPos() {
        return roostPos;
    }

    /**
     * Validates that the flow field is still navigable.
     * This is currently a stub.
     */
    public boolean isValid(Level level) {
        return true;
    }
}
