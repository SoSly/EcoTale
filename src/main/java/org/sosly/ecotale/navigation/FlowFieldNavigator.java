package org.sosly.ecotale.navigation;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.sosly.ecotale.api.IFlowFieldSolutionProvider;
import org.sosly.ecotale.api.entities.IFlyingMob;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;

public class FlowFieldNavigator<T extends Mob> {
    private FlowFieldCell lastCell = null;
    private final FlowFieldSolution solution;
    private T entity = null;
    private FlowFieldDirection direction;

    public static Optional<FlowFieldNavigator<?>> create(BlockEntity be, Mob entity, FlowFieldDirection direction) {
        if (!(entity instanceof IFlyingMob<?>)) {
            return Optional.empty();
        }

        if (!(be instanceof IFlowFieldSolutionProvider provider)) {
            return Optional.empty();
        }

        FlowFieldSolution solution = provider.getFlowFieldSolution();
        if (solution == null) {
            return Optional.empty();
        }
        return Optional.of(new FlowFieldNavigator<>(solution, entity, direction));
    }

    private FlowFieldNavigator(FlowFieldSolution solution, T entity, FlowFieldDirection direction) {
        this.entity = entity;
        this.solution = solution;
        this.direction = direction;
    }

    private boolean shouldSkipNavigation() {
        if (solution == null || solution.isFailed()) {
            return true;
        }

        FlowFieldCell currentCell = getCurrentCell();
        if (currentCell.equals(lastCell) && entity.getNavigation().isInProgress()) {
            return true;
        }
        lastCell = currentCell;
        return false;
    }

    public Optional<BlockPos> getStartPoint() {
        return switch (direction) {
            case INWARD -> Optional.of(solution.getExitPoint());
            case OUTWARD -> solution.getHubPosition(solution.getStartCell());
        };
    }

    public Optional<BlockPos> getExitPoint() {
        return switch (direction) {
            case INWARD -> solution.getHubPosition(solution.getStartCell());
            case OUTWARD -> Optional.of(solution.getExitPoint());
        };
    }

    public boolean isEntityInTargetCell() {
        WalkTarget flyTarget = entity.getBrain().getMemory(MemoryModuleTypes.FLY_TARGET.get()).orElse(null);
        if (flyTarget == null) {
            return false;
        }

        BlockPos targetPos = flyTarget.getTarget().currentBlockPosition();
        FlowFieldCell targetCell = FlowFieldCell.fromBlockPos(targetPos);
        return targetCell.contains(entity.blockPosition());
    }

    protected FlowFieldCell getCurrentCell() {
        return FlowFieldCell.fromBlockPos(entity.blockPosition());
    }

    public Optional<BlockPos> next() {
        if (shouldSkipNavigation()) {
            return Optional.empty();
        }

        return switch (direction) {
            case INWARD -> navigateInward();
            case OUTWARD -> navigateOutward();
        };
    }

    private Optional<BlockPos> navigateInward() {
        FlowFieldCell currentCell = getCurrentCell();

        Optional<Vec3> direction = solution.getInwardDirection(currentCell);
        if (direction.isEmpty()) {
            return Optional.empty();
        }

        FlowFieldCell nextCell = FlowFieldCell.cellInDirection(currentCell, direction.get());
        BlockPos nextHub = solution.getHubPosition(nextCell).orElse(null);
        if (nextHub == null) {
            return Optional.empty();
        }

        return Optional.of(nextHub);
    }

    private Optional<BlockPos> navigateOutward() {
        FlowFieldCell currentCell = getCurrentCell();

        Optional<Vec3> direction = solution.getOutwardDirection(currentCell);
        if (direction.isEmpty()) {
            return Optional.empty();
        }

        FlowFieldCell nextCell = FlowFieldCell.cellInDirection(currentCell, direction.get());
        BlockPos nextHub = solution.getHubPosition(nextCell).orElse(null);
        if (nextHub == null) {
            return Optional.empty();
        }

        return Optional.of(nextHub);
    }
}
