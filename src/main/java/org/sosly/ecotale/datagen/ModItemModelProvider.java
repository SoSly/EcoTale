package org.sosly.ecotale.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.api.IItemModelGenerating;
import org.sosly.ecotale.blocks.BlockRegistry;
import org.sosly.ecotale.items.ItemRegistry;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, EcoTale.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        for (RegistryObject<Block> entry : BlockRegistry.BLOCKS.getEntries()) {
            Block block = entry.get();
            if (block instanceof IItemModelGenerating generator) {
                generator.generateItemModel(this);
            }
        }

        for (RegistryObject<Item> entry : ItemRegistry.ITEMS.getEntries()) {
            Item item = entry.get();
            if (item instanceof IItemModelGenerating generator) {
                generator.generateItemModel(this);
            }
        }
    }
}
