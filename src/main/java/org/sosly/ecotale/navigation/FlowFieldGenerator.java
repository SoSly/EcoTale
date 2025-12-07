package org.sosly.ecotale.navigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Generates flow field navigation data for bat cave navigation.
 * Uses BFS flood fill starting from roost, building inward (toward roost)
 * and outward (toward exit) vector fields.
 */
public class FlowFieldGenerator {
    private static final int MAX_RADIUS_BLOCKS = 128;
    private static final int MAX_RADIUS_CELLS = MAX_RADIUS_BLOCKS / FlowFieldCell.RESOLUTION;
    private static final int HUB_SEARCH_RADIUS = 4;
    private static final int EXTENSION_CELLS = 3;
    private static final int EXIT_CLEARANCE = 3;

    private final BlockPos roostPos;
    private final Level level;
    private final FlowFieldCell startCell;
    private final Queue<FlowFieldCell> frontier;
    private final Set<FlowFieldCell> visited;
    private final Map<FlowFieldCell, FlowFieldCell> cameFrom;
    private final Map<FlowFieldCell, BlockPos> hubCache;

    public FlowFieldGenerator(BlockPos roostPos, Level level) {
        this.roostPos = roostPos;
        this.level = level;
        this.startCell = FlowFieldCell.fromBlockPos(roostPos);
        this.frontier = new ArrayDeque<>();
        this.visited = new HashSet<>();
        this.cameFrom = new HashMap<>();
        this.hubCache = new HashMap<>();
    }

    /**
     * Generates a flow field solution for navigation from roost to cave exit.
     *
     * @return A valid FlowFieldSolution, or a failed solution if no exit found
     */
    public FlowFieldSolution generate() {
        FlowFieldCell exitCell = runFloodFill();
        if (exitCell == null) {
            return FlowFieldSolution.failed(roostPos);
        }

        BlockPos exitPoint = findPreciseExitPoint(exitCell);
        if (exitPoint == null) {
            return FlowFieldSolution.failed(roostPos);
        }

        Set<FlowFieldCell> pathCells = tracePathToExit(exitCell);
        Map<FlowFieldCell, Vec3> inwardField = buildInwardField(pathCells);
        extendInwardFieldOutside(inwardField, exitCell, exitPoint);
        Map<FlowFieldCell, Vec3> outwardField = buildOutwardField(inwardField);

        Map<FlowFieldCell, BlockPos> pathHubCache = new HashMap<>();
        for (FlowFieldCell cell : inwardField.keySet()) {
            BlockPos hub = hubCache.get(cell);
            if (hub != null) {
                pathHubCache.put(cell, hub);
            }
        }

        return FlowFieldSolution.create(outwardField, inwardField, pathHubCache, exitPoint, roostPos);
    }

    private Set<FlowFieldCell> tracePathToExit(FlowFieldCell exitCell) {
        Set<FlowFieldCell> pathCells = new HashSet<>();
        FlowFieldCell current = exitCell;

        while (current != null) {
            pathCells.add(current);
            current = cameFrom.get(current);
        }

        return pathCells;
    }

    private FlowFieldCell findReachableStartCell() {
        BlockPos hub = findHub(startCell);
        if (hub != null && canReachHub(hub)) {
            hubCache.put(startCell, hub);
            return startCell;
        }

        FlowFieldCell[] neighbors = {
            new FlowFieldCell(startCell.x(), startCell.y() - 1, startCell.z()),
            new FlowFieldCell(startCell.x() - 1, startCell.y(), startCell.z()),
            new FlowFieldCell(startCell.x() + 1, startCell.y(), startCell.z()),
            new FlowFieldCell(startCell.x(), startCell.y(), startCell.z() - 1),
            new FlowFieldCell(startCell.x(), startCell.y(), startCell.z() + 1),
            new FlowFieldCell(startCell.x(), startCell.y() + 1, startCell.z())
        };

        for (FlowFieldCell neighbor : neighbors) {
            BlockPos neighborHub = findHub(neighbor);
            if (neighborHub != null && canReachHub(neighborHub)) {
                hubCache.put(neighbor, neighborHub);
                return neighbor;
            }
        }

        return null;
    }

    private boolean canReachHub(BlockPos hub) {
        Vec3 roostVec = Vec3.atCenterOf(roostPos.below());
        Vec3 hubVec = Vec3.atCenterOf(hub);
        return raycastClear(roostVec, hubVec);
    }

