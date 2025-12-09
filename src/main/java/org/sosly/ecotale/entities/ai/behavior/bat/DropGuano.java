package org.sosly.ecotale.entities.ai.behavior.bat;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.sosly.ecotale.entities.ai.AIConstants;
import org.sosly.ecotale.blocks.BlockRegistry;
import org.sosly.ecotale.entities.EcoTaleBat;

public class DropGuano extends Behavior<EcoTaleBat> {

    public DropGuano() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EcoTaleBat bat) {
        return bat.isSleeping();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EcoTaleBat bat, long gameTime) {
        return bat.isSleeping();
    }

    @Override
    protected void tick(ServerLevel level, EcoTaleBat bat, long gameTime) {
        RandomSource random = bat.getRandom();
        if (random.nextInt(AIConstants.TICKS_PER_DAY) != 0) {
            return;
        }

        BlockPos spawnPos = bat.blockPosition();
        BlockState currentState = level.getBlockState(spawnPos);
        if (!currentState.isAir()) {
            return;
        }

        BlockState guanoState = BlockRegistry.GUANO.get().defaultBlockState();
        level.setBlock(spawnPos, guanoState, Block.UPDATE_ALL);
    }
}
