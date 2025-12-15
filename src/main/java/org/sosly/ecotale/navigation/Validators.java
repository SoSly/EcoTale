package org.sosly.ecotale.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Validators {
    private Validators() {}

    public static boolean isCellTransitionValid(Level level, Cell from, Cell to, Entity entity) {
        Direction direction = getDirectionBetweenCells(from, to);
        if (direction == null) {
            return false;
        }

        int resolution = Cell.RESOLUTION;
        BlockPos boundaryStart = getBoundaryStart(from, direction);

        BlockPos[] samples = getSamplePositions(boundaryStart, direction, resolution);
        for (BlockPos sample : samples) {
            if (checkCrossing(level, sample, direction, from.getHub(), to.getHub(), entity)) {
                return true;
            }
        }

        for (int u = 0; u < resolution; u++) {
            for (int v = 0; v < resolution; v++) {
                BlockPos pos = getPositionOnBoundary(boundaryStart, direction, u, v);
                if (checkCrossing(level, pos, direction, from.getHub(), to.getHub(), entity)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isHubValid(Level level, BlockPos hub) {
        try {
            return level.getBlockState(hub).isAir();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isExitValid(Level level, BlockPos exit) {
        try {
            return level.getBlockState(exit).isAir() && level.canSeeSky(exit);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isFullyOutside(Level level, AABB bounds) {
        int minX = (int) bounds.minX;
        int minY = (int) bounds.minY;
        int minZ = (int) bounds.minZ;
        int resolution = Cell.RESOLUTION;

        for (int dx = 0; dx < resolution; dx++) {
            for (int dz = 0; dz < resolution; dz++) {
                BlockPos columnBase = new BlockPos(minX + dx, minY, minZ + dz);
                try {
                    if (!level.canSeeSky(columnBase)) {
                        return false;
                    }
                    for (int dy = 0; dy < resolution; dy++) {
                        if (!level.getBlockState(columnBase.above(dy)).isAir()) {
                            return false;
                        }
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Direction getDirectionBetweenCells(Cell from, Cell to) {
        Vec3 fromCenter = from.getBounds().getCenter();
        Vec3 toCenter = to.getBounds().getCenter();

        int dx = (int) Math.round((toCenter.x - fromCenter.x) / Cell.RESOLUTION);
        int dy = (int) Math.round((toCenter.y - fromCenter.y) / Cell.RESOLUTION);
        int dz = (int) Math.round((toCenter.z - fromCenter.z) / Cell.RESOLUTION);

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

    private static BlockPos getBoundaryStart(Cell cell, Direction direction) {
        int resolution = Cell.RESOLUTION;
        AABB bounds = cell.getBounds();
        int baseX = (int) bounds.minX;
        int baseY = (int) bounds.minY;
        int baseZ = (int) bounds.minZ;

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

    private static boolean checkCrossing(Level level, BlockPos boundaryPos, Direction direction,
                                         BlockPos fromHub, BlockPos toHub, Entity entity) {
        try {
            if (!level.getBlockState(boundaryPos).isAir()) {
                return false;
            }

            BlockPos otherSide = boundaryPos.relative(direction);
            if (!level.getBlockState(otherSide).isAir()) {
                return false;
            }

            if (!raycastClear(level, Vec3.atCenterOf(fromHub), Vec3.atCenterOf(boundaryPos), entity)) {
                return false;
            }

            return raycastClear(level, Vec3.atCenterOf(otherSide), Vec3.atCenterOf(toHub), entity);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean raycastClear(Level level, Vec3 from, Vec3 to, Entity entity) {
        try {
            ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
            BlockHitResult result = level.clip(context);
            return result.getType() == HitResult.Type.MISS;
        } catch (Exception e) {
            return false;
        }
    }
}
