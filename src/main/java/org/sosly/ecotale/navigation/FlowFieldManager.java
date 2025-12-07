package org.sosly.ecotale.navigation;

import org.sosly.ecotale.blocks.RoostBlockEntity;

/**
 * Manages flow field generation requests.
 * Stub implementation for Phase 1; Phase 5 adds threaded generation queue.
 */
public class FlowFieldManager {
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
     * Currently a noop.
     */
    public void requestGeneration(RoostBlockEntity roost) {
    }
}
