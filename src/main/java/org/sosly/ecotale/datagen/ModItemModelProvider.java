package org.sosly.ecotale.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.sosly.ecotale.EcoTale;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, EcoTale.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        withExistingParent("guano_block", new ResourceLocation(EcoTale.MOD_ID, "block/guano_block"));
    }
}
