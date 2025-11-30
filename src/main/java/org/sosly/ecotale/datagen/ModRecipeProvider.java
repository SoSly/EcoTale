package org.sosly.ecotale.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.sosly.ecotale.items.ItemRegistry;

import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.BROWN_DYE)
                .requires(ItemRegistry.GUANO.get())
                .unlockedBy("has_guano", has(ItemRegistry.GUANO.get()))
                .save(writer, "ecotale:brown_dye_from_guano");

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(ItemRegistry.GUANO.get()),
                        RecipeCategory.MISC,
                        Items.GUNPOWDER,
                        0.1F,
                        200)
                .unlockedBy("has_guano", has(ItemRegistry.GUANO.get()))
                .save(writer, "ecotale:gunpowder_from_guano");

        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ItemRegistry.GUANO_BLOCK.get())
                .requires(ItemRegistry.GUANO.get(), 8)
                .unlockedBy("has_guano", has(ItemRegistry.GUANO.get()))
                .save(writer, "ecotale:guano_block_from_guano");

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ItemRegistry.GUANO.get(), 8)
                .requires(ItemRegistry.GUANO_BLOCK.get())
                .unlockedBy("has_guano_block", has(ItemRegistry.GUANO_BLOCK.get()))
                .save(writer, "ecotale:guano_from_guano_block");
    }
}
