package org.sosly.ecotale.entities.ai.sensor;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.sosly.ecotale.blocks.AbstractRoostBlock;
import org.sosly.ecotale.entities.EcoTaleBat;

import java.util.Set;

public class HomeSensor extends Sensor<EcoTaleBat> {
    @Override
    protected void doTick(ServerLevel level, EcoTaleBat bat) {
        GlobalPos home = bat.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null) {
            return;
        }

        if (!home.dimension().equals(level.dimension())) {
            return;
        }

        if (!(level.getBlockState(home.pos()).getBlock() instanceof AbstractRoostBlock)) {
            bat.getBrain().eraseMemory(MemoryModuleType.HOME);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.HOME);
    }
}
