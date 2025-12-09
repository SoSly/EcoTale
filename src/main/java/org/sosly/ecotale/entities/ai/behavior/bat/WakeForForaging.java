package org.sosly.ecotale.entities.ai.behavior.bat;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import org.sosly.ecotale.entities.EcoTaleBat;

public class WakeForForaging extends Behavior<EcoTaleBat> {
    public WakeForForaging() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EcoTaleBat bat) {
        return bat.isResting();
    }

    @Override
    protected void start(ServerLevel level, EcoTaleBat bat, long gameTime) {
        bat.setResting(false);
    }
}
