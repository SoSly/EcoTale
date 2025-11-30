package org.sosly.ecotale.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.blocks.BlockRegistry;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, EcoTale.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(BlockRegistry.STONE_ROOST.get())
                .add(BlockRegistry.DEEPSLATE_ROOST.get())
                .add(BlockRegistry.BLACKSTONE_ROOST.get())
                .add(BlockRegistry.BASALT_ROOST.get())
                .add(BlockRegistry.TUFF_ROOST.get())
                .add(BlockRegistry.CALCITE_ROOST.get())
                .add(BlockRegistry.DRIPSTONE_ROOST.get());
    }
}
