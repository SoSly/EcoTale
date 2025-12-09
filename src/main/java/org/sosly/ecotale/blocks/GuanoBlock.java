package org.sosly.ecotale.blocks;

import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.registries.ForgeRegistries;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.api.generators.IBlockStateGenerating;
import org.sosly.ecotale.api.generators.IItemModelGenerating;
import org.sosly.ecotale.api.generators.ILootTableGenerating;
import org.sosly.ecotale.api.generators.IRecipeGenerating;
import org.sosly.ecotale.items.ItemRegistry;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class GuanoBlock extends FallingBlock
        implements IBlockStateGenerating, IItemModelGenerating, ILootTableGenerating, IRecipeGenerating {

    public GuanoBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void generateBlockState(BlockStateProvider provider) {
        String blockName = ForgeRegistries.BLOCKS.getKey(this).getPath();
        ResourceLocation texture = new ResourceLocation("minecraft", "block/mycelium_top");
        provider.simpleBlock(this, provider.models().cubeAll(blockName, texture));
    }

    @Override
    public void generateItemModel(ItemModelProvider provider) {
        String blockName = ForgeRegistries.BLOCKS.getKey(this).getPath();
        provider.withExistingParent(blockName, new ResourceLocation(EcoTale.MOD_ID, "block/" + blockName));
    }

    @Override
    public void generateLootTable(Block block, BiConsumer<Block, LootTable.Builder> register) {
        register.accept(block, LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(block))));
    }

    @Override
    public void generateRecipes(Consumer<FinishedRecipe> writer, RecipeProvider provider) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, this)
                .requires(ItemRegistry.GUANO.get(), 8)
                .unlockedBy("has_guano", InventoryChangeTrigger.TriggerInstance.hasItems(ItemRegistry.GUANO.get()))
                .save(writer, new ResourceLocation(EcoTale.MOD_ID, "guano_block_from_guano"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ItemRegistry.GUANO.get(), 8)
                .requires(this)
                .unlockedBy("has_guano_block", InventoryChangeTrigger.TriggerInstance.hasItems(this))
                .save(writer, new ResourceLocation(EcoTale.MOD_ID, "guano_from_guano_block"));
    }
}
