package org.sosly.ecotale.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared validators for flow field generation and validation.
 * All methods return false on exceptions - callers handle their own abort/retry logic.
 */
public final class FlowFieldValidators {
    private FlowFieldValidators() {}

    /**
     * Validates that a bat can cross from one cell to an adjacent cell.
     * Checks for an air boundary crossing with line-of-sight to both hubs.
     */
    public static boolean isCellTransitionValid(Level level, FlowFieldCell from, FlowFieldCell to, BlockPos fromHub, BlockPos toHub) {
        Direction direction = getDirectionBetweenCells(from, to);
        if (direction == null) {
            return false;
        }

        int resolution = FlowFieldCell.RESOLUTION;
        BlockPos boundaryStart = getBoundaryStart(from, direction);

        BlockPos[] samples = getSamplePositions(boundaryStart, direction, resolution);
        for (BlockPos sample : samples) {
            if (checkCrossing(level, sample, direction, fromHub, toHub)) {
                return true;
            }
        }

        for (int u = 0; u < resolution; u++) {
            for (int v = 0; v < resolution; v++) {
                BlockPos pos = getPositionOnBoundary(boundaryStart, direction, u, v);
                if (checkCrossing(level, pos, direction, fromHub, toHub)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Validates that a hub position is still navigable (air block).
     */
    public static boolean isHubValid(Level level, BlockPos hub) {
        try {
            return level.getBlockState(hub).isAir();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates that an exit point is still usable (air with sky access).
     */
    public static boolean isExitValid(Level level, BlockPos exit) {
        try {
            return level.getBlockState(exit).isAir() && level.canSeeSky(exit);
        } catch (Exception e) {
            return false;
        }
    }

    private static Direction getDirectionBetweenCells(FlowFieldCell from, FlowFieldCell to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();

        if (dx == 1 && dy == 0 && dz == 0) {
            return Direction.EAST;
        }
        if (dx == -1 && dy == 0 && dz == 0) {
            return Direction.WEST;
        }
        if (dx == 0 && dy == 1 && dz == 0) {
            return Direction.UP;
        }
        if (dx == 0 && dy == -1 && dz == 0) {
            return Direction.DOWN;
        }
        if (dx == 0 && dy == 0 && dz == 1) {
            return Direction.SOUTH;
        }
        if (dx == 0 && dy == 0 && dz == -1) {
            return Direction.NORTH;
        }
        return null;
    }

    private static BlockPos[] getSamplePositions(BlockPos boundaryStart, Direction direction, int resolution) {
        int mid = resolution / 2;
        int max = resolution - 1;

        return new BlockPos[] {
            getPositionOnBoundary(boundaryStart, direction, mid, mid),
            getPositionOnBoundary(boundaryStart, direction, 0, 0),
            getPositionOnBoundary(boundaryStart, direction, max, 0),
            getPositionOnBoundary(boundaryStart, direction, 0, max),
            getPositionOnBoundary(boundaryStart, direction, max, max),
            getPositionOnBoundary(boundaryStart, direction, mid, 0),
            getPositionOnBoundary(boundaryStart, direction, mid, max),
            getPositionOnBoundary(boundaryStart, direction, 0, mid),
            getPositionOnBoundary(boundaryStart, direction, max, mid)
        };
    }

    private static BlockPos getBoundaryStart(FlowFieldCell cell, Direction direction) {
        int resolution = FlowFieldCell.RESOLUTION;
        int baseX = cell.x() * resolution;
        int baseY = cell.y() * resolution;
        int baseZ = cell.z() * resolution;

        return switch (direction) {
            case EAST -> new BlockPos(baseX + resolution - 1, baseY, baseZ);
            case WEST -> new BlockPos(baseX, baseY, baseZ);
            case UP -> new BlockPos(baseX, baseY + resolution - 1, baseZ);
            case DOWN -> new BlockPos(baseX, baseY, baseZ);
            case SOUTH -> new BlockPos(baseX, baseY, baseZ + resolution - 1);
            case NORTH -> new BlockPos(baseX, baseY, baseZ);
        };
    }

    private static BlockPos getPositionOnBoundary(BlockPos start, Direction direction, int u, int v) {
        return switch (direction.getAxis()) {
            case X -> start.offset(0, u, v);
            case Y -> start.offset(u, 0, v);
            case Z -> start.offset(u, v, 0);
        };
    }

    private static boolean checkCrossing(Level level, BlockPos boundaryPos, Direction direction, BlockPos fromHub, BlockPos toHub) {
        try {
            if (!level.getBlockState(boundaryPos).isAir()) {
                return false;
            }

            BlockPos otherSide = boundaryPos.relative(direction);
            if (!level.getBlockState(otherSide).isAir()) {
                return false;
            }

            if (!raycastClear(level, Vec3.atCenterOf(fromHub), Vec3.atCenterOf(boundaryPos))) {
                return false;
            }

            return raycastClear(level, Vec3.atCenterOf(otherSide), Vec3.atCenterOf(toHub));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean raycastClear(Level level, Vec3 from, Vec3 to) {
        try {
            ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            BlockHitResult result = level.clip(context);
            return result.getType() == HitResult.Type.MISS;
        } catch (Exception e) {
            return false;
        }
    }
}
