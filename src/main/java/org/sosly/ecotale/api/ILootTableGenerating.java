package org.sosly.ecotale.api;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.function.BiConsumer;

/**
 * Implemented by blocks that generate their own loot tables during datagen.
 *
 * <p>This interface enables self-contained loot table generation, keeping loot logic colocated
 * with the block it applies to rather than scattered across a monolithic provider.</p>
 */
public interface ILootTableGenerating {
    /**
     * Generates the loot table for this block.
     *
     * @param block    the block instance to generate loot for
     * @param register a consumer that accepts a block and its loot table builder
     */
    void generateLootTable(Block block, BiConsumer<Block, LootTable.Builder> register);
}
