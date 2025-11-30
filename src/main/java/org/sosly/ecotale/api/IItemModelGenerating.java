package org.sosly.ecotale.api;

import net.minecraftforge.client.model.generators.ItemModelProvider;

/**
 * Implemented by items and blocks that generate their own item models during datagen.
 *
 * <p>This interface enables self-contained item model generation, keeping model logic colocated
 * with the item or block it applies to rather than scattered across a monolithic provider.</p>
 */
public interface IItemModelGenerating {
    /**
     * Generates the item model for this item or block item.
     *
     * @param provider the item model provider with model builder utilities
     */
    void generateItemModel(ItemModelProvider provider);
}
