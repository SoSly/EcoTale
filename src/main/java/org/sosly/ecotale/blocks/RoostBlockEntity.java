package org.sosly.ecotale.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RoostBlockEntity extends BlockEntity {
    public RoostBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.ROOST.get(), pos, state);
    }
}
