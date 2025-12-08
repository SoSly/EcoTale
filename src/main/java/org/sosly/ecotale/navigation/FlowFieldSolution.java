package org.sosly.ecotale.navigation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Holds the computed flow field data for a roost.
 * Immutable after construction.
 */
public class FlowFieldSolution {
    private static final String TAG_OUTWARD = "outward";
    private static final String TAG_INWARD = "inward";
    private static final String TAG_HUBS = "hubs";
    private static final String TAG_EXIT = "exit";
    private static final String TAG_ROOST = "roost";
    private static final String TAG_FAILED = "failed";
    private static final String TAG_CELL_X = "cx";
    private static final String TAG_CELL_Y = "cy";
    private static final String TAG_CELL_Z = "cz";
    private static final String TAG_VEC_X = "vx";
    private static final String TAG_VEC_Y = "vy";
    private static final String TAG_VEC_Z = "vz";
    private static final String TAG_POS_X = "px";
    private static final String TAG_POS_Y = "py";
    private static final String TAG_POS_Z = "pz";

    private final Map<FlowFieldCell, Vec3> outwardField;
    private final Map<FlowFieldCell, Vec3> inwardField;
    private final Map<FlowFieldCell, BlockPos> hubCache;
    private final BlockPos exitPoint;
    private final BlockPos roostPos;
    private final boolean failed;

    private FlowFieldSolution(
            Map<FlowFieldCell, Vec3> outwardField,
            Map<FlowFieldCell, Vec3> inwardField,
            Map<FlowFieldCell, BlockPos> hubCache,
            BlockPos exitPoint,
            BlockPos roostPos,
            boolean failed) {
        this.outwardField = outwardField;
        this.inwardField = inwardField;
        this.hubCache = hubCache;
        this.exitPoint = exitPoint;
        this.roostPos = roostPos;
        this.failed = failed;
    }

    public static FlowFieldSolution create(
            Map<FlowFieldCell, Vec3> outwardField,
            Map<FlowFieldCell, Vec3> inwardField,
            Map<FlowFieldCell, BlockPos> hubCache,
            BlockPos exitPoint,
            BlockPos roostPos) {
        return new FlowFieldSolution(
            new HashMap<>(outwardField),
            new HashMap<>(inwardField),
            new HashMap<>(hubCache),
            exitPoint,
            roostPos,
            false
        );
    }

    public static FlowFieldSolution failed(BlockPos roostPos) {
        return new FlowFieldSolution(
            Map.of(),
            Map.of(),
            Map.of(),
            null,
            roostPos,
            true
        );
    }

    public boolean isFailed() {
        return failed;
    }

    public Optional<Vec3> getOutwardDirection(FlowFieldCell cell) {
        return Optional.ofNullable(outwardField.get(cell));
    }

    public Optional<Vec3> getInwardDirection(FlowFieldCell cell) {
        return Optional.ofNullable(inwardField.get(cell));
    }

    public Optional<BlockPos> getHubPosition(FlowFieldCell cell) {
        return Optional.ofNullable(hubCache.get(cell));
    }

    public BlockPos getExitPoint() {
        return exitPoint;
    }

    public BlockPos getRoostPos() {
        return roostPos;
    }