    private FlowFieldCell runFloodFill() {
        FlowFieldCell actualStart = findReachableStartCell();
        if (actualStart == null) {
            return null;
        }

        frontier.add(actualStart);
        visited.add(actualStart);

        while (!frontier.isEmpty()) {
            FlowFieldCell current = frontier.poll();

            if (isExit(current)) {
                return current;
            }

            if (distanceFromStart(current) > MAX_RADIUS_CELLS) {
                continue;
            }

            for (FlowFieldCell neighbor : getPassableNeighbors(current)) {
                if (visited.contains(neighbor)) {
                    continue;
                }
                visited.add(neighbor);
                frontier.add(neighbor);
                cameFrom.put(neighbor, current);
            }
        }

        return null;
    }

    private List<FlowFieldCell> getPassableNeighbors(FlowFieldCell cell) {
        List<FlowFieldCell> neighbors = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            FlowFieldCell neighbor = new FlowFieldCell(
                cell.x() + direction.getStepX(),
                cell.y() + direction.getStepY(),
                cell.z() + direction.getStepZ()
            );

            BlockPos neighborHub = hubCache.get(neighbor);
            if (neighborHub == null) {
                neighborHub = findHub(neighbor);
                if (neighborHub == null) {
                    continue;
                }
                hubCache.put(neighbor, neighborHub);
            }

            if (tryBoundaryCrossing(cell, neighbor, direction, neighborHub)) {
                neighbors.add(neighbor);
            }
        }

