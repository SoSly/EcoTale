package org.sosly.ecotale.navigation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * A navigation cell representing a 4×4×4 block region with routing information.
 *
 * <p>Each cell contains a navigable waypoint (hub) and knows how to route toward
 * any destination in the navigation graph. Cells are created during graph generation
 * and store precomputed paths to all known destinations.</p>
 */
public class Cell {
    public static final int RESOLUTION = 4;

    private final AABB bounds;
    private final BlockPos hub;
    private final Map<BlockPos, BlockPos> paths;

    public Cell(AABB bounds, BlockPos hub) {
        this.bounds = bounds;
        this.hub = hub;
        this.paths = new HashMap<>();
    }

    /**
     * Creates a cell from a hub position by computing the 4×4×4 grid cell bounds
     * that contain the hub.
     *
     * @param hub the navigable waypoint within the cell
     * @return a new cell with computed bounds
     */
    public static Cell fromHubPosition(BlockPos hub) {
        int minX = Math.floorDiv(hub.getX(), RESOLUTION) * RESOLUTION;
        int minY = Math.floorDiv(hub.getY(), RESOLUTION) * RESOLUTION;
        int minZ = Math.floorDiv(hub.getZ(), RESOLUTION) * RESOLUTION;

        AABB bounds = new AABB(
            minX,
            minY,
            minZ,
            minX + RESOLUTION,
            minY + RESOLUTION,
            minZ + RESOLUTION
        );

        return new Cell(bounds, hub);
    }

    /**
     * Checks if a position is within this cell's bounds.
     *
     * @param pos the position to check
     * @return true if the position is within the cell's 4×4×4 region
     */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= bounds.minX && pos.getX() < bounds.maxX
            && pos.getY() >= bounds.minY && pos.getY() < bounds.maxY
            && pos.getZ() >= bounds.minZ && pos.getZ() < bounds.maxZ;
    }

    public AABB getBounds() {
        return bounds;
    }

    public BlockPos getHub() {
        return hub;
    }

    /**
     * Returns the grid-aligned identifier for this cell.
     *
     * <p>Two cells covering the same 4×4×4 region will have the same grid ID,
     * regardless of hub position. Use this for deduplication during graph generation.</p>
     *
     * @return the min corner of the cell bounds as a BlockPos
     */
    public BlockPos getGridId() {
        return new BlockPos((int) bounds.minX, (int) bounds.minY, (int) bounds.minZ);
    }

    public Map<BlockPos, BlockPos> getPaths() {
        return Collections.unmodifiableMap(paths);
    }

    /**
     * Sets the next hop for a destination during graph generation.
     *
     * @param destination the destination hub to route toward
     * @param nextHop the next hub on the path (null if this cell contains the destination)
     */
    public void setPath(BlockPos destination, BlockPos nextHop) {
        paths.put(destination, nextHop);
    }
}
