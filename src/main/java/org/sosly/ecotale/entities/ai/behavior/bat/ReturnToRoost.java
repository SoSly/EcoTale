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
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.navigation.FlowFieldCell;
import org.sosly.ecotale.navigation.FlowFieldSolution;

import java.util.Optional;

public class ReturnToRoost extends Behavior<EcoTaleBat> {
    private static final int CLOSE_ENOUGH = 1;

    private FlowFieldCell lastCell;

    public ReturnToRoost() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT), 1, 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EcoTaleBat bat) {
        if (bat.isResting()) {
            return false;
        }

        if (bat.getNavigation().isInProgress()) {
            return false;
        }

        GlobalPos home = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null || !home.dimension().equals(level.dimension())) {
            return false;
        }

        BlockPos hangPos = home.pos().below();
        return !bat.blockPosition().closerThan(hangPos, CLOSE_ENOUGH);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EcoTaleBat bat, long gameTime) {
        if (bat.isResting()) {
            return false;
        }

        GlobalPos home = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null || !home.dimension().equals(level.dimension())) {
            return false;
        }

        BlockPos hangPos = home.pos().below();
        return !bat.blockPosition().closerThan(hangPos, CLOSE_ENOUGH);
    }

    @Override
    protected void start(ServerLevel level, EcoTaleBat bat, long gameTime) {
        lastCell = null;
    }

    @Override
    protected void tick(ServerLevel level, EcoTaleBat bat, long gameTime) {
        if (bat.getNavigation().isInProgress()) {
            FlowFieldCell currentCell = FlowFieldCell.fromBlockPos(bat.blockPosition());
            if (currentCell.equals(lastCell)) {
                return;
            }
        }

        GlobalPos home = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null) {
            return;
        }

        BlockPos roostPos = home.pos();
        BlockEntity blockEntity = level.getBlockEntity(roostPos);
        if (!(blockEntity instanceof RoostBlockEntity roost)) {
            navigateDirectlyToward(bat, Vec3.atCenterOf(roostPos.below()));
            return;
        }

        FlowFieldSolution solution = roost.getFlowFieldSolution();
        if (solution == null || solution.isFailed()) {
            navigateDirectlyToward(bat, Vec3.atCenterOf(roostPos.below()));
            return;
        }

        FlowFieldCell currentCell = FlowFieldCell.fromBlockPos(bat.blockPosition());
        lastCell = currentCell;
        Optional<Vec3> direction = solution.getInwardDirection(currentCell);

        if (direction.isPresent()) {
            FlowFieldCell nextCell = FlowFieldCell.cellInDirection(currentCell, direction.get());
            Optional<BlockPos> nextHub = solution.getHubPosition(nextCell);
            if (nextHub.isPresent()) {
                navigateToHub(bat, nextHub.get());
            } else {
                navigateDirectlyToward(bat, Vec3.atCenterOf(roostPos.below()));
            }
        } else {
            BlockPos exitPoint = solution.getExitPoint();
            if (exitPoint != null) {
                navigateDirectlyToward(bat, Vec3.atCenterOf(exitPoint));
            } else {
                navigateDirectlyToward(bat, Vec3.atCenterOf(roostPos.below()));
            }
        }
    }

    private void navigateToHub(EcoTaleBat bat, BlockPos hub) {
        bat.getNavigation().moveTo(hub.getX() + 0.5, hub.getY() + 0.5, hub.getZ() + 0.5, 1.0);
    }

    private void navigateDirectlyToward(EcoTaleBat bat, Vec3 target) {
        bat.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
    }

    @Override
    protected void stop(ServerLevel level, EcoTaleBat bat, long gameTime) {
        bat.getNavigation().stop();
    }
}