        return neighbors;
    }

    private BlockPos findHub(FlowFieldCell cell) {
        BlockPos center = cell.centerBlockPos();
        if (isAir(center)) {
            return center;
        }

        for (int radius = 1; radius <= HUB_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) {
                            continue;
                        }
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isWithinCell(candidate, cell) && isAir(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean tryBoundaryCrossing(FlowFieldCell from, FlowFieldCell to, Direction direction, BlockPos toHub) {
        BlockPos boundaryStart = getBoundaryStart(from, direction);
        int resolution = FlowFieldCell.RESOLUTION;

        BlockPos[] samples = getSamplePositions(boundaryStart, direction, resolution);
        for (BlockPos sample : samples) {
            if (checkCrossing(sample, direction, toHub)) {
                return true;
            }
        }

        for (int u = 0; u < resolution; u++) {
            for (int v = 0; v < resolution; v++) {
                BlockPos pos = getPositionOnBoundary(boundaryStart, direction, u, v);
                if (checkCrossing(pos, direction, toHub)) {
                    return true;
                }
            }
        }

        return false;
    }

    private BlockPos[] getSamplePositions(BlockPos boundaryStart, Direction direction, int resolution) {
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

    private BlockPos getBoundaryStart(FlowFieldCell cell, Direction direction) {
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

    private BlockPos getPositionOnBoundary(BlockPos start, Direction direction, int u, int v) {
        return switch (direction.getAxis()) {
            case X -> start.offset(0, u, v);
            case Y -> start.offset(u, 0, v);
            case Z -> start.offset(u, v, 0);
        };
    }

    private boolean checkCrossing(BlockPos boundaryPos, Direction direction, BlockPos toHub) {
        if (!isAir(boundaryPos)) {
            return false;
        }

        BlockPos otherSide = boundaryPos.relative(direction);
        if (!isAir(otherSide)) {
            return false;
        }

        return raycastClear(Vec3.atCenterOf(otherSide), Vec3.atCenterOf(toHub));
    }

    private boolean isExit(FlowFieldCell cell) {
        int resolution = FlowFieldCell.RESOLUTION;
        int baseX = cell.x() * resolution;
        int baseY = cell.y() * resolution;
        int baseZ = cell.z() * resolution;

        for (int dx = 0; dx < resolution; dx++) {
            for (int dy = 0; dy < resolution; dy++) {
                for (int dz = 0; dz < resolution; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (isAir(pos) && hasSkyAccess(pos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private BlockPos findPreciseExitPoint(FlowFieldCell exitCell) {
        FlowFieldCell previousCell = cameFrom.get(exitCell);
        if (previousCell == null) {
            return findAnySkyAccessBlock(exitCell);
        }

        Direction entryDirection = getDirectionBetweenCells(previousCell, exitCell);
        BlockPos boundaryStart = getBoundaryStart(previousCell, entryDirection);
        int resolution = FlowFieldCell.RESOLUTION;

        BlockPos bestCandidate = null;
        for (int u = 0; u < resolution; u++) {
            for (int v = 0; v < resolution; v++) {
                BlockPos pos = getPositionOnBoundary(boundaryStart, entryDirection, u, v);
                BlockPos inExitCell = pos.relative(entryDirection);
                if (isAir(inExitCell) && hasSkyAccess(inExitCell)) {
                    if (bestCandidate == null) {
                        bestCandidate = inExitCell;
                    }
                }
            }
        }

        if (bestCandidate == null) {
            return findAnySkyAccessBlock(exitCell);
        }

        return bestCandidate.above(EXIT_CLEARANCE);
    }

    private BlockPos findAnySkyAccessBlock(FlowFieldCell cell) {
        int resolution = FlowFieldCell.RESOLUTION;
        int baseX = cell.x() * resolution;
        int baseY = cell.y() * resolution;
        int baseZ = cell.z() * resolution;

        for (int dx = 0; dx < resolution; dx++) {
            for (int dy = 0; dy < resolution; dy++) {
                for (int dz = 0; dz < resolution; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (isAir(pos) && hasSkyAccess(pos)) {
                        return pos.above(EXIT_CLEARANCE);
                    }
                }
            }
        }

        return null;
    }

    private Direction getDirectionBetweenCells(FlowFieldCell from, FlowFieldCell to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();

        if (dx == 1) {
            return Direction.EAST;
        }
        if (dx == -1) {
            return Direction.WEST;
        }
        if (dy == 1) {
            return Direction.UP;
        }
        if (dy == -1) {
            return Direction.DOWN;
        }
        if (dz == 1) {
            return Direction.SOUTH;
        }
        return Direction.NORTH;
    }

    private Map<FlowFieldCell, Vec3> buildInwardField(Set<FlowFieldCell> pathCells) {
        Map<FlowFieldCell, Vec3> inwardField = new HashMap<>();

        for (FlowFieldCell cell : pathCells) {
            FlowFieldCell parent = cameFrom.get(cell);
            if (parent == null) {
                continue;
            }
            Vec3 direction = vectorBetweenCells(cell, parent).normalize();
            inwardField.put(cell, direction);
        }

        return inwardField;
    }

    private void extendInwardFieldOutside(Map<FlowFieldCell, Vec3> inwardField, FlowFieldCell exitCell, BlockPos exitPoint) {
        FlowFieldCell previousCell = cameFrom.get(exitCell);
        if (previousCell == null) {
            return;
        }

        Vec3 exitDirection = vectorBetweenCells(previousCell, exitCell).normalize().scale(-1);
        FlowFieldCell exitPointCell = FlowFieldCell.fromBlockPos(exitPoint);

        for (int i = 1; i <= EXTENSION_CELLS; i++) {
            FlowFieldCell extensionCell = new FlowFieldCell(
                exitPointCell.x() + (int) Math.round(exitDirection.x * -i),
                exitPointCell.y() + (int) Math.round(exitDirection.y * -i),
                exitPointCell.z() + (int) Math.round(exitDirection.z * -i)
            );

            if (!cellHasSkyAccess(extensionCell)) {
                break;
            }

            Vec3 towardExit = vectorBetweenCells(extensionCell, exitPointCell).normalize();
            inwardField.put(extensionCell, towardExit);
            hubCache.put(extensionCell, extensionCell.centerBlockPos());
        }
    }

    private boolean cellHasSkyAccess(FlowFieldCell cell) {
        BlockPos center = cell.centerBlockPos();
        return hasSkyAccess(center);
    }

    private Map<FlowFieldCell, Vec3> buildOutwardField(Map<FlowFieldCell, Vec3> inwardField) {
        Map<FlowFieldCell, Vec3> outwardField = new HashMap<>();

        for (Map.Entry<FlowFieldCell, Vec3> entry : inwardField.entrySet()) {
            outwardField.put(entry.getKey(), entry.getValue().scale(-1));
        }

        return outwardField;
    }

    private Vec3 vectorBetweenCells(FlowFieldCell from, FlowFieldCell to) {
        return new Vec3(
            to.x() - from.x(),
            to.y() - from.y(),
            to.z() - from.z()
        );
    }

    private int distanceFromStart(FlowFieldCell cell) {
        return Math.max(
            Math.max(Math.abs(cell.x() - startCell.x()), Math.abs(cell.y() - startCell.y())),
            Math.abs(cell.z() - startCell.z())
        );
    }

    private boolean isAir(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir();
    }

    private boolean hasSkyAccess(BlockPos pos) {
        return level.canSeeSky(pos);
    }

    private boolean isWithinCell(BlockPos pos, FlowFieldCell cell) {
        int resolution = FlowFieldCell.RESOLUTION;
        int baseX = cell.x() * resolution;
        int baseY = cell.y() * resolution;
        int baseZ = cell.z() * resolution;

        return pos.getX() >= baseX && pos.getX() < baseX + resolution
            && pos.getY() >= baseY && pos.getY() < baseY + resolution
            && pos.getZ() >= baseZ && pos.getZ() < baseZ + resolution;
    }

    private boolean raycastClear(Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(
            from,
            to,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            null
        );
        BlockHitResult result = level.clip(context);
        return result.getType() == HitResult.Type.MISS;
    }
}
