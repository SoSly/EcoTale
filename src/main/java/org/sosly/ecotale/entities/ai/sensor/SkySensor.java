package org.sosly.ecotale.entities.ai.sensor;

import com.google.common.collect.ImmutableSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;

import java.util.Set;

public class SkySensor extends Sensor<EcoTaleBat> {
    @Override
    protected void doTick(ServerLevel level, EcoTaleBat bat) {
        boolean isOutside = bat.getBrain()
                .getMemory(MemoryModuleTypes.IS_OUTSIDE.get())
                .orElse(false);
        if (!isOutside) {
            return;
        }

        if (!level.canSeeSky(bat.blockPosition())) {
            bat.getBrain().eraseMemory(MemoryModuleTypes.IS_OUTSIDE.get());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleTypes.IS_OUTSIDE.get());
    }
}
