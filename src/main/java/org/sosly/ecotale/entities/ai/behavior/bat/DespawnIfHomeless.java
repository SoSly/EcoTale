package org.sosly.ecotale.entities.ai.behavior.bat;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.sosly.ecotale.entities.EcoTaleBat;

public class DespawnIfHomeless extends Behavior<EcoTaleBat> {
    public DespawnIfHomeless() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected void start(ServerLevel level, EcoTaleBat bat, long gameTime) {
        bat.discard();
    }
}
