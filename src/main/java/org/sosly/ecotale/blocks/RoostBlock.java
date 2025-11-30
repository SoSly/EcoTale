package org.sosly.ecotale.blocks;

import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.registries.ForgeRegistries;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.api.IBlockStateGenerating;
import org.sosly.ecotale.api.IItemModelGenerating;
import org.sosly.ecotale.api.ILootTableGenerating;
import org.sosly.ecotale.api.IRecipeGenerating;
import org.sosly.ecotale.items.ItemRegistry;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RoostBlock extends AbstractRoostBlock
        implements IBlockStateGenerating, IItemModelGenerating, ILootTableGenerating, IRecipeGenerating {

    private final Supplier<Block> sourceStone;
    private final String texturePath;

    public RoostBlock(Properties properties, Supplier<Block> sourceStone, String texturePath) {
        super(properties);
        this.sourceStone = sourceStone;
        this.texturePath = texturePath;
    }

    @Override
    public void generateBlockState(BlockStateProvider provider) {
        String blockName = ForgeRegistries.BLOCKS.getKey(this).getPath();
        ResourceLocation texture = new ResourceLocation(texturePath);
        provider.simpleBlock(this, provider.models().cubeAll(blockName, texture));
    }

    @Override
    public void generateItemModel(ItemModelProvider provider) {
        String blockName = ForgeRegistries.BLOCKS.getKey(this).getPath();
        provider.withExistingParent(blockName, new ResourceLocation(EcoTale.MOD_ID, "block/" + blockName));
    }

    @Override
    public void generateLootTable(Block block, BiConsumer<Block, LootTable.Builder> register) {
        Block source = sourceStone.get();
        Block normalDrop = getNormalDrop(source);

        if (normalDrop == source) {
            register.accept(block, LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1.0F))
                            .add(LootItem.lootTableItem(source))));
        } else {
            register.accept(block, LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1.0F))
                            .add(AlternativesEntry.alternatives(
                                    LootItem.lootTableItem(source)
                                            .when(MatchTool.toolMatches(ItemPredicate.Builder.item()
                                                    .hasEnchantment(new EnchantmentPredicate(
                                                            Enchantments.SILK_TOUCH,
                                                            MinMaxBounds.Ints.atLeast(1))))),
                                    LootItem.lootTableItem(normalDrop)))));
        }
    }

    private Block getNormalDrop(Block source) {
        if (source == Blocks.STONE) {
            return Blocks.COBBLESTONE;
        }
        if (source == Blocks.DEEPSLATE) {
            return Blocks.COBBLED_DEEPSLATE;
        }
        return source;
    }

    @Override
    public void generateRecipes(Consumer<FinishedRecipe> writer, RecipeProvider provider) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, this)
                .pattern("GGG")
                .pattern("GSG")
                .pattern("GGG")
                .define('G', ItemRegistry.GUANO.get())
                .define('S', sourceStone.get())
                .unlockedBy("has_guano", InventoryChangeTrigger.TriggerInstance.hasItems(ItemRegistry.GUANO.get()))
                .save(writer);
    }
}
