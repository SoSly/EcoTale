package org.sosly.ecotale.navigation;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.sosly.ecotale.blocks.RoostBlockEntity;

/**
 * Manages flow field generation requests.
 * Phase 3: synchronous generation on main thread.
 * Phase 5 will add threaded generation queue.
 */
public class FlowFieldManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static FlowFieldManager instance;

    private FlowFieldManager() {
    }

    public static FlowFieldManager getInstance() {
        if (instance == null) {
            instance = new FlowFieldManager();
        }
        return instance;
    }

    /**
     * Requests generation of a flow field for the given roost.
     * Currently runs synchronously on the calling thread.
     */
    public void requestGeneration(RoostBlockEntity roost) {
        Level level = roost.getLevel();
        if (level == null) {
            return;
        }

        BlockPos roostPos = roost.getBlockPos();
        long startTime = System.nanoTime();

        FlowFieldGenerator generator = new FlowFieldGenerator(roostPos, level);
        FlowFieldSolution solution = generator.generate();
        roost.setFlowFieldSolution(solution);

        long elapsed = System.nanoTime() - startTime;
        if (solution.isFailed()) {
            LOGGER.debug("FlowField generation FAILED at {}: {}ms",
                    roostPos, elapsed / 1_000_000.0);
        } else {
            LOGGER.debug("FlowField generation at {}: {}ms, {} outward cells, {} inward cells, exit at {}",
                    roostPos,
                    elapsed / 1_000_000.0,
                    solution.getOutwardCellCount(),
                    solution.getInwardCellCount(),
                    solution.getExitPoint());
        }
    }
}
