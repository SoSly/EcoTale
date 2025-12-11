package org.sosly.ecotale.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Represents a coarse grid cell for flow field navigation.
 * Each cell covers a 4x4x4 block region.
 */
public record FlowFieldCell(int x, int y, int z) {
    public static final int RESOLUTION = 4;

    public static FlowFieldCell fromBlockPos(BlockPos pos) {
        return new FlowFieldCell(
            Math.floorDiv(pos.getX(), RESOLUTION),
            Math.floorDiv(pos.getY(), RESOLUTION),
            Math.floorDiv(pos.getZ(), RESOLUTION)
        );
    }

    public static FlowFieldCell cellInDirection(FlowFieldCell cell, Vec3 direction) {
        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);
        double maxComponent = Math.max(absX, Math.max(absY, absZ));

        if (absX == maxComponent) {
            return new FlowFieldCell(cell.x() + (direction.x > 0 ? 1 : -1), cell.y(), cell.z());
        }
        if (absY == maxComponent) {
            return new FlowFieldCell(cell.x(), cell.y() + (direction.y > 0 ? 1 : -1), cell.z());
        }
        return new FlowFieldCell(cell.x(), cell.y(), cell.z() + (direction.z > 0 ? 1 : -1));
    }

    public BlockPos centerBlockPos() {
        return new BlockPos(
            x * RESOLUTION + RESOLUTION / 2,
            y * RESOLUTION + RESOLUTION / 2,
            z * RESOLUTION + RESOLUTION / 2
        );
    }

    public boolean contains(BlockPos position) {
        int minX = x * RESOLUTION;
        int minY = y * RESOLUTION;
        int minZ = z * RESOLUTION;

        return position.getX() >= minX && position.getX() < minX + RESOLUTION
            && position.getY() >= minY && position.getY() < minY + RESOLUTION
            && position.getZ() >= minZ && position.getZ() < minZ + RESOLUTION;
    }
}
