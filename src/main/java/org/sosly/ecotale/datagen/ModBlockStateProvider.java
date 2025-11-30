package org.sosly.ecotale.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.blocks.BlockRegistry;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, EcoTale.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        guanoLayerBlock(BlockRegistry.GUANO.get());
        simpleBlockWithExistingTexture(BlockRegistry.GUANO_BLOCK.get(), "minecraft:block/mycelium_top");
    }

    private void guanoLayerBlock(Block block) {
        String blockName = ForgeRegistries.BLOCKS.getKey(block).getPath();
        ResourceLocation texture = new ResourceLocation("minecraft", "block/mycelium_top");

        getVariantBuilder(block).forAllStates(state -> {
            int layers = state.getValue(BlockStateProperties.LAYERS);
            int height = layers * 2;

            ModelFile model;
            if (layers == 8) {
                model = models().cubeAll(blockName + "_height16", texture);
            } else {
                model = models().withExistingParent(blockName + "_height" + height, "minecraft:block/snow_height" + height)
                        .texture("texture", texture)
                        .texture("particle", texture);
            }
            return ConfiguredModel.builder().modelFile(model).build();
        });
    }

    private void simpleBlockWithExistingTexture(Block block, String texturePath) {
        String blockName = ForgeRegistries.BLOCKS.getKey(block).getPath();
        ResourceLocation texture = new ResourceLocation(texturePath);
        ModelFile model = models().cubeAll(blockName, texture);
        simpleBlock(block, model);
    }
}
