package org.sosly.ecotale.items;

import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.api.generators.IRecipeGenerating;

import java.util.function.Consumer;

public class GuanoItem extends BlockItem implements IRecipeGenerating {
    public GuanoItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof CropBlock || block instanceof SaplingBlock) {
            if (BoneMealItem.applyBonemeal(context.getItemInHand(), level, pos, context.getPlayer())) {
                if (!level.isClientSide) {
                    level.levelEvent(1505, pos, 0);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useOn(context);
    }

    @Override
    public void generateRecipes(Consumer<FinishedRecipe> writer, RecipeProvider provider) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.BROWN_DYE)
                .requires(this)
                .unlockedBy("has_guano", InventoryChangeTrigger.TriggerInstance.hasItems(this))
                .save(writer, new ResourceLocation(EcoTale.MOD_ID, "brown_dye_from_guano"));

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(this),
                        RecipeCategory.MISC,
                        Items.GUNPOWDER,
                        0.1F,
                        200)
                .unlockedBy("has_guano", InventoryChangeTrigger.TriggerInstance.hasItems(this))
                .save(writer, new ResourceLocation(EcoTale.MOD_ID, "gunpowder_from_guano"));
    }
}
