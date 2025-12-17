package org.sosly.ecotale.navigation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * A navigation cell representing a 4×4×4 block region with routing information.
 *
 * <p>Cells store precomputed paths to all known destinations. Hubs (navigable
 * waypoints) are derived from the path entries rather than stored explicitly.</p>
 */
public class Cell {
    public static final int RESOLUTION = 4;

    private final AABB bounds;
    private final Map<BlockPos, BlockPos> paths;

    public Cell(AABB bounds) {
        this.bounds = bounds;
        this.paths = new HashMap<>();
    }

    /**
     * Creates a cell from any world position by computing the 4×4×4 grid cell bounds
     * that contain the position.
     *
     * @param worldPos any position within the desired cell
     * @return a new cell with computed bounds
     */
    public static Cell fromWorldPosition(BlockPos worldPos) {
        int minX = Math.floorDiv(worldPos.getX(), RESOLUTION) * RESOLUTION;
        int minY = Math.floorDiv(worldPos.getY(), RESOLUTION) * RESOLUTION;
        int minZ = Math.floorDiv(worldPos.getZ(), RESOLUTION) * RESOLUTION;

        AABB bounds = new AABB(
            minX,
            minY,
            minZ,
            minX + RESOLUTION,
            minY + RESOLUTION,
            minZ + RESOLUTION
        );

        return new Cell(bounds);
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

    /**
     * Returns the grid-aligned key for this cell.
     *
     * <p>This key uniquely identifies the cell's 4×4×4 region in the graph.
     * Two cells covering the same region will have the same grid key.</p>
     *
     * @return the min corner of the cell bounds as a BlockPos
     */
    public BlockPos getGridKey() {
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
