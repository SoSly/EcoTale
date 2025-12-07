package org.sosly.ecotale.navigation;

import net.minecraft.core.BlockPos;

/**
 * Represents a coarse grid cell for flow field navigation.
 * Each cell covers an 8x8x8 block region.
 */
public record FlowFieldCell(int x, int y, int z) {
    public static final int RESOLUTION = 8;

    public static FlowFieldCell fromBlockPos(BlockPos pos) {
        return new FlowFieldCell(
            Math.floorDiv(pos.getX(), RESOLUTION),
            Math.floorDiv(pos.getY(), RESOLUTION),
            Math.floorDiv(pos.getZ(), RESOLUTION)
        );
    }

    public BlockPos centerBlockPos() {
        return new BlockPos(
            x * RESOLUTION + RESOLUTION / 2,
            y * RESOLUTION + RESOLUTION / 2,
            z * RESOLUTION + RESOLUTION / 2
        );
    }
}
