package org.sosly.ecotale.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.api.generators.IBlockStateGenerating;
import org.sosly.ecotale.blocks.BlockRegistry;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, EcoTale.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        for (RegistryObject<Block> entry : BlockRegistry.BLOCKS.getEntries()) {
            Block block = entry.get();
            if (block instanceof IBlockStateGenerating generator) {
                generator.generateBlockState(this);
            }
        }
    }
}