    /**
     * Validates that the flow field is still navigable.
     * Checks exit accessibility and traces path from exit back to roost using inward field.
     * Thread-safe: all Level access is wrapped in try-catch.
     */
    public boolean isValid(Level level) {
        if (failed || exitPoint == null) {
            return false;
        }

        try {
            if (!level.canSeeSky(exitPoint) || !level.getBlockState(exitPoint).isAir()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        FlowFieldCell roostCell = FlowFieldCell.fromBlockPos(roostPos);

        FlowFieldCell startCell = findStartCellForValidation();
        if (startCell == null) {
            return false;
        }

        if (isNearRoost(startCell, roostCell)) {
            return true;
        }

        return tracePathToRoost(startCell, roostCell, level);
    }

    private FlowFieldCell findStartCellForValidation() {
        FlowFieldCell exitPointCell = FlowFieldCell.fromBlockPos(exitPoint);
        if (inwardField.containsKey(exitPointCell)) {
            return exitPointCell;
        }

        for (FlowFieldCell cell : inwardField.keySet()) {
            int dx = Math.abs(cell.x() - exitPointCell.x());
            int dy = Math.abs(cell.y() - exitPointCell.y());
            int dz = Math.abs(cell.z() - exitPointCell.z());
            if (dx <= 1 && dy <= 1 && dz <= 1) {
                return cell;
            }
        }

        return null;
    }

    private boolean tracePathToRoost(FlowFieldCell startCell, FlowFieldCell roostCell, Level level) {
        FlowFieldCell current = startCell;
        Set<FlowFieldCell> visited = new HashSet<>();

        while (!isNearRoost(current, roostCell)) {
            if (visited.contains(current)) {
                return false;
            }
            visited.add(current);

            Vec3 direction = inwardField.get(current);
            if (direction == null) {
                return false;
            }

            FlowFieldCell next = FlowFieldCell.cellInDirection(current, direction);
            if (!isStepPassable(current, next, level)) {
                return false;
            }

            current = next;
        }

        return true;
    }

    private boolean isNearRoost(FlowFieldCell current, FlowFieldCell roostCell) {
        int dx = Math.abs(current.x() - roostCell.x());
        int dy = Math.abs(current.y() - roostCell.y());
        int dz = Math.abs(current.z() - roostCell.z());
        return dx <= 1 && dy <= 1 && dz <= 1;
    }

    private boolean isStepPassable(FlowFieldCell from, FlowFieldCell to, Level level) {
        BlockPos fromHub = hubCache.get(from);
        BlockPos toHub = hubCache.get(to);

        if (fromHub == null || toHub == null) {
            return false;
        }

        try {
            if (!level.getBlockState(fromHub).isAir() || !level.getBlockState(toHub).isAir()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        Direction direction = getDirectionBetweenCells(from, to);
        if (direction == null) {
            return false;
        }

        return hasBoundaryCrossing(from, direction, toHub, level);
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
        if (dz == -1) {
            return Direction.NORTH;
        }
        return null;
    }

    private boolean hasBoundaryCrossing(FlowFieldCell from, Direction direction, BlockPos toHub, Level level) {
        int resolution = FlowFieldCell.RESOLUTION;
        BlockPos boundaryStart = getBoundaryStart(from, direction);

        BlockPos center = getPositionOnBoundary(boundaryStart, direction, resolution / 2, resolution / 2);
        if (checkCrossing(center, direction, toHub, level)) {
            return true;
        }

        for (int u = 0; u < resolution; u++) {
            for (int v = 0; v < resolution; v++) {
                BlockPos pos = getPositionOnBoundary(boundaryStart, direction, u, v);
                if (checkCrossing(pos, direction, toHub, level)) {
                    return true;
                }
            }
        }

        return false;
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

    private boolean checkCrossing(BlockPos boundaryPos, Direction direction, BlockPos toHub, Level level) {
        try {
            if (!level.getBlockState(boundaryPos).isAir()) {
                return false;
            }

            BlockPos otherSide = boundaryPos.relative(direction);
            if (!level.getBlockState(otherSide).isAir()) {
                return false;
            }

            return raycastClear(Vec3.atCenterOf(otherSide), Vec3.atCenterOf(toHub), level);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean raycastClear(Vec3 from, Vec3 to, Level level) {
        try {
            ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            BlockHitResult result = level.clip(context);
            return result.getType() == HitResult.Type.MISS;
        } catch (Exception e) {
            return false;
        }
    }

    public void forEachOutwardCell(BiConsumer<FlowFieldCell, Vec3> consumer) {
        outwardField.forEach(consumer);
    }

    public void forEachInwardCell(BiConsumer<FlowFieldCell, Vec3> consumer) {
        inwardField.forEach(consumer);
    }

    public int getOutwardCellCount() {
        return outwardField.size();
    }

    public int getInwardCellCount() {
        return inwardField.size();
    }

    /**
     * Serializes this solution to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_FAILED, failed);
        tag.putInt(TAG_ROOST + "X", roostPos.getX());
        tag.putInt(TAG_ROOST + "Y", roostPos.getY());
        tag.putInt(TAG_ROOST + "Z", roostPos.getZ());

        if (failed) {
            return tag;
        }

        tag.putInt(TAG_EXIT + "X", exitPoint.getX());
        tag.putInt(TAG_EXIT + "Y", exitPoint.getY());
        tag.putInt(TAG_EXIT + "Z", exitPoint.getZ());

        tag.put(TAG_OUTWARD, saveVectorField(outwardField));
        tag.put(TAG_INWARD, saveVectorField(inwardField));
        tag.put(TAG_HUBS, saveHubCache(hubCache));

        return tag;
    }

    /**
     * Deserializes a solution from NBT.
     */
    public static FlowFieldSolution load(CompoundTag tag) {
        BlockPos roostPos = new BlockPos(
            tag.getInt(TAG_ROOST + "X"),
            tag.getInt(TAG_ROOST + "Y"),
            tag.getInt(TAG_ROOST + "Z")
        );

        if (tag.getBoolean(TAG_FAILED)) {
            return failed(roostPos);
        }

        BlockPos exitPoint = new BlockPos(
            tag.getInt(TAG_EXIT + "X"),
            tag.getInt(TAG_EXIT + "Y"),
            tag.getInt(TAG_EXIT + "Z")
        );

        Map<FlowFieldCell, Vec3> outwardField = loadVectorField(tag.getList(TAG_OUTWARD, Tag.TAG_COMPOUND));
        Map<FlowFieldCell, Vec3> inwardField = loadVectorField(tag.getList(TAG_INWARD, Tag.TAG_COMPOUND));
        Map<FlowFieldCell, BlockPos> hubCache = loadHubCache(tag.getList(TAG_HUBS, Tag.TAG_COMPOUND));

        return new FlowFieldSolution(outwardField, inwardField, hubCache, exitPoint, roostPos, false);
    }

    private static ListTag saveVectorField(Map<FlowFieldCell, Vec3> field) {
        ListTag list = new ListTag();
        for (Map.Entry<FlowFieldCell, Vec3> entry : field.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            FlowFieldCell cell = entry.getKey();
            Vec3 vec = entry.getValue();
            entryTag.putInt(TAG_CELL_X, cell.x());
            entryTag.putInt(TAG_CELL_Y, cell.y());
            entryTag.putInt(TAG_CELL_Z, cell.z());
            entryTag.putDouble(TAG_VEC_X, vec.x);
            entryTag.putDouble(TAG_VEC_Y, vec.y);
            entryTag.putDouble(TAG_VEC_Z, vec.z);
            list.add(entryTag);
        }
        return list;
    }

    private static Map<FlowFieldCell, Vec3> loadVectorField(ListTag list) {
        Map<FlowFieldCell, Vec3> field = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            FlowFieldCell cell = new FlowFieldCell(
                entryTag.getInt(TAG_CELL_X),
                entryTag.getInt(TAG_CELL_Y),
                entryTag.getInt(TAG_CELL_Z)
            );
            Vec3 vec = new Vec3(
                entryTag.getDouble(TAG_VEC_X),
                entryTag.getDouble(TAG_VEC_Y),
                entryTag.getDouble(TAG_VEC_Z)
            );
            field.put(cell, vec);
        }
        return field;
    }

    private static ListTag saveHubCache(Map<FlowFieldCell, BlockPos> cache) {
        ListTag list = new ListTag();
        for (Map.Entry<FlowFieldCell, BlockPos> entry : cache.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            FlowFieldCell cell = entry.getKey();
            BlockPos pos = entry.getValue();
            entryTag.putInt(TAG_CELL_X, cell.x());
            entryTag.putInt(TAG_CELL_Y, cell.y());
            entryTag.putInt(TAG_CELL_Z, cell.z());
            entryTag.putInt(TAG_POS_X, pos.getX());
            entryTag.putInt(TAG_POS_Y, pos.getY());
            entryTag.putInt(TAG_POS_Z, pos.getZ());
            list.add(entryTag);
        }
        return list;
    }

    private static Map<FlowFieldCell, BlockPos> loadHubCache(ListTag list) {
        Map<FlowFieldCell, BlockPos> cache = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            FlowFieldCell cell = new FlowFieldCell(
                entryTag.getInt(TAG_CELL_X),
                entryTag.getInt(TAG_CELL_Y),
                entryTag.getInt(TAG_CELL_Z)
            );
            BlockPos pos = new BlockPos(
                entryTag.getInt(TAG_POS_X),
                entryTag.getInt(TAG_POS_Y),
                entryTag.getInt(TAG_POS_Z)
            );
            cache.put(cell, pos);
        }
        return cache;
    }
}
