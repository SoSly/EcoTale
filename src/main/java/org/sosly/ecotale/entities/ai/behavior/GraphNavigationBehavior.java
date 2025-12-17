package org.sosly.ecotale.entities.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.sosly.ecotale.api.IGraphProvider;
import org.sosly.ecotale.api.entities.IFlyingMob;
import org.sosly.ecotale.entities.ai.AIConstants;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;
import org.sosly.ecotale.navigation.Cell;
import org.sosly.ecotale.navigation.Graph;

public abstract class GraphNavigationBehavior<T extends Mob & IFlyingMob<T>> extends Behavior<T> {
    private static final int ARRIVAL_DISTANCE = 4;
    private static final double HUB_ARRIVAL_DISTANCE = 1.75;
    private static final double DIRECT_MOVE_DISTANCE = 6.0;

    private final MemoryModuleType<GlobalPos> graphProviderMemory;

    private Phase phase;
    private Graph graph;
    private BlockPos destination;
    private BlockPos targetHub;
    private long lastNavTick;

    protected GraphNavigationBehavior(MemoryModuleType<GlobalPos> graphProviderMemory) {
        super(ImmutableMap.of(
            graphProviderMemory, MemoryStatus.VALUE_PRESENT
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

        BlockEntity blockEntity = level.getBlockEntity(pos.pos());
        if (!(blockEntity instanceof IGraphProvider provider)) {
            return false;
        }

        Graph g = provider.getGraph();
        if (g == null) {
            return false;
        }

        BlockPos dest = getDestination(g);
        if (dest != null && mob.blockPosition().closerThan(dest, ARRIVAL_DISTANCE)) {
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
        targetHub = null;

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
        boolean alreadyAtEntry = entryPoint != null && mob.blockPosition().closerThan(entryPoint, ARRIVAL_DISTANCE);
        if (entryPoint == null || alreadyAtEntry) {
            phase = Phase.FOLLOW_GRAPH;
            BlockPos queryPos = entryPoint != null ? entryPoint : mob.blockPosition();
            targetHub = graph.getNextHop(queryPos, destination);
            WalkTarget target = new WalkTarget(destination, (float) AIConstants.DEFAULT_FLY_SPEED, 1);
            mob.getBrain().setMemory(MemoryModuleTypes.FLY_TARGET.get(), target);
        } else {
            phase = Phase.GO_TO_ENTRY;
            WalkTarget target = new WalkTarget(entryPoint, (float) AIConstants.DEFAULT_FLY_SPEED, 2);
            mob.getBrain().setMemory(MemoryModuleTypes.FLY_TARGET.get(), target);
        }
    }

    @Override
    protected void stop(ServerLevel level, T mob, long gameTime) {
        if (destination != null && mob.blockPosition().closerThan(destination, ARRIVAL_DISTANCE)) {
            mob.getBrain().eraseMemory(MemoryModuleTypes.FLY_TARGET.get());
        }
        graph = null;
        destination = null;
        targetHub = null;
    }

    @Override
    protected void tick(ServerLevel level, T mob, long gameTime) {
        boolean nearHub = targetHub != null && mob.blockPosition().closerThan(targetHub, HUB_ARRIVAL_DISTANCE);
        if (!nearHub && gameTime - lastNavTick < AIConstants.NAV_INTERVAL_TICKS) {
            return;
        }
        lastNavTick = gameTime;

        if (phase == Phase.GO_TO_ENTRY) {
            WalkTarget flyTarget = mob.getBrain().getMemory(MemoryModuleTypes.FLY_TARGET.get()).orElse(null);
            if (flyTarget == null) {
                return;
            }

            BlockPos entryPoint = flyTarget.getTarget().currentBlockPosition();
            Cell cell = graph.findContainingCell(mob.blockPosition());
            if (cell != null && cell.contains(entryPoint)) {
                transitionToFollowGraph(mob, entryPoint);
            } else {
                mob.getNavigation().moveTo(
                    entryPoint.getX() + 0.5,
                    entryPoint.getY() + 0.5,
                    entryPoint.getZ() + 0.5,
                    AIConstants.DEFAULT_FLY_SPEED
                );
                return;
            }
        }

        if (phase == Phase.FOLLOW_GRAPH) {
            if (targetHub != null && mob.getNavigation().isInProgress() && !nearHub) {
                return;
            }

            if (targetHub == null) {
                targetHub = graph.getNextHop(mob.blockPosition(), destination);
            } else if (nearHub) {
                targetHub = graph.getNextHop(targetHub, destination);
            }

            if (targetHub == null) {
                phase = Phase.COMPLETE;
                return;
            }

            double dist = Math.sqrt(mob.blockPosition().distSqr(targetHub));
            if (dist <= DIRECT_MOVE_DISTANCE && hasLineOfSight(level, mob, targetHub)) {
                mob.getMoveControl().setWantedPosition(
                    targetHub.getX() + 0.5,
                    targetHub.getY() + 0.5,
                    targetHub.getZ() + 0.5,
                    AIConstants.DEFAULT_FLY_SPEED
                );
                return;
            }

            mob.getNavigation().moveTo(
                targetHub.getX() + 0.5,
                targetHub.getY() + 0.5,
                targetHub.getZ() + 0.5,
                AIConstants.DEFAULT_FLY_SPEED
            );
        }
    }

    private boolean hasLineOfSight(ServerLevel level, T mob, BlockPos target) {
        Vec3 start = mob.getEyePosition();
        Vec3 end = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        ClipContext ctx = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob);
        BlockHitResult result = level.clip(ctx);
        return result.getType() == HitResult.Type.MISS;
    }

    private void transitionToFollowGraph(T mob, BlockPos entryPoint) {
        WalkTarget target = new WalkTarget(destination, (float) AIConstants.DEFAULT_FLY_SPEED, 1);
        mob.getBrain().setMemory(MemoryModuleTypes.FLY_TARGET.get(), target);
        mob.getNavigation().stop();
        phase = Phase.FOLLOW_GRAPH;
        targetHub = graph.getNextHop(entryPoint, destination);
    }

    protected abstract BlockPos getEntryPoint(Graph graph, T mob);

    protected abstract BlockPos getDestination(Graph graph);

    private enum Phase {
        GO_TO_ENTRY,
        FOLLOW_GRAPH,
        COMPLETE
    }
}
