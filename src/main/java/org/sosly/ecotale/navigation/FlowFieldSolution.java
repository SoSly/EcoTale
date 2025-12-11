package org.sosly.ecotale.navigation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
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
    private static final String TAG_START = "start";
    private static final String TAG_CELL_X = "cx";
    private static final String TAG_CELL_Y = "cy";
    private static final String TAG_CELL_Z = "cz";
    private static final String TAG_VEC_X = "vx";
    private static final String TAG_VEC_Y = "vy";
    private static final String TAG_VEC_Z = "vz";

    private final Map<FlowFieldCell, Vec3> outwardField;
    private final Map<FlowFieldCell, Vec3> inwardField;
    private final Map<FlowFieldCell, BlockPos> hubCache;
    private final BlockPos exitPoint;
    private final BlockPos roostPos;
    private final FlowFieldCell startCell;
    private final boolean failed;

    private FlowFieldSolution(
            Map<FlowFieldCell, Vec3> outwardField,
            Map<FlowFieldCell, Vec3> inwardField,
            Map<FlowFieldCell, BlockPos> hubCache,
            BlockPos exitPoint,
            BlockPos roostPos,
            FlowFieldCell startCell,
            boolean failed) {
        this.outwardField = outwardField;
        this.inwardField = inwardField;
        this.hubCache = hubCache;
        this.exitPoint = exitPoint;
        this.roostPos = roostPos;
        this.startCell = startCell;
        this.failed = failed;
    }

    public static FlowFieldSolution create(
            Map<FlowFieldCell, Vec3> outwardField,
            Map<FlowFieldCell, Vec3> inwardField,
            Map<FlowFieldCell, BlockPos> hubCache,
            BlockPos exitPoint,
            BlockPos roostPos,
            FlowFieldCell startCell) {
        return new FlowFieldSolution(
            new HashMap<>(outwardField),
            new HashMap<>(inwardField),
            new HashMap<>(hubCache),
            exitPoint,
            roostPos,
            startCell,
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
            null,
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

    public FlowFieldCell getStartCell() {
        return startCell;
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

        if (!FlowFieldValidators.isExitValid(level, exitPoint)) {
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

        if (!FlowFieldValidators.isHubValid(level, fromHub) || !FlowFieldValidators.isHubValid(level, toHub)) {
            return false;
        }

        return FlowFieldValidators.isCellTransitionValid(level, from, to, fromHub, toHub);
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
        tag.putLong(TAG_ROOST, roostPos.asLong());

        if (failed) {
            return tag;
        }

        tag.putLong(TAG_EXIT, exitPoint.asLong());

        tag.putInt(TAG_START + "X", startCell.x());
        tag.putInt(TAG_START + "Y", startCell.y());
        tag.putInt(TAG_START + "Z", startCell.z());

        tag.put(TAG_OUTWARD, saveVectorField(outwardField));
        tag.put(TAG_INWARD, saveVectorField(inwardField));
        tag.put(TAG_HUBS, saveHubCache(hubCache));

        return tag;
    }

    /**
     * Deserializes a solution from NBT.
     */
    public static FlowFieldSolution load(CompoundTag tag) {
        BlockPos roostPos = BlockPos.of(tag.getLong(TAG_ROOST));

        if (tag.getBoolean(TAG_FAILED)) {
            return failed(roostPos);
        }

        BlockPos exitPoint = BlockPos.of(tag.getLong(TAG_EXIT));

        FlowFieldCell startCell = new FlowFieldCell(
            tag.getInt(TAG_START + "X"),
            tag.getInt(TAG_START + "Y"),
            tag.getInt(TAG_START + "Z")
        );

        Map<FlowFieldCell, Vec3> outwardField = loadVectorField(tag.getList(TAG_OUTWARD, Tag.TAG_COMPOUND));
        Map<FlowFieldCell, Vec3> inwardField = loadVectorField(tag.getList(TAG_INWARD, Tag.TAG_COMPOUND));
        Map<FlowFieldCell, BlockPos> hubCache = loadHubCache(tag.getList(TAG_HUBS, Tag.TAG_COMPOUND));

        return new FlowFieldSolution(outwardField, inwardField, hubCache, exitPoint, roostPos, startCell, false);
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
            entryTag.putLong("pos", pos.asLong());
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
            BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
            cache.put(cell, pos);
        }
        return cache;
    }
}
