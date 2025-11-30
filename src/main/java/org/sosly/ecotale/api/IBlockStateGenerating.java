package org.sosly.ecotale.api;

import net.minecraftforge.client.model.generators.BlockStateProvider;

/**
 * Implemented by blocks that generate their own blockstate definitions and models during datagen.
 *
 * <p>This interface enables self-contained blockstate generation, keeping model and state logic
 * colocated with the block it applies to rather than scattered across a monolithic provider.</p>
 */
public interface IBlockStateGenerating {
    /**
     * Generates the blockstate definition and block model for this block.
     *
     * @param provider the blockstate provider with model builder utilities
     */
    void generateBlockState(BlockStateProvider provider);
}
