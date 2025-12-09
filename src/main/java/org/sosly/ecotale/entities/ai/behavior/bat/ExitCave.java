package org.sosly.ecotale.entities.ai.behavior.bat;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.sosly.ecotale.entities.ai.AIConstants;
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;
import org.sosly.ecotale.navigation.FlowFieldCell;
import org.sosly.ecotale.navigation.FlowFieldSolution;

import java.util.Optional;

public class ExitCave extends Behavior<EcoTaleBat> {
    private FlowFieldCell lastCell;
    private long lastNavTick;

    public ExitCave() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT), 1, 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EcoTaleBat bat) {
        if (bat.getBrain().getMemory(MemoryModuleTypes.IS_OUTSIDE.get()).orElse(false)) {
            return false;
        }
        if (bat.isSleeping()) {
            return false;
        }
        if (bat.getNavigation().isInProgress()) {
            return false;
        }

        GlobalPos home = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null || !home.dimension().equals(level.dimension())) {
            return false;
        }

        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EcoTaleBat bat, long gameTime) {
        if (bat.getBrain().getMemory(MemoryModuleTypes.IS_OUTSIDE.get()).orElse(false)) {
            return false;
        }
        if (bat.isSleeping()) {
            return false;
        }

        GlobalPos home = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        return home != null && home.dimension().equals(level.dimension());
    }

    @Override
    protected void start(ServerLevel level, EcoTaleBat bat, long gameTime) {
        lastCell = null;
        lastNavTick = 0;
    }

    @Override
    protected void tick(ServerLevel level, EcoTaleBat bat, long gameTime) {
        if (gameTime - lastNavTick < AIConstants.NAV_INTERVAL_TICKS) {
            return;
        }
        lastNavTick = gameTime;

        FlowFieldCell currentCell = FlowFieldCell.fromBlockPos(bat.blockPosition());
        if (currentCell.equals(lastCell) && bat.getNavigation().isInProgress()) {
            return;
        }
        lastCell = currentCell;

        GlobalPos home = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null) {
            return;
        }

        BlockPos roostPos = home.pos();
        BlockEntity blockEntity = level.getBlockEntity(roostPos);
        if (!(blockEntity instanceof RoostBlockEntity roost)) {
            return;
        }

        FlowFieldSolution solution = roost.getFlowFieldSolution();
        if (solution == null || solution.isFailed()) {
            return;
        }

        BlockPos exitPoint = solution.getExitPoint();
        Optional<Vec3> direction = solution.getOutwardDirection(currentCell);

        if (direction.isPresent()) {
            FlowFieldCell nextCell = FlowFieldCell.cellInDirection(currentCell, direction.get());
            Optional<BlockPos> nextHub = solution.getHubPosition(nextCell);
            if (nextHub.isPresent()) {
                navigateToHub(bat, nextHub.get());
            } else if (exitPoint != null) {
                navigateDirectlyToward(bat, Vec3.atCenterOf(exitPoint));
            }
            return;
        }

        FlowFieldCell neighborWithDirection = findNeighborWithOutwardDirection(currentCell, solution);
        if (neighborWithDirection != null) {
            Optional<BlockPos> neighborHub = solution.getHubPosition(neighborWithDirection);
            if (neighborHub.isPresent()) {
                navigateToHub(bat, neighborHub.get());
                return;
            }
        }

        if (exitPoint != null) {
            markAsOutside(bat);
        }
    }

    private FlowFieldCell findNeighborWithOutwardDirection(FlowFieldCell cell, FlowFieldSolution solution) {
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            FlowFieldCell neighbor = new FlowFieldCell(
                    cell.x() + offset[0],
                    cell.y() + offset[1],
                    cell.z() + offset[2]
            );
            if (solution.getOutwardDirection(neighbor).isPresent()) {
                return neighbor;
            }
        }
        return null;
    }

    private void markAsOutside(EcoTaleBat bat) {
        bat.getBrain().setMemory(MemoryModuleTypes.IS_OUTSIDE.get(), true);
    }

    private void navigateToHub(EcoTaleBat bat, BlockPos hub) {
        bat.getNavigation().moveTo(hub.getX() + 0.5, hub.getY() + 0.5, hub.getZ() + 0.5, AIConstants.DEFAULT_FLY_SPEED);
    }

    private void navigateDirectlyToward(EcoTaleBat bat, Vec3 target) {
        bat.getNavigation().moveTo(target.x, target.y, target.z, AIConstants.DEFAULT_FLY_SPEED);
    }
}
