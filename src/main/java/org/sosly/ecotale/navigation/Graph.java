package org.sosly.ecotale.navigation;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * A complete navigation solution covering all reachable cells and destinations.
 *
 * <p>The graph provides O(1) navigation queries by storing precomputed paths from
 * every cell to every destination. Destinations include the home location (graphStart)
 * and all cave exits (graphExits).</p>
 */
public class Graph {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BlockPos id;
    private final EntityType<?> entityType;
    private final Map<BlockPos, Cell> cells;
    private final Set<BlockPos> destinations;
    private final BlockPos graphStart;
    private final Set<BlockPos> graphExits;

    private Graph(Builder builder) {
        this.id = builder.id;
        this.entityType = builder.entityType;
        this.cells = new HashMap<>(builder.cells);
        this.graphStart = builder.graphStart;
        this.graphExits = new HashSet<>(builder.graphExits);
        this.destinations = new HashSet<>();
        this.destinations.add(graphStart);
        this.destinations.addAll(graphExits);
    }

    /**
     * Looks up a cell by world position with O(1) grid key computation.
     *
     * @param worldPos any position to look up
     * @return the cell containing the position, or null if not in graph
     */
    @Nullable
    public Cell getCellAt(BlockPos worldPos) {
        int minX = Math.floorDiv(worldPos.getX(), Cell.RESOLUTION) * Cell.RESOLUTION;
        int minY = Math.floorDiv(worldPos.getY(), Cell.RESOLUTION) * Cell.RESOLUTION;
        int minZ = Math.floorDiv(worldPos.getZ(), Cell.RESOLUTION) * Cell.RESOLUTION;
        BlockPos gridKey = new BlockPos(minX, minY, minZ);
        return cells.get(gridKey);
    }

    /**
     * Finds the cell whose bounds contain the given position.
     *
     * @param pos the position to search for
     * @return the containing cell, or null if the position is not in any cell
     */
    @Nullable
    public Cell findContainingCell(BlockPos pos) {
        return getCellAt(pos);
    }

    /**
     * Returns the next hop toward a destination from a current position.
     *
     * <p>This is the primary navigation query. Given the entity's current position
     * and desired destination, it returns the next hop to path toward.</p>
     *
     * @param currentPos the position the entity is currently at
     * @param destination the target destination
     * @return the next hop position, or null if already at destination or path not found
     */
    @Nullable
    public BlockPos getNextHop(BlockPos currentPos, BlockPos destination) {
        Cell cell = getCellAt(currentPos);
        if (cell == null) {
            return null;
        }

        return cell.getPaths().get(destination);
    }

    /**
     * Returns all hub positions within a cell.
     *
     * <p>Hubs are implicit - a cell has a hub at any position that appears as a
     * nextHop in another cell's path entries. This allows cells to have multiple
     * hubs when different routes converge at different waypoints.</p>
     *
     * @param cell the cell to find hubs for
     * @return the set of hub positions within the cell, empty if no paths point to it
     */
    public Set<BlockPos> getHubsForCell(Cell cell) {
        Set<BlockPos> hubs = new HashSet<>();
        for (Cell other : cells.values()) {
            for (BlockPos nextHop : other.getPaths().values()) {
                if (nextHop != null && cell.contains(nextHop)) {
                    hubs.add(nextHop);
                }
            }
        }
        return hubs;
    }

    public BlockPos getId() {
        return id;
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }

    public Map<BlockPos, Cell> getCells() {
        return Collections.unmodifiableMap(cells);
    }

    public Set<BlockPos> getDestinations() {
        return Collections.unmodifiableSet(destinations);
    }

    public BlockPos getGraphStart() {
        return graphStart;
    }

    public Set<BlockPos> getGraphExits() {
        return Collections.unmodifiableSet(graphExits);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id.asLong());
        tag.putString("entityType", ForgeRegistries.ENTITY_TYPES.getKey(entityType).toString());
        tag.putLong("graphStart", graphStart.asLong());

        long[] exits = new long[graphExits.size()];
        int i = 0;
        for (BlockPos exit : graphExits) {
            exits[i++] = exit.asLong();
        }
        tag.putLongArray("graphExits", exits);

        ListTag cellsList = new ListTag();
        for (Cell cell : cells.values()) {
            cellsList.add(saveCell(cell));
        }
        tag.put("cells", cellsList);

