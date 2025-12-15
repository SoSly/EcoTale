package org.sosly.ecotale.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import org.sosly.ecotale.blocks.AbstractRoostBlock;

public class TestUtils {
    public static BlockPos findRoostInStructure(GameTestHelper helper) {
        return findRoostInStructure(helper, 48, 36, 48);
    }

    public static BlockPos findRoostInStructure(GameTestHelper helper, int maxX, int maxY, int maxZ) {
        for (int x = 0; x < maxX; x++) {
            for (int y = 0; y < maxY; y++) {
                for (int z = 0; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).getBlock() instanceof AbstractRoostBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
