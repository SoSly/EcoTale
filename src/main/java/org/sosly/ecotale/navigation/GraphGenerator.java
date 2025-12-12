package org.sosly.ecotale.navigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class GraphGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RADIUS_BLOCKS = 128;
    private static final int HUB_SEARCH_RADIUS = 4;
    private static final int EXIT_CLEARANCE = 3;
    private static final long TIMEOUT_MS = 5000;
    private static final int TIMEOUT_CHECK_INTERVAL = 100;

    private final BlockPos roostPos;
    private final Level level;
    private final EntityType<?> entityType;
    private final Entity dummyEntity;

    private long startTime;
    private boolean aborted;
    private int iterationCount;

    public GraphGenerator(BlockPos roostPos, Level level, EntityType<?> entityType) {
        this.roostPos = roostPos;
        this.level = level;
        this.entityType = entityType;
        this.dummyEntity = entityType.create(level);
    }

    @Nullable
    public Graph generate() {
        startTime = System.currentTimeMillis();
        aborted = false;
        iterationCount = 0;

        BlockPos startHub = findReachableStartCell();
        if (aborted || startHub == null) {
            LOGGER.warn("Graph generation failed: no navigable start cell found near {}", roostPos);
            return null;
        }

        Cell startCell = Cell.fromHubPosition(startHub);
        Map<BlockPos, Cell> cells = new HashMap<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();
        List<Cell> exitCells = new ArrayList<>();

        cells.put(startHub, startCell);
        parents.put(startHub, null);
        adjacency.put(startHub, new HashSet<>());

        long floodFillStart = System.currentTimeMillis();
        if (!runFloodFill(startCell, cells, parents, adjacency, exitCells)) {
            LOGGER.warn("Graph generation failed: flood fill found no exits (cells={}, aborted={}, time={}ms)",
                cells.size(), aborted, System.currentTimeMillis() - floodFillStart);
            return null;
        }
        LOGGER.info("Flood fill completed: {} cells, {} exit cells, {}ms",
            cells.size(), exitCells.size(), System.currentTimeMillis() - floodFillStart);

        long extendStart = System.currentTimeMillis();
        Set<BlockPos> graphExits = new HashSet<>();
        if (!extendExits(exitCells, cells, parents, adjacency, graphExits)) {
            LOGGER.warn("Graph generation failed: exit extension failed (cells={}, exitCells={}, aborted={}, time={}ms)",
                cells.size(), exitCells.size(), aborted, System.currentTimeMillis() - extendStart);
            return null;
        }
        LOGGER.info("Exit extension completed: {} cells, {} graph exits, {}ms",
            cells.size(), graphExits.size(), System.currentTimeMillis() - extendStart);

        long pathStart = System.currentTimeMillis();
        if (!buildPathTables(cells, parents, adjacency, startHub, graphExits)) {
            LOGGER.warn("Graph generation failed: path table building failed (cells={}, exits={}, aborted={}, time={}ms)",
                cells.size(), graphExits.size(), aborted, System.currentTimeMillis() - pathStart);
            return null;
        }
        LOGGER.info("Path tables completed: {} cells, {} destinations, {}ms",
            cells.size(), graphExits.size() + 1, System.currentTimeMillis() - pathStart);

        Graph.Builder builder = Graph.builder(roostPos, entityType);
        builder.setGraphStart(startHub);
        for (BlockPos exitHub : graphExits) {
            builder.addGraphExit(exitHub);
        }
        for (Cell cell : cells.values()) {
            builder.addCell(cell);
        }

        return builder.build();
    }

    @Nullable
    private BlockPos findReachableStartCell() {
        Cell startCellCoords = Cell.fromHubPosition(roostPos);
        BlockPos hub = findHubReachableFromRoost(startCellCoords);
        if (hub != null) {
            return hub;
        }

        Cell[] neighbors = {
            Cell.fromHubPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX,
                (int) startCellCoords.getBounds().minY - Cell.RESOLUTION,
                (int) startCellCoords.getBounds().minZ
            )),
            Cell.fromHubPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX - Cell.RESOLUTION,
                (int) startCellCoords.getBounds().minY,
                (int) startCellCoords.getBounds().minZ
            )),
            Cell.fromHubPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX + Cell.RESOLUTION,
                (int) startCellCoords.getBounds().minY,
                (int) startCellCoords.getBounds().minZ
            )),
            Cell.fromHubPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX,
                (int) startCellCoords.getBounds().minY,
                (int) startCellCoords.getBounds().minZ - Cell.RESOLUTION
            )),
            Cell.fromHubPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX,
                (int) startCellCoords.getBounds().minY,
                (int) startCellCoords.getBounds().minZ + Cell.RESOLUTION
            )),
            Cell.fromHubPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX,
                (int) startCellCoords.getBounds().minY + Cell.RESOLUTION,
                (int) startCellCoords.getBounds().minZ
            ))
        };

        for (Cell neighbor : neighbors) {
            BlockPos neighborHub = findHubReachableFromRoost(neighbor);
            if (neighborHub != null) {
                return neighborHub;
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findHubReachableFromRoost(Cell cell) {
        Vec3 roostVec = Vec3.atCenterOf(roostPos.below());
        AABB bounds = cell.getBounds();
        BlockPos center = new BlockPos(
            (int) (bounds.minX + Cell.RESOLUTION / 2),
            (int) (bounds.minY + Cell.RESOLUTION / 2),
            (int) (bounds.minZ + Cell.RESOLUTION / 2)
        );

        if (isAir(center) && raycastClear(roostVec, Vec3.atCenterOf(center))) {
            return center;
        }

        for (int radius = 1; radius <= HUB_SEARCH_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) {
                            continue;
                        }
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isWithinCell(candidate, bounds) && isAir(candidate)
                                && raycastClear(roostVec, Vec3.atCenterOf(candidate))) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean runFloodFill(Cell startCell, Map<BlockPos, Cell> cells,
                                  Map<BlockPos, BlockPos> parents,
                                  Map<BlockPos, Set<BlockPos>> adjacency,
                                  List<Cell> exitCells) {
        Queue<Cell> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        frontier.add(startCell);
        visited.add(startCell.getGridId());

        while (!frontier.isEmpty()) {
            if (checkAborted()) {
                return false;
            }

            Cell current = frontier.poll();

            if (isExit(current)) {
                exitCells.add(current);
                continue;
            }

            if (roostPos.distSqr(current.getHub()) > MAX_RADIUS_BLOCKS * MAX_RADIUS_BLOCKS) {
                continue;
            }

            for (Cell neighbor : getPassableNeighbors(current)) {
                if (aborted) {
                    return false;
                }
                if (visited.contains(neighbor.getGridId())) {
                    continue;
                }

                visited.add(neighbor.getGridId());
                cells.put(neighbor.getHub(), neighbor);
                parents.put(neighbor.getHub(), current.getHub());

                adjacency.putIfAbsent(current.getHub(), new HashSet<>());
                adjacency.putIfAbsent(neighbor.getHub(), new HashSet<>());
                adjacency.get(current.getHub()).add(neighbor.getHub());
                adjacency.get(neighbor.getHub()).add(current.getHub());

                frontier.add(neighbor);
            }
        }

        return !exitCells.isEmpty();
    }

    private List<Cell> getPassableNeighbors(Cell cell) {
        List<Cell> neighbors = new ArrayList<>();
        BlockPos cellHub = cell.getHub();

        for (Direction direction : Direction.values()) {
            AABB neighborBounds = cell.getBounds().move(
                direction.getStepX() * Cell.RESOLUTION,
                direction.getStepY() * Cell.RESOLUTION,
                direction.getStepZ() * Cell.RESOLUTION
            );

            BlockPos neighborHub = findHubReachableFrom(neighborBounds, direction.getOpposite(), cellHub);
            if (neighborHub != null) {
                Cell neighbor = new Cell(neighborBounds, neighborHub);
                if (Validators.isCellTransitionValid(level, cell, neighbor, dummyEntity)) {
                    neighbors.add(neighbor);
                }
            }
        }

        return neighbors;
    }

    @Nullable
    private BlockPos findHubReachableFrom(AABB bounds, Direction entryDirection, BlockPos sourceHub) {
        BlockPos boundaryStart = getBoundaryStart(bounds, entryDirection);
        int resolution = Cell.RESOLUTION;

        for (int u = 0; u < resolution; u++) {
            for (int v = 0; v < resolution; v++) {
                BlockPos boundaryPos = getPositionOnBoundary(boundaryStart, entryDirection, u, v);
                if (!isAir(boundaryPos)) {
                    continue;
                }

                BlockPos otherSide = boundaryPos.relative(entryDirection.getOpposite());
                if (!isAir(otherSide)) {
                    continue;
                }

                if (!raycastClear(Vec3.atCenterOf(sourceHub), Vec3.atCenterOf(otherSide))) {
                    continue;
                }

                BlockPos hub = findHubReachableFromEntry(bounds, boundaryPos);
                if (hub != null) {
                    return hub;
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findHubReachableFromEntry(AABB bounds, BlockPos entryPoint) {
        BlockPos center = new BlockPos(
            (int) (bounds.minX + Cell.RESOLUTION / 2),
            (int) (bounds.minY + Cell.RESOLUTION / 2),
            (int) (bounds.minZ + Cell.RESOLUTION / 2)
        );

        if (isAir(center) && raycastClear(Vec3.atCenterOf(entryPoint), Vec3.atCenterOf(center))) {
            return center;
        }

        for (int radius = 1; radius <= HUB_SEARCH_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) {
                            continue;
                        }
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isWithinCell(candidate, bounds) && isAir(candidate)
                                && raycastClear(Vec3.atCenterOf(entryPoint), Vec3.atCenterOf(candidate))) {
                            return candidate;
                        }
                    }
                }
            }
        }

        if (isWithinCell(entryPoint, bounds) && isAir(entryPoint)) {
            return entryPoint;
        }

        return null;
    }

    private boolean isExit(Cell cell) {
        BlockPos hub = cell.getHub();
        AABB bounds = cell.getBounds();
        int baseX = (int) bounds.minX;
        int baseY = (int) bounds.minY;
        int baseZ = (int) bounds.minZ;

        for (int dx = 0; dx < Cell.RESOLUTION; dx++) {
            for (int dy = 0; dy < Cell.RESOLUTION; dy++) {
                for (int dz = 0; dz < Cell.RESOLUTION; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (isAir(pos) && hasSkyAccess(pos) && raycastClear(Vec3.atCenterOf(hub), Vec3.atCenterOf(pos))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean extendExits(List<Cell> exitCells, Map<BlockPos, Cell> cells,
                                 Map<BlockPos, BlockPos> parents,
                                 Map<BlockPos, Set<BlockPos>> adjacency,
                                 Set<BlockPos> graphExits) {
        for (Cell exitCell : exitCells) {
            if (checkAborted()) {
                return false;
            }

            BlockPos exitPoint = findPreciseExitPoint(exitCell);
            if (exitPoint == null) {
                continue;
            }

            Cell extendedCell = extendExitOutside(exitCell, exitPoint, cells, parents, adjacency);
            if (extendedCell != null) {
                graphExits.add(extendedCell.getHub());
            }
        }

        return !graphExits.isEmpty();
    }

    @Nullable
    private BlockPos findPreciseExitPoint(Cell exitCell) {
        BlockPos hub = exitCell.getHub();
        AABB bounds = exitCell.getBounds();
        int baseX = (int) bounds.minX;
        int baseY = (int) bounds.minY;
        int baseZ = (int) bounds.minZ;

        BlockPos bestCandidate = null;
        for (int dx = 0; dx < Cell.RESOLUTION; dx++) {
            for (int dy = 0; dy < Cell.RESOLUTION; dy++) {
                for (int dz = 0; dz < Cell.RESOLUTION; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (isAir(pos) && hasSkyAccess(pos) && raycastClear(Vec3.atCenterOf(hub), Vec3.atCenterOf(pos))) {
                        if (bestCandidate == null || pos.getY() > bestCandidate.getY()) {
                            bestCandidate = pos;
                        }
                    }
                }
            }
        }

        return elevateExitPoint(bestCandidate);
    }

    @Nullable
    private BlockPos elevateExitPoint(BlockPos baseExit) {
        if (baseExit == null) {
            return null;
        }

        BlockPos best = baseExit;
        for (int i = 1; i <= EXIT_CLEARANCE; i++) {
            BlockPos candidate = baseExit.above(i);
            if (!isAir(candidate)) {
                break;
            }
            if (!hasSkyAccess(candidate)) {
                break;
            }
            best = candidate;
        }
        return best;
    }

    @Nullable
    private Cell extendExitOutside(Cell exitCell, BlockPos exitPoint,
                                     Map<BlockPos, Cell> cells,
                                     Map<BlockPos, BlockPos> parents,
                                     Map<BlockPos, Set<BlockPos>> adjacency) {
        Cell previousCell = exitCell;
        Cell lastCell = exitCell;

        for (int extension = 0; extension < MAX_RADIUS_BLOCKS / Cell.RESOLUTION; extension++) {
            if (checkAborted()) {
                return null;
            }

            BlockPos extensionHub = new BlockPos(
                exitPoint.getX(),
                (int) previousCell.getBounds().minY + Cell.RESOLUTION * (extension + 1),
                exitPoint.getZ()
            );

            Cell extensionCell = Cell.fromHubPosition(extensionHub);

            if (!extensionCell.getHub().equals(lastCell.getHub())) {
                cells.put(extensionCell.getHub(), extensionCell);
                parents.put(extensionCell.getHub(), lastCell.getHub());

                adjacency.putIfAbsent(lastCell.getHub(), new HashSet<>());
                adjacency.putIfAbsent(extensionCell.getHub(), new HashSet<>());
                adjacency.get(lastCell.getHub()).add(extensionCell.getHub());
                adjacency.get(extensionCell.getHub()).add(lastCell.getHub());

                lastCell = extensionCell;
            }

            if (Validators.isFullyOutside(level, extensionCell.getBounds())) {
                return extensionCell;
            }
        }

        return lastCell;
    }

    private boolean buildPathTables(Map<BlockPos, Cell> cells,
                                     Map<BlockPos, BlockPos> parents,
                                     Map<BlockPos, Set<BlockPos>> adjacency,
                                     BlockPos graphStart,
                                     Set<BlockPos> graphExits) {
        for (Cell cell : cells.values()) {
            BlockPos cellHub = cell.getHub();

            if (cellHub.equals(graphStart)) {
                cell.setPath(graphStart, null);
            } else {
                BlockPos nextHop = parents.get(cellHub);
                cell.setPath(graphStart, nextHop);
            }
        }

        for (BlockPos exitHub : graphExits) {
            if (checkAborted()) {
                return false;
            }

            Map<BlockPos, BlockPos> exitPaths = reverseBfsFromExit(exitHub, adjacency);
            for (Cell cell : cells.values()) {
                BlockPos cellHub = cell.getHub();
                if (cellHub.equals(exitHub)) {
                    cell.setPath(exitHub, null);
                } else {
                    BlockPos nextHop = exitPaths.get(cellHub);
                    if (nextHop != null) {
                        cell.setPath(exitHub, nextHop);
                    }
                }
            }
        }

        return true;
    }

    private Map<BlockPos, BlockPos> reverseBfsFromExit(BlockPos exitHub, Map<BlockPos, Set<BlockPos>> adjacency) {
        Map<BlockPos, BlockPos> nextHops = new HashMap<>();
        Queue<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        frontier.add(exitHub);
        visited.add(exitHub);
        nextHops.put(exitHub, null);

        while (!frontier.isEmpty()) {
            BlockPos current = frontier.poll();

            Set<BlockPos> neighbors = adjacency.get(current);
            if (neighbors == null) {
                continue;
            }

            for (BlockPos neighbor : neighbors) {
                if (visited.contains(neighbor)) {
                    continue;
                }

                visited.add(neighbor);
                nextHops.put(neighbor, current);
                frontier.add(neighbor);
            }
        }

        return nextHops;
    }

    private boolean checkAborted() {
        if (Thread.currentThread().isInterrupted()) {
            aborted = true;
            return true;
        }
        return checkTimeout();
    }

    private boolean checkTimeout() {
        iterationCount++;
        if (iterationCount % TIMEOUT_CHECK_INTERVAL != 0) {
            return false;
        }

        if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
            aborted = true;
            return true;
        }
        return false;
    }

    private BlockPos getBoundaryStart(AABB bounds, Direction direction) {
        int resolution = Cell.RESOLUTION;
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

    private BlockPos getPositionOnBoundary(BlockPos start, Direction direction, int u, int v) {
        return switch (direction.getAxis()) {
            case X -> start.offset(0, u, v);
            case Y -> start.offset(u, 0, v);
            case Z -> start.offset(u, v, 0);
        };
    }

    private boolean isAir(BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            return state.isAir();
        } catch (Exception e) {
            aborted = true;
            return false;
        }
    }

    private boolean hasSkyAccess(BlockPos pos) {
        try {
            return level.canSeeSky(pos);
        } catch (Exception e) {
            aborted = true;
            return false;
        }
    }

    private boolean isWithinCell(BlockPos pos, AABB bounds) {
        return pos.getX() >= bounds.minX && pos.getX() < bounds.maxX
            && pos.getY() >= bounds.minY && pos.getY() < bounds.maxY
            && pos.getZ() >= bounds.minZ && pos.getZ() < bounds.maxZ;
    }

    private boolean raycastClear(Vec3 from, Vec3 to) {
        try {
            ClipContext context = new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                dummyEntity
            );
            BlockHitResult result = level.clip(context);
            return result.getType() == HitResult.Type.MISS;
        } catch (Exception e) {
            aborted = true;
            return false;
        }
    }
}