        return tag;
    }

    @Nullable
    public static Graph load(CompoundTag tag) {
        try {
            BlockPos id = BlockPos.of(tag.getLong("id"));
            ResourceLocation entityTypeLocation = new ResourceLocation(tag.getString("entityType"));
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityTypeLocation);
            if (entityType == null) {
                LOGGER.warn("Unknown entity type: {}", entityTypeLocation);
                return null;
            }

            BlockPos graphStart = BlockPos.of(tag.getLong("graphStart"));

            long[] exitsArray = tag.getLongArray("graphExits");
            Set<BlockPos> graphExits = new HashSet<>();
            for (long exitLong : exitsArray) {
                graphExits.add(BlockPos.of(exitLong));
            }

            ListTag cellsList = tag.getList("cells", Tag.TAG_COMPOUND);
            Map<BlockPos, Cell> cells = new HashMap<>();
            for (int i = 0; i < cellsList.size(); i++) {
                Cell cell = loadCell(cellsList.getCompound(i));
                if (cell == null) {
                    LOGGER.warn("Failed to load cell at index {}", i);
                    return null;
                }
                cells.put(cell.getGridKey(), cell);
            }

            Builder builder = builder(id, entityType);
            builder.setGraphStart(graphStart);
            for (BlockPos exit : graphExits) {
                builder.addGraphExit(exit);
            }
            for (Cell cell : cells.values()) {
                builder.addCell(cell);
            }

            return builder.build();
        } catch (Exception e) {
            LOGGER.warn("Failed to load graph from NBT", e);
            return null;
        }
    }

    private static CompoundTag saveCell(Cell cell) {
        CompoundTag cellTag = new CompoundTag();
        AABB bounds = cell.getBounds();
        cellTag.putLong("boundsMin", new BlockPos((int) bounds.minX, (int) bounds.minY, (int) bounds.minZ).asLong());

        ListTag pathsList = new ListTag();
        for (Map.Entry<BlockPos, BlockPos> entry : cell.getPaths().entrySet()) {
            CompoundTag linkTag = new CompoundTag();
            linkTag.putLong("destination", entry.getKey().asLong());
            BlockPos nextHop = entry.getValue();
            linkTag.putBoolean("hasNextHop", nextHop != null);
            if (nextHop != null) {
                linkTag.putLong("nextHop", nextHop.asLong());
            }
            pathsList.add(linkTag);
        }
        cellTag.put("paths", pathsList);

        return cellTag;
    }

    @Nullable
    private static Cell loadCell(CompoundTag tag) {
        try {
            BlockPos boundsMin = BlockPos.of(tag.getLong("boundsMin"));
            AABB bounds = new AABB(
                boundsMin.getX(),
                boundsMin.getY(),
                boundsMin.getZ(),
                boundsMin.getX() + Cell.RESOLUTION,
                boundsMin.getY() + Cell.RESOLUTION,
                boundsMin.getZ() + Cell.RESOLUTION
            );

            Cell cell = new Cell(bounds);

            ListTag pathsList = tag.getList("paths", Tag.TAG_COMPOUND);
            for (int i = 0; i < pathsList.size(); i++) {
                CompoundTag linkTag = pathsList.getCompound(i);
                BlockPos destination = BlockPos.of(linkTag.getLong("destination"));
                BlockPos nextHop;
                if (linkTag.contains("hasNextHop")) {
                    nextHop = linkTag.getBoolean("hasNextHop")
                        ? BlockPos.of(linkTag.getLong("nextHop"))
                        : null;
                } else {
                    long nextHopLong = linkTag.getLong("nextHop");
                    nextHop = nextHopLong == 0L ? null : BlockPos.of(nextHopLong);
                }
                cell.setPath(destination, nextHop);
            }

            return cell;
        } catch (Exception e) {
            LOGGER.warn("Failed to load cell from NBT", e);
            return null;
        }
    }

    /**
     * Creates a new builder for constructing a graph.
     *
     * @param id the origin position (typically the roost BlockPos)
     * @param entityType the entity type this graph was generated for
     * @return a new builder instance
     */
    public static Builder builder(BlockPos id, EntityType<?> entityType) {
        return new Builder(id, entityType);
    }

    /**
     * Builder for constructing immutable Graph instances.
     */
    public static class Builder {
        private final BlockPos id;
        private final EntityType<?> entityType;
        private final Map<BlockPos, Cell> cells = new HashMap<>();
        private final Set<BlockPos> graphExits = new HashSet<>();
        private BlockPos graphStart;

        private Builder(BlockPos id, EntityType<?> entityType) {
            this.id = id;
            this.entityType = entityType;
        }

        public Builder setGraphStart(BlockPos graphStart) {
            this.graphStart = graphStart;
            return this;
        }

        public Builder addCell(Cell cell) {
            this.cells.put(cell.getGridKey(), cell);
            return this;
        }

        public Builder addGraphExit(BlockPos exitHub) {
            this.graphExits.add(exitHub);
            return this;
        }

        public Graph build() {
            if (graphStart == null) {
                throw new IllegalStateException("graphStart must be set before building");
            }
            return new Graph(this);
        }
    }
}
