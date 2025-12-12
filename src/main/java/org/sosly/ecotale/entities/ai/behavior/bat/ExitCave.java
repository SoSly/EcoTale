package org.sosly.ecotale.entities.ai.behavior.bat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;
import org.sosly.ecotale.entities.ai.behavior.GraphNavigationBehavior;
import org.sosly.ecotale.navigation.Graph;

public class ExitCave extends GraphNavigationBehavior<EcoTaleBat> {
    public ExitCave() {
        super(MemoryModuleTypes.ROOST.get());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EcoTaleBat mob) {
        if (mob.level().canSeeSky(mob.blockPosition())) {
            return false;
        }
        return super.checkExtraStartConditions(level, mob);
    }

    @Override
    protected BlockPos getEntryPoint(Graph graph, EcoTaleBat mob) {
        return null;
    }

    @Override
    protected BlockPos getDestination(Graph graph) {
        return graph.getGraphExits().stream().findFirst().orElse(null);
    }
}
