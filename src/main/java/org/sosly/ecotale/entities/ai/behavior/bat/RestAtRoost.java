package org.sosly.ecotale.entities.ai.behavior.bat;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.sosly.ecotale.entities.EcoTaleBat;

public class RestAtRoost extends Behavior<EcoTaleBat> {
    private static final int CLOSE_ENOUGH = 4;

    public RestAtRoost() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EcoTaleBat bat) {
        if (bat.isSleeping()) {
            return false;
        }

        GlobalPos roostPos = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        return roostPos != null && isNearRoost(level, bat, roostPos);
    }

    @Override
    protected void start(ServerLevel level, EcoTaleBat bat, long gameTime) {
        GlobalPos roostPos = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (roostPos == null) {
            return;
        }

        BlockPos hangPos = roostPos.pos().below();
        double offsetX = (bat.getRandom().nextDouble() - 0.5) * 0.8;
        double offsetZ = (bat.getRandom().nextDouble() - 0.5) * 0.8;
        bat.moveTo(hangPos.getX() + 0.5 + offsetX, hangPos.getY() + 0.5, hangPos.getZ() + 0.5 + offsetZ);
        bat.getNavigation().stop();

        if (!bat.isSleeping()) {
            bat.setResting(true);
        }
    }

    private boolean isNearRoost(ServerLevel level, EcoTaleBat bat, GlobalPos roostPos) {
        if (!roostPos.dimension().equals(level.dimension())) {
            return false;
        }

        BlockPos hangPos = roostPos.pos().below();
        return bat.blockPosition().closerThan(hangPos, CLOSE_ENOUGH);
    }
}
