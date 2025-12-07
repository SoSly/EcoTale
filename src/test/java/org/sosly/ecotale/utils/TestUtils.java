package org.sosly.ecotale.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import org.sosly.ecotale.blocks.AbstractRoostBlock;

public class TestUtils {
    public static BlockPos findRoostInStructure(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 12; y++) {
                for (int z = 0; z < 8; z++) {
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
