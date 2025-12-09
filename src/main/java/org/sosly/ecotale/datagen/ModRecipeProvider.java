package org.sosly.ecotale.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.api.generators.IRecipeGenerating;
import org.sosly.ecotale.blocks.BlockRegistry;
import org.sosly.ecotale.items.ItemRegistry;

import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        for (RegistryObject<Block> entry : BlockRegistry.BLOCKS.getEntries()) {
            Block block = entry.get();
            if (block instanceof IRecipeGenerating generator) {
                generator.generateRecipes(writer, this);
            }
        }

        for (RegistryObject<Item> entry : ItemRegistry.ITEMS.getEntries()) {
            Item item = entry.get();
            if (item instanceof IRecipeGenerating generator) {
                generator.generateRecipes(writer, this);
            }
        }
    }
}
