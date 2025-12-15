package org.sosly.ecotale.entities.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.sosly.ecotale.api.IGraphProvider;
import org.sosly.ecotale.api.entities.IFlyingMob;
import org.sosly.ecotale.entities.ai.AIConstants;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;
import org.sosly.ecotale.navigation.Cell;
import org.sosly.ecotale.navigation.Graph;

public abstract class GraphNavigationBehavior<T extends Mob & IFlyingMob<T>> extends Behavior<T> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MemoryModuleType<GlobalPos> graphProviderMemory;

    private Phase phase;
    private Graph graph;
    private BlockPos currentHub;
    private BlockPos destination;
    private long lastNavTick;

    protected GraphNavigationBehavior(MemoryModuleType<GlobalPos> graphProviderMemory) {
        super(ImmutableMap.of(
            graphProviderMemory, MemoryStatus.VALUE_PRESENT,
            MemoryModuleTypes.FLY_TARGET.get(), MemoryStatus.VALUE_ABSENT
        ), 1, 2400);
        this.graphProviderMemory = graphProviderMemory;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, T mob) {
        if (mob.isSleeping()) {
            return false;
        }

        if (mob.getNavigation().isInProgress()) {
            return false;
        }

        GlobalPos pos = mob.getBrain().getMemory(graphProviderMemory).orElse(null);
        if (pos == null || !pos.dimension().equals(level.dimension())) {
            return false;
        }

        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, T mob, long gameTime) {
        return phase != Phase.COMPLETE;
    }

    @Override
    protected void start(ServerLevel level, T mob, long gameTime) {
        lastNavTick = 0;
        currentHub = null;

        GlobalPos pos = mob.getBrain().getMemory(graphProviderMemory).orElse(null);
        if (pos == null) {
            phase = Phase.COMPLETE;
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos.pos());
        if (!(blockEntity instanceof IGraphProvider provider)) {
            phase = Phase.COMPLETE;
            return;
        }

        graph = provider.getGraph();
        if (graph == null) {
            phase = Phase.COMPLETE;
            return;
        }

        destination = getDestination(graph);
        if (destination == null) {
            phase = Phase.COMPLETE;
            return;
        }

        BlockPos entryPoint = getEntryPoint(graph, mob);
        if (entryPoint == null) {
            phase = Phase.FOLLOW_GRAPH;
            Cell cell = graph.findContainingCell(mob.blockPosition());
            if (cell != null) {
                currentHub = cell.getHub();
            }
        } else {
            phase = Phase.GO_TO_ENTRY;
            WalkTarget target = new WalkTarget(entryPoint, (float) AIConstants.DEFAULT_FLY_SPEED, 2);
            mob.getBrain().setMemory(MemoryModuleTypes.FLY_TARGET.get(), target);
        }
    }

    @Override
    protected void stop(ServerLevel level, T mob, long gameTime) {
        mob.getBrain().eraseMemory(MemoryModuleTypes.FLY_TARGET.get());
        graph = null;
        destination = null;
        currentHub = null;
    }

    @Override
    protected void tick(ServerLevel level, T mob, long gameTime) {
        if (phase == Phase.GO_TO_ENTRY) {
            WalkTarget flyTarget = mob.getBrain().getMemory(MemoryModuleTypes.FLY_TARGET.get()).orElse(null);
            if (flyTarget != null) {
                BlockPos targetPos = flyTarget.getTarget().currentBlockPosition();
                Cell cell = graph.findContainingCell(mob.blockPosition());
                if (cell != null && cell.getHub().equals(targetPos)) {
                    transitionToFollowGraph(mob, cell.getHub());
                }
            }
        }

        if (gameTime - lastNavTick < AIConstants.NAV_INTERVAL_TICKS) {
            return;
        }
        lastNavTick = gameTime;

        if (phase == Phase.GO_TO_ENTRY) {
            WalkTarget flyTarget = mob.getBrain().getMemory(MemoryModuleTypes.FLY_TARGET.get()).orElse(null);
            if (flyTarget != null) {
                BlockPos targetPos = flyTarget.getTarget().currentBlockPosition();
                mob.getNavigation().moveTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5,
                    AIConstants.DEFAULT_FLY_SPEED
                );
            }
            return;
        }

        if (phase == Phase.FOLLOW_GRAPH) {
            Cell cell = graph.findContainingCell(mob.blockPosition());
            if (cell == null) {
                phase = Phase.COMPLETE;
                return;
            }

            BlockPos cellHub = cell.getHub();
            if (cellHub.equals(currentHub) && mob.getNavigation().isInProgress()) {
                return;
            }
            currentHub = cellHub;

            BlockPos nextHub = graph.getNextHop(currentHub, destination);
            if (nextHub == null) {
                phase = Phase.COMPLETE;
                return;
            }

            mob.getNavigation().moveTo(
                nextHub.getX() + 0.5,
                nextHub.getY() + 0.5,
                nextHub.getZ() + 0.5,
                AIConstants.DEFAULT_FLY_SPEED
            );
        }
    }

    private void transitionToFollowGraph(T mob, BlockPos hub) {
        mob.getBrain().eraseMemory(MemoryModuleTypes.FLY_TARGET.get());
        mob.getNavigation().stop();
        phase = Phase.FOLLOW_GRAPH;
        currentHub = hub;
    }

    protected abstract BlockPos getEntryPoint(Graph graph, T mob);

    protected abstract BlockPos getDestination(Graph graph);

    private enum Phase {
        GO_TO_ENTRY,
        FOLLOW_GRAPH,
        COMPLETE
    }
}
