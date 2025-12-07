package org.sosly.ecotale.navigation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
     * This is currently a stub.
     */
    public boolean isValid(Level level) {
        return true;
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
