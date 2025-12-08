package org.sosly.ecotale.entities.ai.behavior.bat;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.sosly.ecotale.entities.EcoTaleBat;

public class WakeIfRoostDistant extends Behavior<EcoTaleBat> {
    private static final int CLOSE_ENOUGH = 2;

    public WakeIfRoostDistant() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EcoTaleBat bat) {
        if (!bat.isResting()) {
            return false;
        }

        GlobalPos home = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null || !home.dimension().equals(level.dimension())) {
            return false;
        }

        BlockPos hangPos = home.pos().below();
        return !bat.blockPosition().closerThan(hangPos, CLOSE_ENOUGH);
    }

    @Override
    protected void start(ServerLevel level, EcoTaleBat bat, long gameTime) {
        bat.setResting(false);
    }
}
