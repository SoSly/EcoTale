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
    private static final long TIMEOUT_MS = 10000;
    private static final int TIMEOUT_CHECK_INTERVAL = 100;

    private final BlockPos roostPos;
    private final Level level;
    private final EntityType<?> entityType;
    private final Entity dummyEntity;

    private long startTime;
    private boolean aborted;
    private int iterationCount;

    private static class NeighborData {
        final Cell cell;
        final BlockPos boundaryCrossing;

        NeighborData(Cell cell, BlockPos boundaryCrossing) {
            this.cell = cell;
            this.boundaryCrossing = boundaryCrossing;
        }
    }

    private static class QueuedCell {
        final Cell cell;
        @Nullable
        final Direction entryDirection;
        @Nullable
        final BlockPos entryCrossing;

        QueuedCell(Cell cell, @Nullable Direction entryDirection, @Nullable BlockPos entryCrossing) {
            this.cell = cell;
            this.entryDirection = entryDirection;
            this.entryCrossing = entryCrossing;
        }
    }

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

        try {
            BlockPos startHub = findReachableStartCell();
            if (aborted || startHub == null) {
                LOGGER.warn("Graph generation failed: no navigable start cell found near {}", roostPos);
                return null;
            }

            Cell startCell = Cell.fromWorldPosition(startHub);
            Map<BlockPos, Cell> cells = new HashMap<>();
            Map<BlockPos, BlockPos> cellHubs = new HashMap<>();
            Map<BlockPos, BlockPos> parents = new HashMap<>();
            Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();
            List<Cell> exitCells = new ArrayList<>();

            BlockPos startGridKey = startCell.getGridKey();
            cells.put(startGridKey, startCell);
            cellHubs.put(startGridKey, startHub);
            parents.put(startGridKey, null);
            adjacency.put(startGridKey, new HashSet<>());

            long floodFillStart = System.currentTimeMillis();
            if (!runFloodFill(startCell, cells, cellHubs, parents, adjacency, exitCells)) {
                LOGGER.warn("Graph generation failed: flood fill found no exits (cells={}, aborted={}, time={}ms)",
                    cells.size(), aborted, System.currentTimeMillis() - floodFillStart);
                return null;
            }
            LOGGER.info("Flood fill completed: {} cells, {} exit cells, {}ms",
                cells.size(), exitCells.size(), System.currentTimeMillis() - floodFillStart);

            long extendStart = System.currentTimeMillis();
            Set<BlockPos> graphExits = new HashSet<>();
            if (!extendExits(exitCells, cells, cellHubs, parents, adjacency, graphExits)) {
                LOGGER.warn("Graph generation failed: exit extension failed (cells={}, exitCells={}, aborted={}, time={}ms)",
                    cells.size(), exitCells.size(), aborted, System.currentTimeMillis() - extendStart);
                return null;
            }
            LOGGER.info("Exit extension completed: {} cells, {} graph exits, {}ms",
                cells.size(), graphExits.size(), System.currentTimeMillis() - extendStart);

            long pathStart = System.currentTimeMillis();
            if (!buildPathTables(cells, cellHubs, parents, adjacency, startHub, graphExits)) {
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
        } finally {
            if (dummyEntity != null) {
                dummyEntity.discard();
            }
        }
    }

    @Nullable
    private BlockPos findReachableStartCell() {
        Cell startCellCoords = Cell.fromWorldPosition(roostPos);
        BlockPos hub = findHubReachableFromRoost(startCellCoords);
        if (hub != null) {
            return hub;
        }

        Cell[] neighbors = {
            Cell.fromWorldPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX,
                (int) startCellCoords.getBounds().minY - Cell.RESOLUTION,
                (int) startCellCoords.getBounds().minZ
            )),
            Cell.fromWorldPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX - Cell.RESOLUTION,
                (int) startCellCoords.getBounds().minY,
                (int) startCellCoords.getBounds().minZ
            )),
            Cell.fromWorldPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX + Cell.RESOLUTION,
                (int) startCellCoords.getBounds().minY,
                (int) startCellCoords.getBounds().minZ
            )),
            Cell.fromWorldPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX,
                (int) startCellCoords.getBounds().minY,
                (int) startCellCoords.getBounds().minZ - Cell.RESOLUTION
            )),
            Cell.fromWorldPosition(new BlockPos(
                (int) startCellCoords.getBounds().minX,
                (int) startCellCoords.getBounds().minY,
                (int) startCellCoords.getBounds().minZ + Cell.RESOLUTION
            )),
            Cell.fromWorldPosition(new BlockPos(
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
                        if (!isOnShellSurface(dx, dy, dz, radius)) {
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
                                  Map<BlockPos, BlockPos> cellHubs,
                                  Map<BlockPos, BlockPos> parents,
                                  Map<BlockPos, Set<BlockPos>> adjacency,
                                  List<Cell> exitCells) {
        Queue<QueuedCell> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        frontier.add(new QueuedCell(startCell, null, null));
        visited.add(startCell.getGridKey());

        while (!frontier.isEmpty()) {
            if (checkAborted()) {
                return false;
            }

            QueuedCell queuedCell = frontier.poll();
            Cell current = queuedCell.cell;
            Direction entryDirection = queuedCell.entryDirection;
            BlockPos entryCrossing = queuedCell.entryCrossing;
            BlockPos currentGridKey = current.getGridKey();
            AABB currentBounds = current.getBounds();

            if (isReachableExitCell(current, entryCrossing)) {
                exitCells.add(current);
                continue;
            }

            BlockPos currentCenter = new BlockPos(
                (int) (currentBounds.minX + Cell.RESOLUTION / 2),
                (int) (currentBounds.minY + Cell.RESOLUTION / 2),
                (int) (currentBounds.minZ + Cell.RESOLUTION / 2)
            );
            if (roostPos.distSqr(currentCenter) > MAX_RADIUS_BLOCKS * MAX_RADIUS_BLOCKS) {
                continue;
            }

            for (NeighborData neighborData : getPassableNeighbors(current, entryDirection, entryCrossing)) {
                if (aborted) {
                    return false;
                }
                Cell neighbor = neighborData.cell;
                BlockPos neighborGridKey = neighbor.getGridKey();
                if (visited.contains(neighborGridKey)) {
                    continue;
                }

                Direction exitDirection = getDirectionBetweenCells(current, neighbor);
                if (exitDirection == null) {
                    continue;
                }

                visited.add(neighborGridKey);
                cells.put(neighborGridKey, neighbor);
                parents.put(neighborGridKey, currentGridKey);

                adjacency.putIfAbsent(currentGridKey, new HashSet<>());
                adjacency.putIfAbsent(neighborGridKey, new HashSet<>());
                adjacency.get(currentGridKey).add(neighborGridKey);
                adjacency.get(neighborGridKey).add(currentGridKey);

                BlockPos neighborEntryCrossing = neighborData.boundaryCrossing.relative(exitDirection);
                frontier.add(new QueuedCell(neighbor, exitDirection.getOpposite(), neighborEntryCrossing));
            }
        }

        return !exitCells.isEmpty();
    }

    private List<NeighborData> getPassableNeighbors(Cell cell, @Nullable Direction entryDirection,
                                                      @Nullable BlockPos entryCrossing) {
        List<NeighborData> neighbors = new ArrayList<>();

        for (Direction exitDirection : Direction.values()) {
            AABB neighborBounds = cell.getBounds().move(
                exitDirection.getStepX() * Cell.RESOLUTION,
                exitDirection.getStepY() * Cell.RESOLUTION,
                exitDirection.getStepZ() * Cell.RESOLUTION
            );

            Cell neighborCell = new Cell(neighborBounds);
            if (!hasNavigableAirspace(neighborBounds)) {
                continue;
            }

            if (entryDirection == null) {
                BlockPos boundaryCrossing = findBoundaryCrossing(cell, neighborCell);
                if (boundaryCrossing != null) {
                    neighbors.add(new NeighborData(neighborCell, boundaryCrossing));
                }
            } else {
                BlockPos exitCrossing = findTraversableExitCrossing(cell, entryCrossing, exitDirection);
                if (exitCrossing != null) {
                    neighbors.add(new NeighborData(neighborCell, exitCrossing));
                }
            }
        }

        return neighbors;
    }

    @Nullable
    private Direction getDirectionBetweenCells(Cell from, Cell to) {
        AABB boundsFrom = from.getBounds();
        AABB boundsTo = to.getBounds();

        int deltaX = (int) (boundsTo.minX - boundsFrom.minX) / Cell.RESOLUTION;
        int deltaY = (int) (boundsTo.minY - boundsFrom.minY) / Cell.RESOLUTION;
        int deltaZ = (int) (boundsTo.minZ - boundsFrom.minZ) / Cell.RESOLUTION;

        if (deltaX == 1) {
            return Direction.EAST;
        }
        if (deltaX == -1) {
            return Direction.WEST;
        }
        if (deltaY == 1) {
            return Direction.UP;
        }
        if (deltaY == -1) {
            return Direction.DOWN;
        }
        if (deltaZ == 1) {
            return Direction.SOUTH;
        }
        if (deltaZ == -1) {
            return Direction.NORTH;
        }

        return null;
    }

    @Nullable
    private BlockPos findTraversableExitCrossing(Cell cell, BlockPos entryCrossing, Direction exitDirection) {
        if (!isAir(entryCrossing)) {
            return null;
        }

        List<BlockPos> exitCrossings = getValidBoundaryCrossings(cell, exitDirection);
        if (exitCrossings.isEmpty()) {
            return null;
        }

        AABB bounds = cell.getBounds();
        BlockPos center = new BlockPos(
            (int) (bounds.minX + Cell.RESOLUTION / 2),
            (int) (bounds.minY + Cell.RESOLUTION / 2),
            (int) (bounds.minZ + Cell.RESOLUTION / 2)
        );

        Vec3 entryVec = Vec3.atCenterOf(entryCrossing);
        if (isAir(center) && raycastClear(Vec3.atCenterOf(center), entryVec)) {
            BlockPos reachableExit = findReachableExit(center, exitCrossings);
            if (reachableExit != null) {
                return reachableExit;
            }
        }

        for (int radius = 1; radius <= HUB_SEARCH_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (!isOnShellSurface(dx, dy, dz, radius)) {
                            continue;
                        }
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isWithinCell(candidate, bounds) && isAir(candidate)
                                && raycastClear(Vec3.atCenterOf(candidate), entryVec)) {
                            BlockPos reachableExit = findReachableExit(candidate, exitCrossings);
                            if (reachableExit != null) {
                                return reachableExit;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findReachableExit(BlockPos from, List<BlockPos> exitCrossings) {
        Vec3 fromVec = Vec3.atCenterOf(from);
        for (BlockPos exit : exitCrossings) {
            if (raycastClear(fromVec, Vec3.atCenterOf(exit))) {
                return exit;
            }
        }
        return null;
    }

    private List<BlockPos> getValidBoundaryCrossings(Cell cell, Direction direction) {
        List<BlockPos> crossings = new ArrayList<>();
        AABB bounds = cell.getBounds();

        BlockPos boundaryStart = getBoundaryStart(bounds, direction);
        int resolution = Cell.RESOLUTION;

        for (int u = 0; u < resolution; u++) {
            for (int v = 0; v < resolution; v++) {
                BlockPos boundaryPos = getPositionOnBoundary(boundaryStart, direction, u, v);
                if (isAir(boundaryPos)) {
                    BlockPos otherSide = boundaryPos.relative(direction);
                    if (isAir(otherSide)) {
                        crossings.add(boundaryPos);
                    }
                }
            }
        }

        return crossings;
    }

    private boolean isReachableExitCell(Cell cell, @Nullable BlockPos entryCrossing) {
        List<BlockPos> skyVisibleBlocks = getSkyVisibleBlocks(cell);
        if (skyVisibleBlocks.isEmpty()) {
            return false;
        }

        if (entryCrossing == null) {
            return true;
        }

        return findHubWithLOSToTargets(cell, entryCrossing, skyVisibleBlocks) != null;
    }

    private List<BlockPos> getSkyVisibleBlocks(Cell cell) {
        List<BlockPos> result = new ArrayList<>();
        AABB bounds = cell.getBounds();
        int baseX = (int) bounds.minX;
        int baseY = (int) bounds.minY;
        int baseZ = (int) bounds.minZ;

        for (int dx = 0; dx < Cell.RESOLUTION; dx++) {
            for (int dy = 0; dy < Cell.RESOLUTION; dy++) {
                for (int dz = 0; dz < Cell.RESOLUTION; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (isAir(pos) && hasSkyAccess(pos)) {
                        result.add(pos);
                    }
                }
            }
        }
        return result;
    }

    @Nullable
    private BlockPos findHubWithLOSToTargets(Cell cell, BlockPos entryCrossing, List<BlockPos> targets) {
        Vec3 entryVec = Vec3.atCenterOf(entryCrossing);

        for (BlockPos candidate : getHubCandidates(cell)) {
            if (!raycastClear(Vec3.atCenterOf(candidate), entryVec)) {
                continue;
            }
            for (BlockPos target : targets) {
                if (raycastClear(Vec3.atCenterOf(candidate), Vec3.atCenterOf(target))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private List<BlockPos> getHubCandidates(Cell cell) {
        List<BlockPos> candidates = new ArrayList<>();
        AABB bounds = cell.getBounds();
        BlockPos center = new BlockPos(
            (int) (bounds.minX + Cell.RESOLUTION / 2),
            (int) (bounds.minY + Cell.RESOLUTION / 2),
            (int) (bounds.minZ + Cell.RESOLUTION / 2)
        );

        if (isAir(center)) {
            candidates.add(center);
        }

        for (int radius = 1; radius <= HUB_SEARCH_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (!isOnShellSurface(dx, dy, dz, radius)) {
                            continue;
                        }
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isWithinCell(candidate, bounds) && isAir(candidate)) {
                            candidates.add(candidate);
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private boolean extendExits(List<Cell> exitCells, Map<BlockPos, Cell> cells,
                                 Map<BlockPos, BlockPos> cellHubs,
                                 Map<BlockPos, BlockPos> parents,
                                 Map<BlockPos, Set<BlockPos>> adjacency,
                                 Set<BlockPos> graphExits) {
        for (Cell exitCell : exitCells) {
            if (checkAborted()) {
                return false;
            }

            BlockPos exitCellGridKey = exitCell.getGridKey();
            BlockPos exitPoint = findPreciseExitPoint(exitCell);
            if (exitPoint == null) {
                continue;
            }

            BlockPos extendedHub = extendExitOutside(exitCell, exitCellGridKey, exitPoint, cells, cellHubs, parents, adjacency);
            if (extendedHub != null) {
                graphExits.add(extendedHub);
            }
        }

        return !graphExits.isEmpty();
    }

    @Nullable
    private BlockPos findPreciseExitPoint(Cell exitCell) {
        AABB bounds = exitCell.getBounds();
        int baseX = (int) bounds.minX;
        int baseY = (int) bounds.minY;
        int baseZ = (int) bounds.minZ;

        BlockPos bestCandidate = null;
        for (int dx = 0; dx < Cell.RESOLUTION; dx++) {
            for (int dy = 0; dy < Cell.RESOLUTION; dy++) {
                for (int dz = 0; dz < Cell.RESOLUTION; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (isAir(pos) && hasSkyAccess(pos)) {
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
    private BlockPos extendExitOutside(Cell exitCell, BlockPos exitCellGridKey, BlockPos exitPoint,
                                        Map<BlockPos, Cell> cells,
                                        Map<BlockPos, BlockPos> cellHubs,
                                        Map<BlockPos, BlockPos> parents,
                                        Map<BlockPos, Set<BlockPos>> adjacency) {
        Set<BlockPos> neighbors = adjacency.get(exitCellGridKey);
        if (neighbors == null || neighbors.isEmpty()) {
            return null;
        }

        BlockPos parentGridKey = parents.get(exitCellGridKey);
        if (parentGridKey == null) {
            return null;
        }

        BlockPos entryBoundary = findBoundaryCrossing(cells.get(parentGridKey), exitCell);
        if (entryBoundary == null) {
            return null;
        }

        BlockPos exitHub = findHubWithDualLOS(exitCell, entryBoundary, exitPoint);
        if (exitHub == null) {
            return null;
        }

        cellHubs.put(exitCellGridKey, exitHub);

        BlockPos lastGridKey = exitCellGridKey;
        BlockPos lastHub = exitHub;

        for (int extension = 0; extension < MAX_RADIUS_BLOCKS / Cell.RESOLUTION; extension++) {
            if (checkAborted()) {
                return null;
            }

            BlockPos extensionHub = new BlockPos(
                exitPoint.getX(),
                (int) cells.get(lastGridKey).getBounds().minY + Cell.RESOLUTION,
                exitPoint.getZ()
            );

            Cell extensionCell = Cell.fromWorldPosition(extensionHub);
            BlockPos extensionGridKey = extensionCell.getGridKey();

            if (!extensionGridKey.equals(lastGridKey)) {
                cells.put(extensionGridKey, extensionCell);
                cellHubs.put(extensionGridKey, extensionHub);
                parents.put(extensionGridKey, lastGridKey);

                adjacency.putIfAbsent(lastGridKey, new HashSet<>());
                adjacency.putIfAbsent(extensionGridKey, new HashSet<>());
                adjacency.get(lastGridKey).add(extensionGridKey);
                adjacency.get(extensionGridKey).add(lastGridKey);

                lastGridKey = extensionGridKey;
                lastHub = extensionHub;
            }

            if (Validators.isFullyOutside(level, extensionCell.getBounds())) {
                return lastHub;
            }
        }

        return lastHub;
    }

    private boolean buildPathTables(Map<BlockPos, Cell> cells,
                                     Map<BlockPos, BlockPos> cellHubs,
                                     Map<BlockPos, BlockPos> parents,
                                     Map<BlockPos, Set<BlockPos>> adjacency,
                                     BlockPos graphStart,
                                     Set<BlockPos> graphExits) {
        Map<String, Map<BlockPos, BlockPos>> boundaryCrossings = new HashMap<>();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : adjacency.entrySet()) {
            BlockPos cellA = entry.getKey();
            for (BlockPos cellB : entry.getValue()) {
                BlockPos crossing = findBoundaryCrossing(cells.get(cellA), cells.get(cellB));
                if (crossing != null) {
                    String key = cellA.asLong() + "->" + cellB.asLong();
                    boundaryCrossings.putIfAbsent(key, new HashMap<>());
                    boundaryCrossings.get(key).put(cellA, crossing);
                }
            }
        }

        Set<BlockPos> destinations = new HashSet<>();
        destinations.add(graphStart);
        destinations.addAll(graphExits);

        for (BlockPos destination : destinations) {
            if (checkAborted()) {
                return false;
            }

            if (!buildPathsForDestination(destination, cells, cellHubs, adjacency, boundaryCrossings)) {
                return false;
            }
        }

        return true;
    }

    private boolean hasNavigableAirspace(AABB bounds) {
        int baseX = (int) bounds.minX;
        int baseY = (int) bounds.minY;
        int baseZ = (int) bounds.minZ;

        for (int dx = 0; dx < Cell.RESOLUTION; dx++) {
            for (int dy = 0; dy < Cell.RESOLUTION; dy++) {
                for (int dz = 0; dz < Cell.RESOLUTION; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (isAir(pos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Nullable
    private BlockPos findBoundaryCrossing(Cell cellA, Cell cellB) {
        Direction direction = getDirectionBetweenCells(cellA, cellB);
        if (direction == null) {
            return null;
        }

        BlockPos boundaryStart = getBoundaryStart(cellA.getBounds(), direction);
        int resolution = Cell.RESOLUTION;

        for (int u = 0; u < resolution; u++) {
            for (int v = 0; v < resolution; v++) {
                BlockPos boundaryPos = getPositionOnBoundary(boundaryStart, direction, u, v);
                if (isAir(boundaryPos)) {
                    BlockPos otherSide = boundaryPos.relative(direction);
                    if (isAir(otherSide)) {
                        return boundaryPos;
                    }
                }
            }
        }

        return null;
    }

    private boolean buildPathsForDestination(BlockPos destination,
                                              Map<BlockPos, Cell> cells,
                                              Map<BlockPos, BlockPos> cellHubs,
                                              Map<BlockPos, Set<BlockPos>> adjacency,
                                              Map<String, Map<BlockPos, BlockPos>> boundaryCrossings) {
        BlockPos destinationGridKey = null;
        for (Map.Entry<BlockPos, BlockPos> entry : cellHubs.entrySet()) {
            if (entry.getValue().equals(destination)) {
                destinationGridKey = entry.getKey();
                break;
            }
        }

        if (destinationGridKey == null) {
            return true;
        }

        Queue<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Map<BlockPos, BlockPos> pathEntries = new HashMap<>();

        frontier.add(destinationGridKey);
        visited.add(destinationGridKey);
        pathEntries.put(destinationGridKey, null);

        while (!frontier.isEmpty()) {
            if (checkAborted()) {
                return false;
            }

            BlockPos currentGridKey = frontier.poll();
            Cell currentCell = cells.get(currentGridKey);

            Set<BlockPos> neighbors = adjacency.get(currentGridKey);
            if (neighbors == null) {
                continue;
            }

            for (BlockPos neighborGridKey : neighbors) {
                if (visited.contains(neighborGridKey)) {
                    continue;
                }

                Cell neighborCell = cells.get(neighborGridKey);
                Direction entryDirection = getDirectionBetweenCells(neighborCell, currentCell);
                if (entryDirection == null) {
                    continue;
                }

                List<BlockPos> entryCrossings = getValidBoundaryCrossings(neighborCell, entryDirection);
                if (entryCrossings.isEmpty()) {
                    continue;
                }

                BlockPos exitBoundary = getExitBoundary(currentGridKey, pathEntries.get(currentGridKey), boundaryCrossings);
                BlockPos exitTarget = exitBoundary != null ? exitBoundary : destination;

                BlockPos hub = findHubForAnyCrossing(currentCell, entryCrossings, exitTarget);
                if (hub != null) {
                    visited.add(neighborGridKey);
                    pathEntries.put(neighborGridKey, hub);
                    frontier.add(neighborGridKey);
                    continue;
                }

                BlockPos alternativeHub = findAlternativePath(neighborGridKey, neighborCell, destination,
                    cells, adjacency, boundaryCrossings, pathEntries);
                if (alternativeHub != null) {
                    visited.add(neighborGridKey);
                    pathEntries.put(neighborGridKey, alternativeHub);
                    frontier.add(neighborGridKey);
                    continue;
                }

                BlockPos fallbackHub = findHubForAnyCrossing(currentCell, entryCrossings, null);
                if (fallbackHub != null) {
                    visited.add(neighborGridKey);
                    pathEntries.put(neighborGridKey, fallbackHub);
                    frontier.add(neighborGridKey);
                }
            }
        }

        for (Map.Entry<BlockPos, BlockPos> entry : pathEntries.entrySet()) {
            Cell cell = cells.get(entry.getKey());
            if (cell != null) {
                cell.setPath(destination, entry.getValue());
            }
        }

        return true;
    }

    @Nullable
    private BlockPos getExitBoundary(BlockPos currentGridKey,
                                      @Nullable BlockPos currentNextHop,
                                      Map<String, Map<BlockPos, BlockPos>> boundaryCrossings) {
        if (currentNextHop == null) {
            return null;
        }

        BlockPos nextGridKey = Cell.fromWorldPosition(currentNextHop).getGridKey();
        String exitKey = currentGridKey.asLong() + "->" + nextGridKey.asLong();
        Map<BlockPos, BlockPos> exitMap = boundaryCrossings.get(exitKey);
        if (exitMap == null) {
            return null;
        }

        return exitMap.get(currentGridKey);
    }

    @Nullable
    private BlockPos findHubWithSingleLOS(Cell cell, BlockPos boundary) {
        AABB bounds = cell.getBounds();
        BlockPos center = new BlockPos(
            (int) (bounds.minX + Cell.RESOLUTION / 2),
            (int) (bounds.minY + Cell.RESOLUTION / 2),
            (int) (bounds.minZ + Cell.RESOLUTION / 2)
        );

        if (isAir(center) && raycastClear(Vec3.atCenterOf(center), Vec3.atCenterOf(boundary))) {
            return center;
        }

        for (int radius = 1; radius <= HUB_SEARCH_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (!isOnShellSurface(dx, dy, dz, radius)) {
                            continue;
                        }
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isWithinCell(candidate, bounds) && isAir(candidate)
                                && raycastClear(Vec3.atCenterOf(candidate), Vec3.atCenterOf(boundary))) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findHubForAnyCrossing(Cell cell, List<BlockPos> crossings, @Nullable BlockPos exitTarget) {
        for (BlockPos crossing : crossings) {
            BlockPos hub = findHubVisibleFromCrossing(cell, crossing, exitTarget);
            if (hub != null) {
                return hub;
            }
        }
        return null;
    }

    @Nullable
    private BlockPos findHubVisibleFromCrossing(Cell cell, BlockPos crossing, @Nullable BlockPos exitTarget) {
        Vec3 crossingVec = Vec3.atCenterOf(crossing);

        for (BlockPos candidate : getHubCandidates(cell)) {
            Vec3 candidateVec = Vec3.atCenterOf(candidate);
            if (!raycastClear(crossingVec, candidateVec)) {
                continue;
            }
            if (exitTarget != null && !raycastClear(candidateVec, Vec3.atCenterOf(exitTarget))) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    @Nullable
    private BlockPos findHubWithDualLOS(Cell cell, BlockPos entryBoundary, @Nullable BlockPos exitBoundary) {
        AABB bounds = cell.getBounds();
        BlockPos center = new BlockPos(
            (int) (bounds.minX + Cell.RESOLUTION / 2),
            (int) (bounds.minY + Cell.RESOLUTION / 2),
            (int) (bounds.minZ + Cell.RESOLUTION / 2)
        );

        if (isAir(center) && hasLOSToBoundaries(center, entryBoundary, exitBoundary)) {
            return center;
        }

        for (int radius = 1; radius <= HUB_SEARCH_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (!isOnShellSurface(dx, dy, dz, radius)) {
                            continue;
                        }
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isWithinCell(candidate, bounds) && isAir(candidate)
                                && hasLOSToBoundaries(candidate, entryBoundary, exitBoundary)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean hasLOSToBoundaries(BlockPos hub, BlockPos entryBoundary, @Nullable BlockPos exitBoundary) {
        if (!raycastClear(Vec3.atCenterOf(hub), Vec3.atCenterOf(entryBoundary))) {
            return false;
        }
        return exitBoundary == null || raycastClear(Vec3.atCenterOf(hub), Vec3.atCenterOf(exitBoundary));
    }

    @Nullable
    private BlockPos findAlternativePath(BlockPos cellAGridKey,
                                          Cell cellA,
                                          BlockPos destination,
                                          Map<BlockPos, Cell> cells,
                                          Map<BlockPos, Set<BlockPos>> adjacency,
                                          Map<String, Map<BlockPos, BlockPos>> boundaryCrossings,
                                          Map<BlockPos, BlockPos> pathEntries) {
        Queue<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        frontier.add(cellAGridKey);
        visited.add(cellAGridKey);

        while (!frontier.isEmpty()) {
            if (checkAborted()) {
                return null;
            }

            BlockPos currentGridKey = frontier.poll();

            Set<BlockPos> neighbors = adjacency.get(currentGridKey);
            if (neighbors == null) {
                continue;
            }

            for (BlockPos neighborGridKey : neighbors) {
                if (visited.contains(neighborGridKey)) {
                    continue;
                }

                visited.add(neighborGridKey);

                if (pathEntries.containsKey(neighborGridKey)) {
                    Direction entryDirection = getDirectionBetweenCells(cellA, cells.get(neighborGridKey));
                    if (entryDirection == null) {
                        continue;
                    }

                    List<BlockPos> entryCrossings = getValidBoundaryCrossings(cellA, entryDirection);
                    if (entryCrossings.isEmpty()) {
                        continue;
                    }

                    Cell neighborCell = cells.get(neighborGridKey);
                    BlockPos exitBoundary = getExitBoundary(neighborGridKey, pathEntries.get(neighborGridKey), boundaryCrossings);
                    BlockPos exitTarget = exitBoundary != null ? exitBoundary : destination;

                    BlockPos hub = findHubForAnyCrossing(neighborCell, entryCrossings, exitTarget);
                    if (hub != null) {
                        return hub;
                    }
                }

                frontier.add(neighborGridKey);
            }
        }

        return null;
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

    private static boolean isOnShellSurface(int dx, int dy, int dz, int radius) {
        return Math.abs(dx) == radius || Math.abs(dy) == radius || Math.abs(dz) == radius;
    }
}
