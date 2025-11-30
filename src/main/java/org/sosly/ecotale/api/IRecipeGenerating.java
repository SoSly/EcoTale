package org.sosly.ecotale.api;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;

import java.util.function.Consumer;

/**
 * Implemented by blocks and items that generate their own crafting recipes during datagen.
 *
 * <p>This interface enables self-contained recipe generation, keeping recipe logic colocated
 * with the block or item it applies to rather than scattered across a monolithic provider.</p>
 */
public interface IRecipeGenerating {
    /**
     * Generates recipes for this block or item.
     *
     * @param writer   the recipe consumer that accepts finished recipes
     * @param provider the recipe provider, available for utility methods
     */
    void generateRecipes(Consumer<FinishedRecipe> writer, RecipeProvider provider);
}
